import { Injectable } from '@nestjs/common';
import { QuestsRepository } from './quests.repository';

// TODO: xem 3 quest moi ngay, nop anh xac minh, AI verify -> thuong frame. AI lam cuoi.
@Injectable()
export class QuestsService {
  constructor(private readonly repo: QuestsRepository) {}
}
