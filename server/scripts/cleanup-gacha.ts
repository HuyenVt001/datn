/* eslint-disable no-console */
/**
 * Don sach du lieu gacha cu tren Firestore (2026-11-15 — SHOP_PLAN.md muc S7).
 *
 * Chay thu (MAC DINH — chi dem, KHONG xoa):  npm run cleanup:gacha
 * Xoa that:                                  npm run cleanup:gacha -- --confirm
 * Yeu cau .env: FIREBASE_SERVICE_ACCOUNT.
 *
 * Xoa:
 *  1. Collection `gachaItems` (catalog quay cu)
 *  2. Collection `gachaRolls` (lich su quay)
 *  3. Field `gachaPity` tren moi user doc
 *  4. Dong so cai `astriteTransactions` type GACHA_SPEND / GACHA_REFUND
 *
 * GIU NGUYEN: so du `astrite` va vat pham da so huu (`unlockedFrames/Skins/Effects`).
 *
 * ⚠️ KHONG HOAN TAC DUOC. Chay `npm run seed:shop` TRUOC (seed doc gachaItems
 * de mang ten/anh sang shopItems).
 * ⚠️ Sau buoc 4, tai khoan tung quay gacha se co tong so cai ≠ so du (cac
 * khoan tru/hoan gacha bien mat khoi so cai) — user da chap nhan 2026-11-15.
 */
import 'dotenv/config';
import * as admin from 'firebase-admin';
import { existsSync, readFileSync } from 'fs';
import { resolve } from 'path';
import { Collections } from '../src/common/constants';

const LEGACY_COLLECTIONS = ['gachaItems', 'gachaRolls'];
const LEGACY_TX_TYPES = ['GACHA_SPEND', 'GACHA_REFUND'];
/** Firestore gioi han 500 thao tac / batch — chunk 450 cho an toan (luat server muc 6.2). */
const BATCH_SIZE = 450;

async function commitInChunks(
  db: FirebaseFirestore.Firestore,
  refs: FirebaseFirestore.DocumentReference[],
  op: (batch: FirebaseFirestore.WriteBatch, ref: FirebaseFirestore.DocumentReference) => void,
): Promise<void> {
  for (let i = 0; i < refs.length; i += BATCH_SIZE) {
    const batch = db.batch();
    refs.slice(i, i + BATCH_SIZE).forEach((ref) => op(batch, ref));
    await batch.commit();
  }
}

async function main(): Promise<void> {
  const confirm = process.argv.includes('--confirm');
  const saPath = resolve(
    process.cwd(),
    process.env.FIREBASE_SERVICE_ACCOUNT ?? 'firebase-service-account.json',
  );
  if (!existsSync(saPath)) {
    throw new Error(`Khong tim thay service account key: ${saPath}`);
  }
  admin.initializeApp({
    credential: admin.credential.cert(JSON.parse(readFileSync(saPath, 'utf8'))),
  });
  const db = admin.firestore();

  console.log(confirm ? '⚠️  CHE DO XOA THAT (--confirm)\n' : 'Che do CHAY THU — khong xoa gi.\n');

  // 1 + 2. Collection cu
  const collectionRefs: Record<string, FirebaseFirestore.DocumentReference[]> = {};
  for (const name of LEGACY_COLLECTIONS) {
    collectionRefs[name] = (await db.collection(name).select().get()).docs.map((d) => d.ref);
    console.log(`- ${name}: ${collectionRefs[name].length} doc`);
  }

  // 3. Field gachaPity tren user
  const users = await db.collection(Collections.USERS).get();
  const pityRefs = users.docs.filter((d) => d.data().gachaPity !== undefined).map((d) => d.ref);
  console.log(`- users co field gachaPity: ${pityRefs.length}`);

  // 4. Dong so cai gacha (`in` toi da 10 phan tu — o day chi 2)
  const txRefs = (
    await db
      .collection(Collections.ASTRITE_TRANSACTIONS)
      .where('type', 'in', LEGACY_TX_TYPES)
      .select()
      .get()
  ).docs.map((d) => d.ref);
  console.log(`- astriteTransactions GACHA_SPEND/GACHA_REFUND: ${txRefs.length}`);

  if (!confirm) {
    console.log('\nChay lai voi `npm run cleanup:gacha -- --confirm` de xoa that.');
    return;
  }

  for (const name of LEGACY_COLLECTIONS) {
    await commitInChunks(db, collectionRefs[name], (b, ref) => b.delete(ref));
  }
  await commitInChunks(db, pityRefs, (b, ref) =>
    b.update(ref, { gachaPity: admin.firestore.FieldValue.delete() }),
  );
  await commitInChunks(db, txRefs, (b, ref) => b.delete(ref));

  console.log('\n✔ Da don sach du lieu gacha.');
}

main()
  .then(() => process.exit(0))
  .catch((err) => {
    console.error(err);
    process.exit(1);
  });
