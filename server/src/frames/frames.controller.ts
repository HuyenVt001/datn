import { Body, Controller, Delete, Get, Param, Post, UseGuards } from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { AuthUser, CurrentUser } from '../common/decorators/current-user.decorator';
import { Roles } from '../common/decorators/roles.decorator';
import { AdminJwtGuard } from '../common/guards/admin-jwt.guard';
import { FirebaseAuthGuard } from '../common/guards/firebase-auth.guard';
import { RolesGuard } from '../common/guards/roles.guard';
import { CreateFrameDto } from './dto/create-frame.dto';
import { FramesService } from './frames.service';

@ApiTags('frames')
@Controller('frames')
export class FramesController {
  constructor(private readonly framesService: FramesService) {}

  // ==== Luong USER (Firebase token) ====

  @Get()
  @ApiBearerAuth('firebase')
  @UseGuards(FirebaseAuthGuard)
  @ApiOperation({ summary: 'Catalog khung anh + trang thai da mo khoa cua minh' })
  list(@CurrentUser() user: AuthUser) {
    return this.framesService.listForUser(user.uid);
  }

  // ==== Luong ADMIN (JWT server) ====

  @Post()
  @ApiBearerAuth('admin')
  @UseGuards(AdminJwtGuard, RolesGuard)
  @Roles('admin')
  @ApiOperation({ summary: '[Admin] Them khung anh moi' })
  async create(@Body() dto: CreateFrameDto) {
    const frame = await this.framesService.create(dto);
    return { message: 'Da them khung anh.', data: frame };
  }

  @Delete(':id')
  @ApiBearerAuth('admin')
  @UseGuards(AdminJwtGuard, RolesGuard)
  @Roles('admin')
  @ApiOperation({ summary: '[Admin] Xoa khung anh' })
  async delete(@Param('id') frameId: string) {
    await this.framesService.delete(frameId);
    return { message: 'Da xoa khung anh.', data: { frameId } };
  }

  @Post(':id/grant/:uid')
  @ApiBearerAuth('admin')
  @UseGuards(AdminJwtGuard, RolesGuard)
  @Roles('admin')
  @ApiOperation({ summary: '[Admin] Mo khoa khung cho 1 user (demo phan thuong)' })
  async grant(@Param('id') frameId: string, @Param('uid') uid: string) {
    await this.framesService.unlockForUser(uid, frameId);
    return { message: 'Da mo khoa khung cho user.', data: { frameId, uid } };
  }
}
