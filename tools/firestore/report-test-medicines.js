#!/usr/bin/env node
/*
 * READ-ONLY report before removing test medicines. Only reads (get). Never writes.
 *
 *   node report-test-medicines.js --project meduminderv-1        (names from option-a-delete.json)
 *   node report-test-medicines.js --project meduminderv-1 --names "Tes124,Tes2g1x"
 *
 * Matches catalog entries by NAME (same name rules as the app), and ONLY entries that are also in
 * private-candidates.json (the non-official list from scan-catalog.js). Official entries are never included.
 * For each medication using them: owner + email, and how many schedules, logs, notifications and
 * phone alarms depend on it. Saves the exact list to test-medicines-report.json (used by the cleanup script).
 */
const fs = require('fs');
const path = require('path');
const admin = require('firebase-admin');
const { guardQuota, fatal } = require('./quota');
const { key: nameKey } = require('./medicine-name');

const TESTY = /(^|[^a-z])(test|tes|testing|dummy|coba|asdf|qwer|sample|contoh|demo)([^a-z]|\d|$)|@(example\.(com|org)|test\.com|mailinator\.com|yopmail\.com)$/i;

function arg(name) {
  const i = process.argv.indexOf(name);
  return i >= 0 ? process.argv[i + 1] : null;
}

async function inChunks(query, field, values) {
  const out = [];
  for (let i = 0; i < values.length; i += 30) {
    const snap = await query.where(field, 'in', values.slice(i, i + 30)).get();
    out.push(...snap.docs);
  }
  return out;
}

