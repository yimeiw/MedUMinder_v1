// Shared by report-orphans.js and remove-orphans.js, so both always pick exactly the same documents.
// An orphan = a uid used in the data that has NO users/{uid} document.
//
// planOrphan() holds the rules. It works on plain {path, data} lists, so it gives the same answer for
// live Firestore data (report-orphans.js) and for a backup on disk (remove-orphans.js, no reads).

async function inChunks(query, field, values) {
  const out = [];
  for (let i = 0; i < values.length; i += 30) {
    const snap = await query.where(field, 'in', values.slice(i, i + 30)).get();
    out.push(...snap.docs);
  }
  return out;
}

const plain = (docs) => docs.map((d) => ({ path: d.ref.path, data: d.data() }));

function uniq(items) {
  const seen = new Map();
  items.forEach((d) => seen.set(d.path, d));
  return [...seen.values()];
}

// Firestore Timestamp, or a timestamp saved by backup.js ({__type:'timestamp', seconds})
function ms(v) {
  if (!v) return null;
  if (typeof v.toMillis === 'function') return v.toMillis();
  if (v.__type === 'timestamp') return v.seconds * 1000;
  return null;
}

// delete order: children first, so a stopped run can simply be started again
const DELETE_ORDER = ['notifications', 'medication_logs', 'medication_schedules', 'customMedicines',
  'medications', 'care_links', 'care_relationships'];

/**
 * The rules, on plain data. `all` = { medications, medication_schedules, medication_logs, notifications,
 * care_links, care_relationships, customMedicines } as lists of {path, data} (may contain more than this uid).
 * hasUserDoc(uid) -> Promise<boolean>, checked LIVE by the caller.
 */
async function planOrphan(all, uid, hasUserDoc) {
  const meds = all.medications.filter((d) => d.data.users_id === uid);
  const medIds = new Set(meds.map((m) => m.path.split('/').pop()));

  const schedules = uniq(all.medication_schedules.filter((d) => d.data.users_id === uid || medIds.has(d.data.medication_id)));
  const scheduleIds = new Set(schedules.map((s) => s.path.split('/').pop()));
  const logs = uniq(all.medication_logs.filter((d) => d.data.users_id === uid || scheduleIds.has(d.data.medication_schedules_id)));

  // only notifications RECEIVED by the orphan; ones received by people with an account stay
  const notifications = all.notifications.filter((d) => d.data.receiver_uid === uid);
  const refIds = new Set([...medIds, ...scheduleIds, ...logs.map((l) => l.path.split('/').pop())]);
  const othersNotifications = all.notifications.filter((d) => d.data.receiver_uid !== uid && refIds.has(d.data.reference_id)).length;

  const party = (d) => d.data.consumer_uid === uid || d.data.caregiver_uid === uid;
  const careLinks = uniq(all.care_links.filter(party));
  const careRelationships = uniq(all.care_relationships.filter(party));
  const customMedicines = all.customMedicines.filter((d) => d.path.startsWith(`users/${uid}/customMedicines/`));

  // never delete a schedule/log owned (users_id) by someone else who still has a user document
  const protectedDocs = [];
  const guard = async (items) => {
    const keep = [];
    for (const d of items) {
      const owner = d.data.users_id;
      if (owner && owner !== uid && (await hasUserDoc(owner))) { protectedDocs.push(`${d.path} (belongs to ${owner})`); continue; }
      keep.push(d);
    }
    return keep;
  };
  const safeSchedules = await guard(schedules);
  const safeLogs = await guard(logs);

  const now = Date.now();
  const active = safeSchedules.filter((s) => s.data.is_active !== false && !s.data.deleted_at
    && !(ms(s.data.end_date) && ms(s.data.end_date) < now));

  const partners = new Set();
  [...careLinks, ...careRelationships].forEach((d) => {
    [d.data.consumer_uid, d.data.caregiver_uid].forEach((p) => { if (p && p !== uid) partners.add(p); });
  });

  return {
    toDelete: { notifications, medication_logs: safeLogs, medication_schedules: safeSchedules,
      customMedicines, medications: meds, care_links: careLinks, care_relationships: careRelationships },
    info: { protected: protectedDocs, activeSchedules: active.length,
      alarmsPerDay: active.reduce((n, s) => n + (s.data.times_of_day || []).length, 0),
      othersNotifications, partners: [...partners],
      catalogIds: [...new Set(meds.map((m) => m.data.catalog_id).filter(Boolean))] },
  };
}

