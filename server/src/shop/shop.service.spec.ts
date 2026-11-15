import { BadRequestException, ConflictException, NotFoundException } from '@nestjs/common';
import { AstriteRepository } from '../astrite/astrite.repository';
import { FramesRepository } from '../frames/frames.repository';
import { UsersRepository } from '../users/users.repository';
import { ShopItem } from './entities/shop-item.entity';
import { ShopRepository, UserWallet } from './shop.repository';
import { ShopService } from './shop.service';

const item = (over: Partial<ShopItem> = {}): ShopItem => ({
  itemId: 'i1',
  itemName: 'Snow',
  itemType: 'SKIN',
  refId: '1',
  price: 4800,
  isActive: true,
  sortOrder: 0,
  createdAt: '2026-11-15T00:00:00.000Z',
  ...over,
});

const wallet = (over: Partial<UserWallet> = {}): UserWallet => ({
  exists: true,
  astrite: 5000,
  unlockedFrames: new Set(),
  unlockedSkins: new Set(),
  unlockedEffects: new Set(),
  ...over,
});

describe('ShopService', () => {
  let service: ShopService;
  let repo: jest.Mocked<ShopRepository>;
  let astriteRepo: jest.Mocked<AstriteRepository>;
  let framesRepo: jest.Mocked<FramesRepository>;
  let usersRepo: jest.Mocked<UsersRepository>;
  const tx = {} as FirebaseFirestore.Transaction;

  beforeEach(() => {
    repo = {
      listActiveItems: jest.fn(),
      listAllItems: jest.fn(),
      findItemById: jest.fn(),
      findItemByRef: jest.fn().mockResolvedValue(null),
      createItem: jest.fn().mockImplementation(async (i) => ({ itemId: 'new', ...i })),
      updateItem: jest.fn(),
      deleteItem: jest.fn(),
      getWallet: jest.fn(),
      getWalletInTransaction: jest.fn(),
      unlockInTransaction: jest.fn(),
      newPurchaseRef: jest.fn().mockReturnValue({ id: 'p1' }),
      addPurchaseInTransaction: jest.fn(),
      listPurchasesByUid: jest.fn(),
      listAllPurchases: jest.fn(),
      countPurchasesSince: jest.fn(),
      countAllPurchases: jest.fn(),
      runTransaction: jest.fn().mockImplementation(async (fn) => fn(tx)),
    } as unknown as jest.Mocked<ShopRepository>;
    astriteRepo = {
      setBalanceInTransaction: jest.fn(),
      addEntryInTransaction: jest.fn(),
    } as unknown as jest.Mocked<AstriteRepository>;
    framesRepo = { findById: jest.fn() } as unknown as jest.Mocked<FramesRepository>;
    usersRepo = {
      findByUid: jest.fn(),
      unlockCollectible: jest.fn(),
      listByCollectible: jest.fn(),
      getFullNamesByUids: jest.fn().mockResolvedValue(new Map()),
    } as unknown as jest.Mocked<UsersRepository>;

    service = new ShopService(repo, astriteRepo, framesRepo, usersRepo);
  });

  // ==================== Catalog ====================

  it('getCatalogForUser: tra so du + isOwned dung theo tung loai', async () => {
    repo.listActiveItems.mockResolvedValue([
      item({ itemId: 'a', itemType: 'SKIN', refId: '1' }),
      item({ itemId: 'b', itemType: 'EFFECT', refId: '2' }),
      item({ itemId: 'c', itemType: 'FRAME', refId: 'f1' }),
      item({ itemId: 'd', itemType: 'SKIN', refId: '2' }),
    ]);
    repo.getWallet.mockResolvedValue(
      wallet({
        astrite: 1234,
        unlockedSkins: new Set(['1']),
        unlockedEffects: new Set(['2']),
        unlockedFrames: new Set(['f1']),
      }),
    );

    const result = await service.getCatalogForUser('u1');

    expect(result.astrite).toBe(1234);
    expect(result.items.map((i) => [i.itemId, i.isOwned])).toEqual([
      ['a', true],
      ['b', true],
      ['c', true],
      ['d', false],
    ]);
  });

  // ==================== Mua ====================

  it('buyItem SKIN: tru dung gia, mo khoa dang SO, ghi lich su + so cai SHOP_PURCHASE', async () => {
    repo.findItemById.mockResolvedValue(item());
    repo.getWalletInTransaction.mockResolvedValue(wallet({ astrite: 5000 }));

    const outcome = await service.buyItem('u1', 'i1');

    expect(outcome.astriteAfter).toBe(200);
    expect(outcome.purchase).toEqual(
      expect.objectContaining({ purchaseId: 'p1', uid: 'u1', price: 4800, balanceAfter: 200 }),
    );
    expect(astriteRepo.setBalanceInTransaction).toHaveBeenCalledWith(tx, 'u1', 200);
    expect(repo.unlockInTransaction).toHaveBeenCalledWith(tx, 'u1', 'unlockedSkins', 1);
    expect(repo.addPurchaseInTransaction).toHaveBeenCalledWith(
      tx,
      { id: 'p1' },
      expect.objectContaining({ itemId: 'i1', itemType: 'SKIN', price: 4800 }),
    );
    expect(astriteRepo.addEntryInTransaction).toHaveBeenCalledWith(tx, {
      uid: 'u1',
      type: 'SHOP_PURCHASE',
      amount: -4800,
      balanceAfter: 200,
      refId: 'p1',
    });
  });

  it('buyItem EFFECT: mo khoa vao unlockedEffects dang so', async () => {
    repo.findItemById.mockResolvedValue(item({ itemType: 'EFFECT', refId: '3', price: 1600 }));
    repo.getWalletInTransaction.mockResolvedValue(wallet({ astrite: 1600 }));

    const outcome = await service.buyItem('u1', 'i1');

    expect(outcome.astriteAfter).toBe(0);
    expect(repo.unlockInTransaction).toHaveBeenCalledWith(tx, 'u1', 'unlockedEffects', 3);
  });

  it('buyItem FRAME: khung SHOP -> mo khoa vao unlockedFrames dang CHUOI', async () => {
    repo.findItemById.mockResolvedValue(item({ itemType: 'FRAME', refId: 'f1', price: 320 }));
    framesRepo.findById.mockResolvedValue({ frameId: 'f1', unlockType: 'SHOP' } as never);
    repo.getWalletInTransaction.mockResolvedValue(wallet({ astrite: 500 }));

    await service.buyItem('u1', 'i1');

    expect(repo.unlockInTransaction).toHaveBeenCalledWith(tx, 'u1', 'unlockedFrames', 'f1');
  });

  it('buyItem FRAME: khung khong con la SHOP -> 400, khong mo transaction', async () => {
    repo.findItemById.mockResolvedValue(item({ itemType: 'FRAME', refId: 'f1' }));
    framesRepo.findById.mockResolvedValue({ frameId: 'f1', unlockType: 'DEFAULT' } as never);

    await expect(service.buyItem('u1', 'i1')).rejects.toThrow(BadRequestException);
    expect(repo.runTransaction).not.toHaveBeenCalled();
  });

  it('buyItem: khong du Astrite -> 400, khong ghi gi', async () => {
    repo.findItemById.mockResolvedValue(item({ price: 4800 }));
    repo.getWalletInTransaction.mockResolvedValue(wallet({ astrite: 4799 }));

    await expect(service.buyItem('u1', 'i1')).rejects.toThrow('Bạn không đủ Astrite.');
    expect(astriteRepo.setBalanceInTransaction).not.toHaveBeenCalled();
    expect(astriteRepo.addEntryInTransaction).not.toHaveBeenCalled();
    expect(repo.unlockInTransaction).not.toHaveBeenCalled();
  });

  it('buyItem: da so huu -> 409, khong tru tien', async () => {
    repo.findItemById.mockResolvedValue(item());
    repo.getWalletInTransaction.mockResolvedValue(
      wallet({ astrite: 99999, unlockedSkins: new Set(['1']) }),
    );

    await expect(service.buyItem('u1', 'i1')).rejects.toThrow(ConflictException);
    expect(astriteRepo.setBalanceInTransaction).not.toHaveBeenCalled();
  });

  it('buyItem: mat hang dang tat hoac khong ton tai -> 400', async () => {
    repo.findItemById.mockResolvedValueOnce(item({ isActive: false }));
    await expect(service.buyItem('u1', 'i1')).rejects.toThrow(BadRequestException);

    repo.findItemById.mockResolvedValueOnce(null);
    await expect(service.buyItem('u1', 'nope')).rejects.toThrow(BadRequestException);

    expect(repo.runTransaction).not.toHaveBeenCalled();
  });

  it('buyItem: user doc khong ton tai -> 404', async () => {
    repo.findItemById.mockResolvedValue(item());
    repo.getWalletInTransaction.mockResolvedValue(wallet({ exists: false }));

    await expect(service.buyItem('ghost', 'i1')).rejects.toThrow(NotFoundException);
  });

  it('buyItem: mon gia 0 van ghi 1 dong so cai (giu bat bien so du <-> so cai)', async () => {
    repo.findItemById.mockResolvedValue(item({ price: 0 }));
    repo.getWalletInTransaction.mockResolvedValue(wallet({ astrite: 0 }));

    await service.buyItem('u1', 'i1');

    expect(astriteRepo.addEntryInTransaction).toHaveBeenCalledWith(
      tx,
      expect.objectContaining({ type: 'SHOP_PURCHASE', amount: -0, balanceAfter: 0 }),
    );
  });

  // ==================== Admin CRUD ====================

  it('createItem SKIN: chuan hoa refId "01" -> "1", gia mac dinh 4800 khi bo trong', async () => {
    const created = await service.createItem({
      itemName: 'Snow',
      itemType: 'SKIN',
      refId: '01',
    });

    expect(created).toEqual(
      expect.objectContaining({ refId: '1', price: 4800, isActive: true, sortOrder: 0 }),
    );
    expect(repo.findItemByRef).toHaveBeenCalledWith('SKIN', '1');
  });

  it('createItem EFFECT: gia admin nhap duoc giu nguyen', async () => {
    const created = await service.createItem({
      itemName: 'Leaf',
      itemType: 'EFFECT',
      refId: '3',
      price: 999,
    });
    expect(created.price).toBe(999);
  });

  it.each(['0', '-1', '1.5', 'abc'])(
    'createItem SKIN: refId "%s" khong hop le -> 400',
    async (r) => {
      await expect(
        service.createItem({ itemName: 'x', itemType: 'SKIN', refId: r }),
      ).rejects.toThrow(BadRequestException);
    },
  );

  it('createItem FRAME: khung SHOP -> dung anh khung khi khong gui anh, gia mac dinh 320', async () => {
    framesRepo.findById.mockResolvedValue({
      frameId: 'f1',
      unlockType: 'SHOP',
      imageUrl: 'http://f.png',
    } as never);

    const created = await service.createItem({ itemName: 'Fire', itemType: 'FRAME', refId: 'f1' });

    expect(created).toEqual(expect.objectContaining({ price: 320, imageUrl: 'http://f.png' }));
  });

  it('createItem FRAME: khung khong phai SHOP / khong ton tai -> 400', async () => {
    framesRepo.findById.mockResolvedValueOnce({ frameId: 'f1', unlockType: 'POST_COUNT' } as never);
    await expect(
      service.createItem({ itemName: 'x', itemType: 'FRAME', refId: 'f1' }),
    ).rejects.toThrow(BadRequestException);

    framesRepo.findById.mockResolvedValueOnce(null);
    await expect(
      service.createItem({ itemName: 'x', itemType: 'FRAME', refId: 'nope' }),
    ).rejects.toThrow(BadRequestException);
  });

  it('createItem: niem yet trung (itemType, refId) -> 409', async () => {
    repo.findItemByRef.mockResolvedValue(item());

    await expect(
      service.createItem({ itemName: 'Snow 2', itemType: 'SKIN', refId: '1' }),
    ).rejects.toThrow(ConflictException);
    expect(repo.createItem).not.toHaveBeenCalled();
  });

  it('updateItem: merge cac field gui len, bo qua undefined', async () => {
    repo.findItemById.mockResolvedValue(item({ price: 4800 }));

    const updated = await service.updateItem('i1', { price: 3000, itemName: undefined });

    expect(repo.updateItem).toHaveBeenCalledWith('i1', { price: 3000, itemName: undefined });
    expect(updated.price).toBe(3000);
    expect(updated.itemName).toBe('Snow');
  });

  it('updateItem / deleteItem: khong ton tai -> 404', async () => {
    repo.findItemById.mockResolvedValue(null);
    await expect(service.updateItem('x', {})).rejects.toThrow(NotFoundException);
    await expect(service.deleteItem('x')).rejects.toThrow(NotFoundException);
  });

  it('grantItem: mo khoa thang (SKIN dang so), khong dung toi so cai', async () => {
    repo.findItemById.mockResolvedValue(item({ refId: '2' }));
    usersRepo.findByUid.mockResolvedValue({ uid: 'u2' } as never);

    await service.grantItem('i1', 'u2');

    expect(usersRepo.unlockCollectible).toHaveBeenCalledWith('u2', 'unlockedSkins', 2);
    expect(astriteRepo.addEntryInTransaction).not.toHaveBeenCalled();
  });

  it('grantItem: uid khong ton tai -> 404 (chong sinh user doc "ma")', async () => {
    repo.findItemById.mockResolvedValue(item());
    usersRepo.findByUid.mockResolvedValue(null);

    await expect(service.grantItem('i1', 'ghost')).rejects.toThrow(NotFoundException);
    expect(usersRepo.unlockCollectible).not.toHaveBeenCalled();
  });

  it('listAllPurchases: loc uid / loai / ngay + enrich ten', async () => {
    repo.listAllPurchases.mockResolvedValue([
      { purchaseId: 'p1', uid: 'u1', itemType: 'SKIN', createdAt: '2026-11-15T01:00:00Z' },
      { purchaseId: 'p2', uid: 'u1', itemType: 'FRAME', createdAt: '2026-11-15T02:00:00Z' },
      { purchaseId: 'p3', uid: 'u2', itemType: 'SKIN', createdAt: '2026-11-14T02:00:00Z' },
    ] as never);
    usersRepo.getFullNamesByUids.mockResolvedValue(new Map([['u1', 'An']]));

    const rows = await service.listAllPurchases({
      uid: 'u1',
      itemType: 'SKIN',
      date: '2026-11-15',
    });

    expect(rows).toHaveLength(1);
    expect(rows[0]).toEqual(expect.objectContaining({ purchaseId: 'p1', fullName: 'An' }));
  });

  // ==================== Dong bo khung ====================

  it('syncFrameListing(true): chua co -> tao mat hang gia mac dinh', async () => {
    await service.syncFrameListing(
      { frameId: 'f1', frameName: 'Fire', imageUrl: 'http://f.png', unlockType: 'SHOP' } as never,
      true,
    );

    expect(repo.createItem).toHaveBeenCalledWith(
      expect.objectContaining({ itemType: 'FRAME', refId: 'f1', price: 320, itemName: 'Fire' }),
    );
  });

  it('syncFrameListing(true): da co -> KHONG ghi de (giu gia admin da sua)', async () => {
    repo.findItemByRef.mockResolvedValue(item({ itemType: 'FRAME', refId: 'f1', price: 50 }));

    await service.syncFrameListing({ frameId: 'f1' } as never, true);

    expect(repo.createItem).not.toHaveBeenCalled();
    expect(repo.updateItem).not.toHaveBeenCalled();
  });

  it('syncFrameListing(false): co mat hang -> go khoi Shop', async () => {
    repo.findItemByRef.mockResolvedValue(item({ itemId: 'g1', itemType: 'FRAME', refId: 'f1' }));

    await service.syncFrameListing({ frameId: 'f1' } as never, false);

    expect(repo.deleteItem).toHaveBeenCalledWith('g1');
  });
});
