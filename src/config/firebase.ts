import { initializeApp, getApps, getApp, cert, App } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import { getAuth } from 'firebase-admin/auth';
import { getStorage } from 'firebase-admin/storage';
import path from 'path';
import fs from 'fs';

const storageBucket = process.env.FIREBASE_STORAGE_BUCKET ||
  (process.env.FIREBASE_PROJECT_ID ? `${process.env.FIREBASE_PROJECT_ID}.firebasestorage.app` : undefined);

const initializeFirebase = (): App => {
  const apps = getApps();
  if (apps.length > 0) {
    return apps[0];
  }

  const serviceAccountPath = process.env.FIREBASE_SERVICE_ACCOUNT_PATH;
  if (!serviceAccountPath) {
    throw new Error('ERROR: FIREBASE_SERVICE_ACCOUNT_PATH is not defined in the environment variables.');
  }

  const absolutePath = path.isAbsolute(serviceAccountPath)
    ? serviceAccountPath
    : path.join(process.cwd(), serviceAccountPath);

  if (!fs.existsSync(absolutePath)) {
    throw new Error(`ERROR: Firebase Service Account file not found at: ${absolutePath}`);
  }

  console.log(`Initializing Firebase Admin SDK via Service Account file: ${absolutePath}`);

  let defaultStorageBucket: string | undefined;
  try {
    const serviceAccount = JSON.parse(fs.readFileSync(absolutePath, 'utf8'));
    if (serviceAccount.project_id) {
      defaultStorageBucket = `${serviceAccount.project_id}.firebasestorage.app`;
    }
  } catch (err) {
    console.warn('WARNING: Could not parse service account file to extract project ID.', err);
  }

  const storageBucket = process.env.FIREBASE_STORAGE_BUCKET || defaultStorageBucket;

  return initializeApp({
    credential: cert(absolutePath),
    storageBucket,
  });
};

const app = initializeFirebase();

export const db = getFirestore(app);
export const auth = getAuth(app);
export const bucket = getStorage(app).bucket();

export default app;
