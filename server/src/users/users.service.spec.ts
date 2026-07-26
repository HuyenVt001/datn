import { AuthUser } from '../common/decorators/current-user.decorator';
import { UsersRepository } from './users.repository';
import { UsersService } from './users.service';

describe('UsersService', () => {
  let service: UsersService;
  let repo: jest.Mocked<UsersRepository>;

  beforeEach(() => {
    repo = {
      findByUid: jest.fn(),
      create: jest.fn(),
      update: jest.fn(),
    } as unknown as jest.Mocked<UsersRepository>;
    service = new UsersService(repo);
  });

  describe('ensureUser', () => {
    it('tra ve user co san neu da ton tai', async () => {
      repo.findByUid.mockResolvedValue({ uid: 'u1', email: 'a@b.c' } as never);
      const result = await service.ensureUser({ uid: 'u1' } as AuthUser);
      expect(result.uid).toBe('u1');
      expect(repo.create).not.toHaveBeenCalled();
    });

    it('tao user moi khi dang nhap lan dau', async () => {
      repo.findByUid.mockResolvedValue(null);
      const auth: AuthUser = { uid: 'u2', email: 'new@snap.get' };
      const result = await service.ensureUser(auth);
      expect(repo.create).toHaveBeenCalled();
      expect(result.email).toBe('new@snap.get');
      expect(result.personalStreak).toBe(0);
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
