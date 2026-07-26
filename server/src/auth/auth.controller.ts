import { Body, Controller, Post } from '@nestjs/common';
import { ApiOperation, ApiTags } from '@nestjs/swagger';
import { Throttle } from '@nestjs/throttler';
import { Public } from '../common/decorators/public.decorator';
import { AdminLoginDto } from './dto/admin-login.dto';
import { AuthService } from './auth.service';

@ApiTags('auth')
@Controller('auth')
export class AuthController {
  constructor(private readonly authService: AuthService) {}

  @Public()
  @Throttle({ default: { ttl: 60_000, limit: 10 } }) // siet rieng: chong brute-force login
  @Post('admin/login')
  @ApiOperation({ summary: 'Admin dang nhap: doi Firebase token lay JWT server' })
  adminLogin(@Body() dto: AdminLoginDto) {
    return this.authService.adminLogin(dto.idToken);
  }
}
