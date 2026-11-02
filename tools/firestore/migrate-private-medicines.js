#!/usr/bin/env node
/*
 * Make user-added catalog medicines private.  DRY-RUN BY DEFAULT (changes nothing).
 *
 *   node migrate-private-medicines.js --project meduminderv-1            -> dry run: prints what it WOULD do
 *   node migrate-private-medicines.js --project meduminderv-1 --apply    -> real run (asks you to type the project id)
 *
 * Input: private-candidates.json from scan-catalog.js (exactly the entries in the table you approved).
 *
 * What it does (only ADDS data; never deletes, never renames, never changes ids):
 *   1. care_links/{consumer}_{caregiver}: one per caregiver relationship, if missing.
 *   2. For every medication using a candidate catalog entry:
 *        - users/{owner}/customMedicines/{id-from-name}: created if missing (owner = medication.users_id)
 *        - medication.custom_medicine_name / custom_medicine_id: filled in if empty
 *      catalog_id stays as it is, so older app versions still show the name.
 * Owners whose users/{uid} document is missing (orphans) are skipped and listed; nothing of theirs changes.
 * Safe to run twice: everything already done is skipped, so no duplicates.
 * Deleting the catalog entries is a SEPARATE script (delete-catalog-entries.js), later.
 */
const fs = require('fs');
const path = require('path');
const readline = require('readline');
const admin = require('firebase-admin');
const { guardQuota, fatal } = require('./quota');
const { key: nameKey, docId } = require('./medicine-name');

const MAX_BATCH = 400;

function arg(name) {
  const i = process.argv.indexOf(name);
  return i >= 0 ? process.argv[i + 1] : null;
}

function ask(question) {
  const rl = readline.createInterface({ input: process.stdin, output: process.stdout });
  return new Promise((resolve) => rl.question(question, (a) => { rl.close(); resolve(a.trim()); }));
}

