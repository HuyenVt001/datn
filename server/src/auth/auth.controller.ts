import { Body, Controller, Post } from '@nestjs/common';
import { ApiOperation, ApiTags } from '@nestjs/swagger';
import { Public } from '../common/decorators/public.decorator';
import { AdminLoginDto } from './dto/admin-login.dto';
import { AuthService } from './auth.service';

@ApiTags('auth')
@Controller('auth')
export class AuthController {
  constructor(private readonly authService: AuthService) {}

  @Public()
  @Post('admin/login')
  @ApiOperation({ summary: 'Admin dang nhap: doi Firebase token lay JWT server' })
  adminLogin(@Body() dto: AdminLoginDto) {
    return this.authService.adminLogin(dto.idToken);
  }
}
