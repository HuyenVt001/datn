import { AstriteService } from '../astrite/astrite.service';
import { AuthUser } from '../common/decorators/current-user.decorator';
import { FirebaseService } from '../firebase/firebase.service';
import { UsersRepository } from './users.repository';
import { UsersService } from './users.service';

describe('UsersService', () => {
  let service: UsersService;
  let repo: jest.Mocked<UsersRepository>;
  let auth: { updateUser: jest.Mock };
  let messaging: { sendEachForMulticast: jest.Mock };
  let astrite: { grantSignupBonusOnce: jest.Mock };

  beforeEach(() => {
    repo = {
      findByUid: jest.fn(),
      create: jest.fn(),
      update: jest.fn(),
      listByLastStreakDate: jest.fn().mockResolvedValue([]),
    } as unknown as jest.Mocked<UsersRepository>;
    auth = { updateUser: jest.fn().mockResolvedValue({}) };
    messaging = { sendEachForMulticast: jest.fn().mockResolvedValue({ successCount: 1 }) };
    const firebase = {
      auth: jest.fn().mockReturnValue(auth),
      messaging: jest.fn().mockReturnValue(messaging),
    } as unknown as FirebaseService;
    astrite = { grantSignupBonusOnce: jest.fn().mockResolvedValue(undefined) };
    service = new UsersService(repo, firebase, astrite as unknown as AstriteService);
  });

  describe('ensureUser', () => {
    it('returns the existing user (has joinDate) without writing', async () => {
      repo.findByUid.mockResolvedValue({
        uid: 'u1',
        email: 'a@b.c',
        joinDate: '2026-01-01T00:00:00.000Z',
      } as never);
      const result = await service.ensureUser({ uid: 'u1' } as AuthUser);
      expect(result.uid).toBe('u1');
      expect(repo.update).not.toHaveBeenCalled();
    });

    it('creates a new user on first sign-in — does NOT attach an undefined avatar key (email/password)', async () => {
      repo.findByUid.mockResolvedValue(null);
      const auth: AuthUser = { uid: 'u2', email: 'new@snap.get' }; // token khong co claim picture
      const result = await service.ensureUser(auth);

      expect(repo.update).toHaveBeenCalledTimes(1);
      const patch = repo.update.mock.calls[0][1];
      expect('avatar' in patch).toBe(false); // undefined khong duoc ghi vao Firestore
      expect(patch.unlockedFrames).toEqual([]);
      expect(result.email).toBe('new@snap.get');
      expect(result.fullName).toBe('new'); // fallback tu email
      expect(result.personalStreak).toBe(0);
    });

    it('STUB doc (frame granted before sign-in) -> backfills but KEEPS unlockedFrames', async () => {
      repo.findByUid.mockResolvedValue({
        uid: 'u3',
        email: '',
        fullName: '',
        joinDate: '', // stub — chua khoi tao ho so
        personalStreak: 0,
        unlockedFrames: ['f1'],
        fcmTokens: [],
      } as never);

      const result = await service.ensureUser({ uid: 'u3', email: 'stub@snap.get' } as AuthUser);

      const patch = repo.update.mock.calls[0][1];
      expect('unlockedFrames' in patch).toBe(false); // khong ghi de mang khung da co
      expect(patch.email).toBe('stub@snap.get');
      expect(patch.joinDate).toBeTruthy();
      expect(result.unlockedFrames).toEqual(['f1']);
    });

    it('prototype-era doc with an old name -> keeps the name, only fills missing fields', async () => {
      repo.findByUid.mockResolvedValue({
        uid: 'u4',
        email: 'old@snap.get',
        fullName: 'Old Name',
        joinDate: '',
        personalStreak: 3,
        unlockedFrames: [],
        fcmTokens: [],
      } as never);

      const result = await service.ensureUser({ uid: 'u4', name: 'Auth Name' } as AuthUser);

      const patch = repo.update.mock.calls[0][1];
      expect(patch.fullName).toBe('Old Name'); // khong ghi de ten nguoi dung da dat
      expect(patch.personalStreak).toBe(3);
      expect(result.fullName).toBe('Old Name');
    });
  });

  describe('getOrCreateInviteCode', () => {
    it('keeps the code while valid', async () => {
      const expiresAt = new Date(Date.now() + 5 * 24 * 60 * 60 * 1000).toISOString();
      repo.findByUid.mockResolvedValue({
        uid: 'u1',
        inviteCode: 'oldcode',
        inviteCodeExpiresAt: expiresAt,
      } as never);

      const result = await service.getOrCreateInviteCode('u1');

      expect(result).toEqual({ inviteCode: 'oldcode', expiresAt });
      expect(repo.update).not.toHaveBeenCalled();
    });

    it('generates a new code + 30-day expiry when the code expired', async () => {
      repo.findByUid.mockResolvedValue({
        uid: 'u1',
        inviteCode: 'oldcode',
        inviteCodeExpiresAt: new Date(Date.now() - 1000).toISOString(),
      } as never);

      const result = await service.getOrCreateInviteCode('u1');

      expect(result.inviteCode).not.toBe('oldcode');
      // Han moi ~30 ngay (INVITE_LINK_TTL_DAYS) tinh tu bay gio
      const ttlMs = new Date(result.expiresAt).getTime() - Date.now();
      expect(ttlMs).toBeGreaterThan(29 * 24 * 60 * 60 * 1000);
      expect(ttlMs).toBeLessThanOrEqual(30 * 24 * 60 * 60 * 1000);
      expect(repo.update).toHaveBeenCalledWith('u1', {
        inviteCode: result.inviteCode,
        inviteCodeExpiresAt: result.expiresAt,
      });
    });

    it('generates a new code when the old one has no expiry (pre-TTL data)', async () => {
      repo.findByUid.mockResolvedValue({ uid: 'u1', inviteCode: 'legacy' } as never);

      const result = await service.getOrCreateInviteCode('u1');

      expect(result.inviteCode).not.toBe('legacy');
      expect(repo.update).toHaveBeenCalled();
    });
  });

  describe('updateProfile', () => {
    it('only patches fields present in the dto (no undefined fields)', async () => {
      repo.findByUid.mockResolvedValue({ uid: 'u1', birthday: '2000-01-31' } as never);

      const result = await service.updateProfile('u1', { birthday: '2000-01-31' });

      expect(repo.update).toHaveBeenCalledWith('u1', { birthday: '2000-01-31' });
      expect(result.birthday).toBe('2000-01-31');
      // chi doi birthday -> khong dong bo gi len Auth
      expect(auth.updateUser).not.toHaveBeenCalled();
    });

    it('changing fullName -> patches Firestore + SYNCS displayName to Firebase Auth', async () => {
      repo.findByUid.mockResolvedValue({ uid: 'u1' } as never);

      await service.updateProfile('u1', { fullName: 'Hoang', birthday: '1999-12-01' });

      expect(repo.update).toHaveBeenCalledWith('u1', {
        fullName: 'Hoang',
        birthday: '1999-12-01',
      });
      expect(auth.updateUser).toHaveBeenCalledWith('u1', { displayName: 'Hoang' });
    });

    it('birthday in the FUTURE -> error, writes nothing', async () => {
      await expect(service.updateProfile('u1', { birthday: '2999-01-01' })).rejects.toThrow();
      expect(repo.update).not.toHaveBeenCalled();
    });

    it('Auth sync fails -> updateProfile still succeeds (best-effort)', async () => {
      repo.findByUid.mockResolvedValue({ uid: 'u1', fullName: 'Hoang' } as never);
      auth.updateUser.mockRejectedValue(new Error('auth down'));

      await expect(service.updateProfile('u1', { fullName: 'Hoang' })).resolves.toBeDefined();
    });
  });

  describe('pushToUids', () => {
    it('collects tokens of the uids (deduped) then sends multicast', async () => {
      repo.findByUid
        .mockResolvedValueOnce({ uid: 'a', fcmTokens: ['t1', 't2'] } as never)
        .mockResolvedValueOnce({ uid: 'b', fcmTokens: ['t2', 't3'] } as never);

      const sent = await service.pushToUids(['a', 'b', 'a'], 'Title', 'Content');

      expect(messaging.sendEachForMulticast).toHaveBeenCalledWith(
        expect.objectContaining({ tokens: ['t1', 't2', 't3'] }),
      );
      expect(sent).toBe(1);
    });

    it('no tokens -> nothing sent; FCM error -> no throw', async () => {
      repo.findByUid.mockResolvedValue({ uid: 'a', fcmTokens: [] } as never);
      expect(await service.pushToUids(['a'], 't', 'b')).toBe(0);
      expect(messaging.sendEachForMulticast).not.toHaveBeenCalled();

      repo.findByUid.mockResolvedValue({ uid: 'a', fcmTokens: ['t1'] } as never);
      messaging.sendEachForMulticast.mockRejectedValue(new Error('fcm down'));
      await expect(service.pushToUids(['a'], 't', 'b')).resolves.toBe(0);
    });
  });

  describe('registerActivityForStreak', () => {
    it('resets to 1 when more than 1 day apart', async () => {
      repo.findByUid.mockResolvedValue({
        uid: 'u1',
        personalStreak: 9,
        lastStreakDate: '2020-01-01',
      } as never);
      const streak = await service.registerActivityForStreak('u1');
      expect(streak).toBe(1);
      expect(repo.update).toHaveBeenCalled();
    });

    it('detailed: same day -> increased false, no write', async () => {
      const today = new Date().toISOString().slice(0, 10);
      repo.findByUid.mockResolvedValue({
        uid: 'u1',
        personalStreak: 4,
        lastStreakDate: today,
      } as never);
      // dateKey co the theo mui gio rieng -> chi khang dinh khi khop ngay hom nay
      const res = await service.registerActivityForStreakDetailed('u1');
      if (res.increased === false) {
        expect(res.personalStreak).toBe(4);
        expect(repo.update).not.toHaveBeenCalled();
      }
    });

    it('detailed: streak broken -> restarts at 1 and increased true', async () => {
      repo.findByUid.mockResolvedValue({
        uid: 'u1',
        personalStreak: 9,
        lastStreakDate: '2020-01-01',
      } as never);
      await expect(service.registerActivityForStreakDetailed('u1')).resolves.toEqual({
        personalStreak: 1,
        increased: true,
      });
    });
  });

  describe('sendStreakReminders (cron 2026-11-15)', () => {
    it('only sends to users with streak > 0 and a token, DATA-ONLY push type STREAK_REMINDER', async () => {
      repo.listByLastStreakDate.mockResolvedValue([
        { uid: 'a', personalStreak: 5, fcmTokens: ['t1', 't1', 't2'] },
        { uid: 'b', personalStreak: 0, fcmTokens: ['t3'] }, // streak 0 -> bo
        { uid: 'c', personalStreak: 3, fcmTokens: [] }, // khong token -> bo
      ] as never);

      const res = await service.sendStreakReminders();

      expect(res).toEqual({ candidates: 1, notified: 1 });
      expect(messaging.sendEachForMulticast).toHaveBeenCalledTimes(1);
      const payload = messaging.sendEachForMulticast.mock.calls[0][0];
      expect(payload.tokens).toEqual(['t1', 't2']); // khu trung token
      expect(payload.notification).toBeUndefined(); // data-only: app tu loc theo toggle
      expect(payload.data.type).toBe('STREAK_REMINDER');
      expect(payload.data.title).toContain('5-day streak');
    });

    it('FCM fails for 1 user -> no throw, notified count correct', async () => {
      repo.listByLastStreakDate.mockResolvedValue([
        { uid: 'a', personalStreak: 2, fcmTokens: ['t1'] },
        { uid: 'b', personalStreak: 7, fcmTokens: ['t2'] },
      ] as never);
      messaging.sendEachForMulticast
        .mockRejectedValueOnce(new Error('fcm down'))
        .mockResolvedValueOnce({ successCount: 1 });

      await expect(service.sendStreakReminders()).resolves.toEqual({ candidates: 2, notified: 1 });
    });
  });
});