async function main() {
  const project = arg('--project');
  const apply = process.argv.includes('--apply');
  const candidatesPath = arg('--candidates') || path.join(__dirname, 'private-candidates.json');
  const keyPath = process.env.GOOGLE_APPLICATION_CREDENTIALS;

  if (!project) { console.error('Missing --project <firebase-project-id>'); process.exit(1); }
  if (!keyPath || !fs.existsSync(keyPath)) { console.error('Set GOOGLE_APPLICATION_CREDENTIALS to the key file.'); process.exit(1); }
  const key = JSON.parse(fs.readFileSync(keyPath, 'utf8'));
  if (key.project_id !== project) { console.error(`Key file is for "${key.project_id}", not "${project}". Stopping.`); process.exit(1); }
  if (!fs.existsSync(candidatesPath)) { console.error(`Missing ${candidatesPath}. Run scan-catalog.js first.`); process.exit(1); }
  const candidates = JSON.parse(fs.readFileSync(candidatesPath, 'utf8'));
  if (candidates.project !== project) { console.error(`Candidates file is for "${candidates.project}". Stopping.`); process.exit(1); }

  console.log(`Project: ${project}`);
  console.log(apply ? 'MODE: APPLY (will write)\n' : 'MODE: DRY RUN (changes nothing)\n');
  if (apply) {
    const typed = await ask(`Type the project id (${project}) to write to it: `);
    if (typed !== project) { console.log('Not confirmed. Nothing changed.'); process.exit(1); }
  }

  admin.initializeApp({ credential: admin.credential.cert(key), projectId: project });
  const db = guardQuota(admin, admin.firestore());

  // collect writes, then commit in small batches (only when --apply)
  const writes = [];
  const counts = { linksNew: 0, linksDone: 0, privateNew: 0, privateDone: 0, medsUpdated: 0, medsDone: 0 };
  const warnings = [];

  // orphan owners: users/{uid} document is missing (account gone). Left alone, never migrated.
  const userExists = {};
  const isOrphan = async (uid) => {
    if (!(uid in userExists)) userExists[uid] = (await db.collection('users').doc(uid).get()).exists;
    return !userExists[uid];
  };
  const orphans = {};
  const skipOrphan = (uid, what) => { (orphans[uid] = orphans[uid] || []).push(what); };

  // ---- 1. care_links ----
  const rels = await db.collection('care_relationships').get();
  const seenLinks = new Set();
  for (const r of rels.docs) {
    const c = r.get('consumer_uid');
    const g = r.get('caregiver_uid');
    if (!c || !g) { warnings.push(`care_relationships/${r.id}: missing consumer_uid or caregiver_uid, skipped`); continue; }
    const id = `${c}_${g}`;
    if (seenLinks.has(id)) continue; // same pair twice in care_relationships
    seenLinks.add(id);
    if (await isOrphan(c)) { skipOrphan(c, `care link ${id}`); continue; }
    if (await isOrphan(g)) { skipOrphan(g, `care link ${id}`); continue; }
    const ref = db.collection('care_links').doc(id);
    if ((await ref.get()).exists) { counts.linksDone++; continue; }
    counts.linksNew++;
    writes.push({ op: 'set', ref, data: { consumer_uid: c, caregiver_uid: g, created_at: r.get('created_at') || admin.firestore.Timestamp.now(), migrated: true },
      say: `create care_links/${id}` });
  }

  // ---- 2. private copies + medication fields ----
  // names chosen for complete deletion (option A) never get a private copy
  const optionAPath = path.join(__dirname, 'option-a-delete.json');
  const deleteKeys = new Set(fs.existsSync(optionAPath)
    ? JSON.parse(fs.readFileSync(optionAPath, 'utf8')).names.map(nameKey) : []);
  const plannedPrivate = new Set();
  for (const entry of candidates.entries) {
    if (deleteKeys.has(nameKey(entry.name))) {
      console.log(`"${entry.name}"  skipped: chosen for deletion (option-a-delete.json), no private copy`);
      continue;
    }
    const catRef = db.collection('medicine_catalog').doc(entry.catalog_id);
    const cat = await catRef.get();
    if (!cat.exists) { warnings.push(`"${entry.name}" (${entry.catalog_id}) is no longer in the catalog, skipped`); continue; }
    const name = cat.get('nama_obat');
    if (!name || !nameKey(name)) { warnings.push(`catalog ${entry.catalog_id} has no name, skipped`); continue; }
    const privateId = docId(name);

    const meds = await db.collection('medications').where('catalog_id', '==', entry.catalog_id).get();
    console.log(`"${name}"  (${meds.size} medication${meds.size === 1 ? '' : 's'})`);
    for (const m of meds.docs) {
      const owner = m.get('users_id');
      if (!owner) { warnings.push(`medications/${m.id} ("${name}") has no users_id, skipped`); continue; }
      if (await isOrphan(owner)) { skipOrphan(owner, `medications/${m.id} ("${name}")`); continue; }

      const privRef = db.collection('users').doc(owner).collection('customMedicines').doc(privateId);
      const privPath = privRef.path;
      if (!plannedPrivate.has(privPath)) {
        plannedPrivate.add(privPath);
        if ((await privRef.get()).exists) {
          counts.privateDone++;
        } else {
          counts.privateNew++;
          writes.push({ op: 'set', ref: privRef, say: `  create ${privPath}`, data: {
            name, name_key: nameKey(name),
            created_at: cat.get('created_at') || admin.firestore.Timestamp.now(),
            created_by: m.get('created_by') || owner,
            migrated_from_catalog_id: entry.catalog_id,
          } });
        }
      }

      const update = {};
      const existingName = m.get('custom_medicine_name');
      if (!existingName) update.custom_medicine_name = name;
      else if (nameKey(existingName) !== nameKey(name)) {
        warnings.push(`medications/${m.id}: already has custom name "${existingName}" (catalog says "${name}"), name left as is`);
      }
      if (!m.get('custom_medicine_id')) update.custom_medicine_id = privateId;
      if (Object.keys(update).length === 0) { counts.medsDone++; continue; }
      counts.medsUpdated++;
      writes.push({ op: 'update', ref: m.ref, data: update, say: `  update medications/${m.id} (owner ${owner}): ${Object.keys(update).join(', ')}` });
    }
  }

  // ---- report ----
  console.log('');
  writes.forEach((w) => console.log((apply ? '' : 'WOULD ') + w.say));
  console.log(`\ncare_links:          ${counts.linksNew} to create, ${counts.linksDone} already there`);
  console.log(`private medicines:   ${counts.privateNew} to create, ${counts.privateDone} already there`);
  console.log(`medications:         ${counts.medsUpdated} to update, ${counts.medsDone} already done`);
  console.log(`total writes:        ${writes.length}`);
  for (const [uid, items] of Object.entries(orphans)) {
    console.log(`\nSkipped orphan owner ${uid} (users document missing), left untouched:`);
    items.forEach((i) => console.log(`  ${i}`));
  }
  if (warnings.length) { console.log('\nWarnings:'); warnings.forEach((w) => console.log('  ' + w)); }

  if (!apply) { console.log('\nDRY RUN: nothing was changed.'); return; }

  for (let i = 0; i < writes.length; i += MAX_BATCH) {
    const batch = db.batch();
    writes.slice(i, i + MAX_BATCH).forEach((w) => (w.op === 'set' ? batch.set(w.ref, w.data) : batch.update(w.ref, w.data)));
    await batch.commit();
    console.log(`committed ${Math.min(i + MAX_BATCH, writes.length)}/${writes.length}`);
  }
  console.log('\nDone. Run the dry run again: it should say 0 to create / 0 to update.');
}

main().catch(fatal);
