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
