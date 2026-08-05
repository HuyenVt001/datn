import { Module } from '@nestjs/common';
import { AstriteModule } from '../astrite/astrite.module';
import { UsersController } from './users.controller';
import { UsersRepository } from './users.repository';
import { UsersService } from './users.service';

@Module({
  imports: [AstriteModule], // thuong tan thu 1600 Astrite khi tao tai khoan
  controllers: [UsersController],
  providers: [UsersService, UsersRepository],
  exports: [UsersService, UsersRepository],
})
export class UsersModule {}
