import { Controller, UseGuards } from '@nestjs/common';
import { ApiBearerAuth, ApiTags } from '@nestjs/swagger';
import { FirebaseAuthGuard } from '../common/guards/firebase-auth.guard';

// TODO: GET /quests/today, POST /quests/:id/submit (nop anh xac minh)
@ApiTags('quests')
@ApiBearerAuth('firebase')
@UseGuards(FirebaseAuthGuard)
@Controller('quests')
export class QuestsController {}
