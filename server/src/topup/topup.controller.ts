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
  @ApiOperation({ summary: 'Danh sách gói nạp đang bật' })
  listPackages() {
    return this.topupService.listPackagesForApp();
  }

  @Post('orders')
  @ApiBearerAuth('firebase')
  @UseGuards(FirebaseAuthGuard)
  @ApiOperation({
    summary: 'Tạo đơn nạp và lấy link thanh toán PayOS',
    description:
      'Body CHỈ có packageId. Số tiền và số Astrite tra từ topupPackages ở server — ' +
      'không bao giờ nhận từ client.',
  })
  createOrder(@CurrentUser() user: AuthUser, @Body() dto: CreateTopupOrderDto) {
    return this.topupService.createOrder(user.uid, dto.packageId);
  }

  @Get('orders/:orderCode')
  @ApiBearerAuth('firebase')
  @UseGuards(FirebaseAuthGuard)
  @ApiOperation({
    summary: 'Trạng thái đơn nạp của mình',
    description: 'App poll endpoint này sau khi người dùng đóng trang thanh toán.',
  })
  getOrder(@CurrentUser() user: AuthUser, @Param('orderCode', ParseIntPipe) orderCode: number) {
    return this.topupService.getOrderForUser(user.uid, orderCode);
  }

  @Get('history')
  @ApiBearerAuth('firebase')
  @UseGuards(FirebaseAuthGuard)
  @ApiOperation({ summary: 'Lịch sử nạp của mình (mới nhất trước)' })
  @ApiQuery({ name: 'limit', required: false, description: 'Số bản ghi tối đa (mặc định 50)' })
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
    summary: '[DEV] Giả lập PayOS báo đã thanh toán',
    description:
      'Chỉ chạy khi NODE_ENV !== production. Tiết kiệm hạn mức 100 giao dịch của gói FREE-100: ' +
      'đi đúng đường mà webhook thật đi (verify chữ ký → transaction → cộng Astrite → ghi sổ cái). ' +
      'Gọi nhiều lần cùng orderCode để kiểm tra idempotent.',
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
    summary: '[PayOS] Webhook báo kết quả thanh toán',
    description:
      'Verify chữ ký bằng PAYOS_CHECKSUM_KEY (sai → 401). Idempotent theo orderCode: ' +
      'gọi lại bao nhiêu lần cũng chỉ cộng Astrite một lần.',
  })
  @ApiBody({ description: 'Payload PayOS gửi (code, desc, success, data, signature)' })
  handleWebhook(@Body() body: Webhook) {
    return this.topupService.handleWebhook(body);
  }

  @Get('return')
  @Public()
  @ApiExcludeEndpoint()
  paymentReturn(@Res() res: Response): void {
    sendClosePage(res, 'Thanh toán thành công', 'Astrite sẽ được cộng vào ví trong giây lát.');
  }

  @Get('cancel')
  @Public()
  @ApiExcludeEndpoint()
  paymentCancel(@Res() res: Response): void {
    sendClosePage(res, 'Đã huỷ thanh toán', 'Không có khoản tiền nào bị trừ.');
  }

  // ==== Luong ADMIN (JWT server) ====

  @Get('packages/admin')
  @ApiBearerAuth('admin')
  @UseGuards(AdminJwtGuard, RolesGuard)
  @Roles('admin')
  @ApiOperation({ summary: '[Admin] Toàn bộ gói nạp, kể cả gói đang tắt' })
  listPackagesAsAdmin() {
    return this.topupService.listAllPackages();
  }

  @Get('history/admin')
  @ApiBearerAuth('admin')
  @UseGuards(AdminJwtGuard, RolesGuard)
  @Roles('admin')
  @ApiOperation({
    summary: '[Admin] Lịch sử nạp toàn hệ thống + thống kê doanh thu',
    description: 'Bộ lọc chạy trong bộ nhớ — quy mô DATN, không cần composite index.',
  })
  listOrdersAsAdmin(@Query() query: AdminTopupsDto) {
    return this.topupService.listAllOrders(query);
  }

  @Post('packages')
  @ApiBearerAuth('admin')
  @UseGuards(AdminJwtGuard, RolesGuard)
  @Roles('admin')
  @ApiOperation({ summary: '[Admin] Thêm gói nạp' })
  async createPackage(@CurrentUser() actor: AuthUser, @Body() dto: CreateTopupPackageDto) {
    const pkg = await this.topupService.createPackage(dto);
    await this.auditService.log(actor, 'TOPUP_PACKAGE_CREATE', {
      id: pkg.packageId,
      label: pkg.name,
    });
    return { message: 'Da them goi nap.', data: pkg };
  }

  @Patch('packages/:id')
  @ApiBearerAuth('admin')
  @UseGuards(AdminJwtGuard, RolesGuard)
  @Roles('admin')
  @ApiOperation({
    summary: '[Admin] Sửa gói nạp',
    description: 'Đơn đã tạo giữ nguyên giá và số Astrite của chính nó — sửa ở đây không hồi tố.',
  })
  async updatePackage(
    @CurrentUser() actor: AuthUser,
    @Param('id') packageId: string,
    @Body() dto: UpdateTopupPackageDto,
  ) {
    const pkg = await this.topupService.updatePackage(packageId, dto);
    await this.auditService.log(actor, 'TOPUP_PACKAGE_UPDATE', { id: packageId, label: pkg.name });
    return { message: 'Da cap nhat goi nap.', data: pkg };
  }

  @Delete('packages/:id')
  @ApiBearerAuth('admin')
  @UseGuards(AdminJwtGuard, RolesGuard)
  @Roles('admin')
  @ApiOperation({
    summary: '[Admin] Xoá gói nạp',
    description: 'Lịch sử đơn cũ vẫn giữ. Muốn tạm ẩn khỏi app thì dùng isActive=false.',
  })
  async deletePackage(@CurrentUser() actor: AuthUser, @Param('id') packageId: string) {
    const pkg = await this.topupService.deletePackage(packageId);
    await this.auditService.log(actor, 'TOPUP_PACKAGE_DELETE', { id: packageId, label: pkg.name });
    return { message: 'Da xoa goi nap.', data: { packageId } };
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
<html lang="vi"><head><meta charset="utf-8">
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
<p style="margin-top:16px">Bạn có thể đóng tab này và quay lại ứng dụng Snapget.</p>
</div></body></html>`);
}
