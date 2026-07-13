import {
  BadRequestException,
  Controller,
  MaxFileSizeValidator,
  ParseFilePipe,
  Post,
  UploadedFile,
  UseGuards,
  UseInterceptors,
} from '@nestjs/common';
import { FileInterceptor } from '@nestjs/platform-express';
import { ApiBearerAuth, ApiBody, ApiConsumes, ApiOperation, ApiTags } from '@nestjs/swagger';
import { Roles } from '../common/decorators/roles.decorator';
import { AdminJwtGuard } from '../common/guards/admin-jwt.guard';
import { FirebaseAuthGuard } from '../common/guards/firebase-auth.guard';
import { RolesGuard } from '../common/guards/roles.guard';
import { UploadService } from './upload.service';

const MAX_UPLOAD_BYTES = 25 * 1024 * 1024; // 25MB — du cho anh + video 5s

/** Swagger schema dung chung cho body multipart. */
const FILE_BODY_SCHEMA = {
  schema: {
    type: 'object' as const,
    properties: { file: { type: 'string' as const, format: 'binary' } },
  },
};

@ApiTags('upload')
@Controller('upload')
export class UploadController {
  constructor(private readonly uploadService: UploadService) {}

  // ==== Luong USER (Firebase token) ====

  @Post()
  @ApiBearerAuth('firebase')
  @UseGuards(FirebaseAuthGuard)
  @UseInterceptors(FileInterceptor('file'))
  @ApiConsumes('multipart/form-data')
  @ApiBody(FILE_BODY_SCHEMA)
  @ApiOperation({ summary: 'Upload anh/video len Cloudinary (video toi da 5s), tra ve URL' })
  async upload(
    @UploadedFile(
      new ParseFilePipe({
        validators: [new MaxFileSizeValidator({ maxSize: MAX_UPLOAD_BYTES })],
        fileIsRequired: true,
      }),
    )
    file: Express.Multer.File,
  ) {
    return this.doUpload(file);
  }

  // ==== Luong ADMIN (JWT server) — upload anh khung tu trang admin ====

  @Post('admin')
  @ApiBearerAuth('admin')
  @UseGuards(AdminJwtGuard, RolesGuard)
  @Roles('admin')
  @UseInterceptors(FileInterceptor('file'))
  @ApiConsumes('multipart/form-data')
  @ApiBody(FILE_BODY_SCHEMA)
  @ApiOperation({ summary: '[Admin] Upload anh (khung anh...) len Cloudinary, tra ve URL' })
  async uploadAsAdmin(
    @UploadedFile(
      new ParseFilePipe({
        validators: [new MaxFileSizeValidator({ maxSize: MAX_UPLOAD_BYTES })],
        fileIsRequired: true,
      }),
    )
    file: Express.Multer.File,
  ) {
    return this.doUpload(file);
  }

  private doUpload(file: Express.Multer.File) {
    // audio/: tin nhan thoai (VOICE) ghi tu app (.m4a = audio/mp4)
    const allowed = ['image/', 'video/', 'audio/'];
    if (!allowed.some((prefix) => file.mimetype.startsWith(prefix))) {
      throw new BadRequestException('Chi nhan file anh, video hoac ghi am.');
    }
    return this.uploadService.upload(file);
  }
}
