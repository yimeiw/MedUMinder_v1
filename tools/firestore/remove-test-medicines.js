#!/usr/bin/env node
/*
 * OPTION A: completely remove test medicines.  DRY-RUN BY DEFAULT (changes nothing).
 *
 *   node remove-test-medicines.js --project meduminderv-1            -> dry run: prints what it WOULD delete
 *   node remove-test-medicines.js --project meduminderv-1 --apply    -> real run (asks you to type the project id)
 *
 * Names come from option-a-delete.json (matched by NAME with the app's name rules, never by row number).
 * A catalog entry is only touched if it is ALSO on private-candidates.json (the non-official list from
 * scan-catalog.js). Official entries are never touched, even if they had the same name.
 *
 * For every medication using one of these entries it deletes, in this order (children first, so a
 * stopped run can simply be started again):
 *   notifications -> medication_logs -> medication_schedules -> private copy made by the migration
 *   -> medications -> the catalog entry.
 * It never creates anything (no private copy).
 * --apply refuses to run unless ~/MedUMinder-backups has a backup made AFTER the decision
 * (decided_at in option-a-delete.json) that includes schedules, logs and notifications.
 */
const fs = require('fs');
const os = require('os');
const path = require('path');
const readline = require('readline');
const admin = require('firebase-admin');
const { guardQuota, fatal } = require('./quota');
const { key: nameKey, docId } = require('./medicine-name');

const MAX_BATCH = 400;
const NEEDED_IN_BACKUP = ['medicine_catalog', 'medications', 'medication_schedules', 'medication_logs', 'notifications'];

function arg(name) {
  const i = process.argv.indexOf(name);
  return i >= 0 ? process.argv[i + 1] : null;
}

function ask(question) {
  const rl = readline.createInterface({ input: process.stdin, output: process.stdout });
  return new Promise((resolve) => rl.question(question, (a) => { rl.close(); resolve(a.trim()); }));
}

async function inChunks(query, field, values) {
  const out = [];
  for (let i = 0; i < values.length; i += 30) {
    const snap = await query.where(field, 'in', values.slice(i, i + 30)).get();
    out.push(...snap.docs);
  }
  return out;
}

/** Newest backup for this project made after decidedAt, containing everything we delete. */
function checkBackup(project, decidedAt) {
  const root = arg('--backups') || path.join(os.homedir(), 'MedUMinder-backups');
  if (!fs.existsSync(root)) return { ok: false, why: `no backup folder at ${root}` };
  const found = fs.readdirSync(root)
    .map((d) => path.join(root, d, 'summary.json'))
    .filter((f) => fs.existsSync(f))
    .map((f) => ({ dir: path.dirname(f), ...JSON.parse(fs.readFileSync(f, 'utf8')) }))
    .filter((b) => b.project === project)
    .sort((a, b) => Date.parse(b.created) - Date.parse(a.created));
  if (!found.length) return { ok: false, why: `no backup for ${project} in ${root}` };
  const newest = found[0];
  if (Date.parse(newest.created) <= Date.parse(decidedAt)) {
    return { ok: false, why: `newest backup (${newest.created}) is OLDER than the decision (${decidedAt})` };
  }
  const missing = NEEDED_IN_BACKUP.filter((c) => !(c in (newest.counts || {})));
  if (missing.length) return { ok: false, why: `newest backup has no ${missing.join(', ')} (old backup.js?)` };
  return { ok: true, why: `${newest.dir} (${newest.created})` };
}

