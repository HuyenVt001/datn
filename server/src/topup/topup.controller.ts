import {
  Body,
  Controller,
  Delete,
  Get,
  Param,
  ParseIntPipe,
  Patch,
  Post,
  Query,
  Res,
  UseGuards,
} from '@nestjs/common';
import {
  ApiBearerAuth,
  ApiBody,
  ApiExcludeEndpoint,
  ApiOperation,
  ApiQuery,
  ApiTags,
} from '@nestjs/swagger';
import type { Webhook } from '@payos/node';
import type { Response } from 'express';
import { AuditService } from '../audit/audit.service';
import { AuthUser, CurrentUser } from '../common/decorators/current-user.decorator';
import { Public } from '../common/decorators/public.decorator';
import { Roles } from '../common/decorators/roles.decorator';
import { AdminJwtGuard } from '../common/guards/admin-jwt.guard';
import { FirebaseAuthGuard } from '../common/guards/firebase-auth.guard';
import { RolesGuard } from '../common/guards/roles.guard';
import { AdminTopupsDto } from './dto/admin-topups.dto';
import { CreateTopupOrderDto } from './dto/create-order.dto';
import { CreateTopupPackageDto } from './dto/create-package.dto';
import { SimulateTopupDto } from './dto/simulate-topup.dto';
import { UpdateTopupPackageDto } from './dto/update-package.dto';
import { TopupService } from './topup.service';

@ApiTags('topup')
@Controller('topup')
export class TopupController {
  constructor(
    private readonly topupService: TopupService,
    private readonly auditService: AuditService,
  ) {}

  // ==== Luong USER (Firebase token) ====

  @Get('packages')
  @ApiBearerAuth('firebase')
  @UseGuards(FirebaseAuthGuard)
  @ApiOperation({ summary: 'Active top-up packages' })
  listPackages() {
    return this.topupService.listPackagesForApp();
  }

  @Post('orders')
  @ApiBearerAuth('firebase')
  @UseGuards(FirebaseAuthGuard)
  @ApiOperation({
    summary: 'Create a top-up order and get the PayOS payment link',
    description:
      'The body has ONLY packageId. Amount and Astrite are looked up from topupPackages on the server —' +
      'never taken from the client.',
  })
  createOrder(@CurrentUser() user: AuthUser, @Body() dto: CreateTopupOrderDto) {
    return this.topupService.createOrder(user.uid, dto.packageId);
  }

  @Get('orders/:orderCode')
  @ApiBearerAuth('firebase')
  @UseGuards(FirebaseAuthGuard)
  @ApiOperation({
    summary: 'Your top-up order status',
    description: 'The app polls this endpoint after the user closes the payment page.',
  })
  getOrder(@CurrentUser() user: AuthUser, @Param('orderCode', ParseIntPipe) orderCode: number) {
    return this.topupService.getOrderForUser(user.uid, orderCode);
  }

  @Get('history')
  @ApiBearerAuth('firebase')
  @UseGuards(FirebaseAuthGuard)
  @ApiOperation({ summary: 'Your top-up history (newest first)' })
  @ApiQuery({ name: 'limit', required: false, description: 'Max records (default 50)' })
  getHistory(@CurrentUser() user: AuthUser, @Query('limit') limit?: string) {
    const parsed = Number(limit);
    return this.topupService.listHistory(
      user.uid,
      Number.isFinite(parsed) && parsed > 0 ? Math.min(parsed, 200) : 50,
    );
  }

  @Post('simulate')
  @ApiBearerAuth('firebase')
  @UseGuards(FirebaseAuthGuard)
  @ApiOperation({
    summary: '[DEV] Simulate PayOS reporting a payment',
    description:
      'Only runs when NODE_ENV !== production. Saves the 100-transaction quota of the FREE-100 plan:' +
      'follows the exact path of the real webhook (verify signature → transaction → credit Astrite → write ledger).' +
      'Call repeatedly with the same orderCode to test idempotency.',
  })
  simulate(@CurrentUser() user: AuthUser, @Body() dto: SimulateTopupDto) {
    return this.topupService.simulate(user.uid, dto);
  }

  // ==== PUBLIC — PayOS goi ve ====

  /**
   * ⚠️ `body` phai la INTERFACE, khong duoc la class DTO.
   * `ValidationPipe` toan cuc dang bat `whitelist + forbidNonWhitelisted`: neu
   * khai bang class thi moi field PayOS them ve sau se lam request bi 400, va
   * cac field bi loai bo se pha vo chu ky (chu ky tinh tren nguyen ven `data`).
   * Nest bo qua validate khi metatype la `Object` — dung interface la duoc.
   */
  @Post('webhook')
  @Public()
  @ApiOperation({
    summary: '[PayOS] Payment result webhook',
    description:
      'Verifies the signature with PAYOS_CHECKSUM_KEY (wrong → 401). Idempotent per orderCode:' +
      'no matter how many calls, Astrite is credited only once.',
  })
  @ApiBody({ description: 'Payload sent by PayOS (code, desc, success, data, signature)' })
  handleWebhook(@Body() body: Webhook) {
    return this.topupService.handleWebhook(body);
  }

