#!/usr/bin/env node
/*
 * READ-ONLY scan of medicine_catalog.
 * Only reads (get). Never writes, updates, or deletes anything.
 *
 * Usage:
 *   export GOOGLE_APPLICATION_CREDENTIALS=/path/OUTSIDE/the/repo/service-account.json
 *   node scan-catalog.js --project <firebase-project-id>
 *
 * The --project value must match the project inside the key file, otherwise the script stops.
 * Output: a summary in the terminal + catalog-scan.csv in this folder.
 */
const fs = require('fs');
const path = require('path');
const admin = require('firebase-admin');
const { guardQuota, fatal } = require('./quota');
const { key: nameKey } = require('./medicine-name');

// non-official entries from the first scan (2026-10-07), to name the ones deleted since then
const FIRST_SCAN = {
  Al1d4sBgNoIGYNLjZnMG: 'D3', IUaqupHVZpvIudyZaHOE: 'Artes', JzUDQCStWujMLcJ0wRSS: 'Vitamin C',
  KwD5A92TVn591syaG8lS: 'Tes153', NV9blHACSNQGVdz9TKEj: 'Hi Verin', OBO4oOLm8nUM2EBP5dtS: 'Obh Combi',
  RVtpWeXmEAMdiCwxDXbj: 'Obat Abc', S3pI1vC3i5aTfApbpLpk: 'Lo Han Kuo', ThHQwZwZWod3QVt8J0Lq: 'Obat Batuk Ibu Dan Anak',
  WrMgpq28RxYrAsqcGpuO: 'Tesobatcair', XkNyxNkFnQOMsRbbMg2D: 'Saya Ibel', YMZBLu6geXXMm3LtXihO: 'Sirup',
  Yv7bPpa32JOBDGLOZrNA: 'Metformin', ZLKMa4rvgjStKKEUTwba: 'Y123', b88nIENa08z4cNRS52yu: 'Tesi',
  bwCGRbnYbYpU5UowaKHx: 'Woi Ibel Minum Obat Co', eeaTH7cyZTkixeYGvoQt: 'Sertraline', jhMYx0TWemeKi28Heg8N: 'Asdfg',
  jxPEWKMxzrqSaUbVwUAJ: 'Y', o2MmnJfkhpjkeqPvVcRZ: 'Obat Baru Xyz', r0C2BY2YqzaXmhIJhDkl: 'Tesenddatenull',
  sNvgyW4b9TnHxN5Nk8LA: 'Formula 44', tbb9OIYiRsuGsL1jXMUB: 'Hi', uYk7ZwRuE3dixjF6ontL: 'Te',
  uaoMhkuoqBCWfOKmlkPm: 'Obat Penghilang Rasa Sakit, Batuk, Tenggerokan, Flu, Remam, Sakit Kepala',
  uvyPbGx52VvaM32GmSeq: 'Clofr', wK1cxPKAFg666b0bQPeD: 'Hivtes', wxLt8bEfA8lVkPoqXBJh: 'Clofritis',
  xzUkUgbgX4kgbDhagL40: 'Nama Obat Cair', ymzza1EbH9dFgwE5Rbn4: 'Tes124', zyQYA4ICDQWlMwf7PubN: 'Tes2g1x',
};

const BULK_WINDOW_MS = 60 * 1000;   // docs created within the same minute...
const BULK_MIN_SIZE = 5;            // ...in a group of 5+ = bulk import (official)
const LINK_WINDOW_MS = 5 * 60 * 1000; // app creates the catalog doc right before the medication doc

function arg(name) {
  const i = process.argv.indexOf(name);
  return i >= 0 ? process.argv[i + 1] : null;
}

function norm(name) {
  return nameKey(name);
}

function ms(ts) {
  if (!ts) return null;
  if (typeof ts.toMillis === 'function') return ts.toMillis();
  if (ts instanceof Date) return ts.getTime();
  return null;
}

function looksLikeJunk(name) {
  const n = norm(name);
  if (n.length < 3) return true;
  if (/^[\d\s.,-]+$/.test(n)) return true;          // only numbers
  if (/(.)\1{3,}/.test(n)) return true;             // aaaa
  if (!/[aeiou]/.test(n)) return true;              // no vowels: "xkcd", "qwrt"
  if (/\b(test|tes|coba|asdf|qwer|dummy|sample|contoh)\b/.test(n)) return true;
  return false;
}

