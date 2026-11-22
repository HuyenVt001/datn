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

  it('getCatalogForUser: returns balance + correct isOwned per type', async () => {
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

  it('buyItem SKIN: debits the exact price, unlocks as NUMBER, writes history + SHOP_PURCHASE ledger', async () => {
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

  it('buyItem EFFECT: unlocks into unlockedEffects as number', async () => {
    repo.findItemById.mockResolvedValue(item({ itemType: 'EFFECT', refId: '3', price: 1600 }));
    repo.getWalletInTransaction.mockResolvedValue(wallet({ astrite: 1600 }));

    const outcome = await service.buyItem('u1', 'i1');

    expect(outcome.astriteAfter).toBe(0);
    expect(repo.unlockInTransaction).toHaveBeenCalledWith(tx, 'u1', 'unlockedEffects', 3);
  });

  it('buyItem FRAME: SHOP frame -> unlocks into unlockedFrames as STRING', async () => {
    repo.findItemById.mockResolvedValue(item({ itemType: 'FRAME', refId: 'f1', price: 320 }));
    framesRepo.findById.mockResolvedValue({ frameId: 'f1', unlockType: 'SHOP' } as never);
    repo.getWalletInTransaction.mockResolvedValue(wallet({ astrite: 500 }));

    await service.buyItem('u1', 'i1');

    expect(repo.unlockInTransaction).toHaveBeenCalledWith(tx, 'u1', 'unlockedFrames', 'f1');
  });

  it('buyItem FRAME: frame no longer SHOP -> 400, no transaction', async () => {
    repo.findItemById.mockResolvedValue(item({ itemType: 'FRAME', refId: 'f1' }));
    framesRepo.findById.mockResolvedValue({ frameId: 'f1', unlockType: 'DEFAULT' } as never);

    await expect(service.buyItem('u1', 'i1')).rejects.toThrow(BadRequestException);
    expect(repo.runTransaction).not.toHaveBeenCalled();
  });

  it('buyItem: not enough Astrite -> 400, writes nothing', async () => {
    repo.findItemById.mockResolvedValue(item({ price: 4800 }));
    repo.getWalletInTransaction.mockResolvedValue(wallet({ astrite: 4799 }));

    await expect(service.buyItem('u1', 'i1')).rejects.toThrow('You don\'t have enough Astrite.');
    expect(astriteRepo.setBalanceInTransaction).not.toHaveBeenCalled();
    expect(astriteRepo.addEntryInTransaction).not.toHaveBeenCalled();
    expect(repo.unlockInTransaction).not.toHaveBeenCalled();
  });

  it('buyItem: already owned -> 409, no debit', async () => {
    repo.findItemById.mockResolvedValue(item());
    repo.getWalletInTransaction.mockResolvedValue(
      wallet({ astrite: 99999, unlockedSkins: new Set(['1']) }),
    );

    await expect(service.buyItem('u1', 'i1')).rejects.toThrow(ConflictException);
    expect(astriteRepo.setBalanceInTransaction).not.toHaveBeenCalled();
  });

  it('buyItem: item disabled or missing -> 400', async () => {
    repo.findItemById.mockResolvedValueOnce(item({ isActive: false }));
    await expect(service.buyItem('u1', 'i1')).rejects.toThrow(BadRequestException);

    repo.findItemById.mockResolvedValueOnce(null);
    await expect(service.buyItem('u1', 'nope')).rejects.toThrow(BadRequestException);

    expect(repo.runTransaction).not.toHaveBeenCalled();
  });

  it('buyItem: user doc missing -> 404', async () => {
    repo.findItemById.mockResolvedValue(item());
    repo.getWalletInTransaction.mockResolvedValue(wallet({ exists: false }));

    await expect(service.buyItem('ghost', 'i1')).rejects.toThrow(NotFoundException);
  });

  it('buyItem: a 0-price item still writes 1 ledger row (keeps balance <-> ledger invariant)', async () => {
    repo.findItemById.mockResolvedValue(item({ price: 0 }));
    repo.getWalletInTransaction.mockResolvedValue(wallet({ astrite: 0 }));

    await service.buyItem('u1', 'i1');

    expect(astriteRepo.addEntryInTransaction).toHaveBeenCalledWith(
      tx,
      expect.objectContaining({ type: 'SHOP_PURCHASE', amount: -0, balanceAfter: 0 }),
    );
  });

  // ==================== Admin CRUD ====================

  it('createItem SKIN: normalizes refId "01" -> "1", default price 4800 when empty', async () => {
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

  it('createItem EFFECT: the admin-entered price is kept', async () => {
    const created = await service.createItem({
      itemName: 'Leaf',
      itemType: 'EFFECT',
      refId: '3',
      price: 999,
    });
    expect(created.price).toBe(999);
  });

  it.each(['0', '-1', '1.5', 'abc'])(
    'createItem SKIN: invalid refId "%s" -> 400',
    async (r) => {
      await expect(
        service.createItem({ itemName: 'x', itemType: 'SKIN', refId: r }),
      ).rejects.toThrow(BadRequestException);
    },
  );

  it('createItem FRAME: SHOP frame -> uses the frame image when none is sent, default price 320', async () => {
    framesRepo.findById.mockResolvedValue({
      frameId: 'f1',
      unlockType: 'SHOP',
      imageUrl: 'http://f.png',
    } as never);

    const created = await service.createItem({ itemName: 'Fire', itemType: 'FRAME', refId: 'f1' });

    expect(created).toEqual(expect.objectContaining({ price: 320, imageUrl: 'http://f.png' }));
  });

  it('createItem FRAME: frame not SHOP / missing -> 400', async () => {
    framesRepo.findById.mockResolvedValueOnce({ frameId: 'f1', unlockType: 'POST_COUNT' } as never);
    await expect(
      service.createItem({ itemName: 'x', itemType: 'FRAME', refId: 'f1' }),
    ).rejects.toThrow(BadRequestException);

    framesRepo.findById.mockResolvedValueOnce(null);
    await expect(
      service.createItem({ itemName: 'x', itemType: 'FRAME', refId: 'nope' }),
    ).rejects.toThrow(BadRequestException);
  });

  it('createItem: duplicate listing (itemType, refId) -> 409', async () => {
    repo.findItemByRef.mockResolvedValue(item());

    await expect(
      service.createItem({ itemName: 'Snow 2', itemType: 'SKIN', refId: '1' }),
    ).rejects.toThrow(ConflictException);
    expect(repo.createItem).not.toHaveBeenCalled();
  });

  it('updateItem: merges sent fields, ignores undefined', async () => {
    repo.findItemById.mockResolvedValue(item({ price: 4800 }));

    const updated = await service.updateItem('i1', { price: 3000, itemName: undefined });

    expect(repo.updateItem).toHaveBeenCalledWith('i1', { price: 3000, itemName: undefined });
    expect(updated.price).toBe(3000);
    expect(updated.itemName).toBe('Snow');
  });

  it('updateItem / deleteItem: missing -> 404', async () => {
    repo.findItemById.mockResolvedValue(null);
    await expect(service.updateItem('x', {})).rejects.toThrow(NotFoundException);
    await expect(service.deleteItem('x')).rejects.toThrow(NotFoundException);
  });

  it('grantItem: unlocks directly (SKIN as number), ledger untouched', async () => {
    repo.findItemById.mockResolvedValue(item({ refId: '2' }));
    usersRepo.findByUid.mockResolvedValue({ uid: 'u2' } as never);

    await service.grantItem('i1', 'u2');

    expect(usersRepo.unlockCollectible).toHaveBeenCalledWith('u2', 'unlockedSkins', 2);
    expect(astriteRepo.addEntryInTransaction).not.toHaveBeenCalled();
  });

  it('grantItem: missing uid -> 404 (prevents creating a "ghost" user doc)', async () => {
    repo.findItemById.mockResolvedValue(item());
    usersRepo.findByUid.mockResolvedValue(null);

    await expect(service.grantItem('i1', 'ghost')).rejects.toThrow(NotFoundException);
    expect(usersRepo.unlockCollectible).not.toHaveBeenCalled();
  });

  it('listAllPurchases: filters uid / type / date + enriches names', async () => {
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

  it('syncFrameListing(true): not listed -> creates an item at the default price', async () => {
    await service.syncFrameListing(
      { frameId: 'f1', frameName: 'Fire', imageUrl: 'http://f.png', unlockType: 'SHOP' } as never,
      true,
    );

    expect(repo.createItem).toHaveBeenCalledWith(
      expect.objectContaining({ itemType: 'FRAME', refId: 'f1', price: 320, itemName: 'Fire' }),
    );
  });

  it('syncFrameListing(true): already listed -> does NOT overwrite (keeps the admin-edited price)', async () => {
    repo.findItemByRef.mockResolvedValue(item({ itemType: 'FRAME', refId: 'f1', price: 50 }));

    await service.syncFrameListing({ frameId: 'f1' } as never, true);

    expect(repo.createItem).not.toHaveBeenCalled();
    expect(repo.updateItem).not.toHaveBeenCalled();
  });

  it('syncFrameListing(false): item exists -> removed from the Shop', async () => {
    repo.findItemByRef.mockResolvedValue(item({ itemId: 'g1', itemType: 'FRAME', refId: 'f1' }));

    await service.syncFrameListing({ frameId: 'f1' } as never, false);

    expect(repo.deleteItem).toHaveBeenCalledWith('g1');
  });
});
