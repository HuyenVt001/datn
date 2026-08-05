import { BadRequestException, NotFoundException } from '@nestjs/common';
import { UsersRepository } from '../users/users.repository';
import { FramesRepository } from './frames.repository';
import { FramesService } from './frames.service';

describe('FramesService', () => {
  let service: FramesService;
  let repo: jest.Mocked<FramesRepository>;
  let usersRepo: jest.Mocked<UsersRepository>;

  beforeEach(() => {
    repo = {
      list: jest.fn(),
      findById: jest.fn(),
      create: jest.fn(),
      update: jest.fn(),
      delete: jest.fn(),
    } as unknown as jest.Mocked<FramesRepository>;
    usersRepo = {
      findByUid: jest.fn(),
      unlockFrame: jest.fn(),
      listByUnlockedFrame: jest.fn(),
    } as unknown as jest.Mocked<UsersRepository>;

    service = new FramesService(repo, usersRepo);
  });

  it('listForUser: gan isUnlocked theo unlockedFrames cua user; khung DEFAULT luon mo', async () => {
    repo.list.mockResolvedValue([
      { frameId: 'f1', frameName: 'Fire', unlockType: 'GACHA' },
      { frameId: 'f2', frameName: 'Star', unlockType: 'GACHA' },
      { frameId: 'f3', frameName: 'Basic', unlockType: 'DEFAULT' },
    ] as never);
    usersRepo.findByUid.mockResolvedValue({ unlockedFrames: ['f1'] } as never);

    const result = await service.listForUser('me');

    expect(result.find((f) => f.frameId === 'f1')?.isUnlocked).toBe(true);
    expect(result.find((f) => f.frameId === 'f2')?.isUnlocked).toBe(false);
    expect(result.find((f) => f.frameId === 'f3')?.isUnlocked).toBe(true); // DEFAULT = mo san
  });

  it('create: dieu kien nguong (POST_COUNT) thieu N -> bao loi', async () => {
    await expect(service.create({ frameName: 'x', unlockType: 'POST_COUNT' })).rejects.toThrow(
      BadRequestException,
    );
  });

  it('create: STREAK_MILESTONE nguong khong thuoc 3/7/14/30 -> bao loi', async () => {
    await expect(
      service.create({ frameName: 'x', unlockType: 'STREAK_MILESTONE', unlockValue: 5 }),
    ).rejects.toThrow(BadRequestException);
  });

  it('create: mac dinh GACHA, giu milestone legacy dong bo', async () => {
    repo.create.mockImplementation(async (f) => ({ frameId: 'new', ...f }) as never);

    const frame = await service.create({ frameName: 'Thuong' });

    expect(frame.unlockType).toBe('GACHA');
    expect(frame.milestone).toBeNull();
  });

  it('update: bao loi khi frame khong ton tai', async () => {
    repo.findById.mockResolvedValue(null);
    await expect(service.update('bad', { frameName: 'x' })).rejects.toThrow(NotFoundException);
  });

  it('update: merge field moi vao frame cu (giu dieu kien cu khi khong doi)', async () => {
    repo.findById.mockResolvedValue({
      frameId: 'f1',
      frameName: 'Old',
      imageUrl: 'http://old.png',
      unlockType: 'STREAK_MILESTONE',
      unlockValue: 7,
    } as never);

    const result = await service.update('f1', { frameName: 'New' });

    expect(repo.update).toHaveBeenCalledWith('f1', {
      frameName: 'New',
      imageUrl: undefined,
      unlockType: 'STREAK_MILESTONE',
      unlockValue: 7,
      milestone: 7,
    });
    expect(result.frameName).toBe('New');
    expect(result.imageUrl).toBe('http://old.png');
  });

  it('update: doi sang loai khong nguong -> xoa nguong + milestone legacy', async () => {
    repo.findById.mockResolvedValue({
      frameId: 'f1',
      frameName: 'Old',
      unlockType: 'STREAK_MILESTONE',
      unlockValue: 7,
    } as never);

    const result = await service.update('f1', { unlockType: 'COOP_FIRST' });

    expect(repo.update).toHaveBeenCalledWith(
      'f1',
      expect.objectContaining({ unlockType: 'COOP_FIRST', unlockValue: null, milestone: null }),
    );
    expect(result.unlockValue).toBeNull();
  });

  it('update: doi sang loai can nguong ma khong gui N -> bao loi (khong mang nguong cu sang)', async () => {
    repo.findById.mockResolvedValue({
      frameId: 'f1',
      frameName: 'Old',
      unlockType: 'STREAK_MILESTONE',
      unlockValue: 7,
    } as never);

    await expect(service.update('f1', { unlockType: 'FRIEND_COUNT' })).rejects.toThrow(
      BadRequestException,
    );
  });

  it('delete: bao loi khi frame khong ton tai', async () => {
    repo.findById.mockResolvedValue(null);
    await expect(service.delete('bad')).rejects.toThrow(NotFoundException);
  });

  it('unlockForUser: mo khoa khi frame ton tai', async () => {
    repo.findById.mockResolvedValue({ frameId: 'f1' } as never);
    await service.unlockForUser('me', 'f1');
    expect(usersRepo.unlockFrame).toHaveBeenCalledWith('me', 'f1');
  });

  it('unlockByThreshold: chi mo khung dat nguong va CHUA so huu', async () => {
    repo.list.mockResolvedValue([
      { frameId: 'p10', frameName: '10 bai', unlockType: 'POST_COUNT', unlockValue: 10 },
      { frameId: 'p50', frameName: '50 bai', unlockType: 'POST_COUNT', unlockValue: 50 },
      { frameId: 'owned', frameName: 'Da co', unlockType: 'POST_COUNT', unlockValue: 5 },
      { frameId: 'fr5', frameName: '5 ban', unlockType: 'FRIEND_COUNT', unlockValue: 5 },
    ] as never);
    usersRepo.findByUid.mockResolvedValue({ unlockedFrames: ['owned'] } as never);

    const unlocked = await service.unlockByThreshold('me', 'POST_COUNT', 12);

    expect(unlocked).toEqual(['p10']);
    expect(usersRepo.unlockFrame).toHaveBeenCalledTimes(1);
    expect(usersRepo.unlockFrame).toHaveBeenCalledWith('me', 'p10');
  });

  it('unlockCoopFrames: mo moi khung COOP_FIRST chua so huu', async () => {
    repo.list.mockResolvedValue([
      { frameId: 'coop1', frameName: 'Duo', unlockType: 'COOP_FIRST' },
      { frameId: 'other', frameName: 'Khac', unlockType: 'GACHA' },
    ] as never);
    usersRepo.findByUid.mockResolvedValue({ unlockedFrames: [] } as never);

    const unlocked = await service.unlockCoopFrames('me');

    expect(unlocked).toEqual(['coop1']);
  });

  it('listOwners: tra frame + danh sach user so huu; frame khong ton tai -> loi', async () => {
    repo.findById.mockResolvedValue({ frameId: 'f1', frameName: 'Fire' } as never);
    usersRepo.listByUnlockedFrame.mockResolvedValue([
      { uid: 'u1', email: 'a@test.com', fullName: 'An', avatar: 'http://a.png' },
    ] as never);

    const result = await service.listOwners('f1');

    expect(result.owners).toHaveLength(1);
    expect(result.owners[0]).toEqual({
      uid: 'u1',
      email: 'a@test.com',
      fullName: 'An',
      avatar: 'http://a.png',
    });

    repo.findById.mockResolvedValue(null);
    await expect(service.listOwners('bad')).rejects.toThrow(NotFoundException);
  });
});
