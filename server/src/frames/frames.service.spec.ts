import { NotFoundException } from '@nestjs/common';
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
    } as unknown as jest.Mocked<UsersRepository>;

    service = new FramesService(repo, usersRepo);
  });

  it('listForUser: gan isUnlocked theo unlockedFrames cua user', async () => {
    repo.list.mockResolvedValue([
      { frameId: 'f1', frameName: 'Fire' },
      { frameId: 'f2', frameName: 'Star' },
    ] as never);
    usersRepo.findByUid.mockResolvedValue({ unlockedFrames: ['f1'] } as never);

    const result = await service.listForUser('me');

    expect(result.find((f) => f.frameId === 'f1')?.isUnlocked).toBe(true);
    expect(result.find((f) => f.frameId === 'f2')?.isUnlocked).toBe(false);
  });

  it('update: bao loi khi frame khong ton tai', async () => {
    repo.findById.mockResolvedValue(null);
    await expect(service.update('bad', { frameName: 'x' })).rejects.toThrow(NotFoundException);
  });

  it('update: merge field moi vao frame cu', async () => {
    repo.findById.mockResolvedValue({
      frameId: 'f1',
      frameName: 'Old',
      imageUrl: 'http://old.png',
    } as never);

    const result = await service.update('f1', { frameName: 'New' });

    expect(repo.update).toHaveBeenCalledWith('f1', { frameName: 'New', imageUrl: undefined });
    expect(result.frameName).toBe('New');
    expect(result.imageUrl).toBe('http://old.png');
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
});
