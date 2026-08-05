import * as Joi from 'joi';

/**
 * Schema kiem tra bien moi truong luc khoi dong.
 * Thieu bien bat buoc -> app fail som (fail-fast), khong chay voi config sai.
 */
export const envValidationSchema = Joi.object({
  NODE_ENV: Joi.string().valid('development', 'production', 'test').default('development'),
  PORT: Joi.number().default(3000),
  CORS_ORIGINS: Joi.string().default('http://localhost:5173'),

  // Firebase Admin
  FIREBASE_SERVICE_ACCOUNT: Joi.string().required(),
  FIREBASE_PROJECT_ID: Joi.string().optional().allow(''),

  // JWT cho luong admin
  JWT_SECRET: Joi.string().required(),
  JWT_EXPIRES_IN: Joi.string().default('1d'),

  // Cloudinary (co the de trong o giai doan dau)
  CLOUDINARY_CLOUD_NAME: Joi.string().optional().allow(''),
  CLOUDINARY_API_KEY: Joi.string().optional().allow(''),
  CLOUDINARY_API_SECRET: Joi.string().optional().allow(''),

  // PayOS — nap Astrite bang TIEN THAT (2026-08-05, GACHA_PLAN.md muc 4.3).
  // De trong duoc: server van boot, chi rieng luong nap tat (POST /topup/orders
  // tra 503). Nho vay dev/CI khong can khoa that va e2e chay duoc offline.
  PAYOS_CLIENT_ID: Joi.string().optional().allow(''),
  PAYOS_API_KEY: Joi.string().optional().allow(''),
  PAYOS_CHECKSUM_KEY: Joi.string().optional().allow(''),
  PAYOS_RETURN_URL: Joi.string().optional().allow(''),
  PAYOS_CANCEL_URL: Joi.string().optional().allow(''),
});
