// Tests for firestore.rules on the LOCAL emulator only (never touches the real project).
//   npm run test:rules
// Needs: firebase-tools + @firebase/rules-unit-testing (devDependencies) and Java.
const fs = require('fs');
const path = require('path');
const test = require('node:test');
const {
  initializeTestEnvironment, assertSucceeds, assertFails,
} = require('@firebase/rules-unit-testing');
const { doc, getDoc, getDocs, setDoc, deleteDoc, collection, query, where } = require('firebase/firestore');

let env;
const A = 'userA';        // consumer
const B = 'userB';        // stranger
const CG = 'caregiverC';  // caregiver of A

test.before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-meduminder-rules',
    firestore: { rules: fs.readFileSync(path.join(__dirname, 'firestore.rules'), 'utf8') },
  });
});
test.after(async () => env && env.cleanup());
test.beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, 'medicine_catalog/off1'), { nama_obat: 'Paracetamol', created_at: new Date() });
    await setDoc(doc(db, `care_links/${A}_${CG}`), { consumer_uid: A, caregiver_uid: CG, created_at: new Date() });
    await setDoc(doc(db, `users/${A}/customMedicines/x1`), { name: 'Obh Combi', name_key: 'obh combi' });
    await setDoc(doc(db, 'medications/m1'), { users_id: A, catalog_id: 'off1' });
    await setDoc(doc(db, 'app_config/android'), { min_version_code: 2 });
  });
});

const as = (uid) => env.authenticatedContext(uid).firestore();
const guest = () => env.unauthenticatedContext().firestore();
const med = (name) => ({ name, name_key: name.toLowerCase(), created_at: new Date(), created_by: A });

test('catalog: signed-in users can read, nobody can change or delete', async () => {
  await assertSucceeds(getDoc(doc(as(A), 'medicine_catalog/off1')));
  await assertFails(getDoc(doc(guest(), 'medicine_catalog/off1')));
  await assertFails(setDoc(doc(as(A), 'medicine_catalog/off1'), { nama_obat: 'Hacked', created_at: new Date() }));
  await assertFails(deleteDoc(doc(as(A), 'medicine_catalog/off1')));
});

test('catalog transition: old app can still add a plain name, nothing else', async () => {
  await assertSucceeds(setDoc(doc(as(A), 'medicine_catalog/new1'), { nama_obat: 'Old App Med', created_at: new Date() }));
  await assertFails(setDoc(doc(as(A), 'medicine_catalog/new2'), { nama_obat: 'X', created_at: new Date(), extra: 1 }));
});

test('A adds a private medicine, B cannot see it', async () => {
  await assertSucceeds(setDoc(doc(as(A), `users/${A}/customMedicines/x2`), med('Sertraline')));
  await assertSucceeds(getDoc(doc(as(A), `users/${A}/customMedicines/x2`)));
  await assertFails(getDoc(doc(as(B), `users/${A}/customMedicines/x2`)));
  await assertFails(getDocs(collection(as(B), `users/${A}/customMedicines`)));
  await assertFails(setDoc(doc(as(B), `users/${A}/customMedicines/x3`), med('Evil')));
});

test('A and B both add the same name: each has their own copy', async () => {
  await assertSucceeds(setDoc(doc(as(A), `users/${A}/customMedicines/same`), med('Vitamin C')));
  await assertSucceeds(setDoc(doc(as(B), `users/${B}/customMedicines/same`), med('Vitamin C')));
  await assertFails(getDoc(doc(as(A), `users/${B}/customMedicines/same`)));
});

test('caregiver can read and add to the consumer list; not after the link is removed', async () => {
  await assertSucceeds(getDocs(collection(as(CG), `users/${A}/customMedicines`)));
  await assertSucceeds(setDoc(doc(as(CG), `users/${A}/customMedicines/x4`), med('Metformin')));
  await assertSucceeds(deleteDoc(doc(as(A), `care_links/${A}_${CG}`)));
  await assertFails(getDocs(collection(as(CG), `users/${A}/customMedicines`)));
});

test('private medicine: only allowed fields, sane name', async () => {
  await assertFails(setDoc(doc(as(A), `users/${A}/customMedicines/bad1`), { ...med('X'), owner_override: B }));
  await assertFails(setDoc(doc(as(A), `users/${A}/customMedicines/bad2`), { ...med(''), name: '' }));
});

test('care_links: only a party can create/read, id must match the two uids', async () => {
  await assertSucceeds(setDoc(doc(as(B), `care_links/${B}_${CG}`), { consumer_uid: B, caregiver_uid: CG, created_at: new Date() }));
  await assertFails(setDoc(doc(as(B), `care_links/${A}_${CG}`), { consumer_uid: A, caregiver_uid: CG, created_at: new Date() }));
  await assertFails(setDoc(doc(as(B), `care_links/wrongid`), { consumer_uid: B, caregiver_uid: CG, created_at: new Date() }));
  await assertFails(getDoc(doc(as(B), `care_links/${A}_${CG}`)));
});

test('existing collections keep today access (no breakage)', async () => {
  await assertSucceeds(getDoc(doc(as(B), 'medications/m1')));
  await assertSucceeds(setDoc(doc(as(A), 'medications/m2'), { users_id: A, custom_medicine_name: 'Obh Combi', custom_medicine_id: 'x1' }));
  await assertFails(getDoc(doc(guest(), 'medications/m1')));
});

test('app_config: anyone can read, nobody can write', async () => {
  await assertSucceeds(getDoc(doc(guest(), 'app_config/android')));
  await assertFails(setDoc(doc(as(A), 'app_config/android'), { min_version_code: 0 }));
});

test('app save path: same name saved twice with merge (create, then update)', async () => {
  const ref = doc(as(A), `users/${A}/customMedicines/merge1`);
  await assertSucceeds(setDoc(ref, { name: 'Formula 44', name_key: 'formula 44', created_at: new Date(), created_by: A }, { merge: true }));
  await assertSucceeds(setDoc(ref, { name: 'Formula 44', name_key: 'formula 44' }, { merge: true }));
});

test('account deletion queries: own private list and own care_links', async () => {
  await assertSucceeds(getDocs(collection(as(A), `users/${A}/customMedicines`)));
  await assertSucceeds(getDocs(query(collection(as(A), 'care_links'), where('consumer_uid', '==', A))));
  await assertSucceeds(getDocs(query(collection(as(CG), 'care_links'), where('caregiver_uid', '==', CG))));
  await assertFails(getDocs(collection(as(A), 'care_links')));
});

test('unknown collections are closed now (the catch-all rule is gone)', async () => {
  await assertFails(getDoc(doc(as(A), 'something_else/x')));
});
