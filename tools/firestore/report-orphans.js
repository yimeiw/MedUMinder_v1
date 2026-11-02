#!/usr/bin/env node
/*
 * READ-ONLY report of orphan uids (used in the data, but NO users/{uid} document). Never writes to Firestore.
 *
 *   node report-orphans.js --project meduminderv-1
 *
 * Prints one table per orphan uid and saves orphan-report.json (the exact uid list for remove-orphans.js).
 */
const fs = require('fs');
const path = require('path');
const admin = require('firebase-admin');
const { guardQuota, fatal } = require('./quota');
const { findOrphanUids, collectOrphan, userDocChecker, DELETE_ORDER } = require('./orphans-lib');

function arg(name) {
  const i = process.argv.indexOf(name);
  return i >= 0 ? process.argv[i + 1] : null;
}

async function authStatus(uid) {
  try {
    const u = await admin.auth().getUser(uid);
    const last = u.metadata && u.metadata.lastSignInTime;
    return { exists: true, text: `STILL HAS a sign-in account${u.email ? ` (${u.email})` : ''}${last ? `, last sign-in ${last}` : ''}` };
  } catch (e) {
    if (e && e.code === 'auth/user-not-found') return { exists: false, text: 'no sign-in account (deleted)' };
    return { exists: null, text: `could not check sign-in account (${e && e.code ? e.code : e})` };
  }
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
  console.log(`Project: ${project} (READ-ONLY)\n`);

  const hasUserDoc = userDocChecker(db);
  const uids = await findOrphanUids(db, hasUserDoc);
  if (!uids.length) { console.log('No orphan uids found.'); return; }

  const emailOf = async (uid) => {
    const u = await db.collection('users').doc(uid).get();
    return u.exists ? (u.get('email') || uid) : `${uid} (also no user document)`;
  };
  const catalogName = async (id) => {
    const c = await db.collection('medicine_catalog').doc(id).get();
    return c.exists ? c.get('nama_obat') : null;
  };

  const report = { project, created_at: new Date().toISOString(), orphans: [] };
  for (const uid of uids) {
    const { toDelete, info } = await collectOrphan(db, uid, hasUserDoc);
    const auth = await authStatus(uid);
    const names = (await Promise.all(info.catalogIds.map(catalogName))).filter(Boolean).sort();

    console.log(`=== Orphan ${uid} ===`);
    console.log(`  sign-in account: ${auth.text}`);
    console.log('  Would delete:');
    DELETE_ORDER.forEach((c) => console.log(`    ${c.padEnd(22)} ${String(toDelete[c].length).padStart(5)}`));
    const total = DELETE_ORDER.reduce((n, c) => n + toDelete[c].length, 0);
    console.log(`    ${'TOTAL'.padEnd(22)} ${String(total).padStart(5)}`);
    console.log(`  Schedules still active: ${info.activeSchedules}  (alarms per day on whatever phone set them: ${info.alarmsPerDay})`);
    console.log('  Kept (not deleted, only counted):');
    console.log(`    appointments           ${String(info.appointments).padStart(5)}`);
    console.log(`    invitations            ${String(info.invitations).padStart(5)}`);
    console.log(`    notifications received by OTHER people about this uid's medicines: ${info.othersNotifications}`);
    if (info.partners.length) {
      console.log('  Linked (caregiver) with (only the link is deleted, never the other person):');
      for (const p of info.partners) console.log(`    ${await emailOf(p)}`);
    }
    if (info.protected.length) {
      console.log('  Protected (points to this uid, but belongs to someone with a user document, NOT deleted):');
      info.protected.forEach((p) => console.log(`    ${p}`));
    }
    if (names.length) console.log(`  Catalog entries its medications use (still in the catalog): ${names.join(', ')}`);
    console.log('');

    report.orphans.push({ uid, auth_account: auth.exists, counts: Object.fromEntries(DELETE_ORDER.map((c) => [c, toDelete[c].length])),
      active_schedules: info.activeSchedules });
  }

  const out = path.join(__dirname, 'orphan-report.json');
  fs.writeFileSync(out, JSON.stringify(report, null, 1));
  console.log(`Orphan uids: ${uids.length}`);
  console.log(`Saved: ${out}`);
  console.log('Next: run backup.js (the cleanup needs a backup NEWER than this report).');
}

main().catch(fatal);
