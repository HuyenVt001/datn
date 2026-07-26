import { Injectable } from '@nestjs/common';
import { PaginatedResult } from '../common/dto/pagination.dto';
import { FirebaseService } from '../firebase/firebase.service';
import { QuestsService } from '../quests/quests.service';
import { AdminRepository, AdminStats } from './admin.repository';
import { ListUsersDto } from './dto/list-users.dto';

/** 1 dong trong danh sach user cua trang admin. */
export interface AdminUserRow {
  uid: string;
  email?: string;
  fullName: string;
  disabled: boolean;
  createdAt: string;
}

@Injectable()
export class AdminService {
  constructor(
    private readonly firebase: FirebaseService,
    private readonly adminRepo: AdminRepository,
    private readonly questsService: QuestsService,
  ) {}

  /**
   * Danh sach nguoi dung (tu Firebase Auth), tim kiem + phan trang trong bo nho.
   * Quy mo DATN (< 1000 user) nen listUsers 1 trang 1000 la du.
   */
  async listUsers(query: ListUsersDto): Promise<PaginatedResult<AdminUserRow>> {
    const result = await this.firebase.auth().listUsers(1000);
    let users = result.users.map((u) => ({
      uid: u.uid,
      email: u.email,
      fullName: u.displayName ?? '',
      disabled: u.disabled,
      createdAt: u.metadata.creationTime,
    }));

    if (query.search) {
      const q = query.search.toLowerCase();
      users = users.filter(
        (u) => u.email?.toLowerCase().includes(q) || u.fullName.toLowerCase().includes(q),
      );
    }

    const { page, limit } = query;
    const start = (page - 1) * limit;
    const items = users.slice(start, start + limit);

    // Enrich fullName tu Firestore (chi cho trang hien tai — tranh N read lon).
    const names = await this.adminRepo.getFullNames(items.map((u) => u.uid));
    for (const item of items) {
      item.fullName = names.get(item.uid) || item.fullName;
    }

    return { items, page, limit, total: users.length };
  }

  /** Thong ke tong quan cho dashboard (kem so luot hoan thanh quest hom nay). */
  async getStats(): Promise<AdminStats> {
    const [stats, questCompletionsToday] = await Promise.all([
      this.adminRepo.getStats(),
      this.questsService.countCompletionsToday(),
    ]);
    return { ...stats, questCompletionsToday };
  }

  /** Khoa / mo khoa tai khoan nguoi dung (Firebase Auth). */
  async setUserDisabled(
    uid: string,
    disabled: boolean,
  ): Promise<{ uid: string; disabled: boolean }> {
    await this.firebase.auth().updateUser(uid, { disabled });
    return { uid, disabled };
  }

  /** Cap quyen admin cho 1 user qua custom claims { admin: true }. */
  async grantAdmin(uid: string): Promise<{ uid: string; admin: boolean }> {
    await this.firebase.auth().setCustomUserClaims(uid, { admin: true });
    return { uid, admin: true };
  }
}
