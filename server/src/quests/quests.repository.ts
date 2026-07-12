import { Injectable } from '@nestjs/common';
import { Collections } from '../common/constants';
import { FirebaseService } from '../firebase/firebase.service';

/** NOI DUY NHAT cham Firestore cho daily quests + user quests. */
@Injectable()
export class QuestsRepository {
  constructor(private readonly firebase: FirebaseService) {}

  private get dailyQuests() {
    return this.firebase.firestore().collection(Collections.DAILY_QUESTS);
  }

  private get userQuests() {
    return this.firebase.firestore().collection(Collections.USER_QUESTS);
  }

  // TODO: lay quest theo ngay, nop proof, cap nhat status. AI sinh quest — lam cuoi cung.
}
