import { BadRequestException, NotFoundException } from '@nestjs/common';
import { AuditService } from '../audit/audit.service';
import { AuthUser } from '../common/decorators/current-user.decorator';
import { FirebaseService } from '../firebase/firebase.service';
import { GachaService } from '../gacha/gacha.service';
import { MomentsRepository } from '../moments/moments.repository';
import { QuestsService } from '../quests/quests.service';
import { AdminRepository } from './admin.repository';
import { AdminService } from './admin.service';

const actor = (uid: string): AuthUser => ({ uid, email: `${uid}@test.com` });

describe('AdminService', () => {
  let service: AdminService;
  let firebase: { auth: jest.Mock };
  let auth: {
    listUsers: jest.Mock;
    updateUser: jest.Mock;
    setCustomUserClaims: jest.Mock;
    revokeRefreshTokens: jest.Mock;
    getUser: jest.Mock;
  };
  let adminRepo: jest.Mocked<AdminRepository>;
  let momentsRepo: jest.Mocked<MomentsRepository>;
  let questsService: jest.Mocked<QuestsService>;
  let gachaService: jest.Mocked<GachaService>;
  let audit: jest.Mocked<AuditService>;

  const authUsers = [
    {
      uid: 'u1',
      email: 'an@test.com',
      displayName: 'An',
      disabled: false,
      customClaims: { admin: true },
      metadata: { creationTime: '2026-01-01', lastSignInTime: '2026-07-01' },
    },
    {
      uid: 'u2',
      email: 'binh@test.com',
      displayName: 'Binh',
      disabled: true,
      customClaims: undefined,
      metadata: { creationTime: '2026-01-02', lastSignInTime: undefined },
    },
  ];

  beforeEach(() => {
    auth = {
      listUsers: jest.fn().mockResolvedValue({ users: authUsers }),
      updateUser: jest.fn().mockResolvedValue({}),
      setCustomUserClaims: jest.fn().mockResolvedValue(undefined),
      revokeRefreshTokens: jest.fn().mockResolvedValue(undefined),
      getUser: jest.fn().mockImplementation((uid: string) => {
        const found = authUsers.find((u) => u.uid === uid);
        if (!found) {
          return Promise.reject(new Error('not found'));
        }
        return Promise.resolve(found);
      }),
    };
    firebase = { auth: jest.fn().mockReturnValue(auth) };
    adminRepo = {
      getStats: jest.fn(),
      getAllFullNames: jest.fn().mockResolvedValue(new Map([['u1', 'Nguyen Van An']])),
      getAllUserSummaries: jest
        .fn()
        .mockResolvedValue(new Map([['u1', { fullName: 'Nguyen Van An', astrite: 1600 }]])),
      countMomentsByDay: jest.fn(),
    } as unknown as jest.Mocked<AdminRepository>;
    momentsRepo = {
      findById: jest.fn(),
      delete: jest.fn().mockResolvedValue(undefined),
      listAll: jest.fn().mockResolvedValue([]),
    } as unknown as jest.Mocked<MomentsRepository>;
    questsService = {
      countCompletionsToday: jest.fn().mockResolvedValue(0),
      getAiVerificationStatsToday: jest
        .fn()
        .mockResolvedValue({ date: 'd', total: 0, matched: 0, notMatched: 0, skipped: 0 }),
      listAiVerifications: jest.fn().mockResolvedValue({ items: [], page: 1, limit: 15, total: 0 }),
    } as unknown as jest.Mocked<QuestsService>;
    gachaService = {
      getRollCounts: jest.fn().mockResolvedValue({ today: 0, total: 0 }),
    } as unknown as jest.Mocked<GachaService>;
    audit = { log: jest.fn().mockResolvedValue(undefined) } as unknown as jest.Mocked<AuditService>;

    service = new AdminService(
      firebase as unknown as FirebaseService,
      adminRepo,
      momentsRepo,
      questsService,
      gachaService,
      audit,
    );
  });

  it('getStats: gop thong ke chung + quest hom nay + so luot quay gacha', async () => {
    adminRepo.getStats.mockResolvedValue({ users: 2, moments: 5 } as never);
    questsService.countCompletionsToday.mockResolvedValue(3);
    gachaService.getRollCounts.mockResolvedValue({ today: 7, total: 42 });

    const result = await service.getStats();

    expect(result.users).toBe(2);
    expect(result.questCompletionsToday).toBe(3);
    expect(result.gachaRollsToday).toBe(7);
    expect(result.gachaRollsTotal).toBe(42);
  });

  it('getStats: kem so lan AI verify / khop hom nay (2026-08-16)', async () => {
    adminRepo.getStats.mockResolvedValue({ users: 1 } as never);
    gachaService.getRollCounts.mockResolvedValue({ today: 0, total: 0 });
    questsService.getAiVerificationStatsToday.mockResolvedValue({
      date: '2026-08-16',
      total: 5,
      matched: 3,
      notMatched: 1,
      skipped: 1,
    });

    const result = await service.getStats();

    expect(result.aiVerificationsToday).toBe(5);
    expect(result.aiMatchedToday).toBe(3);
  });

  it('getStats: thong ke AI loi (collection chua co) -> 0, dashboard van tra', async () => {
    adminRepo.getStats.mockResolvedValue({ users: 1 } as never);
    gachaService.getRollCounts.mockResolvedValue({ today: 0, total: 0 });
    questsService.getAiVerificationStatsToday.mockRejectedValue(new Error('no collection'));

    const result = await service.getStats();

    expect(result.users).toBe(1);
    expect(result.aiVerificationsToday).toBe(0);
  });

  it('listAiVerifications: chuyen filter outcome/date/uid sang QuestsService', async () => {
    await service.listAiVerifications({
      page: 2,
      limit: 15,
      outcome: 'MATCHED',
      date: '2026-08-16',
      uid: 'u1',
    } as never);

    expect(questsService.listAiVerifications).toHaveBeenCalledWith(
      expect.objectContaining({ page: 2, limit: 15 }),
      { outcome: 'MATCHED', date: '2026-08-16', uid: 'u1' },
    );
  });

  it('listUsers: kem so du Astrite tu Firestore (user chua co field -> 0)', async () => {
    const result = await service.listUsers({ page: 1, limit: 10 } as never);

    expect(result.items.find((u) => u.uid === 'u1')?.astrite).toBe(1600);
    expect(result.items.find((u) => u.uid === 'u2')?.astrite).toBe(0);
  });

  it('getDailyStats: gop so moment theo ngay + dem user moi theo creationTime', async () => {
    adminRepo.countMomentsByDay.mockResolvedValue([
      { date: '2026-01-01', moments: 4, newUsers: 0 },
      { date: '2026-01-02', moments: 1, newUsers: 0 },
    ]);

    const result = await service.getDailyStats(2);

    expect(result).toEqual([
      { date: '2026-01-01', moments: 4, newUsers: 1 }, // u1 tao 2026-01-01
      { date: '2026-01-02', moments: 1, newUsers: 1 }, // u2 tao 2026-01-02
    ]);
  });

  it('listUsers: tim theo email + enrich fullName tu Firestore + co flag admin', async () => {
    const result = await service.listUsers({ search: 'an@', page: 1, limit: 20 });

    expect(result.total).toBe(1);
    expect(result.items[0].uid).toBe('u1');
    expect(result.items[0].fullName).toBe('Nguyen Van An'); // enrich tu Firestore
    expect(result.items[0].admin).toBe(true); // tu custom claims
  });

  it('listUsers: search chay tren ten FIRESTORE dang hien thi (khong phai displayName Auth)', async () => {
    // u1 displayName Auth = "An" nhung Firestore = "Nguyen Van An" — search theo ten dang thay
    const result = await service.listUsers({ search: 'nguyen van', page: 1, limit: 20 });

    expect(result.total).toBe(1);
    expect(result.items[0].uid).toBe('u1');
  });

  it('listUsers: khong search thi tra het + phan trang', async () => {
    const result = await service.listUsers({ page: 1, limit: 1 });
    expect(result.total).toBe(2);
    expect(result.items).toHaveLength(1);
  });

  it('setUserDisabled: goi updateUser; khoa thi thu hoi refresh token + ghi audit', async () => {
    const result = await service.setUserDisabled(actor('admin1'), 'u2', true);
    expect(auth.updateUser).toHaveBeenCalledWith('u2', { disabled: true });
    expect(auth.revokeRefreshTokens).toHaveBeenCalledWith('u2');
    expect(audit.log).toHaveBeenCalledWith(
      expect.objectContaining({ uid: 'admin1' }),
      'USER_DISABLE',
      expect.objectContaining({ id: 'u2' }),
    );
    expect(result).toEqual({ uid: 'u2', disabled: true });
  });

  it('setUserDisabled: mo khoa thi KHONG thu hoi refresh token', async () => {
    await service.setUserDisabled(actor('admin1'), 'u2', false);
    expect(auth.revokeRefreshTokens).not.toHaveBeenCalled();
  });

  it('setUserDisabled: khong the tu khoa chinh minh', async () => {
    await expect(service.setUserDisabled(actor('u1'), 'u1', true)).rejects.toThrow(
      BadRequestException,
    );
    expect(auth.updateUser).not.toHaveBeenCalled();
  });

  it('grantAdmin: set claim admin=true va GIU cac claim khac', async () => {
    auth.getUser.mockResolvedValue({ uid: 'u2', customClaims: { beta: true } });

    const result = await service.grantAdmin(actor('me'), 'u2');

    expect(auth.setCustomUserClaims).toHaveBeenCalledWith('u2', { beta: true, admin: true });
    expect(result).toEqual({ uid: 'u2', admin: true });
  });

  it('revokeAdmin: thu quyen admin cua nguoi khac + ghi audit', async () => {
    const result = await service.revokeAdmin(actor('me'), 'u1'); // u1 dang la admin

    expect(auth.setCustomUserClaims).toHaveBeenCalledWith('u1', { admin: false });
    expect(audit.log).toHaveBeenCalledWith(
      expect.objectContaining({ uid: 'me' }),
      'REVOKE_ADMIN',
      expect.objectContaining({ id: 'u1' }),
    );
    expect(result).toEqual({ uid: 'u1', admin: false });
  });

  it('revokeAdmin: khong the tu thu quyen chinh minh', async () => {
    await expect(service.revokeAdmin(actor('u1'), 'u1')).rejects.toThrow(BadRequestException);
    expect(auth.setCustomUserClaims).not.toHaveBeenCalled();
  });

  it('revokeAdmin: nguoi dung khong phai admin -> bao loi', async () => {
    await expect(service.revokeAdmin(actor('u1'), 'u2')).rejects.toThrow(BadRequestException);
    expect(auth.setCustomUserClaims).not.toHaveBeenCalled();
  });

  it('revokeAdmin: race dua he thong ve 0 admin -> KHOI PHUC claim + bao loi', async () => {
    // Sau khi ghi admin:false, dem lai thay khong con admin hoat dong nao
    auth.listUsers.mockResolvedValueOnce({
      users: [{ uid: 'u1', disabled: false, customClaims: { admin: false } }],
    });

    await expect(service.revokeAdmin(actor('me'), 'u1')).rejects.toThrow(BadRequestException);

    // Lan 1: thu quyen; lan 2: khoi phuc
    expect(auth.setCustomUserClaims).toHaveBeenNthCalledWith(1, 'u1', { admin: false });
    expect(auth.setCustomUserClaims).toHaveBeenNthCalledWith(2, 'u1', { admin: true });
  });

  it('listMoments: enrich ten tac gia tu Firestore + phan trang', async () => {
    momentsRepo.listAll.mockResolvedValue([
      {
        momentId: 'm1',
        userId: 'u1',
        contentType: 'PHOTO',
        mediaUrl: 'http://a.jpg',
        postTime: '2026-07-26',
      },
    ] as never);

    const result = await service.listMoments({ page: 1, limit: 10 });

    expect(result.total).toBe(1);
    expect(result.items[0].authorName).toBe('Nguyen Van An');
  });

  it('deleteMoment: xoa + ghi audit; khong ton tai -> 404', async () => {
    momentsRepo.findById.mockResolvedValue({ momentId: 'm1', userId: 'u1' } as never);

    await service.deleteMoment(actor('me'), 'm1');

    expect(momentsRepo.delete).toHaveBeenCalledWith('m1');
    expect(audit.log).toHaveBeenCalledWith(
      expect.objectContaining({ uid: 'me' }),
      'MOMENT_DELETE',
      expect.objectContaining({ id: 'm1' }),
    );

    momentsRepo.findById.mockResolvedValue(null);
    await expect(service.deleteMoment(actor('me'), 'bad')).rejects.toThrow(NotFoundException);
  });
});
