/* eslint-disable no-console */
/**
 * Cap quyen admin (custom claim { admin: true }) cho 1 tai khoan Firebase DA TON TAI.
 * Day la loi thoat "con ga - qua trung": endpoint grant-admin cua server yeu cau quyen admin,
 * nen admin DAU TIEN phai duoc seed ngoai luong bang script nay.
 *
 * Chay:  npm run seed:admin -- <email>
 * Mac dinh (khong truyen email): viethoang5301314@gmail.com
 *
 * Yeu cau: server/.env co FIREBASE_SERVICE_ACCOUNT tro toi service account key.
 * Sau khi seed, tai khoan phai DANG NHAP LAI de ID token moi chua claim admin.
 */
import 'dotenv/config';
import * as admin from 'firebase-admin';
import { existsSync, readFileSync } from 'fs';
import { resolve } from 'path';

const DEFAULT_ADMIN_EMAIL = 'viethoang5301314@gmail.com';

async function main(): Promise<void> {
  const email = process.argv[2] ?? DEFAULT_ADMIN_EMAIL;

  const saPath = resolve(
    process.cwd(),
    process.env.FIREBASE_SERVICE_ACCOUNT ?? 'firebase-service-account.json',
  );
  if (!existsSync(saPath)) {
    throw new Error(
      `Khong tim thay service account key: ${saPath}. Kiem tra FIREBASE_SERVICE_ACCOUNT trong .env.`,
    );
  }

  admin.initializeApp({
    credential: admin.credential.cert(JSON.parse(readFileSync(saPath, 'utf8'))),
  });

  const user = await admin.auth().getUserByEmail(email);
  // Giu nguyen cac claim khac (neu co), chi bo sung admin: true
  await admin.auth().setCustomUserClaims(user.uid, { ...(user.customClaims ?? {}), admin: true });

  console.log(`OK: ${email} (uid=${user.uid}) da co quyen admin.`);
  console.log('Luu y: tai khoan can dang nhap lai de token moi mang claim admin.');
}

main().catch((err: unknown) => {
  const message = err instanceof Error ? err.message : String(err);
  console.error(`Seed admin that bai: ${message}`);
  process.exit(1);
});
