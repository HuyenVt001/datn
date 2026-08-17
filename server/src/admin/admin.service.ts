import { BadRequestException, Injectable, NotFoundException } from '@nestjs/common';
import { AuditService } from '../audit/audit.service';
import { AuthUser } from '../common/decorators/current-user.decorator';
import { PaginatedResult, PaginationDto } from '../common/dto/pagination.dto';
import { FirebaseService } from '../firebase/firebase.service';
import { GachaService } from '../gacha/gacha.service';
import { MomentsRepository } from '../moments/moments.repository';
import { QuestsService } from '../quests/quests.service';
import { AdminRepository, AdminStats, DailyStat } from './admin.repository';
import { ListAiVerificationsDto } from './dto/list-ai-verifications.dto';
import { ListUsersDto } from './dto/list-users.dto';

/** 1 dong trong danh sach user cua trang admin. */
export interface AdminUserRow {
  uid: string;
  email?: string;
  fullName: string;
  disabled: boolean;
  /** true = co custom claim admin (truy cap duoc trang quan tri). */
  admin: boolean;
  createdAt: string;
  lastSignInAt?: string;
  /** **(2026-08-05)** So du Astrite (doc Firestore; user chua co field = 0). */
  astrite: number;
}

/** 1 dong trong danh sach bai dang cua trang admin kiem duyet. */
export interface AdminMomentRow {
  momentId: string;
  userId: string;
  authorName: string;
  contentType: string;
  mediaUrl: string;
  caption?: string;
  coopUserId?: string;
  postTime: string;
}

@Injectable()
export class AdminService {
  constructor(
    private readonly firebase: FirebaseService,
    private readonly adminRepo: AdminRepository,
    private readonly momentsRepo: MomentsRepository,
    private readonly questsService: QuestsService,
    private readonly gachaService: GachaService,
    private readonly audit: AuditService,
  ) {}

  /**
   * Danh sach nguoi dung (tu Firebase Auth), tim kiem + phan trang trong bo nho.
   * Quy mo DATN (< 1000 user) nen listUsers 1 trang 1000 la du.
   * fullName Firestore duoc merge TRUOC khi search (fix 2026-07-26): ten hien
   * thi la ten Firestore nen search phai chay tren ten do, khong phai
   * displayName cua Auth (user doi ten trong app khong sync len Auth).
   */
  async listUsers(query: ListUsersDto): Promise<PaginatedResult<AdminUserRow>> {
    const [result, summaries] = await Promise.all([
      this.firebase.auth().listUsers(1000),
      this.adminRepo.getAllUserSummaries(),
    ]);
    let users: AdminUserRow[] = result.users.map((u) => ({
      uid: u.uid,
      email: u.email,
      fullName: summaries.get(u.uid)?.fullName || u.displayName || '',
      disabled: u.disabled,
      admin: u.customClaims?.admin === true,
      createdAt: u.metadata.creationTime,
      lastSignInAt: u.metadata.lastSignInTime,
      astrite: summaries.get(u.uid)?.astrite ?? 0,
    }));

    if (query.search) {
      const q = query.search.toLowerCase();
      users = users.filter(
        (u) => u.email?.toLowerCase().includes(q) || u.fullName.toLowerCase().includes(q),
      );
    }

    const { page, limit } = query;
    const start = (page - 1) * limit;
    return { items: users.slice(start, start + limit), page, limit, total: users.length };
  }

  /** Thong ke tong quan cho dashboard (kem quest hom nay + so luot quay gacha + AI verify hom nay). */
  async getStats(): Promise<AdminStats> {
    const [stats, questCompletionsToday, rolls, ai] = await Promise.all([
      this.adminRepo.getStats(),
      this.questsService.countCompletionsToday(),
      this.gachaService.getRollCounts(),
      // Best-effort: collection aiVerifications co the chua ton tai / AI tat -> 0, khong fail dashboard
      this.questsService.getAiVerificationStatsToday().catch(() => undefined),
    ]);
    return {
      ...stats,
      questCompletionsToday,
      gachaRollsToday: rolls.today,
      gachaRollsTotal: rolls.total,
      aiVerificationsToday: ai?.total ?? 0,
      aiMatchedToday: ai?.matched ?? 0,
    };
  }

  /** Log AI xac minh anh quest (trang admin) — uy quyen QuestsService (chu collection). */
  listAiVerifications(query: ListAiVerificationsDto) {
    return this.questsService.listAiVerifications(query, {
      outcome: query.outcome,
      date: query.date,
      uid: query.uid,
    });
  }

  /**
   * Thong ke theo ngay cho bieu do dashboard: so moment + so user dang ky moi
   * cua `days` ngay gan nhat (tinh theo UTC, khop dateKey cua streak/quest).
   */
  async getDailyStats(days: number): Promise<DailyStat[]> {
    const [momentsByDay, authList] = await Promise.all([
      this.adminRepo.countMomentsByDay(days),
      this.firebase.auth().listUsers(1000),
    ]);

    // Dem user moi theo ngay tao tai khoan (trong bo nho — quy mo DATN).
    const newUsersByDay = new Map<string, number>();
    for (const u of authList.users) {
      const day = new Date(u.metadata.creationTime).toISOString().slice(0, 10);
      newUsersByDay.set(day, (newUsersByDay.get(day) ?? 0) + 1);
    }

    return momentsByDay.map((d) => ({
      ...d,
      newUsers: newUsersByDay.get(d.date) ?? 0,
    }));
  }

