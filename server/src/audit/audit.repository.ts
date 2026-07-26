import { Injectable } from '@nestjs/common';
import { Collections } from '../common/constants';
import { FirebaseService } from '../firebase/firebase.service';
import { AdminLog } from './entities/admin-log.entity';

/** NOI DUY NHAT cham Firestore cho audit log (collection adminLogs). */
@Injectable()
export class AuditRepository {
  constructor(private readonly firebase: FirebaseService) {}

  private get col() {
    return this.firebase.firestore().collection(Collections.ADMIN_LOGS);
  }

  async add(log: Omit<AdminLog, 'logId'>): Promise<void> {
    const data = Object.fromEntries(Object.entries(log).filter(([, v]) => v !== undefined));
    await this.col.add(data);
  }

  /**
   * Nhat ky moi nhat truoc (orderBy 1 field — index tu dong). Gioi han 500 dong
   * gan nhat roi phan trang trong bo nho — du cho quy mo DATN.
   */
  async listLatest(max = 500): Promise<AdminLog[]> {
    const snap = await this.col.orderBy('createdAt', 'desc').limit(max).get();
    return snap.docs.map((d) => ({ logId: d.id, ...(d.data() as Omit<AdminLog, 'logId'>) }));
  }
}