async function main() {
  const project = arg('--project');
  const optionAPath = path.join(__dirname, 'option-a-delete.json');
  const namesArg = arg('--names') || (fs.existsSync(optionAPath) ? JSON.parse(fs.readFileSync(optionAPath, 'utf8')).names.join(',') : null);
  const keyPath = process.env.GOOGLE_APPLICATION_CREDENTIALS;
  if (!project || !namesArg) { console.error('Usage: --project <id> --names "Name 1,Name 2"'); process.exit(1); }
  if (!keyPath || !fs.existsSync(keyPath)) { console.error('Set GOOGLE_APPLICATION_CREDENTIALS to the key file.'); process.exit(1); }
  const key = JSON.parse(fs.readFileSync(keyPath, 'utf8'));
  if (key.project_id !== project) { console.error(`Key file is for "${key.project_id}", not "${project}". Stopping.`); process.exit(1); }
  const candidates = JSON.parse(fs.readFileSync(path.join(__dirname, 'private-candidates.json'), 'utf8'));
  if (candidates.project !== project) { console.error(`private-candidates.json is for "${candidates.project}". Stopping.`); process.exit(1); }

  admin.initializeApp({ credential: admin.credential.cert(key), projectId: project });
  const db = guardQuota(admin, admin.firestore());
  const now = Date.now();
  console.log(`Project: ${project} (READ-ONLY)\n`);

  const allowed = new Set(candidates.entries.map((e) => e.catalog_id));
  const names = namesArg.split(',').map((n) => n.trim()).filter(Boolean);
  const catSnap = await db.collection('medicine_catalog').get();
  const userCache = {};
  const user = async (uid) => {
    if (!uid) return { email: '(none)', name: '' };
    if (!userCache[uid]) {
      const u = await db.collection('users').doc(uid).get();
      userCache[uid] = u.exists ? { email: u.get('email') || '(no email)', name: u.get('name') || '' } : { email: '(user document missing)', name: '' };
    }
    return userCache[uid];
  };

  const report = { project, created_at: new Date().toISOString(), names, entries: [] };
  const totals = { meds: 0, schedules: 0, activeSchedules: 0, logs: 0, notifications: 0, alarmsPerDay: 0 };
  const owners = {};

  for (const wanted of names) {
    const matches = catSnap.docs.filter((d) => nameKey(d.get('nama_obat')) === nameKey(wanted));
    const usable = matches.filter((d) => allowed.has(d.id));
    const blocked = matches.filter((d) => !allowed.has(d.id));
    console.log(`=== "${wanted}" ===`);
    if (!matches.length) { console.log('  not in the catalog (already gone?)\n'); continue; }
    blocked.forEach((d) => console.log(`  SKIPPED ${d.id}: not on the non-official list (could be official), will never be touched`));

    for (const cat of usable) {
      const entry = { catalog_id: cat.id, name: cat.get('nama_obat'), medications: [] };
      const meds = (await db.collection('medications').where('catalog_id', '==', cat.id).get()).docs;
      if (!meds.length) console.log(`  catalog ${cat.id}: not used by any medication -> only the catalog entry`);
      for (const m of meds) {
        const ownerUid = m.get('users_id');
        const owner = await user(ownerUid);
        const creatorUid = m.get('created_by');
        const creator = creatorUid && creatorUid !== ownerUid ? await user(creatorUid) : null;

        const schedules = (await db.collection('medication_schedules').where('medication_id', '==', m.id).get()).docs;
        const scheduleIds = schedules.map((s) => s.id);
        const logs = await inChunks(db.collection('medication_logs'), 'medication_schedules_id', scheduleIds);
        const logIds = logs.map((l) => l.id);
        const notifs = await inChunks(db.collection('notifications'), 'reference_id', [m.id, ...scheduleIds, ...logIds]);
        const active = schedules.filter((s) => s.get('is_active') !== false && !s.get('deleted_at')
          && !(s.get('end_date') && s.get('end_date').toMillis() < now));
        const alarmsPerDay = active.reduce((n, s) => n + ((s.get('times_of_day') || []).length), 0);

        console.log(`  medication ${m.id}`);
        console.log(`    owner:   ${owner.email}  ${owner.name ? `(${owner.name})` : ''}  uid=${ownerUid}`);
        if (creator) console.log(`    added by: ${creator.email} (caregiver?)  uid=${creatorUid}`);
        console.log(`    schedules: ${schedules.length} (${active.length} still active)   logs: ${logs.length}   notifications: ${notifs.length}   alarms on owner's phone: ${alarmsPerDay} per day`);

        entry.medications.push({ id: m.id, owner_uid: ownerUid, owner_email: owner.email,
          schedule_ids: scheduleIds, log_ids: logIds, notification_ids: notifs.map((n) => n.id), alarms_per_day: alarmsPerDay });
        owners[ownerUid] = owners[ownerUid] || { email: owner.email, name: owner.name, meds: new Set() };
        owners[ownerUid].meds.add(m.id);
        totals.meds++; totals.schedules += schedules.length; totals.activeSchedules += active.length;
        totals.logs += logs.length; totals.notifications += notifs.length; totals.alarmsPerDay += alarmsPerDay;
      }
      report.entries.push(entry);
    }
    console.log('');
  }

  console.log('=== OWNERS: test account or real user? ===');
  for (const [uid, o] of Object.entries(owners)) {
    const allMeds = (await db.collection('medications').where('users_id', '==', uid).get()).docs;
    const others = allMeds.filter((d) => !o.meds.has(d.id));
    const otherActive = others.filter((d) => d.get('is_active') !== false && !d.get('deleted_at'));
    const testyEmail = TESTY.test(o.email) || TESTY.test(o.name);
    const verdict = testyEmail && otherActive.length === 0 ? 'looks like a TEST account'
      : testyEmail ? 'test-like email, but has other active medicines: CHECK'
        : otherActive.length > 0 ? 'MAY BE A REAL USER: CHECK before deleting'
          : 'normal-looking email, no other active medicines: CHECK';
    console.log(`  ${o.email} ${o.name ? `(${o.name})` : ''}`);
    console.log(`    medicines on this list: ${o.meds.size}   other medicines: ${others.length} (${otherActive.length} active)   -> ${verdict}`);
  }

  console.log(`\nTOTAL that option A would delete: ${report.entries.length} catalog entries, ${totals.meds} medications, ` +
    `${totals.schedules} schedules (${totals.activeSchedules} active), ${totals.logs} logs, ${totals.notifications} notifications`);
  console.log(`Phone alarms depending on them: ${totals.alarmsPerDay} per day (alarms live on the owners' phones, see notes)`);

  const out = path.join(__dirname, 'test-medicines-report.json');
  fs.writeFileSync(out, JSON.stringify(report, null, 1));
  console.log(`\nSaved: ${out}`);
}

main().catch(fatal);
