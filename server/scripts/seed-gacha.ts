/* eslint-disable no-console */
/**
 * Seed catalog vat pham gacha (`gachaItems`) + chuyen khung QUEST_RANDOM sang GACHA.
 *
 * Chay:  npm run seed:gacha
 * Yeu cau .env: FIREBASE_SERVICE_ACCOUNT.
 *
 * Script IDEMPOTENT — chay lai nhieu lan khong tao ban sao (khoa theo
 * itemType + refId). An toan chay lai sau khi them skin/hieu ung moi.
 *
 * Anh dai dien (`imageUrl`):
 *  - FRAME: lay luon `imageUrl` cua khung (da co tren Cloudinary).
 *  - SKIN / EFFECT: de TRONG — asset that nam trong APK, admin tu upload anh
 *    dai dien qua trang quan tri (dung spec: "Anh dai vat pham" do admin quan ly).
 */
import 'dotenv/config';
import * as admin from 'firebase-admin';
import { existsSync, readFileSync } from 'fs';
import { resolve } from 'path';
import { Collections } from '../src/common/constants';
import { ItemType } from '../src/gacha/entities/gacha-item.entity';

interface SeedItem {
  itemName: string;
  itemType: ItemType;
  rarity: 'R' | 'SR' | 'SSR';
  refId: string;
  sortOrder: number;
  imageUrl?: string;
}

/** Skin bundled trong APK — khop SkinRegistry (Snapget/.claude/SKIN_PLAN.md muc 0.2). */
const SKINS: SeedItem[] = [
  { itemName: 'Snow', itemType: 'SKIN', rarity: 'SSR', refId: '1', sortOrder: 10 },
  { itemName: 'Forest', itemType: 'SKIN', rarity: 'SSR', refId: '2', sortOrder: 11 },
];

/**
 * Hieu ung touch bundled trong APK — khop TouchEffectRegistry.
 *
 * ⚠️ Doi 2026-08-11: app xoa het 5 hieu ung particle cu (Snowfall / Leaf /
 * Sparkle / Bubble / Ember) va lam lai bang spritesheet, hien chi con `Flower`
 * (refId 1). User bo sung dan, **toi da 10**.
 *
 * Script nay CHI THEM item con thieu, khong sua/xoa item da co (xem vong lap
 * duoi, `seen` bo qua key da ton tai). Nen voi Firestore da seed ban cu:
 *  - item refId '1' van con ten 'Snowfall' -> doi ten thanh 'Flower' o trang admin.
 *  - item refId '2'..'5' tro toi hieu ung **khong con trong app** -> phai
 *    `isActive = false` o trang admin, khong thi quay ra se nhan duoc mot hieu
 *    ung khong hien thi duoc gi (app fallback ve None).
 */
const EFFECTS: SeedItem[] = [
  { itemName: 'Flower', itemType: 'EFFECT', rarity: 'SR', refId: '1', sortOrder: 20 },
];

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
  const itemsCol = db.collection(Collections.GACHA_ITEMS);
  const framesCol = db.collection(Collections.FRAMES);

  // ==== 1. Khung anh: chuan hoa unlockType cu -> GACHA ====
  // Thuong "xong 2/2 quest -> mo khung ngau nhien" da doi thanh "+60 Astrite",
  // nen cac khung do gio la vat pham bac R cua gacha.
  //
  // FramesRepository.toEntity DA tu map doc cu -> GACHA khi doc, nen buoc ghi
  // duoi day chi la don dep du lieu cho gon; server chay dung ca khi chua seed.
  // PHAI suy y HET nhu repo, neu khong se co khung server coi la GACHA nhung
  // khong nam trong catalog -> khong bao gio quay ra duoc.
  const frames = await framesCol.get();
  const frameItems: SeedItem[] = [];
  let converted = 0;

  for (const doc of frames.docs) {
    const data = doc.data();
    const raw = data.unlockType as string | undefined;
    const legacyMilestone = typeof data.milestone === 'number' ? data.milestone : undefined;
    const unlockType =
      raw === 'QUEST_RANDOM' ? 'GACHA' : (raw ?? (legacyMilestone ? 'STREAK_MILESTONE' : 'GACHA'));

    if (raw !== unlockType) {
      await doc.ref.set({ unlockType }, { merge: true });
      converted++;
      console.log(`- Khung "${data.frameName}" : ${raw ?? '(trong)'} -> ${unlockType}`);
    }
    // Chi khung GACHA moi vao pool quay; khung moc streak van thuong theo streak
    if (unlockType === 'GACHA') {
      frameItems.push({
        itemName: data.frameName ?? 'Khung anh',
        itemType: 'FRAME',
        rarity: 'R',
        refId: doc.id,
        sortOrder: frameItems.length,
        imageUrl: data.imageUrl,
      });
    }
  }
  console.log(`\n=> Da chuan hoa ${converted} khung; ${frameItems.length} khung vao pool gacha.`);

  // ==== 2. Tao gachaItems (bo qua cai da co) ====
  const existing = await itemsCol.get();
  const seen = new Set(existing.docs.map((d) => `${d.data().itemType}:${d.data().refId}`));

  let created = 0;
  for (const item of [...frameItems, ...EFFECTS, ...SKINS]) {
    const key = `${item.itemType}:${item.refId}`;
    if (seen.has(key)) {
      console.log(`- Bo qua (da ton tai): [${item.itemType}] ${item.itemName}`);
      continue;
    }
    await itemsCol.add({
      itemName: item.itemName,
      itemType: item.itemType,
      rarity: item.rarity,
      refId: item.refId,
      imageUrl: item.imageUrl ?? null,
      isActive: true,
      sortOrder: item.sortOrder,
      createdAt: new Date().toISOString(),
    });
    created++;
    console.log(`✔ Them [${item.rarity}] ${item.itemName} (${item.itemType} #${item.refId})`);
  }

  // ==== 3. Tong ket pool theo bac ====
  const all = await itemsCol.get();
  const byRarity = all.docs.reduce<Record<string, number>>((acc, d) => {
    const r = d.data().rarity as string;
    acc[r] = (acc[r] ?? 0) + 1;
    return acc;
  }, {});

  console.log(`\n=== Xong: them moi ${created} vat pham ===`);
  console.log('Pool hien tai:', byRarity);
  if (!byRarity.R) {
    console.warn('⚠️  Chua co vat pham bac R — quay trung bac R se tra Astrite thay khung.');
  }
  console.log('\nAnh dai dien cua SKIN/EFFECT dang trong — upload qua trang admin.');
}

main()
  .then(() => process.exit(0))
  .catch((err) => {
    console.error(err);
    process.exit(1);
  });
