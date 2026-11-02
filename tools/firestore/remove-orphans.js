#!/usr/bin/env node
/*
 * Delete the data of orphan uids (NO users/{uid} document).  DRY-RUN BY DEFAULT (changes nothing).
 *
 *   node remove-orphans.js --project meduminderv-1 --uids "uid1,uid2"                -> dry run (cheap: about 1 read per uid)
 *   node remove-orphans.js --project meduminderv-1 --uids "uid1,uid2" --verify       -> dry run that also re-reads each document
 *   node remove-orphans.js --project meduminderv-1 --uids "uid1,uid2" --apply        -> real run (asks you to type the project id)
 *
 * Deletes, per uid: notifications it received, medication logs, schedules, private medicines,
 * medications, care links and care relationships it is part of. Appointments and invitations are NOT deleted.
 *
 * Cheap on reads: the list of documents comes from the newest COMPLETE backup on disk (it must be newer
 * than orphan-report.json), using the same rules as the report (orphans-lib.js). Firestore is read only for:
 *  - users/{uid} of each uid and of other owners (is there a user document NOW?)
 *  - with --apply or --verify: each document it is about to delete, re-read, and skipped if it is
 *    already gone or no longer belongs to the orphan.
 * Documents added AFTER that backup are not deleted (run report-orphans.js + backup.js again to catch them).
 *
 * Safety:
 *  - every uid must be in orphan-report.json, and must STILL have no users/{uid} document;
 *  - a uid that still has a sign-in account (Firebase Authentication) is skipped unless --include-signed-in;
 *  - a schedule/log owned by someone who has a user document is never deleted;
 *  - needs a complete backup NEWER than orphan-report.json; --apply also needs you to type the project id.
 */
const fs = require('fs');
const path = require('path');
const readline = require('readline');
const admin = require('firebase-admin');
const { guardQuota, fatal } = require('./quota');
const { planOrphan, userDocChecker, DELETE_ORDER } = require('./orphans-lib');
const { checkBackup } = require('./backup-check');

const MAX_BATCH = 400;
const GET_CHUNK = 100;
const BACKUP_FILES = {
  medications: 'medications.json', medication_schedules: 'medication_schedules.json',
  medication_logs: 'medication_logs.json', notifications: 'notifications.json',
  care_links: 'care_links.json', care_relationships: 'care_relationships.json',
  customMedicines: 'users_customMedicines.json',
};
const NEEDED_IN_BACKUP = ['medications', 'medication_schedules', 'medication_logs', 'notifications',
  'care_relationships', 'care_links', 'users/*/customMedicines'];

function arg(name) {
  const i = process.argv.indexOf(name);
  return i >= 0 ? process.argv[i + 1] : null;
}

function ask(question) {
  const rl = readline.createInterface({ input: process.stdin, output: process.stdout });
  return new Promise((resolve) => rl.question(question, (a) => { rl.close(); resolve(a.trim()); }));
}

async function hasSignInAccount(uid) {
  try { await admin.auth().getUser(uid); return true; } catch (e) {
    if (e && e.code === 'auth/user-not-found') return false;
    return null; // could not check
  }
}

/** Does this LIVE document still belong to the orphan, by the same rules as planOrphan? */
function stillBelongs(col, data, uid, plan) {
  switch (col) {
    case 'medications': return data.users_id === uid;
    case 'notifications': return data.receiver_uid === uid;
    case 'care_links':
    case 'care_relationships': return data.consumer_uid === uid || data.caregiver_uid === uid;
    case 'customMedicines': return true; // its path is users/{uid}/customMedicines/..., checked by planOrphan
    case 'medication_schedules': return data.users_id === uid || plan.medIds.has(data.medication_id);
    case 'medication_logs': return data.users_id === uid || plan.scheduleIds.has(data.medication_schedules_id);
    default: return false;
  }
}

