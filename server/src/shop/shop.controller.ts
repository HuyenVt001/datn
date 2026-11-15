import {
  Body,
  Controller,
  Delete,
  Get,
  Param,
  Patch,
  Post,
  Query,
  UseGuards,
} from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiQuery, ApiResponse, ApiTags } from '@nestjs/swagger';
import { AuditService } from '../audit/audit.service';
import { AuthUser, CurrentUser } from '../common/decorators/current-user.decorator';
import { Roles } from '../common/decorators/roles.decorator';
import { AdminJwtGuard } from '../common/guards/admin-jwt.guard';
import { FirebaseAuthGuard } from '../common/guards/firebase-auth.guard';
import { RolesGuard } from '../common/guards/roles.guard';
import { AdminPurchasesDto } from './dto/admin-purchases.dto';
import { BuyItemDto } from './dto/buy-item.dto';
import { CreateShopItemDto } from './dto/create-shop-item.dto';
import { UpdateShopItemDto } from './dto/update-shop-item.dto';
import { ShopService } from './shop.service';

/**
 * Cua hang (2026-11-15 — SHOP_PLAN.md). Thay toan bo `/gacha/*`.
 * Goi nap Astrite van o `/topup/*` (khong doi) — app gop chung vao man Shop.
 */
@ApiTags('shop')
@Controller('shop')
export class ShopController {
  constructor(
    private readonly shopService: ShopService,
    private readonly auditService: AuditService,
  ) {}

  // ==== Luong USER (Firebase token) ====

  @Get('items')
  @ApiBearerAuth('firebase')
  @UseGuards(FirebaseAuthGuard)
  @ApiOperation({
    summary: 'Catalog Shop: mặt hàng đang mở bán + cờ đã sở hữu + số dư Astrite',
    description: 'Trả { astrite, items[] } để màn Shop mở lên chỉ tốn 1 request.',
  })
  getCatalog(@CurrentUser() user: AuthUser) {
    return this.shopService.getCatalogForUser(user.uid);
  }

  @Post('buy')
  @ApiBearerAuth('firebase')
  @UseGuards(FirebaseAuthGuard)
  @ApiOperation({
    summary: 'Mua 1 vật phẩm bằng Astrite',
    description:
      'Chỉ nhận itemId — giá luôn tra ở server. Trừ Astrite, mở khóa vật phẩm, ghi lịch sử mua ' +
      'và sổ cái SHOP_PURCHASE trong MỘT transaction Firestore.',
  })
  @ApiResponse({ status: 201, description: 'Mua thành công: { purchase, astriteAfter }' })
  @ApiResponse({ status: 400, description: 'Không đủ Astrite / vật phẩm không mở bán' })
  @ApiResponse({ status: 409, description: 'Đã sở hữu vật phẩm này' })
  buy(@CurrentUser() user: AuthUser, @Body() dto: BuyItemDto) {
    return this.shopService.buyItem(user.uid, dto.itemId);
  }

  @Get('history')
  @ApiBearerAuth('firebase')
  @UseGuards(FirebaseAuthGuard)
  @ApiOperation({ summary: 'Lịch sử mua của mình (mới nhất trước)' })
  @ApiQuery({ name: 'limit', required: false, description: 'Số bản ghi tối đa (mặc định 50)' })
  getHistory(@CurrentUser() user: AuthUser, @Query('limit') limit?: string) {
    const parsed = Number(limit);
    return this.shopService.getPurchaseHistory(
      user.uid,
      Number.isFinite(parsed) && parsed > 0 ? Math.min(parsed, 200) : 50,
    );
  }

  // ==== Luong ADMIN (JWT server) ====

  @Get('items/admin')
  @ApiBearerAuth('admin')
  @UseGuards(AdminJwtGuard, RolesGuard)
  @Roles('admin')
  @ApiOperation({ summary: '[Admin] Toàn bộ mặt hàng, kể cả mặt hàng đang tắt' })
  listItemsAsAdmin() {
    return this.shopService.listAllItems();
  }

