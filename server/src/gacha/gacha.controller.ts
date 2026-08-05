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
import { ApiBearerAuth, ApiOperation, ApiQuery, ApiTags } from '@nestjs/swagger';
import { AuditService } from '../audit/audit.service';
import { AuthUser, CurrentUser } from '../common/decorators/current-user.decorator';
import { Roles } from '../common/decorators/roles.decorator';
import { AdminJwtGuard } from '../common/guards/admin-jwt.guard';
import { FirebaseAuthGuard } from '../common/guards/firebase-auth.guard';
import { RolesGuard } from '../common/guards/roles.guard';
import { AdminRollsDto } from './dto/admin-rolls.dto';
import { CreateGachaItemDto } from './dto/create-gacha-item.dto';
import { RollDto } from './dto/roll.dto';
import { UpdateGachaItemDto } from './dto/update-gacha-item.dto';
import { GachaService } from './gacha.service';

@ApiTags('gacha')
@Controller('gacha')
export class GachaController {
  constructor(
    private readonly gachaService: GachaService,
    private readonly auditService: AuditService,
  ) {}

  // ==== Luong USER (Firebase token) ====

  @Get('state')
  @ApiBearerAuth('firebase')
  @UseGuards(FirebaseAuthGuard)
  @ApiOperation({
    summary: 'Trạng thái màn Gacha: số dư Astrite, bộ đếm pity, giá quay, tỉ lệ gốc',
    description:
      'App dùng chính dữ liệu này để tự sinh popup "Rule gacha" — số hiển thị luôn khớp tỉ lệ đang chạy ở server.',
  })
  getState(@CurrentUser() user: AuthUser) {
    return this.gachaService.getState(user.uid);
  }

  @Get('items')
  @ApiBearerAuth('firebase')
  @UseGuards(FirebaseAuthGuard)
  @ApiOperation({ summary: 'Catalog vật phẩm đang bật + trạng thái đã sở hữu của mình' })
  listItems(@CurrentUser() user: AuthUser) {
    return this.gachaService.listItemsForUser(user.uid);
  }

  @Get('history')
  @ApiBearerAuth('firebase')
  @UseGuards(FirebaseAuthGuard)
  @ApiOperation({ summary: 'Lịch sử quay của mình (mới nhất trước)' })
  @ApiQuery({ name: 'limit', required: false, description: 'Số bản ghi tối đa (mặc định 50)' })
  getHistory(@CurrentUser() user: AuthUser, @Query('limit') limit?: string) {
    const parsed = Number(limit);
    return this.gachaService.getRollHistory(
      user.uid,
      Number.isFinite(parsed) && parsed > 0 ? Math.min(parsed, 200) : 50,
    );
  }

  @Post('roll')
  @ApiBearerAuth('firebase')
  @UseGuards(FirebaseAuthGuard)
  @ApiOperation({
    summary: 'Quay gacha 1 hoặc 10 lần',
    description:
      'Trừ Astrite, random ở SERVER (không bao giờ ở client), áp bảo hiểm pity, mở khoá vật phẩm ' +
      'và ghi sổ cái — tất cả trong MỘT transaction Firestore.',
  })
  roll(@CurrentUser() user: AuthUser, @Body() dto: RollDto) {
    return this.gachaService.roll(user.uid, dto.times);
  }

  // ==== Luong ADMIN (JWT server) ====

  @Get('items/admin')
  @ApiBearerAuth('admin')
  @UseGuards(AdminJwtGuard, RolesGuard)
  @Roles('admin')
  @ApiOperation({ summary: '[Admin] Toàn bộ kho vật phẩm, kể cả vật phẩm đang tắt' })
  listItemsAsAdmin() {
    return this.gachaService.listAllItems();
  }

  @Get('history/admin')
  @ApiBearerAuth('admin')
  @UseGuards(AdminJwtGuard, RolesGuard)
  @Roles('admin')
  @ApiOperation({
    summary: '[Admin] Lịch sử quay toàn hệ thống (lọc theo user / bậc / ngày)',
    description: 'Bộ lọc chạy trong bộ nhớ — quy mô DATN, không cần composite index.',
  })
  listRollsAsAdmin(@Query() query: AdminRollsDto) {
    return this.gachaService.listAllRolls(query);
  }

