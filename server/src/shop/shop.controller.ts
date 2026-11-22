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
    summary: 'Shop catalog: items on sale + owned flag + Astrite balance',
    description: 'Returns { astrite, items[] } so opening the Shop costs a single request.',
  })
  getCatalog(@CurrentUser() user: AuthUser) {
    return this.shopService.getCatalogForUser(user.uid);
  }

  @Post('buy')
  @ApiBearerAuth('firebase')
  @UseGuards(FirebaseAuthGuard)
  @ApiOperation({
    summary: 'Buy an item with Astrite',
    description:
      'Takes only itemId — the price is always looked up on the server. Debits Astrite, unlocks the item, writes purchase history' +
      'and the SHOP_PURCHASE ledger in ONE Firestore transaction.',
  })
  @ApiResponse({ status: 201, description: 'Purchase succeeded: { purchase, astriteAfter }' })
  @ApiResponse({ status: 400, description: 'Not enough Astrite / item not on sale' })
  @ApiResponse({ status: 409, description: 'Item already owned' })
  buy(@CurrentUser() user: AuthUser, @Body() dto: BuyItemDto) {
    return this.shopService.buyItem(user.uid, dto.itemId);
  }

  @Get('history')
  @ApiBearerAuth('firebase')
  @UseGuards(FirebaseAuthGuard)
  @ApiOperation({ summary: 'Your purchase history (newest first)' })
  @ApiQuery({ name: 'limit', required: false, description: 'Max records (default 50)' })
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
  @ApiOperation({ summary: '[Admin] All items, including disabled ones' })
  listItemsAsAdmin() {
    return this.shopService.listAllItems();
  }

  @Get('history/admin')
  @ApiBearerAuth('admin')
  @UseGuards(AdminJwtGuard, RolesGuard)
  @Roles('admin')
  @ApiOperation({
    summary: '[Admin] System-wide purchase history (filter by user / type / date)',
    description: 'Filters run in memory — at this project\'s scale, no composite index is needed.',
  })
  listPurchasesAsAdmin(@Query() query: AdminPurchasesDto) {
    return this.shopService.listAllPurchases(query);
  }

  @Post('items')
  @ApiBearerAuth('admin')
  @UseGuards(AdminJwtGuard, RolesGuard)
  @Roles('admin')
  @ApiOperation({
    summary: '[Admin] List a new item (frame / skin / effect)',
    description:
      'FRAME: refId = frameId of a frame with unlockType SHOP. SKIN/EFFECT: refId = an existing numeric id' +
      'in the APK (≥ 1). Each item can be listed once. Empty price = default price by type.',
  })
  async createItem(@CurrentUser() actor: AuthUser, @Body() dto: CreateShopItemDto) {
    const item = await this.shopService.createItem(dto);
    await this.auditService.log(actor, 'SHOP_ITEM_CREATE', {
      id: item.itemId,
      label: item.itemName,
    });
    return { message: 'Item listed.', data: item };
  }

  @Get('items/:id/owners')
  @ApiBearerAuth('admin')
  @UseGuards(AdminJwtGuard, RolesGuard)
  @Roles('admin')
  @ApiOperation({ summary: '[Admin] List users who own an item' })
  listItemOwners(@Param('id') itemId: string) {
    return this.shopService.listItemOwners(itemId);
  }

  @Post('items/:id/grant/:uid')
  @ApiBearerAuth('admin')
  @UseGuards(AdminJwtGuard, RolesGuard)
  @Roles('admin')
  @ApiOperation({
    summary: '[Admin] Gift an item to a user',
    description:
      'Unlocks directly on the account (demo / compensation). Idempotent. No Astrite debit,' +
      'no ledger entry, no purchase history.',
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
    return { message: 'Item gifted to the user.', data: { itemId, uid } };
  }

  @Patch('items/:id')
  @ApiBearerAuth('admin')
  @UseGuards(AdminJwtGuard, RolesGuard)
  @Roles('admin')
  @ApiOperation({ summary: '[Admin] Edit an item (name / price / image / on-off / order)' })
  async updateItem(
    @CurrentUser() actor: AuthUser,
    @Param('id') itemId: string,
    @Body() dto: UpdateShopItemDto,
  ) {
    const item = await this.shopService.updateItem(itemId, dto);
    await this.auditService.log(actor, 'SHOP_ITEM_UPDATE', { id: itemId, label: item.itemName });
    return { message: 'Item updated.', data: item };
  }

  @Delete('items/:id')
  @ApiBearerAuth('admin')
  @UseGuards(AdminJwtGuard, RolesGuard)
  @Roles('admin')
  @ApiOperation({
    summary: '[Admin] Remove an item from the Shop',
    description: 'Buyers keep it. To hide it temporarily, use isActive=false.',
  })
  async deleteItem(@CurrentUser() actor: AuthUser, @Param('id') itemId: string) {
    const item = await this.shopService.deleteItem(itemId);
    await this.auditService.log(actor, 'SHOP_ITEM_DELETE', { id: itemId, label: item.itemName });
    return { message: 'Item removed from the Shop.', data: { itemId } };
  }
}