  @Get('history/admin')
  @ApiBearerAuth('admin')
  @UseGuards(AdminJwtGuard, RolesGuard)
  @Roles('admin')
  @ApiOperation({
    summary: '[Admin] Lịch sử mua toàn hệ thống (lọc theo user / loại / ngày)',
    description: 'Bộ lọc chạy trong bộ nhớ — quy mô DATN, không cần composite index.',
  })
  listPurchasesAsAdmin(@Query() query: AdminPurchasesDto) {
    return this.shopService.listAllPurchases(query);
  }

  @Post('items')
  @ApiBearerAuth('admin')
  @UseGuards(AdminJwtGuard, RolesGuard)
  @Roles('admin')
  @ApiOperation({
    summary: '[Admin] Niêm yết mặt hàng mới (khung / skin / hiệu ứng)',
    description:
      'FRAME: refId = frameId của khung có unlockType SHOP. SKIN/EFFECT: refId = id số có sẵn ' +
      'trong APK (≥ 1). Mỗi vật phẩm chỉ niêm yết 1 lần. Bỏ trống giá = giá mặc định theo loại.',
  })
  async createItem(@CurrentUser() actor: AuthUser, @Body() dto: CreateShopItemDto) {
    const item = await this.shopService.createItem(dto);
    await this.auditService.log(actor, 'SHOP_ITEM_CREATE', {
      id: item.itemId,
      label: item.itemName,
    });
    return { message: 'Đã niêm yết vật phẩm.', data: item };
  }

  @Get('items/:id/owners')
  @ApiBearerAuth('admin')
  @UseGuards(AdminJwtGuard, RolesGuard)
  @Roles('admin')
  @ApiOperation({ summary: '[Admin] Danh sách user đang sở hữu vật phẩm' })
  listItemOwners(@Param('id') itemId: string) {
    return this.shopService.listItemOwners(itemId);
  }

  @Post('items/:id/grant/:uid')
  @ApiBearerAuth('admin')
  @UseGuards(AdminJwtGuard, RolesGuard)
  @Roles('admin')
  @ApiOperation({
    summary: '[Admin] Tặng vật phẩm cho user',
    description:
      'Mở khóa thẳng vào tài khoản (demo / đền bù). Idempotent. Không trừ Astrite, ' +
      'không ghi sổ cái, không ghi lịch sử mua.',
  })
  async grantItem(
    @CurrentUser() actor: AuthUser,
    @Param('id') itemId: string,
    @Param('uid') uid: string,
  ) {
    const item = await this.shopService.grantItem(itemId, uid);
    await this.auditService.log(actor, 'SHOP_ITEM_GRANT', {
      id: uid,
      label: `${item.itemName} → ${uid}`,
    });
    return { message: 'Đã tặng vật phẩm cho người dùng.', data: { itemId, uid } };
  }

  @Patch('items/:id')
  @ApiBearerAuth('admin')
  @UseGuards(AdminJwtGuard, RolesGuard)
  @Roles('admin')
  @ApiOperation({ summary: '[Admin] Sửa mặt hàng (tên / giá / ảnh / bật-tắt / thứ tự)' })
  async updateItem(
    @CurrentUser() actor: AuthUser,
    @Param('id') itemId: string,
    @Body() dto: UpdateShopItemDto,
  ) {
    const item = await this.shopService.updateItem(itemId, dto);
    await this.auditService.log(actor, 'SHOP_ITEM_UPDATE', { id: itemId, label: item.itemName });
    return { message: 'Đã cập nhật vật phẩm.', data: item };
  }

  @Delete('items/:id')
  @ApiBearerAuth('admin')
  @UseGuards(AdminJwtGuard, RolesGuard)
  @Roles('admin')
  @ApiOperation({
    summary: '[Admin] Gỡ mặt hàng khỏi Shop',
    description: 'Người đã mua vẫn giữ. Muốn tạm ẩn thì dùng isActive=false.',
  })
  async deleteItem(@CurrentUser() actor: AuthUser, @Param('id') itemId: string) {
    const item = await this.shopService.deleteItem(itemId);
    await this.auditService.log(actor, 'SHOP_ITEM_DELETE', { id: itemId, label: item.itemName });
    return { message: 'Đã gỡ vật phẩm khỏi Cửa hàng.', data: { itemId } };
  }
}