async function main() {
  const project = arg('--project');
  const uidsArg = arg('--uids');
  const apply = process.argv.includes('--apply');
  const verify = apply || process.argv.includes('--verify');
  const includeSignedIn = process.argv.includes('--include-signed-in');
  const keyPath = process.env.GOOGLE_APPLICATION_CREDENTIALS;
  if (!project || !uidsArg) { console.error('Usage: --project <id> --uids "uid1,uid2" [--verify] [--apply]'); process.exit(1); }
  if (!keyPath || !fs.existsSync(keyPath)) { console.error('Set GOOGLE_APPLICATION_CREDENTIALS to the key file.'); process.exit(1); }
  const key = JSON.parse(fs.readFileSync(keyPath, 'utf8'));
  if (key.project_id !== project) { console.error(`Key file is for "${key.project_id}", not "${project}". Stopping.`); process.exit(1); }
  const reportPath = path.join(__dirname, 'orphan-report.json');
  if (!fs.existsSync(reportPath)) { console.error('Missing orphan-report.json. Run report-orphans.js first.'); process.exit(1); }
  const report = JSON.parse(fs.readFileSync(reportPath, 'utf8'));
  if (report.project !== project) { console.error(`orphan-report.json is for "${report.project}". Stopping.`); process.exit(1); }

  const uids = [...new Set(uidsArg.split(',').map((u) => u.trim()).filter(Boolean))];
  const inReport = new Set(report.orphans.map((o) => o.uid));
  const notInReport = uids.filter((u) => !inReport.has(u));
  if (notInReport.length) { console.error(`Not in orphan-report.json: ${notInReport.join(', ')}. Stopping.`); process.exit(1); }

  console.log(`Project: ${project}`);
  console.log(apply ? 'MODE: APPLY (will delete)\n' : `MODE: DRY RUN (changes nothing)${verify ? ', with live re-read' : ''}\n`);
  const backup = checkBackup({ project, after: report.created_at, needed: NEEDED_IN_BACKUP, root: arg('--backups') });
  console.log(`Report:       ${report.created_at}`);
  console.log(`Backup check: ${backup.ok ? 'OK, ' : 'NOT OK, '}${backup.why}\n`);
  if (!backup.ok) {
    console.log('This script plans from the backup, so it needs a complete backup made AFTER the report.');
    console.log(`Run: node backup.js --project ${project} --for-orphans   (then this command again). Nothing changed.`);
    process.exit(1);
  }
  if (apply) {
    const typed = await ask(`Type the project id (${project}) to delete from it: `);
    if (typed !== project) { console.log('Not confirmed. Nothing changed.'); process.exit(1); }
  }

  // the backup on disk: no Firestore reads
  const all = {};
  for (const [col, file] of Object.entries(BACKUP_FILES)) {
    all[col] = JSON.parse(fs.readFileSync(path.join(backup.dir, file), 'utf8'));
  }

  admin.initializeApp({ credential: admin.credential.cert(key), projectId: project });
  const db = guardQuota(admin, admin.firestore());
  const hasUserDoc = userDocChecker(db);

  const phases = Object.fromEntries(DELETE_ORDER.map((c) => [c, new Map()])); // path -> ref (each once)
  const skipped = [];
  const warnings = [];
  let reads = 0;

  for (const uid of uids) {
    console.log(`=== ${uid} ===`);
    if (await hasUserDoc(uid)) { skipped.push(uid); console.log('  HAS a user document now -> never touched\n'); continue; }
    const signIn = await hasSignInAccount(uid);
    if (signIn !== false && !includeSignedIn) {
      skipped.push(uid);
      console.log(`  ${signIn ? 'still has a sign-in account' : 'sign-in account could not be checked'} -> skipped (add --include-signed-in to include)\n`);
      continue;
    }

    const { toDelete, info } = await planOrphan(all, uid, hasUserDoc);
    info.protected.forEach((p) => warnings.push(`${p}: has a user document, NOT deleted`));
    const plan = {
      medIds: new Set(toDelete.medications.map((m) => m.path.split('/').pop())),
      scheduleIds: new Set(toDelete.medication_schedules.map((s) => s.path.split('/').pop())),
    };

    for (const col of DELETE_ORDER) {
      let items = toDelete[col];
      let gone = 0, changed = 0;
      if (verify) {
        const kept = [];
        for (let i = 0; i < items.length; i += GET_CHUNK) {
          const chunk = items.slice(i, i + GET_CHUNK);
          const snaps = await db.getAll(...chunk.map((d) => db.doc(d.path)));
          reads += snaps.length;
          for (const snap of snaps) {
            if (!snap.exists) { gone++; continue; }
            const data = snap.data();
            const owner = data.users_id;
            const ownerOk = !(owner && owner !== uid && (await hasUserDoc(owner)));
            if (!stillBelongs(col, data, uid, plan) || !ownerOk) {
              changed++;
              warnings.push(`${snap.ref.path}: changed since the backup, NOT deleted`);
              continue;
            }
            kept.push({ path: snap.ref.path });
          }
        }
        items = kept;
      }
      items.forEach((d) => phases[col].set(d.path, db.doc(d.path)));
      const extra = verify ? `   (${gone} already gone${changed ? `, ${changed} changed` : ''})` : '';
      console.log(`  ${apply ? 'delete' : 'WOULD delete'} ${col.padEnd(22)} ${String(items.length).padStart(5)}${extra}`);
    }
    console.log('');
  }

  const total = DELETE_ORDER.reduce((n, c) => n + phases[c].size, 0);
  console.log(`TOTAL deletes: ${total}`);
  if (skipped.length) console.log(`Skipped uids: ${skipped.join(', ')}`);
  console.log(verify
    ? `Documents re-read live: ${reads}`
    : 'Planned from the backup (documents not re-read). --verify re-reads them; --apply always does.');
  if (warnings.length) { console.log('\nWarnings:'); warnings.forEach((w) => console.log('  ' + w)); }

  if (!apply) { console.log('\nDRY RUN: nothing was changed.'); return; }

  for (const col of DELETE_ORDER) {
    const refs = [...phases[col].values()];
    for (let i = 0; i < refs.length; i += MAX_BATCH) {
      const batch = db.batch();
      refs.slice(i, i + MAX_BATCH).forEach((r) => batch.delete(r));
      await batch.commit();
    }
    if (refs.length) console.log(`deleted ${col}: ${refs.length}`);
  }
  console.log('\nDone. Check with --verify (re-reads only these documents): it should find 0 to delete.');
}

main().catch(fatal);
