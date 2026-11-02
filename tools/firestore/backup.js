#!/usr/bin/env node
/*
 * READ-ONLY backup to JSON files on this computer. Only reads (get). Never writes to Firestore.
 *
 * Saves: medicine_catalog, medications, medication_schedules, medication_logs, notifications,
 *        care_relationships, care_links, users
 *        + every users/{uid}/customMedicines (private medicines).
 *
 * Usage:
 *   export GOOGLE_APPLICATION_CREDENTIALS=~/Keys/meduminder-key.json
 *   node backup.js --project meduminderv-1                 -> everything (about 2,500 reads today)
 *   node backup.js --project meduminderv-1 --for-orphans   -> without medicine_catalog (about 1,200 reads),
 *                                                             enough for remove-orphans.js
 *
 * Files go to ~/MedUMinder-backups/<date-time>/ (outside the project, so they are never committed).
 * Each file: [{ path, data }] with Firestore types kept (timestamps, references) so they can be restored.
 */
const fs = require('fs');
const os = require('os');
const path = require('path');
const admin = require('firebase-admin');
const { guardQuota, fatal } = require('./quota');

const ALL_COLLECTIONS = ['medicine_catalog', 'medications', 'medication_schedules', 'medication_logs', 'notifications',
  'care_relationships', 'care_links', 'users'];
// users is kept: it also saves every users/*/customMedicines
const FOR_ORPHANS = ALL_COLLECTIONS.filter((c) => c !== 'medicine_catalog');

function arg(name) {
  const i = process.argv.indexOf(name);
  return i >= 0 ? process.argv[i + 1] : null;
}

// keep Firestore types readable and restorable
function encode(value) {
  if (value === null || value === undefined) return value;
  if (value instanceof admin.firestore.Timestamp) {
    return { __type: 'timestamp', seconds: value.seconds, nanoseconds: value.nanoseconds, iso: value.toDate().toISOString() };
  }
  if (value instanceof admin.firestore.DocumentReference) return { __type: 'reference', path: value.path };
  if (value instanceof admin.firestore.GeoPoint) return { __type: 'geopoint', latitude: value.latitude, longitude: value.longitude };
  if (Buffer.isBuffer(value)) return { __type: 'bytes', base64: value.toString('base64') };
  if (Array.isArray(value)) return value.map(encode);
  if (typeof value === 'object') {
    const out = {};
    for (const [k, v] of Object.entries(value)) out[k] = encode(v);
    return out;
  }
  return value;
}

async function main() {
  const project = arg('--project');
  const keyPath = process.env.GOOGLE_APPLICATION_CREDENTIALS;
  if (!project) { console.error('Missing --project <firebase-project-id>'); process.exit(1); }
  if (!keyPath || !fs.existsSync(keyPath)) { console.error('Set GOOGLE_APPLICATION_CREDENTIALS to the key file.'); process.exit(1); }
  const key = JSON.parse(fs.readFileSync(keyPath, 'utf8'));
  if (key.project_id !== project) { console.error(`Key file is for "${key.project_id}", not "${project}". Stopping.`); process.exit(1); }

  admin.initializeApp({ credential: admin.credential.cert(key), projectId: project });
  const db = guardQuota(admin, admin.firestore());

  const COLLECTIONS = process.argv.includes('--for-orphans') ? FOR_ORPHANS : ALL_COLLECTIONS;
  const stamp = new Date().toISOString().replace(/[:.]/g, '-');
  const dir = path.join(os.homedir(), 'MedUMinder-backups', stamp);
  fs.mkdirSync(dir, { recursive: true });
  // removed at the end; a folder that still has it never finished (e.g. daily limit reached) and is never used
  const marker = path.join(dir, 'INCOMPLETE.txt');
  fs.writeFileSync(marker, 'This backup did not finish. Do not use it. Scripts ignore it (no summary.json).\n');
  console.log(`Project: ${project} (READ-ONLY backup)\nSaving to: ${dir}\n`);

  const summary = {};
  for (const name of COLLECTIONS) {
    const snap = await db.collection(name).get();
    const docs = snap.docs.map((d) => ({ path: d.ref.path, data: encode(d.data()) }));
    fs.writeFileSync(path.join(dir, `${name}.json`), JSON.stringify(docs, null, 1));
    summary[name] = docs.length;
    console.log(`  ${name}: ${docs.length} documents`);

    if (name === 'users') {
      // collection group: also finds private lists whose users/{uid} document does not exist
      const sub = await db.collectionGroup('customMedicines').get();
      const custom = sub.docs.map((d) => ({ path: d.ref.path, data: encode(d.data()) }));
      fs.writeFileSync(path.join(dir, 'users_customMedicines.json'), JSON.stringify(custom, null, 1));
      summary['users/*/customMedicines'] = custom.length;
      console.log(`  users/*/customMedicines: ${custom.length} documents`);
    }
  }

  fs.writeFileSync(path.join(dir, 'summary.json'), JSON.stringify({ project, created: new Date().toISOString(),
    collections: COLLECTIONS, counts: summary }, null, 1));
  fs.unlinkSync(marker);
  console.log(`\nDone. Check that the folder above has ${COLLECTIONS.length + 2} files.`);
}

main().catch(fatal);
