/* eslint-disable no-console */
/**
 * Seed 5 goi nap Astrite (`topupPackages`) — GACHA_PLAN.md muc 0.1.
 *
 * Chay:  npm run seed:topup
 * Yeu cau .env: FIREBASE_SERVICE_ACCOUNT.
 *
 * Script IDEMPOTENT — khoa theo `seedKey`, chay lai khong tao ban sao.
 * Goi da co CHI duoc bo sung field con thieu; ten/gia/so Astrite giu nguyen de
 * khong ghi de thay doi admin da chinh tren web.
 *
 * ⚠️ Goi #5 (5.201.314 Astrite / 2.000d) la goi TEST nhung van `isActive: true`
 * — user chot 2026-08-05 dung chinh goi nay de nap that luc demo. Muon an thi
 * tat bang `isActive` o trang admin, KHONG sua code.
 */
import 'dotenv/config';
import * as admin from 'firebase-admin';
import { existsSync, readFileSync } from 'fs';
import { resolve } from 'path';
import { Collections } from '../src/common/constants';

interface SeedPackage {
  /** Khoa de nhan dien goi da seed — khong doi ve sau. */
  seedKey: string;
  name: string;
  astrite: number;
  priceVnd: number;
  isTest: boolean;
  sortOrder: number;
}

const PACKAGES: SeedPackage[] = [
  { seedKey: 'astrite-600', name: '600 Astrite', astrite: 600, priceVnd: 5_000, isTest: false, sortOrder: 1 },
  { seedKey: 'astrite-3000', name: '3.000 Astrite', astrite: 3_000, priceVnd: 24_000, isTest: false, sortOrder: 2 },
  { seedKey: 'astrite-9800', name: '9.800 Astrite', astrite: 9_800, priceVnd: 75_000, isTest: false, sortOrder: 3 },
  { seedKey: 'astrite-20000', name: '20.000 Astrite', astrite: 20_000, priceVnd: 145_000, isTest: false, sortOrder: 4 },
  {
    seedKey: 'astrite-test',
    name: '5.201.314 Astrite',
    astrite: 5_201_314,
    priceVnd: 2_000,
    isTest: true,
    sortOrder: 5,
  },
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
  const col = admin.firestore().collection(Collections.TOPUP_PACKAGES);

  const existing = await col.get();
  const bySeedKey = new Map(existing.docs.map((d) => [d.data().seedKey as string, d]));

  let created = 0;
  for (const pkg of PACKAGES) {
    const found = bySeedKey.get(pkg.seedKey);
    if (found) {
      console.log(`- Bo qua (da ton tai): ${pkg.name} — ${pkg.priceVnd.toLocaleString('vi-VN')}d`);
      continue;
    }
    await col.add({
      seedKey: pkg.seedKey,
      name: pkg.name,
      astrite: pkg.astrite,
      priceVnd: pkg.priceVnd,
      isActive: true,
      isTest: pkg.isTest,
      sortOrder: pkg.sortOrder,
      createdAt: new Date().toISOString(),
    });
    created++;
    console.log(
      `✔ Them ${pkg.name} — ${pkg.priceVnd.toLocaleString('vi-VN')}d${pkg.isTest ? '  [GOI TEST]' : ''}`,
    );
  }

  console.log(`\n=== Xong: them moi ${created} goi nap ===`);
  console.log('Bat/tat tung goi o trang admin muc "Gói nạp" — khong can sua code.');
  console.log(
    '⚠️  Nho: moi lan thanh toan THAT tieu 1 trong 100 giao dich cua goi FREE-100.\n' +
      '    Luc dev hay dung POST /topup/simulate thay vi nap that.',
  );
}

main()
  .then(() => process.exit(0))
  .catch((err) => {
    console.error(err);
    process.exit(1);
  });
