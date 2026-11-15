/* eslint-disable no-console */
/**
 * Seed mat hang Shop (`shopItems`) + chuan hoa khung GACHA / QUEST_RANDOM -> SHOP.
 * (2026-11-15 — SHOP_PLAN.md, thay `seed:gacha`.)
 *
 * Chay:  npm run seed:shop
 * Yeu cau .env: FIREBASE_SERVICE_ACCOUNT.
 *
 * IDEMPOTENT — khoa theo (itemType, refId): chay lai khong tao ban sao, KHONG
 * sua mat hang da co (giu nguyen gia admin da chinh). Chay lai an toan sau khi
 * them skin / hieu ung moi vao app.
 *
 * Mang du lieu tu `gachaItems` cu (neu con): `itemName`, `imageUrl`, `isActive`,
 * `sortOrder` admin da chinh truoc day duoc giu lai. ⚠️ Chay script nay TRUOC
 * `cleanup:gacha` (cleanup xoa `gachaItems`).
 */
import 'dotenv/config';
import * as admin from 'firebase-admin';
import { existsSync, readFileSync } from 'fs';
import { resolve } from 'path';
import { Collections } from '../src/common/constants';
import { DEFAULT_PRICE_BY_TYPE, ItemType } from '../src/shop/entities/shop-item.entity';

/** Collection cu — chi doc de mang metadata sang (cleanup:gacha se xoa). */
const LEGACY_GACHA_ITEMS = 'gachaItems';

interface SeedItem {
  itemName: string;
  itemType: ItemType;
  refId: string;
  sortOrder: number;
  imageUrl?: string;
  isActive?: boolean;
}

/** Skin bundled trong APK — khop SkinRegistry (Snapget/.claude/SKIN_PLAN.md muc 0.2). */
const SKINS: SeedItem[] = [
  { itemName: 'Snow', itemType: 'SKIN', refId: '1', sortOrder: 10 },
  { itemName: 'Forest', itemType: 'SKIN', refId: '2', sortOrder: 11 },
];

/** Hieu ung touch bundled trong APK — khop TouchEffectRegistry (toi da 10). */
const EFFECTS: SeedItem[] = [
  { itemName: 'Flower', itemType: 'EFFECT', refId: '1', sortOrder: 20 },
  { itemName: 'Snowflake', itemType: 'EFFECT', refId: '2', sortOrder: 21 },
  { itemName: 'Leaf', itemType: 'EFFECT', refId: '3', sortOrder: 22 },
  { itemName: 'Magic', itemType: 'EFFECT', refId: '4', sortOrder: 23 },
];

const keyOf = (itemType: string, refId: unknown) => `${itemType}:${String(refId)}`;

async function main(): Promise<void> {
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
  const shopCol = db.collection(Collections.SHOP_ITEMS);
  const framesCol = db.collection(Collections.FRAMES);

  // ==== 1. Khung anh: chuan hoa GACHA / QUEST_RANDOM / doc khong co unlockType -> SHOP ====
  // PHAI suy y HET FramesRepository.toEntity, neu khong se co khung server coi
  // la SHOP nhung khong co mat hang -> khong bao gio mua duoc.
  const frames = await framesCol.get();
  const frameItems: SeedItem[] = [];
  let converted = 0;

  for (const doc of frames.docs) {
    const data = doc.data();
    const raw = data.unlockType as string | undefined;
    const legacyMilestone = typeof data.milestone === 'number' ? data.milestone : undefined;
    const unlockType =
      raw === 'QUEST_RANDOM' || raw === 'GACHA'
        ? 'SHOP'
        : (raw ?? (legacyMilestone ? 'STREAK_MILESTONE' : 'SHOP'));

    if (raw !== unlockType) {
      await doc.ref.set({ unlockType }, { merge: true });
      converted++;
      console.log(`- Khung "${data.frameName}" : ${raw ?? '(trong)'} -> ${unlockType}`);
    }
    if (unlockType === 'SHOP') {
      frameItems.push({
        itemName: data.frameName ?? 'Khung anh',
        itemType: 'FRAME',
        refId: doc.id,
        sortOrder: frameItems.length,
        imageUrl: data.imageUrl,
      });
    }
  }
  console.log(`\n=> Da chuan hoa ${converted} khung; ${frameItems.length} khung ban trong Shop.`);

  // ==== 2. Metadata cu tu gachaItems (neu con) ====
  const legacy = new Map<string, FirebaseFirestore.DocumentData>();
  for (const d of (await db.collection(LEGACY_GACHA_ITEMS).get()).docs) {
    legacy.set(keyOf(d.data().itemType, d.data().refId), d.data());
  }
  if (legacy.size > 0) {
    console.log(`=> Tim thay ${legacy.size} vat pham gacha cu — mang ten/anh/bat-tat sang.`);
  }

  // ==== 3. Tao shopItems con thieu ====
  const existing = await shopCol.get();
  const seen = new Set(existing.docs.map((d) => keyOf(d.data().itemType, d.data().refId)));

  let created = 0;
  for (const item of [...frameItems, ...SKINS, ...EFFECTS]) {
    const key = keyOf(item.itemType, item.refId);
    if (seen.has(key)) {
      console.log(`- Bo qua (da co): [${item.itemType}] ${item.itemName}`);
      continue;
    }
    const old = legacy.get(key);
    const doc = {
      itemName: (old?.itemName as string | undefined) || item.itemName,
      itemType: item.itemType,
      refId: item.refId,
      price: DEFAULT_PRICE_BY_TYPE[item.itemType],
      imageUrl: (old?.imageUrl as string | undefined) ?? item.imageUrl ?? null,
      isActive: (old?.isActive as boolean | undefined) ?? item.isActive ?? true,
      sortOrder: (old?.sortOrder as number | undefined) ?? item.sortOrder,
      createdAt: new Date().toISOString(),
    };
    await shopCol.add(doc);
    seen.add(key);
    created++;
    console.log(`✔ Them [${item.itemType} #${item.refId}] ${doc.itemName} — ${doc.price} Astrite`);
  }

  console.log(`\n=== Xong: them moi ${created} mat hang (tong ${seen.size}) ===`);
  console.log('Anh dai dien SKIN/EFFECT de trong thi app tu ve preview tu asset trong APK.');
  if (legacy.size > 0) {
    console.log(
      'Buoc tiep theo (tuy chon): npm run cleanup:gacha  (dry-run, them --confirm de xoa)',
    );
  }
}

main()
  .then(() => process.exit(0))
  .catch((err) => {
    console.error(err);
    process.exit(1);
  });