  @Post('items')
  @ApiBearerAuth('admin')
  @UseGuards(AdminJwtGuard, RolesGuard)
  @Roles('admin')
  @ApiOperation({
    summary: '[Admin] Thêm vật phẩm vào kho quay (CHỈ khung ảnh)',
    description:
      'Skin và hiệu ứng touch có asset nằm trong APK nên không tạo mới được từ đây — chỉ sửa thông tin.',
  })
  async createItem(@CurrentUser() actor: AuthUser, @Body() dto: CreateGachaItemDto) {
    const item = await this.gachaService.createItem(dto);
    await this.auditService.log(actor, 'GACHA_ITEM_CREATE', {
      id: item.itemId,
      label: item.itemName,
    });
    return { message: 'Da them vat pham vao kho quay.', data: item };
  }

  @Get('items/:id/owners')
  @ApiBearerAuth('admin')
  @UseGuards(AdminJwtGuard, RolesGuard)
  @Roles('admin')
  @ApiOperation({
    summary: '[Admin] Danh sách user đang sở hữu vật phẩm',
    description: 'Dùng cho drawer "Ai đang sở hữu?" ở trang Kho vật phẩm.',
  })
  listItemOwners(@Param('id') itemId: string) {
    return this.gachaService.listItemOwners(itemId);
  }

  @Post('items/:id/grant/:uid')
  @ApiBearerAuth('admin')
  @UseGuards(AdminJwtGuard, RolesGuard)
  @Roles('admin')
  @ApiOperation({
    summary: '[Admin] Tặng vật phẩm cho user (kho thưởng)',
    description:
      'Mở khoá thẳng vào tài khoản — dùng để demo/đền bù. Idempotent: tặng lại vật phẩm ' +
      'đã sở hữu thì không đổi gì. Không liên quan Astrite, không ghi sổ cái.',
  })
  async grantItem(
    @CurrentUser() actor: AuthUser,
    @Param('id') itemId: string,
    @Param('uid') uid: string,
  ) {
    const item = await this.gachaService.grantItem(itemId, uid);
    await this.auditService.log(actor, 'GACHA_ITEM_GRANT', {
      id: uid,
      label: `${item.itemName} → ${uid}`,
    });
    return { message: 'Da tang vat pham cho nguoi dung.', data: { itemId, uid } };
  }

  @Patch('items/:id')
  @ApiBearerAuth('admin')
  @UseGuards(AdminJwtGuard, RolesGuard)
  @Roles('admin')
  @ApiOperation({ summary: '[Admin] Sửa vật phẩm (tên / phẩm chất / ảnh / bật-tắt / thứ tự)' })
  async updateItem(
    @CurrentUser() actor: AuthUser,
    @Param('id') itemId: string,
    @Body() dto: UpdateGachaItemDto,
  ) {
    const item = await this.gachaService.updateItem(itemId, dto);
    await this.auditService.log(actor, 'GACHA_ITEM_UPDATE', { id: itemId, label: item.itemName });
    return { message: 'Da cap nhat vat pham.', data: item };
  }

  @Delete('items/:id')
  @ApiBearerAuth('admin')
  @UseGuards(AdminJwtGuard, RolesGuard)
  @Roles('admin')
  @ApiOperation({
    summary: '[Admin] Xoá vật phẩm khỏi kho quay',
    description: 'Chỉ xoá khỏi kho — người đã sở hữu vẫn giữ. Muốn tạm ẩn thì dùng isActive=false.',
  })
  async deleteItem(@CurrentUser() actor: AuthUser, @Param('id') itemId: string) {
    const item = await this.gachaService.deleteItem(itemId);
    await this.auditService.log(actor, 'GACHA_ITEM_DELETE', { id: itemId, label: item.itemName });
    return { message: 'Da xoa vat pham khoi kho quay.', data: { itemId } };
  }
}
