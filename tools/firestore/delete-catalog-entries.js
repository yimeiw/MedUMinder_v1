#!/usr/bin/env node
/*
 * LAST STEP, only after your approval: delete the user-added entries from medicine_catalog.
 * DRY-RUN BY DEFAULT (changes nothing).
 *
 *   node delete-catalog-entries.js --project meduminderv-1            -> dry run
 *   node delete-catalog-entries.js --project meduminderv-1 --apply    -> real run (asks you to type the project id)
 *
 * Safety: an entry is deleted ONLY if every medication that uses it already has its private name
 * (custom_medicine_name + custom_medicine_id, set by migrate-private-medicines.js). Otherwise it is skipped.
 * Only entries listed in private-candidates.json are ever touched; official entries never are.
 */
const fs = require('fs');
const path = require('path');
const readline = require('readline');
const admin = require('firebase-admin');
const { guardQuota, fatal } = require('./quota');

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
  const candidates = JSON.parse(fs.readFileSync(candidatesPath, 'utf8'));
  if (candidates.project !== project) { console.error(`Candidates file is for "${candidates.project}". Stopping.`); process.exit(1); }

  console.log(`Project: ${project}`);
  console.log(apply ? 'MODE: APPLY (will delete)\n' : 'MODE: DRY RUN (changes nothing)\n');
  if (apply) {
    const typed = await ask(`Type the project id (${project}) to delete from it: `);
    if (typed !== project) { console.log('Not confirmed. Nothing changed.'); process.exit(1); }
  }

  admin.initializeApp({ credential: admin.credential.cert(key), projectId: project });
  const db = guardQuota(admin, admin.firestore());

  let ok = 0, skipped = 0, gone = 0;
  for (const entry of candidates.entries) {
    const ref = db.collection('medicine_catalog').doc(entry.catalog_id);
    if (!(await ref.get()).exists) { gone++; console.log(`  already gone: "${entry.name}"`); continue; }
    const meds = await db.collection('medications').where('catalog_id', '==', entry.catalog_id).get();
    const notReady = meds.docs.filter((m) => !m.get('custom_medicine_name') || !m.get('custom_medicine_id'));
    if (notReady.length) {
      skipped++;
      console.log(`  SKIP "${entry.name}": ${notReady.length} medication(s) not migrated yet (run migrate-private-medicines.js)`);
      continue;
    }
    ok++;
    console.log(`  ${apply ? 'delete' : 'WOULD delete'} "${entry.name}" (${entry.catalog_id}), used by ${meds.size} migrated medication(s)`);
    if (apply) await ref.delete();
  }
  console.log(`\n${apply ? 'Deleted' : 'Would delete'}: ${ok}   skipped: ${skipped}   already gone: ${gone}`);
  if (!apply) console.log('DRY RUN: nothing was changed.');
}

main().catch(fatal);
