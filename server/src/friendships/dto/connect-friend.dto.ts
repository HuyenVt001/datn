import { ApiProperty } from '@nestjs/swagger';
import { IsNotEmpty, IsString } from 'class-validator';

/** Ket ban bang ma moi (lay tu invite link cua nguoi kia). */
export class ConnectFriendDto {
  @ApiProperty({ description: 'Friend invite code (inviteCode) of the link sender' })
  @IsString()
  @IsNotEmpty()
  inviteCode!: string;
}
