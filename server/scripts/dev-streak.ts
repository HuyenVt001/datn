/* eslint-disable no-console */
/**
 * DEV TOOL — can thiep du lieu streak/khung cua 1 user de TEST tinh nang mo khoa khung.
 * (Chi dung khi dev/demo — KHONG phai tinh nang production.)
 *
 * Chay:  npm run dev:streak -- --email <email> [hanh dong]
 *
 * Hanh dong (chon 1):
 *   (khong co)        → in trang thai hien tai (streak, ngay streak, khung da mo / catalog)
 *   --streak N        → set personalStreak = N va lastStreakDate = HOM QUA (UTC)
 *                       ⇒ vao app DANG 1 BAI → streak thanh N+1 → server tu mo khung
 *                       neu N+1 la moc (3/7/14/30). Vi du test moc 30: --streak 29.
 *   --unlock-all      → mo khoa TAT CA khung trong catalog (xem UI bo suu tap full)
 *   --lock-all        → xoa het khung da mo + reset streak ve 0 (test lai tu dau)
 *
 * Nhan dien user: --email <email dang nhap Firebase> hoac --uid <uid>.
 * Yeu cau .env: FIREBASE_SERVICE_ACCOUNT (nhu cac script seed).
 */
import 'dotenv/config';
import * as admin from 'firebase-admin';
import { existsSync, readFileSync } from 'fs';
import { resolve } from 'path';

// YYYY-MM-DD theo UTC — PHAI khop dateKey() cua server (common/constants.ts)
const dateKey = (date: Date = new Date()): string => date.toISOString().slice(0, 10);

function getArg(name: string): string | undefined {
  const i = process.argv.indexOf(`--${name}`);
  return i >= 0 ? process.argv[i + 1] : undefined;
}
const hasFlag = (name: string): boolean => process.argv.includes(`--${name}`);

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

  // ==== Xac dinh user ====
  const email = getArg('email');
  let uid = getArg('uid');
  if (!uid && email) {
    uid = (await admin.auth().getUserByEmail(email)).uid;
  }
  if (!uid) {
    throw new Error('Can --email <email> hoac --uid <uid>.');
  }

  const userRef = db.collection('users').doc(uid);
  const framesSnap = await db.collection('frames').get();
  // Suy unlockType nhu server repo (doc cu chi co milestone / con QUEST_RANDOM) — 2026-08-05
  const frames = framesSnap.docs.map((d) => {
    const data = d.data();
    const milestone = typeof data.milestone === 'number' ? data.milestone : undefined;
    const raw = data.unlockType as string | undefined;
    const unlockType =
      raw === 'QUEST_RANDOM' ? 'GACHA' : (raw ?? (milestone ? 'STREAK_MILESTONE' : 'GACHA'));
    const unlockValue = typeof data.unlockValue === 'number' ? data.unlockValue : milestone;
    return {
      frameId: d.id,
      frameName: (data.frameName as string) ?? '(?)',
      unlockType,
      unlockValue,
    };
  });

  // ==== Hanh dong ====
  const streakArg = getArg('streak');
  if (streakArg !== undefined) {
    const streak = Number(streakArg);
    if (!Number.isInteger(streak) || streak < 0) {
      throw new Error(`--streak phai la so nguyen >= 0 (nhan duoc: ${streakArg})`);
    }
    // lastStreakDate = HOM QUA (UTC) ⇒ bai dang tiep theo se +1 streak dung LUONG THAT
    const yesterday = dateKey(new Date(Date.now() - 24 * 60 * 60 * 1000));
    await userRef.set({ personalStreak: streak, lastStreakDate: yesterday }, { merge: true });
    console.log(`✅ ${uid}: personalStreak = ${streak}, lastStreakDate = ${yesterday} (hom qua UTC)`);
    console.log(`👉 Vao app DANG 1 BAI → streak thanh ${streak + 1}` +
      ([3, 7, 14, 30].includes(streak + 1) ? ` = MOC → server tu mo khung moc ${streak + 1} 🎉` : ''));
  } else if (hasFlag('unlock-all')) {
    const all = frames.map((f) => f.frameId);
    if (all.length === 0) {
      // arrayUnion() 0 tham so se throw — catalog rong thi khong co gi de mo
      console.log('⚠️ Catalog khung dang rong — chay `npm run seed:frames` truoc.');
    } else {
      await userRef.set(
        { unlockedFrames: admin.firestore.FieldValue.arrayUnion(...all) },
        { merge: true },
      );
      console.log(`✅ Da mo khoa ${all.length} khung cho ${uid}.`);
    }
  } else if (hasFlag('lock-all')) {
    await userRef.set(
      { unlockedFrames: [], personalStreak: 0, lastStreakDate: admin.firestore.FieldValue.delete() },
      { merge: true },
    );
    console.log(`✅ Da khoa lai toan bo khung + reset streak ve 0 cho ${uid}.`);
  }

  // ==== In trang thai sau cung ====
  const user = (await userRef.get()).data() ?? {};
  const unlocked = new Set<string>((user.unlockedFrames as string[]) ?? []);
  console.log('\n===== TRANG THAI HIEN TAI =====');
  console.log(`User        : ${uid}${email ? ` (${email})` : ''}`);
  console.log(`personalStreak : ${user.personalStreak ?? 0}`);
  console.log(`lastStreakDate : ${user.lastStreakDate ?? '(chua co)'}  (hom nay UTC = ${dateKey()})`);
  console.log(`Khung (${frames.length} trong catalog):`);
  const TAGS: Record<string, (n?: number) => string> = {
    STREAK_MILESTONE: (n) => `[moc ${n}]`,
    POST_COUNT: (n) => `[${n} bai]`,
    FRIEND_COUNT: (n) => `[${n} ban]`,
    COOP_FIRST: () => '[co-op]',
    DEFAULT: () => '[mo san]',
    GACHA: () => '[gacha]',
  };
  for (const f of frames) {
    const tag = (TAGS[f.unlockType] ?? TAGS.GACHA)(f.unlockValue);
    console.log(`  ${unlocked.has(f.frameId) ? '🔓' : '🔒'} ${tag} ${f.frameName} (${f.frameId})`);
  }
}

main()
  .then(() => process.exit(0))
  .catch((e) => {
    console.error('❌', (e as Error).message);
    process.exit(1);
  });
