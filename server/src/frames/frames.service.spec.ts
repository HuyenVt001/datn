import { BadRequestException, NotFoundException } from '@nestjs/common';
import { ShopService } from '../shop/shop.service';
import { UsersRepository } from '../users/users.repository';
import { FramesRepository } from './frames.repository';
import { FramesService } from './frames.service';

describe('FramesService', () => {
  let service: FramesService;
  let repo: jest.Mocked<FramesRepository>;
  let usersRepo: jest.Mocked<UsersRepository>;
  let shopService: jest.Mocked<ShopService>;

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
    shopService = {
      syncFrameListing: jest.fn().mockResolvedValue(undefined),
    } as unknown as jest.Mocked<ShopService>;

    service = new FramesService(repo, usersRepo, shopService);
  });

  it("listForUser: sets isUnlocked from the user's unlockedFrames; DEFAULT frame is always unlocked", async () => {
    repo.list.mockResolvedValue([
      { frameId: 'f1', frameName: 'Fire', unlockType: 'SHOP' },
      { frameId: 'f2', frameName: 'Star', unlockType: 'SHOP' },
      { frameId: 'f3', frameName: 'Basic', unlockType: 'DEFAULT' },
    ] as never);
    usersRepo.findByUid.mockResolvedValue({ unlockedFrames: ['f1'] } as never);

    const result = await service.listForUser('me');

    expect(result.find((f) => f.frameId === 'f1')?.isUnlocked).toBe(true);
    expect(result.find((f) => f.frameId === 'f2')?.isUnlocked).toBe(false);
    expect(result.find((f) => f.frameId === 'f3')?.isUnlocked).toBe(true); // DEFAULT = mo san
  });

  it('create: threshold condition (POST_COUNT) without N -> error', async () => {
    await expect(service.create({ frameName: 'x', unlockType: 'POST_COUNT' })).rejects.toThrow(
      BadRequestException,
    );
  });

  it('create: STREAK_MILESTONE threshold not in 3/7/14/30 -> error', async () => {
    await expect(
      service.create({ frameName: 'x', unlockType: 'STREAK_MILESTONE', unlockValue: 5 }),
    ).rejects.toThrow(BadRequestException);
  });

  it('create: defaults to SHOP, keeps legacy milestone in sync', async () => {
    repo.create.mockImplementation(async (f) => ({ frameId: 'new', ...f }) as never);

    const frame = await service.create({ frameName: 'Regular' });

    expect(frame.unlockType).toBe('SHOP');
    expect(frame.milestone).toBeNull();
  });

  it('create SHOP frame: automatically listed in the Shop', async () => {
    repo.create.mockImplementation(async (f) => ({ frameId: 'new', ...f }) as never);

    await service.create({ frameName: 'Regular', imageUrl: 'http://t.png' });

    expect(shopService.syncFrameListing).toHaveBeenCalledWith(
      expect.objectContaining({ frameId: 'new', frameName: 'Regular', imageUrl: 'http://t.png' }),
      true,
    );
  });

  it('create non-SHOP frame: does not touch the Shop', async () => {
    repo.create.mockImplementation(async (f) => ({ frameId: 'new', ...f }) as never);

    await service.create({ frameName: 'MoSan', unlockType: 'DEFAULT' });

    expect(shopService.syncFrameListing).not.toHaveBeenCalled();
  });

  it('create: Shop sync error does NOT fail frame creation (best-effort)', async () => {
    repo.create.mockImplementation(async (f) => ({ frameId: 'new', ...f }) as never);
    shopService.syncFrameListing.mockRejectedValue(new Error('firestore down'));

    const frame = await service.create({ frameName: 'Regular' });

    expect(frame.frameId).toBe('new');
  });

  it('update: error when frame does not exist', async () => {
    repo.findById.mockResolvedValue(null);
    await expect(service.update('bad', { frameName: 'x' })).rejects.toThrow(NotFoundException);
  });

  it('update: merges new fields into the old frame (keeps old condition when unchanged)', async () => {
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

  it('update: switching to a type without threshold -> clears threshold + legacy milestone', async () => {
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

  it('update: switching to a threshold type without N -> error (old threshold not carried over)', async () => {
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

  it('update: switching condition to SHOP -> frame automatically listed in the Shop', async () => {
    repo.findById.mockResolvedValue({
      frameId: 'f1',
      frameName: 'Old',
      imageUrl: 'http://old.png',
      unlockType: 'DEFAULT',
    } as never);

    await service.update('f1', { unlockType: 'SHOP' });

    expect(shopService.syncFrameListing).toHaveBeenCalledWith(
      expect.objectContaining({ frameId: 'f1', unlockType: 'SHOP' }),
      true,
    );
  });

  it('update: switching condition from SHOP to another type -> frame removed from the Shop', async () => {
    repo.findById.mockResolvedValue({
      frameId: 'f1',
      frameName: 'Old',
      unlockType: 'SHOP',
    } as never);

    await service.update('f1', { unlockType: 'COOP_FIRST' });

    expect(shopService.syncFrameListing).toHaveBeenCalledWith(
      expect.objectContaining({ frameId: 'f1', unlockType: 'COOP_FIRST' }),
      false,
    );
  });

  it('delete: error when frame does not exist', async () => {
    repo.findById.mockResolvedValue(null);
    await expect(service.delete('bad')).rejects.toThrow(NotFoundException);
  });

  it('delete SHOP frame: also removes the Shop item', async () => {
    repo.findById.mockResolvedValue({
      frameId: 'f1',
      frameName: 'Old',
      unlockType: 'SHOP',
    } as never);

    await service.delete('f1');

    expect(repo.delete).toHaveBeenCalledWith('f1');
    expect(shopService.syncFrameListing).toHaveBeenCalledWith(
      expect.objectContaining({ frameId: 'f1' }),
      false,
    );
  });

  it('unlockForUser: unlocks when the frame exists', async () => {
    repo.findById.mockResolvedValue({ frameId: 'f1' } as never);
    await service.unlockForUser('me', 'f1');
    expect(usersRepo.unlockFrame).toHaveBeenCalledWith('me', 'f1');
  });

  it('unlockByThreshold: only unlocks frames that reached the threshold and are NOT owned yet', async () => {
    repo.list.mockResolvedValue([
      { frameId: 'p10', frameName: '10 bai', unlockType: 'POST_COUNT', unlockValue: 10 },
      { frameId: 'p50', frameName: '50 bai', unlockType: 'POST_COUNT', unlockValue: 50 },
      { frameId: 'owned', frameName: 'Owned', unlockType: 'POST_COUNT', unlockValue: 5 },
      { frameId: 'fr5', frameName: '5 friends', unlockType: 'FRIEND_COUNT', unlockValue: 5 },
    ] as never);
    usersRepo.findByUid.mockResolvedValue({ unlockedFrames: ['owned'] } as never);

    const unlocked = await service.unlockByThreshold('me', 'POST_COUNT', 12);

    expect(unlocked).toEqual(['p10']);
    expect(usersRepo.unlockFrame).toHaveBeenCalledTimes(1);
    expect(usersRepo.unlockFrame).toHaveBeenCalledWith('me', 'p10');
  });

  it('unlockCoopFrames: unlocks every COOP_FIRST frame not owned yet', async () => {
    repo.list.mockResolvedValue([
      { frameId: 'coop1', frameName: 'Duo', unlockType: 'COOP_FIRST' },
      { frameId: 'other', frameName: 'Other', unlockType: 'SHOP' },
    ] as never);
    usersRepo.findByUid.mockResolvedValue({ unlockedFrames: [] } as never);

    const unlocked = await service.unlockCoopFrames('me');

    expect(unlocked).toEqual(['coop1']);
  });

  it('listOwners: returns frame + owner list; missing frame -> error', async () => {
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
