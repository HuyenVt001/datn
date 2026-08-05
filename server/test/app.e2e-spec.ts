/**
 * E2E SMOKE TEST (supertest) — boot toan bo AppModule that va kiem tra
 * "khung xuong" HTTP: prefix /api, envelope thanh cong/loi, ValidationPipe,
 * guard chan request thieu token. KHONG cham Firestore (cac case deu bi guard/
 * validation chan truoc khi toi repository) nen chay duoc ma khong can emulator.
 *
 * Yeu cau: .env hop le (nhu khi chay `npm run start:dev`).
 *
 * Mo rong sau (khi can test flow du lieu that): cai firebase-tools roi chay
 *   firebase emulators:exec --only firestore,auth "npm run test:e2e"
 * va set FIRESTORE_EMULATOR_HOST/FIREBASE_AUTH_EMULATOR_HOST — Admin SDK se
 * tu tro ve emulator, khi do viet them cac spec dang bai/streak/thuong khung.
 */
import { INestApplication, ValidationPipe } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import request from 'supertest';
import { AppModule } from '../src/app.module';
import { AllExceptionsFilter } from '../src/common/filters/all-exceptions.filter';
import { ResponseInterceptor } from '../src/common/interceptors/response.interceptor';

describe('Snapget API (e2e smoke)', () => {
  let app: INestApplication;

  beforeAll(async () => {
    const moduleRef = await Test.createTestingModule({ imports: [AppModule] }).compile();

    // Lap lai cau hinh global cua main.ts (bootstrap khong chay trong e2e)
    app = moduleRef.createNestApplication();
    app.setGlobalPrefix('api');
    app.useGlobalPipes(
      new ValidationPipe({
        whitelist: true,
        forbidNonWhitelisted: true,
        transform: true,
        transformOptions: { enableImplicitConversion: true },
      }),
    );
    app.useGlobalInterceptors(new ResponseInterceptor());
    app.useGlobalFilters(new AllExceptionsFilter());
    await app.init();
  });

  afterAll(async () => {
    await app.close();
  });

  it('GET /api/health: 200 + envelope thanh cong', async () => {
    const res = await request(app.getHttpServer()).get('/api/health').expect(200);
    expect(res.body.success).toBe(true);
    expect(res.body.statusCode).toBe(200);
    expect(res.body).toHaveProperty('data');
  });

  it('GET /api/users/me KHONG token: 401 + envelope loi', async () => {
    const res = await request(app.getHttpServer()).get('/api/users/me').expect(401);
    expect(res.body.success).toBe(false);
    expect(res.body.statusCode).toBe(401);
    expect(typeof res.body.message).toBe('string');
  });

  it('GET /api/admin/users KHONG token admin: 401', async () => {
    const res = await request(app.getHttpServer()).get('/api/admin/users').expect(401);
    expect(res.body.success).toBe(false);
  });

  it('GET /api/admin/logs KHONG token admin: 401 (route audit log co guard)', async () => {
    await request(app.getHttpServer()).get('/api/admin/logs').expect(401);
  });

  it('POST /api/auth/admin/login body rong: 400 validation + envelope loi', async () => {
    const res = await request(app.getHttpServer())
      .post('/api/auth/admin/login')
      .send({})
      .expect(400);
    expect(res.body.success).toBe(false);
    expect(res.body.statusCode).toBe(400);
  });

  it('POST /api/auth/admin/login token rac: 401', async () => {
    const res = await request(app.getHttpServer())
      .post('/api/auth/admin/login')
      .send({ idToken: 'khong-phai-token' })
      .expect(401);
    expect(res.body.success).toBe(false);
  });

  it('POST /api/topup/webhook chu ky rac: 401, KHONG cong tien', async () => {
    // Webhook la route @Public duy nhat cham toi tien. Neu route nay lo tay bi
    // doi thanh 200/500 thi bat ky ai cung tu bom Astrite cho minh duoc.
    const res = await request(app.getHttpServer())
      .post('/api/topup/webhook')
      .send({ code: '00', desc: 'ok', success: true, data: { orderCode: 1 }, signature: 'rac' })
      .expect(401);
    expect(res.body.success).toBe(false);
  });

  it('GET /api/topup/packages KHONG token: 401 (goi nap khong lo ra ngoai)', async () => {
    await request(app.getHttpServer()).get('/api/topup/packages').expect(401);
  });

  it('GET /api/topup/cancel: 200 tra HTML (trang PayOS chuyen huong ve)', async () => {
    const res = await request(app.getHttpServer()).get('/api/topup/cancel').expect(200);
    expect(res.headers['content-type']).toContain('text/html');
  });

  it('Route khong ton tai: 404 + envelope loi (filter toan cuc)', async () => {
    const res = await request(app.getHttpServer()).get('/api/khong-ton-tai').expect(404);
    expect(res.body.success).toBe(false);
    expect(res.body.statusCode).toBe(404);
  });
});
