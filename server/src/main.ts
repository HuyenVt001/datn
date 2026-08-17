import { ValidationPipe } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { NestFactory } from '@nestjs/core';
import { DocumentBuilder, SwaggerModule } from '@nestjs/swagger';
import helmet from 'helmet';
import { AppModule } from './app.module';
import { AllExceptionsFilter } from './common/filters/all-exceptions.filter';
import { ResponseInterceptor } from './common/interceptors/response.interceptor';

async function bootstrap() {
  const app = await NestFactory.create(AppModule);
  const config = app.get(ConfigService);

  // Security headers (2026-07-26). CSP tat vi Swagger UI /docs can inline script;
  // cac header con lai (noSniff, frameguard, HSTS...) van bat.
  app.use(helmet({ contentSecurityPolicy: false }));

  // Prefix chung cho toan bo API
  app.setGlobalPrefix('api');

  // CORS cho admin React (Vite)
  const origins = (config.get<string>('CORS_ORIGINS') ?? '').split(',').map((o) => o.trim());
  app.enableCors({ origin: origins, credentials: true });

  // Validate + transform moi input; loai field thua
  app.useGlobalPipes(
    new ValidationPipe({
      whitelist: true,
      forbidNonWhitelisted: true,
      transform: true,
      transformOptions: { enableImplicitConversion: true },
    }),
  );

  // Envelope chuan + xu ly loi tap trung
  app.useGlobalInterceptors(new ResponseInterceptor());
  app.useGlobalFilters(new AllExceptionsFilter());

  // Swagger tai /docs
  const swaggerConfig = new DocumentBuilder()
    .setTitle('Snapget API')
    .setDescription('API server cho app Snapget (NestJS -> Firebase). Xem GUIDE.md.')
    .setVersion('0.1')
    .addBearerAuth(
      { type: 'http', scheme: 'bearer', bearerFormat: 'JWT' },
      'firebase', // Firebase ID token (luong app)
    )
    .addBearerAuth(
      { type: 'http', scheme: 'bearer', bearerFormat: 'JWT' },
      'admin', // JWT server (luong admin)
    )
    .addApiKey(
      { type: 'apiKey', in: 'header', name: 'x-cron-secret' },
      'cron', // Secret cua cron-job.org (POST /quests/ai/generate) — 2026-08-15
    )
    .build();
  const document = SwaggerModule.createDocument(app, swaggerConfig);
  SwaggerModule.setup('docs', app, document);

  const port = config.get<number>('PORT') ?? 3000;
  await app.listen(port);
  // eslint-disable-next-line no-console
  console.log(`Snapget server chay tai http://localhost:${port} (docs: /docs)`);
}

// eslint-disable-next-line @typescript-eslint/no-floating-promises
bootstrap();