function csv(v) {
  const s = v === null || v === undefined ? '' : String(v);
  return /[",\n]/.test(s) ? '"' + s.replace(/"/g, '""') + '"' : s;
}

async function main() {
  const project = arg('--project');
  if (!project) {
    console.error('Missing --project <firebase-project-id>');
    process.exit(1);
  }
  const keyPath = process.env.GOOGLE_APPLICATION_CREDENTIALS;
  if (!keyPath || !fs.existsSync(keyPath)) {
    console.error('Set GOOGLE_APPLICATION_CREDENTIALS to the service account key file.');
    process.exit(1);
  }
  const key = JSON.parse(fs.readFileSync(keyPath, 'utf8'));
  if (key.project_id !== project) {
    console.error(`Key file is for "${key.project_id}", not "${project}". Stopping.`);
    process.exit(1);
  }
  admin.initializeApp({ credential: admin.credential.cert(key), projectId: project });
  const db = guardQuota(admin, admin.firestore());
  console.log(`Project: ${project} (READ-ONLY)\n`);

  const [catSnap, medSnap] = await Promise.all([
    db.collection('medicine_catalog').get(),
    db.collection('medications').get(),
  ]);

  // which fields exist on catalog docs (to see if any creator field exists)
  const fieldCount = {};
  const cats = catSnap.docs.map((d) => {
    const data = d.data();
    Object.keys(data).forEach((k) => (fieldCount[k] = (fieldCount[k] || 0) + 1));
    return { id: d.id, name: data.nama_obat, createdMs: ms(data.created_at), data };
  });

  const meds = medSnap.docs.map((d) => {
    const m = d.data();
    return {
      id: d.id,
      catalogId: m.catalog_id || null,
      usersId: m.users_id || null,
      createdBy: m.created_by || null,
      createdMs: ms(m.created_at),
      deleted: !!m.deleted_at,
      active: m.is_active !== false && !m.deleted_at,
    };
  });
  const medsByCatalog = {};
  meds.forEach((m) => {
    if (!m.catalogId) return;
    (medsByCatalog[m.catalogId] = medsByCatalog[m.catalogId] || []).push(m);
  });

  // bulk groups: catalog docs created close together
  const sorted = cats.filter((c) => c.createdMs !== null).sort((a, b) => a.createdMs - b.createdMs);
  const bulk = new Set();
  let start = 0;
  for (let i = 0; i <= sorted.length; i++) {
    const breakHere = i === sorted.length || sorted[i].createdMs - sorted[i - 1 >= 0 ? i - 1 : 0].createdMs > BULK_WINDOW_MS;
    if (breakHere) {
      if (i - start >= BULK_MIN_SIZE) for (let j = start; j < i; j++) bulk.add(sorted[j].id);
      start = i;
    }
  }

  // duplicate names (same name after lowercase/trim)
  const byNorm = {};
  cats.forEach((c) => (byNorm[norm(c.name)] = (byNorm[norm(c.name)] || 0) + 1));

  const creatorFields = ['createdBy', 'created_by', 'userId', 'user_id', 'users_id', 'owner', 'ownerId', 'uid'];
  const rows = cats.map((c) => {
    const refs = medsByCatalog[c.id] || [];
    const users = [...new Set(refs.map((r) => r.usersId).filter(Boolean))];
    const creatorField = creatorFields.find((f) => c.data[f]);
    // medication made by the same action that created this catalog doc
    const linked = c.createdMs === null ? null : refs
      .filter((r) => r.createdMs !== null && r.createdMs >= c.createdMs && r.createdMs - c.createdMs <= LINK_WINDOW_MS)
      .sort((a, b) => a.createdMs - b.createdMs)[0];

    let cls, owner = '', ownerFor = '';
    if (creatorField) {
      cls = 'USER_ADDED (creator field)';
      owner = c.data[creatorField];
    } else if (bulk.has(c.id) || c.createdMs === null) {
      cls = 'OFFICIAL';
    } else if (linked) {
      cls = 'USER_ADDED (owner known)';
      owner = linked.createdBy || '';
      ownerFor = linked.usersId || '';
    } else {
      cls = 'POSSIBLY_USER_ADDED (owner unknown)';
    }

    const flags = [];
    if (!cls.startsWith('OFFICIAL')) {
      flags.push(looksLikeJunk(c.name) ? 'looks like test/junk' : 'RISKY: looks like a real medicine name');
    }
    if (users.length > 1) flags.push(`used by ${users.length} users`);
    if (byNorm[norm(c.name)] > 1) flags.push('duplicate name in catalog');
    if (refs.length === 0) flags.push('not used by any medication');

    const official = cats.find((o) => o.id !== c.id && bulk.has(o.id) && norm(o.name) === norm(c.name));
    return {
      id: c.id, name: c.name, created: c.createdMs ? new Date(c.createdMs).toISOString() : '',
      cls, owner, ownerFor, uses: refs.length, users: users.length, flags: flags.join('; '),
      noOwner: refs.filter((r) => !r.usersId).length,
      sameAsOfficial: official ? official.name : null,
    };
  });

  const catIds = new Set(cats.map((c) => c.id));
  const nonOfficial = rows.filter((r) => r.cls !== 'OFFICIAL').sort((a, b) => norm(a.name).localeCompare(norm(b.name)));
  const official = rows.length - nonOfficial.length;

  console.log('Fields found on catalog docs:');
  Object.entries(fieldCount).forEach(([k, n]) => console.log(`  ${k}: ${n}/${cats.length}`));
  console.log(creatorFields.some((f) => fieldCount[f]) ? '  -> a creator field EXISTS' : '  -> NO creator field (no createdBy/userId)');

  console.log(`\nCatalog entries: ${cats.length} total = ${official} official + ${nonOfficial.length} non-official`);

  // ---- the one final table ----
  console.log('\nNON-OFFICIAL ENTRIES (all will become private; none stay in the official catalog)');
  const W = Math.min(40, Math.max(4, ...nonOfficial.map((r) => String(r.name).length)));
  const line = (a, b, c, d, e) => `${String(a).padStart(3)}  ${String(b).padEnd(W).slice(0, W)}  ${String(c).padStart(5)}  ${String(d).padStart(11)}  ${e}`;
  console.log(line('#', 'Name', 'Users', 'Medications', 'Status'));
  console.log(line('---', '-'.repeat(W), '-----', '-----------', '-'.repeat(30)));
  nonOfficial.forEach((r, i) => {
    let status = r.users > 0
      ? `Copy to ${r.users === 1 ? "1 user's private list" : `${r.users} users' private lists`}, then delete from catalog`
      : 'Not used: only delete from catalog';
    if (r.noOwner) status += ` (WARNING: ${r.noOwner} medication(s) without owner)`;
    if (r.sameAsOfficial) status += ` (same name as official "${r.sameAsOfficial}")`;
    console.log(line(i + 1, r.name, r.users, r.uses, status));
  });
  const sumUsers = nonOfficial.reduce((n, r) => n + r.users, 0);
  const sumMeds = nonOfficial.reduce((n, r) => n + r.uses, 0);
  console.log(`\nTotals: ${nonOfficial.length} entries, ${nonOfficial.filter((r) => r.users > 0).length} used / ${nonOfficial.filter((r) => r.users === 0).length} unused, ${sumMeds} medications, ${sumUsers} private copies to create (one per entry per user)`);
  if (nonOfficial.some((r) => String(r.name).length > W)) console.log(`(Long names are cut at ${W} characters here; the CSV has them in full.)`);

  // ---- medications pointing to a catalog entry that no longer exists ----
  const broken = meds.filter((m) => m.catalogId && !catIds.has(m.catalogId));
  const brokenIds = [...new Set(broken.map((m) => m.catalogId))];
  console.log(`\nMedications pointing to a catalog entry that no longer exists: ${broken.length}` +
    ` (${broken.filter((m) => m.active).length} active, ${broken.length - broken.filter((m) => m.active).length} inactive/deleted),` +
    ` across ${brokenIds.length} missing entr${brokenIds.length === 1 ? 'y' : 'ies'}`);
  brokenIds.map((id) => ({ id, n: broken.filter((m) => m.catalogId === id).length, users: new Set(broken.filter((m) => m.catalogId === id).map((m) => m.usersId)).size }))
    .sort((a, b) => b.n - a.n)
    .forEach((b) => console.log(`  ${b.id}  medications=${b.n} users=${b.users}  ${FIRST_SCAN[b.id] ? `was "${FIRST_SCAN[b.id]}" (deleted after the first scan)` : '(deleted before the first scan, name unknown)'}`));
  console.log(`  Medications already using custom_medicine_name: ${medSnap.docs.filter((d) => d.get('custom_medicine_name')).length}`);

  // ---- files ----
  const out = path.join(__dirname, 'catalog-scan.csv');
  const header = 'id,name,created_at,class,owner_created_by,owner_users_id,medications_using,users_using,flags';
  fs.writeFileSync(out, [header, ...rows.map((r) =>
    [r.id, r.name, r.created, r.cls, r.owner, r.ownerFor, r.uses, r.users, r.flags].map(csv).join(','))].join('\n'));
  const candidates = path.join(__dirname, 'private-candidates.json');
  fs.writeFileSync(candidates, JSON.stringify({
    project, scanned_at: new Date().toISOString(),
    entries: nonOfficial.map((r) => ({ catalog_id: r.id, name: r.name, users: r.users, medications: r.uses })),
  }, null, 1));
  console.log(`\nFull list: ${out}\nMigration input (exactly the table above): ${candidates}`);
}

main().catch(fatal);
