import { Body, Controller, Delete, Get, Param, Patch, Post, UseGuards } from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { AuditService } from '../audit/audit.service';
import { AuthUser, CurrentUser } from '../common/decorators/current-user.decorator';
import { Roles } from '../common/decorators/roles.decorator';
import { AdminJwtGuard } from '../common/guards/admin-jwt.guard';
import { FirebaseAuthGuard } from '../common/guards/firebase-auth.guard';
import { RolesGuard } from '../common/guards/roles.guard';
import { CreateFrameDto } from './dto/create-frame.dto';
import { UpdateFrameDto } from './dto/update-frame.dto';
import { FramesService } from './frames.service';

@ApiTags('frames')
@Controller('frames')
export class FramesController {
  constructor(
    private readonly framesService: FramesService,
    private readonly auditService: AuditService,
  ) {}

  // ==== Luong USER (Firebase token) ====

  @Get()
  @ApiBearerAuth('firebase')
  @UseGuards(FirebaseAuthGuard)
  @ApiOperation({ summary: 'Catalog khung anh + trang thai da mo khoa cua minh' })
  list(@CurrentUser() user: AuthUser) {
    return this.framesService.listForUser(user.uid);
  }

  // ==== Luong ADMIN (JWT server) ====

  @Get('admin')
  @ApiBearerAuth('admin')
  @UseGuards(AdminJwtGuard, RolesGuard)
  @Roles('admin')
  @ApiOperation({ summary: '[Admin] Danh sach toan bo khung anh (khong can isUnlocked)' })
  listAsAdmin() {
    return this.framesService.listAll();
  }

  @Get(':id/owners')
  @ApiBearerAuth('admin')
  @UseGuards(AdminJwtGuard, RolesGuard)
  @Roles('admin')
  @ApiOperation({ summary: '[Admin] Danh sach user dang so huu khung nay' })
  listOwners(@Param('id') frameId: string) {
    return this.framesService.listOwners(frameId);
  }

  @Post()
  @ApiBearerAuth('admin')
  @UseGuards(AdminJwtGuard, RolesGuard)
  @Roles('admin')
  @ApiOperation({ summary: '[Admin] Them khung anh moi' })
  async create(@CurrentUser() actor: AuthUser, @Body() dto: CreateFrameDto) {
    const frame = await this.framesService.create(dto);
    await this.auditService.log(actor, 'FRAME_CREATE', {
      id: frame.frameId,
      label: frame.frameName,
    });
    return { message: 'Da them khung anh.', data: frame };
  }

  @Patch(':id')
  @ApiBearerAuth('admin')
  @UseGuards(AdminJwtGuard, RolesGuard)
  @Roles('admin')
  @ApiOperation({ summary: '[Admin] Sua khung anh (ten / anh / dieu kien mo khoa)' })
  async update(
    @CurrentUser() actor: AuthUser,
    @Param('id') frameId: string,
    @Body() dto: UpdateFrameDto,
  ) {
    const frame = await this.framesService.update(frameId, dto);
    await this.auditService.log(actor, 'FRAME_UPDATE', { id: frameId, label: frame.frameName });
    return { message: 'Da cap nhat khung anh.', data: frame };
  }

  @Delete(':id')
  @ApiBearerAuth('admin')
  @UseGuards(AdminJwtGuard, RolesGuard)
  @Roles('admin')
  @ApiOperation({ summary: '[Admin] Xoa khung anh' })
  async delete(@CurrentUser() actor: AuthUser, @Param('id') frameId: string) {
    await this.framesService.delete(frameId);
    await this.auditService.log(actor, 'FRAME_DELETE', { id: frameId });
    return { message: 'Da xoa khung anh.', data: { frameId } };
  }

  @Post(':id/grant/:uid')
  @ApiBearerAuth('admin')
  @UseGuards(AdminJwtGuard, RolesGuard)
  @Roles('admin')
  @ApiOperation({ summary: '[Admin] Mo khoa khung cho 1 user (demo phan thuong)' })
  async grant(
    @CurrentUser() actor: AuthUser,
    @Param('id') frameId: string,
    @Param('uid') uid: string,
  ) {
    await this.framesService.unlockForUser(uid, frameId);
    await this.auditService.log(actor, 'FRAME_GRANT', { id: frameId, label: `cho user ${uid}` });
    return { message: 'Da mo khoa khung cho user.', data: { frameId, uid } };
  }
}
