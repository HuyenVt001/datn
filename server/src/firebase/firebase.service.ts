import { Injectable, Logger, OnModuleInit } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import * as admin from 'firebase-admin';
import { existsSync, readFileSync } from 'fs';
import { resolve } from 'path';

/**
 * Khoi tao Firebase Admin SDK va expose cac dich vu con (auth/firestore/messaging).
 * Day la NOI DUY NHAT giu instance admin — cac module khac inject service nay.
 */
@Injectable()
export class FirebaseService implements OnModuleInit {
  private readonly logger = new Logger(FirebaseService.name);
  private app?: admin.app.App;

  constructor(private readonly config: ConfigService) {}

  onModuleInit(): void {
    if (admin.apps.length > 0) {
      this.app = admin.app();
      return;
    }

    const saPath = this.config.get<string>('FIREBASE_SERVICE_ACCOUNT');
    const absolutePath = saPath ? resolve(process.cwd(), saPath) : '';

    // Chua co key: van cho server boot (de xem Swagger/demo), chi canh bao.
    // Endpoint nao goi Firebase se bao loi ro rang qua ensureApp().
    if (!absolutePath || !existsSync(absolutePath)) {
      this.logger.warn(
        `Service account key not found (${absolutePath || 'FIREBASE_SERVICE_ACCOUNT in .env'}).` +
          'The server still runs, but Firebase features will fail until the key is provided.' +
          'Get the key: Firebase Console > Project settings > Service accounts.',
      );
      return;
    }

    const serviceAccount = JSON.parse(readFileSync(absolutePath, 'utf8'));
    this.app = admin.initializeApp({
      credential: admin.credential.cert(serviceAccount),
    });
    this.logger.log('Firebase Admin SDK initialized successfully.');
  }

  private ensureApp(): admin.app.App {
    if (!this.app) {
      throw new Error(
        'Firebase is not initialized — missing service account key. Check the server startup log.',
      );
    }
    return this.app;
  }

  /** Firebase Authentication — verify token, quan ly user, custom claims. */
  auth(): admin.auth.Auth {
    return this.ensureApp().auth();
  }

  /** Cloud Firestore — noi luu du lieu chinh. */
  firestore(): admin.firestore.Firestore {
    return this.ensureApp().firestore();
  }

  /** Firebase Cloud Messaging — gui push notification. */
  messaging(): admin.messaging.Messaging {
    return this.ensureApp().messaging();
  }
}
