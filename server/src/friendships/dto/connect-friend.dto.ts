import { ApiProperty } from '@nestjs/swagger';
import { IsNotEmpty, IsString } from 'class-validator';

/** Ket ban bang ma moi (lay tu invite link cua nguoi kia). */
export class ConnectFriendDto {
  @ApiProperty({ description: 'Ma moi ket ban (inviteCode) cua nguoi gui link' })
  @IsString()
  @IsNotEmpty()
  inviteCode!: string;
}