/**
 * Same rules, live: read only what can belong to this uid (queries), then run planOrphan on it.
 * Also counts appointments and invitations (never deleted, only shown).
 */
async function collectOrphan(db, uid, hasUserDoc) {
  const meds = plain((await db.collection('medications').where('users_id', '==', uid).get()).docs);
  const medIds = meds.map((m) => m.path.split('/').pop());
  const schedules = uniq([
    ...plain((await db.collection('medication_schedules').where('users_id', '==', uid).get()).docs),
    ...plain(await inChunks(db.collection('medication_schedules'), 'medication_id', medIds)),
  ]);
  const scheduleIds = schedules.map((s) => s.path.split('/').pop());
  const logs = uniq([
    ...plain((await db.collection('medication_logs').where('users_id', '==', uid).get()).docs),
    ...plain(await inChunks(db.collection('medication_logs'), 'medication_schedules_id', scheduleIds)),
  ]);
  const refIds = [...medIds, ...scheduleIds, ...logs.map((l) => l.path.split('/').pop())];
  const notifications = uniq([
    ...plain((await db.collection('notifications').where('receiver_uid', '==', uid).get()).docs),
    ...plain(await inChunks(db.collection('notifications'), 'reference_id', refIds)),
  ]);
  const both = async (col) => uniq([
    ...plain((await db.collection(col).where('consumer_uid', '==', uid).get()).docs),
    ...plain((await db.collection(col).where('caregiver_uid', '==', uid).get()).docs),
  ]);
  const all = {
    medications: meds, medication_schedules: schedules, medication_logs: logs, notifications,
    care_links: await both('care_links'), care_relationships: await both('care_relationships'),
    customMedicines: plain((await db.collection('users').doc(uid).collection('customMedicines').get()).docs),
  };
  const plan = await planOrphan(all, uid, hasUserDoc);

  plan.info.appointments = (await db.collection('appointments').where('users_id', '==', uid).get()).size;
  plan.info.invitations = uniq([
    ...plain((await db.collection('invitations').where('sender_uid', '==', uid).get()).docs),
    ...plain((await db.collection('invitations').where('receiver_uid', '==', uid).get()).docs),
  ]).length;
  return plan;
}

/** Every uid that appears in the data but has no users/{uid} document (reads whole collections). */
async function findOrphanUids(db, hasUserDoc) {
  const uids = new Set();
  const add = (v) => { if (typeof v === 'string' && v) uids.add(v); };
  for (const [col, fields] of [
    ['medications', ['users_id']], ['medication_schedules', ['users_id']], ['medication_logs', ['users_id']],
    ['appointments', ['users_id']], ['notifications', ['receiver_uid']],
    ['care_relationships', ['consumer_uid', 'caregiver_uid']], ['care_links', ['consumer_uid', 'caregiver_uid']],
    ['invitations', ['sender_uid', 'receiver_uid']],
  ]) {
    const snap = await db.collection(col).get();
    snap.docs.forEach((d) => fields.forEach((f) => add(d.get(f))));
  }
  const orphans = [];
  for (const uid of [...uids].sort()) if (!(await hasUserDoc(uid))) orphans.push(uid);
  return orphans;
}

/** hasUserDoc with a cache: one read per distinct uid. */
function userDocChecker(db) {
  const cache = {};
  return async (uid) => {
    if (!(uid in cache)) cache[uid] = (await db.collection('users').doc(uid).get()).exists;
    return cache[uid];
  };
}

module.exports = { planOrphan, collectOrphan, findOrphanUids, userDocChecker, DELETE_ORDER, ms };
