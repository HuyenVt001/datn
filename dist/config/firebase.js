"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.bucket = exports.auth = exports.db = void 0;
const app_1 = require("firebase-admin/app");
const firestore_1 = require("firebase-admin/firestore");
const auth_1 = require("firebase-admin/auth");
const storage_1 = require("firebase-admin/storage");
const path_1 = __importDefault(require("path"));
const fs_1 = __importDefault(require("fs"));
const storageBucket = process.env.FIREBASE_STORAGE_BUCKET ||
    (process.env.FIREBASE_PROJECT_ID ? `${process.env.FIREBASE_PROJECT_ID}.firebasestorage.app` : undefined);
const initializeFirebase = () => {
    const apps = (0, app_1.getApps)();
    if (apps.length > 0) {
        return apps[0];
    }
    const serviceAccountPath = process.env.FIREBASE_SERVICE_ACCOUNT_PATH;
    if (!serviceAccountPath) {
        throw new Error('❌ ERROR: FIREBASE_SERVICE_ACCOUNT_PATH is not defined in the environment variables.');
    }
    const absolutePath = path_1.default.isAbsolute(serviceAccountPath)
        ? serviceAccountPath
        : path_1.default.join(process.cwd(), serviceAccountPath);
    if (!fs_1.default.existsSync(absolutePath)) {
        throw new Error(`❌ ERROR: Firebase Service Account file not found at: ${absolutePath}`);
    }
    console.log(`🔥 Initializing Firebase Admin SDK via Service Account file: ${absolutePath}`);
    // Tự động phân tích project_id từ file service account để cấu hình Storage Bucket mặc định nếu không có trong env
    let defaultStorageBucket;
    try {
        const serviceAccount = JSON.parse(fs_1.default.readFileSync(absolutePath, 'utf8'));
        if (serviceAccount.project_id) {
            defaultStorageBucket = `${serviceAccount.project_id}.firebasestorage.app`;
        }
    }
    catch (err) {
        console.warn('⚠️ WARNING: Could not parse service account file to extract project ID.', err);
    }
    const storageBucket = process.env.FIREBASE_STORAGE_BUCKET || defaultStorageBucket;
    return (0, app_1.initializeApp)({
        credential: (0, app_1.cert)(absolutePath),
        storageBucket,
    });
};
const app = initializeFirebase();
exports.db = (0, firestore_1.getFirestore)(app);
exports.auth = (0, auth_1.getAuth)(app);
exports.bucket = (0, storage_1.getStorage)(app).bucket();
exports.default = app;
