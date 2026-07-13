/* eslint-disable no-console */
/**
 * Seed khung anh mau: upload PNG trong assets/frames/ len Cloudinary
 * + tao doc trong collection `frames` (bo qua khung da ton tai theo frameName).
 *
 * Chay:  npm run seed:frames
 * Yeu cau .env: FIREBASE_SERVICE_ACCOUNT + CLOUDINARY_CLOUD_NAME/API_KEY/API_SECRET.
 * Danh sach khung + moc streak khai bao trong assets/frames/manifest.json.
 */
import 'dotenv/config';
import { v2 as cloudinary } from 'cloudinary';
import * as admin from 'firebase-admin';
import { existsSync, readFileSync } from 'fs';
import { join, resolve } from 'path';

interface ManifestEntry {
  file: string;
  frameName: string;
  milestone?: number;
}

const FRAMES_DIR = resolve(process.cwd(), 'assets/frames');

async function main(): Promise<void> {
  // ==== Firebase Admin ====
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

  // ==== Cloudinary ====
  const { CLOUDINARY_CLOUD_NAME, CLOUDINARY_API_KEY, CLOUDINARY_API_SECRET } = process.env;
  if (!CLOUDINARY_CLOUD_NAME || !CLOUDINARY_API_KEY || !CLOUDINARY_API_SECRET) {
    throw new Error('Thieu config Cloudinary trong .env.');
  }
  cloudinary.config({
    cloud_name: CLOUDINARY_CLOUD_NAME,
    api_key: CLOUDINARY_API_KEY,
    api_secret: CLOUDINARY_API_SECRET,
  });

  // ==== Manifest ====
  const manifestPath = join(FRAMES_DIR, 'manifest.json');
  if (!existsSync(manifestPath)) {
    throw new Error(`Khong tim thay ${manifestPath} — chay script generate khung truoc.`);
  }
  const manifest: ManifestEntry[] = JSON.parse(readFileSync(manifestPath, 'utf8'));

  const framesCol = admin.firestore().collection('frames');
  const existing = await framesCol.get();
  const existingNames = new Set(existing.docs.map((d) => d.data().frameName as string));

  for (const entry of manifest) {
    if (existingNames.has(entry.frameName)) {
      console.log(`- Bo qua (da ton tai): ${entry.frameName}`);
      continue;
    }
    const filePath = join(FRAMES_DIR, entry.file);
    if (!existsSync(filePath)) {
      console.warn(`- Thieu file ${entry.file}, bo qua.`);
      continue;
    }

    const uploaded = await cloudinary.uploader.upload(filePath, {
      folder: 'snapget/frames',
      resource_type: 'image',
    });

    await framesCol.add({
      frameName: entry.frameName,
      imageUrl: uploaded.secure_url,
      ...(entry.milestone ? { milestone: entry.milestone } : {}),
      createdAt: new Date().toISOString(),
    });
    console.log(`+ Da seed: ${entry.frameName}${entry.milestone ? ` (moc ${entry.milestone})` : ''}`);
  }

  console.log('Xong. Kiem tra trang admin > Khung anh.');
}

main().catch((err: unknown) => {
  const message = err instanceof Error ? err.message : String(err);
  console.error(`Seed frames that bai: ${message}`);
  process.exit(1);
});