async function main() {
  const project = arg('--project');
  const apply = process.argv.includes('--apply');
  const keyPath = process.env.GOOGLE_APPLICATION_CREDENTIALS;
  if (!project) { console.error('Missing --project <firebase-project-id>'); process.exit(1); }
  if (!keyPath || !fs.existsSync(keyPath)) { console.error('Set GOOGLE_APPLICATION_CREDENTIALS to the key file.'); process.exit(1); }
  const key = JSON.parse(fs.readFileSync(keyPath, 'utf8'));
  if (key.project_id !== project) { console.error(`Key file is for "${key.project_id}", not "${project}". Stopping.`); process.exit(1); }
  const decision = JSON.parse(fs.readFileSync(path.join(__dirname, 'option-a-delete.json'), 'utf8'));
  if (decision.project !== project) { console.error(`option-a-delete.json is for "${decision.project}". Stopping.`); process.exit(1); }
  const candidates = JSON.parse(fs.readFileSync(path.join(__dirname, 'private-candidates.json'), 'utf8'));
  if (candidates.project !== project) { console.error(`private-candidates.json is for "${candidates.project}". Stopping.`); process.exit(1); }

  console.log(`Project: ${project}`);
  console.log(apply ? 'MODE: APPLY (will delete)\n' : 'MODE: DRY RUN (changes nothing)\n');

  const backup = checkBackup(project, decision.decided_at);
  console.log(`Backup check: ${backup.ok ? 'OK, ' : 'NOT OK, '}${backup.why}\n`);
  if (apply && !backup.ok) { console.log('Run backup.js first. Nothing changed.'); process.exit(1); }
  if (apply) {
    const typed = await ask(`Type the project id (${project}) to delete from it: `);
    if (typed !== project) { console.log('Not confirmed. Nothing changed.'); process.exit(1); }
  }

  admin.initializeApp({ credential: admin.credential.cert(key), projectId: project });
  const db = guardQuota(admin, admin.firestore());

  const allowed = new Set(candidates.entries.map((e) => e.catalog_id));
  const wantedKeys = new Set(decision.names.map(nameKey));
  const catSnap = await db.collection('medicine_catalog').get();
  const emailCache = {};
  const emailOf = async (uid) => {
    if (!uid) return '(no owner)';
    if (!(uid in emailCache)) {
      const u = await db.collection('users').doc(uid).get();
      emailCache[uid] = u.exists ? (u.get('email') || '(no email)') : '(user document missing)';
    }
    return emailCache[uid];
  };

  // deletes grouped by phase; committed phase by phase, children before parents
  const phases = { notifications: [], logs: [], schedules: [], privateCopies: [], medications: [], catalog: [] };
  const plan = { project, created_at: new Date().toISOString(), entries: [] };
  const warnings = [];
  let alarmsPerDay = 0;

  for (const wanted of decision.names) {
    const matches = catSnap.docs.filter((d) => nameKey(d.get('nama_obat')) === nameKey(wanted));
    console.log(`=== "${wanted}" ===`);
    if (!matches.length) { console.log('  not in the catalog (already gone)\n'); continue; }
    for (const cat of matches) {
      if (!allowed.has(cat.id)) {
        console.log(`  SKIPPED catalog ${cat.id}: not on the non-official list (may be official), never touched`);
        continue;
      }
      // double check: the entry's own name must be one of the chosen names
      if (!wantedKeys.has(nameKey(cat.get('nama_obat')))) { warnings.push(`catalog ${cat.id} name changed, skipped`); continue; }

      const entryPlan = { catalog_id: cat.id, name: cat.get('nama_obat'), medications: [] };
      const meds = (await db.collection('medications').where('catalog_id', '==', cat.id).get()).docs;
      if (!meds.length) console.log('  not used by any medication -> only the catalog entry');
      for (const m of meds) {
        const owner = m.get('users_id');
        const schedules = (await db.collection('medication_schedules').where('medication_id', '==', m.id).get()).docs;
        const scheduleIds = schedules.map((s) => s.id);
        const logs = await inChunks(db.collection('medication_logs'), 'medication_schedules_id', scheduleIds);
        const notifs = await inChunks(db.collection('notifications'), 'reference_id', [m.id, ...scheduleIds, ...logs.map((l) => l.id)]);
        const active = schedules.filter((s) => s.get('is_active') !== false && !s.get('deleted_at')
          && !(s.get('end_date') && s.get('end_date').toMillis() < Date.now()));
        alarmsPerDay += active.reduce((n, s) => n + (s.get('times_of_day') || []).length, 0);

        // only a private copy that the MIGRATION made from this exact catalog entry
        let privateCopy = null;
        if (owner) {
          const privRef = db.collection('users').doc(owner).collection('customMedicines').doc(docId(cat.get('nama_obat')));
          const priv = await privRef.get();
          if (priv.exists && priv.get('migrated_from_catalog_id') === cat.id) privateCopy = privRef;
          else if (priv.exists) warnings.push(`${privRef.path} exists but was added in the app by the user, NOT deleted`);
        }

        console.log(`  medication ${m.id}  owner: ${await emailOf(owner)}`);
        console.log(`    ${apply ? 'delete' : 'WOULD delete'}: ${notifs.length} notifications, ${logs.length} logs, ` +
          `${schedules.length} schedules (${active.length} active), ${privateCopy ? 1 : 0} private copy, 1 medication`);

        notifs.forEach((d) => phases.notifications.push(d.ref));
        logs.forEach((d) => phases.logs.push(d.ref));
        schedules.forEach((d) => phases.schedules.push(d.ref));
        if (privateCopy) phases.privateCopies.push(privateCopy);
        phases.medications.push(m.ref);
        entryPlan.medications.push({ id: m.id, owner_uid: owner, schedule_ids: scheduleIds,
          log_count: logs.length, notification_count: notifs.length, private_copy: privateCopy ? privateCopy.path : null });
      }
      console.log(`  ${apply ? 'delete' : 'WOULD delete'}: catalog entry ${cat.id}`);
      phases.catalog.push(cat.ref);
      plan.entries.push(entryPlan);
    }
    console.log('');
  }

  // the same document can be found twice (e.g. a notification); delete each once
  for (const k of Object.keys(phases)) {
    const seen = new Set();
    phases[k] = phases[k].filter((r) => (seen.has(r.path) ? false : seen.add(r.path)));
  }
  const total = Object.values(phases).reduce((n, a) => n + a.length, 0);
  console.log('TOTAL');
  console.log(`  catalog entries: ${phases.catalog.length}`);
  console.log(`  medications:     ${phases.medications.length}`);
  console.log(`  schedules:       ${phases.schedules.length}`);
  console.log(`  logs:            ${phases.logs.length}`);
  console.log(`  notifications:   ${phases.notifications.length}`);
  console.log(`  private copies:  ${phases.privateCopies.length}`);
  console.log(`  total deletes:   ${total}`);
  console.log(`  phone alarms still set for these: ${alarmsPerDay} per day (cannot be deleted from here, see notes)`);
  if (warnings.length) { console.log('\nWarnings:'); warnings.forEach((w) => console.log('  ' + w)); }

  fs.writeFileSync(path.join(__dirname, 'option-a-plan.json'), JSON.stringify(plan, null, 1));

  if (!apply) { console.log('\nDRY RUN: nothing was changed.'); return; }

  for (const [phase, refs] of Object.entries(phases)) {
    for (let i = 0; i < refs.length; i += MAX_BATCH) {
      const batch = db.batch();
      refs.slice(i, i + MAX_BATCH).forEach((r) => batch.delete(r));
      await batch.commit();
    }
    if (refs.length) console.log(`deleted ${phase}: ${refs.length}`);
  }
  console.log('\nDone. Run the dry run again: it should find nothing left to delete.');
}

main().catch(fatal);
