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
    it('tra ve user co san (da co joinDate) khong ghi gi them', async () => {
      repo.findByUid.mockResolvedValue({
        uid: 'u1',
        email: 'a@b.c',
        joinDate: '2026-01-01T00:00:00.000Z',
      } as never);
      const result = await service.ensureUser({ uid: 'u1' } as AuthUser);
      expect(result.uid).toBe('u1');
      expect(repo.update).not.toHaveBeenCalled();
    });

    it('tao user moi khi dang nhap lan dau — KHONG dinh key avatar undefined (email/password)', async () => {
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

    it('doc STUB (grant khung truoc khi dang nhap) -> backfill nhung GIU unlockedFrames', async () => {
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

    it('doc thoi prototype co ten cu -> giu ten, chi bo sung field thieu', async () => {
      repo.findByUid.mockResolvedValue({
        uid: 'u4',
        email: 'old@snap.get',
        fullName: 'Ten Cu',
        joinDate: '',
        personalStreak: 3,
        unlockedFrames: [],
        fcmTokens: [],
      } as never);

      const result = await service.ensureUser({ uid: 'u4', name: 'Ten Auth' } as AuthUser);

      const patch = repo.update.mock.calls[0][1];
      expect(patch.fullName).toBe('Ten Cu'); // khong ghi de ten nguoi dung da dat
      expect(patch.personalStreak).toBe(3);
      expect(result.fullName).toBe('Ten Cu');
    });
  });

  describe('getOrCreateInviteCode', () => {
    it('giu nguyen ma khi con han', async () => {
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

    it('sinh ma moi + han 30 ngay khi ma da het han', async () => {
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

    it('sinh ma moi khi ma cu chua co han (du lieu truoc TTL)', async () => {
      repo.findByUid.mockResolvedValue({ uid: 'u1', inviteCode: 'legacy' } as never);

      const result = await service.getOrCreateInviteCode('u1');

      expect(result.inviteCode).not.toBe('legacy');
      expect(repo.update).toHaveBeenCalled();
    });
  });

  describe('updateProfile', () => {
    it('chi patch cac field co trong dto (khong dinh field undefined)', async () => {
      repo.findByUid.mockResolvedValue({ uid: 'u1', birthday: '2000-01-31' } as never);

      const result = await service.updateProfile('u1', { birthday: '2000-01-31' });

      expect(repo.update).toHaveBeenCalledWith('u1', { birthday: '2000-01-31' });
      expect(result.birthday).toBe('2000-01-31');
      // chi doi birthday -> khong dong bo gi len Auth
      expect(auth.updateUser).not.toHaveBeenCalled();
    });

    it('doi fullName -> patch Firestore + SYNC displayName len Firebase Auth', async () => {
      repo.findByUid.mockResolvedValue({ uid: 'u1' } as never);

      await service.updateProfile('u1', { fullName: 'Hoang', birthday: '1999-12-01' });

      expect(repo.update).toHaveBeenCalledWith('u1', {
        fullName: 'Hoang',
        birthday: '1999-12-01',
      });
      expect(auth.updateUser).toHaveBeenCalledWith('u1', { displayName: 'Hoang' });
    });

    it('birthday o TUONG LAI -> bao loi, khong ghi gi', async () => {
      await expect(service.updateProfile('u1', { birthday: '2999-01-01' })).rejects.toThrow();
      expect(repo.update).not.toHaveBeenCalled();
    });

    it('sync Auth loi -> updateProfile van thanh cong (best-effort)', async () => {
      repo.findByUid.mockResolvedValue({ uid: 'u1', fullName: 'Hoang' } as never);
      auth.updateUser.mockRejectedValue(new Error('auth down'));

      await expect(service.updateProfile('u1', { fullName: 'Hoang' })).resolves.toBeDefined();
    });
  });

  describe('pushToUids', () => {
    it('gom token cua cac uid (khu trung) roi gui multicast', async () => {
      repo.findByUid
        .mockResolvedValueOnce({ uid: 'a', fcmTokens: ['t1', 't2'] } as never)
        .mockResolvedValueOnce({ uid: 'b', fcmTokens: ['t2', 't3'] } as never);

      const sent = await service.pushToUids(['a', 'b', 'a'], 'Tieu de', 'Noi dung');

      expect(messaging.sendEachForMulticast).toHaveBeenCalledWith(
        expect.objectContaining({ tokens: ['t1', 't2', 't3'] }),
      );
      expect(sent).toBe(1);
    });

    it('khong co token -> khong gui; loi FCM -> khong throw', async () => {
      repo.findByUid.mockResolvedValue({ uid: 'a', fcmTokens: [] } as never);
      expect(await service.pushToUids(['a'], 't', 'b')).toBe(0);
      expect(messaging.sendEachForMulticast).not.toHaveBeenCalled();

      repo.findByUid.mockResolvedValue({ uid: 'a', fcmTokens: ['t1'] } as never);
      messaging.sendEachForMulticast.mockRejectedValue(new Error('fcm down'));
      await expect(service.pushToUids(['a'], 't', 'b')).resolves.toBe(0);
    });
  });

  describe('registerActivityForStreak', () => {
    it('reset ve 1 khi cach xa hon 1 ngay', async () => {
      repo.findByUid.mockResolvedValue({
        uid: 'u1',
        personalStreak: 9,
        lastStreakDate: '2020-01-01',
      } as never);
      const streak = await service.registerActivityForStreak('u1');
      expect(streak).toBe(1);
      expect(repo.update).toHaveBeenCalled();
    });
  });
});