  @Get('return')
  @Public()
  @ApiExcludeEndpoint()
  paymentReturn(@Res() res: Response): void {
    sendClosePage(res, 'Payment successful', 'Astrite will be added to your wallet in a moment.');
  }

  @Get('cancel')
  @Public()
  @ApiExcludeEndpoint()
  paymentCancel(@Res() res: Response): void {
    sendClosePage(res, 'Payment cancelled', 'You were not charged.');
  }

  // ==== Luong ADMIN (JWT server) ====

  @Get('packages/admin')
  @ApiBearerAuth('admin')
  @UseGuards(AdminJwtGuard, RolesGuard)
  @Roles('admin')
  @ApiOperation({ summary: '[Admin] All top-up packages, including disabled ones' })
  listPackagesAsAdmin() {
    return this.topupService.listAllPackages();
  }

  @Get('history/admin')
  @ApiBearerAuth('admin')
  @UseGuards(AdminJwtGuard, RolesGuard)
  @Roles('admin')
  @ApiOperation({
    summary: '[Admin] System-wide top-up history + revenue stats',
    description: 'Filters run in memory — at this project\'s scale, no composite index is needed.',
  })
  listOrdersAsAdmin(@Query() query: AdminTopupsDto) {
    return this.topupService.listAllOrders(query);
  }

  @Post('packages')
  @ApiBearerAuth('admin')
  @UseGuards(AdminJwtGuard, RolesGuard)
  @Roles('admin')
  @ApiOperation({ summary: '[Admin] Add a top-up package' })
  async createPackage(@CurrentUser() actor: AuthUser, @Body() dto: CreateTopupPackageDto) {
    const pkg = await this.topupService.createPackage(dto);
    await this.auditService.log(actor, 'TOPUP_PACKAGE_CREATE', {
      id: pkg.packageId,
      label: pkg.name,
    });
    return { message: 'Package added.', data: pkg };
  }

  @Patch('packages/:id')
  @ApiBearerAuth('admin')
  @UseGuards(AdminJwtGuard, RolesGuard)
  @Roles('admin')
  @ApiOperation({
    summary: '[Admin] Edit a top-up package',
    description: 'Existing orders keep their own price and Astrite — edits here are not retroactive.',
  })
  async updatePackage(
    @CurrentUser() actor: AuthUser,
    @Param('id') packageId: string,
    @Body() dto: UpdateTopupPackageDto,
  ) {
    const pkg = await this.topupService.updatePackage(packageId, dto);
    await this.auditService.log(actor, 'TOPUP_PACKAGE_UPDATE', { id: packageId, label: pkg.name });
    return { message: 'Package updated.', data: pkg };
  }

  @Delete('packages/:id')
  @ApiBearerAuth('admin')
  @UseGuards(AdminJwtGuard, RolesGuard)
  @Roles('admin')
  @ApiOperation({
    summary: '[Admin] Delete a top-up package',
    description: 'Old order history is kept. To hide it from the app temporarily, use isActive=false.',
  })
  async deletePackage(@CurrentUser() actor: AuthUser, @Param('id') packageId: string) {
    const pkg = await this.topupService.deletePackage(packageId);
    await this.auditService.log(actor, 'TOPUP_PACKAGE_DELETE', { id: packageId, label: pkg.name });
    return { message: 'Package deleted.', data: { packageId } };
  }
}

/**
 * Trang HTML toi gian PayOS chuyen trinh duyet ve sau khi thanh toan/huy.
 * Dung `@Res()` de tra HTML tho — di qua ResponseInterceptor se thanh JSON.
 * App KHONG doc trang nay de biet ket qua: nguon su that duy nhat la webhook
 * (nguoi dung co the sua URL tren thanh dia chi).
 */
function sendClosePage(res: Response, title: string, subtitle: string): void {
  res.status(200).type('html').send(`<!doctype html>
<html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>${title} · Snapget</title>
<style>
  body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;
       font-family:system-ui,-apple-system,"Segoe UI",Roboto,sans-serif;background:#1B1A17;color:#F5F0E6}
  .card{text-align:center;padding:32px}
  h1{font-size:20px;margin:0 0 8px}
  p{margin:0;color:#B9B2A4;font-size:14px}
</style></head>
<body><div class="card"><h1>${title}</h1><p>${subtitle}</p>
<p style="margin-top:16px">You can close this tab and return to the Snapget app.</p>
</div></body></html>`);
}