  /**
   * Khoa / mo khoa tai khoan nguoi dung (Firebase Auth).
   * - Khong the tu khoa chinh minh (dam bao he thong luon con >= 1 admin hoat dong).
   * - Khoa thi thu hoi luon refresh token -> phien app cua user do chet trong <= 1h.
   */
  async setUserDisabled(
    actor: AuthUser,
    uid: string,
    disabled: boolean,
  ): Promise<{ uid: string; disabled: boolean }> {
    if (disabled && uid === actor.uid) {
      throw new BadRequestException('Ban khong the tu khoa tai khoan cua chinh minh.');
    }
    const target = await this.getUserOrThrow(uid); // 404 ro rang thay vi 500 khi uid khong ton tai
    await this.firebase.auth().updateUser(uid, { disabled });
    if (disabled) {
      await this.firebase.auth().revokeRefreshTokens(uid);
    }
    await this.audit.log(actor, disabled ? 'USER_DISABLE' : 'USER_ENABLE', {
      id: uid,
      label: target.email ?? uid,
    });
    return { uid, disabled };
  }

  /** Cap quyen admin cho 1 user qua custom claims { admin: true } (giu cac claim khac). */
  async grantAdmin(actor: AuthUser, uid: string): Promise<{ uid: string; admin: boolean }> {
    const user = await this.getUserOrThrow(uid);
    await this.firebase.auth().setCustomUserClaims(uid, { ...user.customClaims, admin: true });
    await this.audit.log(actor, 'GRANT_ADMIN', { id: uid, label: user.email ?? uid });
    return { uid, admin: true };
  }

  /**
   * Thu hoi quyen admin. Khong the tu thu quyen chinh minh — ket hop voi viec
   * nguoi goi luon la admin (guard) thi thao tac TUAN TU khong bao gio lam mat
   * admin cuoi cung. Chong RACE (2 admin thu quyen lan nhau dong thoi -> ca 2
   * lenh deu chay -> 0 admin): sau khi ghi, dem lai so admin con hoat dong;
   * neu ve 0 thi KHOI PHUC claim va bao loi — he thong tu lanh, khong bao gio
   * ket thuc o trang thai khong con admin nao.
   */
  async revokeAdmin(actor: AuthUser, uid: string): Promise<{ uid: string; admin: boolean }> {
    if (uid === actor.uid) {
      throw new BadRequestException('Ban khong the tu thu quyen admin cua chinh minh.');
    }
    const user = await this.getUserOrThrow(uid);
    if (user.customClaims?.admin !== true) {
      throw new BadRequestException('Nguoi dung nay khong phai admin.');
    }
    await this.firebase.auth().setCustomUserClaims(uid, { ...user.customClaims, admin: false });

    const remaining = await this.countActiveAdmins();
    if (remaining === 0) {
      await this.firebase.auth().setCustomUserClaims(uid, { ...user.customClaims, admin: true });
      throw new BadRequestException(
        'Khong the thu quyen: he thong se khong con admin nao. Quyen cua nguoi nay da duoc giu nguyen.',
      );
    }
    await this.audit.log(actor, 'REVOKE_ADMIN', { id: uid, label: user.email ?? uid });
    return { uid, admin: false };
  }

  /** Danh sach bai dang moi nhat (kiem duyet) — enrich ten tac gia tu Firestore. */
  async listMoments(pagination: PaginationDto): Promise<PaginatedResult<AdminMomentRow>> {
    const [moments, names] = await Promise.all([
      this.momentsRepo.listAll(),
      this.adminRepo.getAllFullNames(),
    ]);
    const rows: AdminMomentRow[] = moments.map((m) => ({
      momentId: m.momentId,
      userId: m.userId,
      authorName: names.get(m.userId) || m.userId,
      contentType: m.contentType,
      mediaUrl: m.mediaUrl,
      caption: m.caption,
      coopUserId: m.coopUserId,
      postTime: m.postTime,
    }));
    const { page, limit } = pagination;
    const start = (page - 1) * limit;
    return { items: rows.slice(start, start + limit), page, limit, total: rows.length };
  }

  /** Admin xoa bai vi pham (xoa kem subcollection views/reactions nhu chu bai tu xoa). */
  async deleteMoment(actor: AuthUser, momentId: string): Promise<{ momentId: string }> {
    const moment = await this.momentsRepo.findById(momentId);
    if (!moment) {
      throw new NotFoundException('Khong tim thay bai dang.');
    }
    await this.momentsRepo.delete(momentId);
    await this.audit.log(actor, 'MOMENT_DELETE', {
      id: momentId,
      label: moment.caption || `bai cua ${moment.userId}`,
    });
    return { momentId };
  }

  /** Dem admin con hieu luc (co claim admin va khong bi khoa). */
  private async countActiveAdmins(): Promise<number> {
    const result = await this.firebase.auth().listUsers(1000);
    return result.users.filter((u) => u.customClaims?.admin === true && !u.disabled).length;
  }

  private async getUserOrThrow(uid: string) {
    try {
      return await this.firebase.auth().getUser(uid);
    } catch {
      throw new NotFoundException('Khong tim thay nguoi dung.');
    }
  }
}
