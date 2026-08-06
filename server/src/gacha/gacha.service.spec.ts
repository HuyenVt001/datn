import { BadRequestException } from '@nestjs/common';
import { AstriteRepository } from '../astrite/astrite.repository';
import {
  GACHA_COST_SINGLE,
  GACHA_COST_TEN,
  GACHA_PITY_SSR,
  GACHA_REFUND_R,
  GACHA_REFUND_SSR,
} from '../common/constants';
import { NewAstriteTransaction } from '../astrite/entities/astrite-transaction.entity';
import { FramesRepository } from '../frames/frames.repository';
import { UsersRepository } from '../users/users.repository';
import { GachaItem } from './entities/gacha-item.entity';
import { GachaRoll } from './entities/gacha-roll.entity';
import { GachaRepository, UserGachaState } from './gacha.repository';
import { GachaService } from './gacha.service';

/**
 * Gacha tieu Astrite (mua bang tien that qua PayOS) nen sai o day = sai tien.
 * Cac test duoi khoa chat 4 dieu de sau nay refactor khong lam vo:
 *   1. Bao hiem pity uu tien bac cao, va CHI reset bo dem cua bac vua trung.
 *   2. SR/SSR khong bao gio ra trung khi con do chua so huu.
 *   3. Quay x10 cap nhat "da so huu" NGAY trong vong lap (khong phat trung skin).
 *   4. So du va so cai luon khop nhau.
 */

const item = (over: Partial<GachaItem>): GachaItem => ({
  itemId: 'i',
  itemName: 'item',
  itemType: 'SKIN',
  rarity: 'SSR',
  refId: '1',
  isActive: true,
  sortOrder: 0,
  createdAt: '',
  ...over,
});

const POOL: GachaItem[] = [
  item({ itemId: 'f1', itemName: 'Khung 1', itemType: 'FRAME', rarity: 'R', refId: 'frame-1' }),
  item({ itemId: 'f2', itemName: 'Khung 2', itemType: 'FRAME', rarity: 'R', refId: 'frame-2' }),
  item({ itemId: 'e1', itemName: 'Snowfall', itemType: 'EFFECT', rarity: 'SR', refId: '1' }),
  item({ itemId: 'e2', itemName: 'Leaf', itemType: 'EFFECT', rarity: 'SR', refId: '2' }),
  item({ itemId: 's1', itemName: 'Snow', itemType: 'SKIN', rarity: 'SSR', refId: '1' }),
  item({ itemId: 's2', itemName: 'Forest', itemType: 'SKIN', rarity: 'SSR', refId: '2' }),
];

/** Service co nguon ngau nhien dieu khien duoc — test khong phu thuoc may man. */
class TestableGachaService extends GachaService {
  floats: number[] = [];
  indexes: number[] = [];
  astriteN = 10;

  protected randomFloat(): number {
    return this.floats.shift() ?? 0.99; // mac dinh roi vao bac N
  }
  protected randomIndex(size: number): number {
    const i = this.indexes.shift() ?? 0;
    return i % Math.max(size, 1);
  }
  protected randomAstrite(): number {
    return this.astriteN;
  }
}

describe('GachaService', () => {
  let service: TestableGachaService;
  let repo: jest.Mocked<GachaRepository>;
  let astriteRepo: jest.Mocked<AstriteRepository>;
  let framesRepo: jest.Mocked<FramesRepository>;
  let usersRepo: jest.Mocked<UsersRepository>;
  let state: UserGachaState;
  let written: UserGachaState | undefined;
  let ledger: NewAstriteTransaction[];
  let savedRoll: Record<string, unknown> | undefined;

  const fakeTx = {} as FirebaseFirestore.Transaction;

  const makeState = (over: Partial<UserGachaState> = {}): UserGachaState => ({
    astrite: 10_000,
    pity: { R: 0, SR: 0, SSR: 0 },
    unlockedFrames: new Set<string>(),
    unlockedSkins: new Set<string>(),
    unlockedEffects: new Set<string>(),
    ...over,
  });

  beforeEach(() => {
    state = makeState();
    written = undefined;
    ledger = [];
    savedRoll = undefined;

    repo = {
      listActiveItems: jest.fn(async () => POOL),
      newRollRef: jest.fn(() => ({ id: 'roll-1' }) as FirebaseFirestore.DocumentReference),
      runTransaction: jest.fn((fn: (tx: FirebaseFirestore.Transaction) => Promise<unknown>) =>
        fn(fakeTx),
      ),
      getUserStateInTransaction: jest.fn(async () => state),
      getUserState: jest.fn(async () => state),
      applyRollInTransaction: jest.fn((_tx, _uid, s: UserGachaState) => {
        written = s;
      }),
      addRollInTransaction: jest.fn((_tx, _ref, roll: Record<string, unknown>) => {
        savedRoll = roll;
      }),
      listRollsByUid: jest.fn(),
      // Quan tri kho vat pham + lich su toan he thong (G3)
      findItemByRef: jest.fn(),
      findItemById: jest.fn(),
      createItem: jest.fn(async (i: Omit<GachaItem, 'itemId'>) => ({ itemId: 'new', ...i })),
      updateItem: jest.fn(),
      deleteItem: jest.fn(),
      listAllRolls: jest.fn(),
    } as unknown as jest.Mocked<GachaRepository>;

    astriteRepo = {
      addEntryInTransaction: jest.fn((_tx, e: NewAstriteTransaction) => {
        ledger.push(e);
      }),
    } as unknown as jest.Mocked<AstriteRepository>;

    framesRepo = {
      findById: jest.fn(async () => ({ frameId: 'frame-9', frameName: 'Khung 9' })),
    } as unknown as jest.Mocked<FramesRepository>;
    usersRepo = {
      getFullNamesByUids: jest.fn(async () => new Map([['u1', 'Nguyen Van An']])),
      // Kho thuong (grant/owners)
      findByUid: jest.fn(async (uid: string) => (uid === 'u1' ? { uid, fullName: 'An' } : null)),
      unlockCollectible: jest.fn(),
      listByCollectible: jest.fn(async () => [
        { uid: 'u1', fullName: 'Nguyen Van An', email: 'an@x.vn' },
      ]),
    } as unknown as jest.Mocked<UsersRepository>;

    service = new TestableGachaService(repo, astriteRepo, framesRepo, usersRepo);
  });

  describe('bảo hiểm pity', () => {
    it('chạm pity SSR -> ép ra SSR dù random rơi vào bậc N', async () => {
      state = makeState({ pity: { R: 3, SR: 20, SSR: GACHA_PITY_SSR - 1 } });
      service.floats = [0.99]; // random nay le ra la bac N

      const out = await service.roll('u1', 1);

      expect(out.results[0].tier).toBe('SSR');
    });

    it('CHỈ reset bộ đếm của bậc vừa trúng — pity R và SR giữ nguyên', async () => {
      state = makeState({ pity: { R: 7, SR: 40, SSR: GACHA_PITY_SSR - 1 } });
      service.floats = [0.99];

      await service.roll('u1', 1);

      // +1 moi bac truoc khi chon, rieng SSR ve 0
      expect(written?.pity).toEqual({ R: 8, SR: 41, SSR: 0 });
    });

    it('ưu tiên bậc cao khi nhiều pity cùng chạm', async () => {
      state = makeState({ pity: { R: 99, SR: 99, SSR: GACHA_PITY_SSR - 1 } });
      service.floats = [0.99];

      const out = await service.roll('u1', 1);

      expect(out.results[0].tier).toBe('SSR');
    });
  });

  describe('chọn vật phẩm', () => {
    it('SSR chưa sở hữu hết -> KHÔNG bao giờ ra trùng', async () => {
      state = makeState({ unlockedSkins: new Set(['1']) }); // da co Snow
      service.floats = [0.0005]; // roi vao SSR
      service.indexes = [0];

      const out = await service.roll('u1', 1);

      expect(out.results[0].refId).toBe('2'); // bat buoc ra Forest
      expect(out.results[0].isDuplicate).toBe(false);
      expect(out.refundTotal).toBe(0);
    });

    it('đã sở hữu HẾT SSR -> mở trùng và hoàn 2000 Astrite', async () => {
      state = makeState({ unlockedSkins: new Set(['1', '2']) });
      service.floats = [0.0005];

      const out = await service.roll('u1', 1);

      expect(out.results[0].tier).toBe('SSR');
      expect(out.results[0].isDuplicate).toBe(true);
      expect(out.results[0].refundAstrite).toBe(GACHA_REFUND_SSR);
      expect(out.refundTotal).toBe(GACHA_REFUND_SSR);
    });

    it('R được phép ra trùng ngay cả khi còn khung chưa sở hữu -> hoàn 160', async () => {
      state = makeState({ unlockedFrames: new Set(['frame-1']) });
      service.floats = [0.02]; // roi vao R
      service.indexes = [0]; // boc phan tu dau cua CA pool R = frame-1 (da co)

      const out = await service.roll('u1', 1);

      expect(out.results[0].tier).toBe('R');
      expect(out.results[0].isDuplicate).toBe(true);
      expect(out.results[0].refundAstrite).toBe(GACHA_REFUND_R);
    });

    it('bậc không còn vật phẩm nào đang bật -> trả Astrite thay vì lỗi', async () => {
      repo.listActiveItems.mockResolvedValue(POOL.filter((i) => i.rarity !== 'SSR'));
      service.floats = [0.0005]; // roi vao SSR nhung pool SSR rong
      service.astriteN = 42;

      const out = await service.roll('u1', 1);

      expect(out.results[0].tier).toBe('N');
      expect(out.results[0].astriteAmount).toBe(42);
    });

    it('bậc bị ẩn hết mà đang chạm pity -> GIỮ NGUYÊN bộ đếm (bảo hiểm chưa bị tiêu)', async () => {
      // Admin ẩn toàn bộ SSR trong lúc người chơi đã quay 99 lần không ra SSR
      repo.listActiveItems.mockResolvedValue(POOL.filter((i) => i.rarity !== 'SSR'));
      state = makeState({ pity: { R: 0, SR: 0, SSR: GACHA_PITY_SSR - 1 } });
      service.floats = [0.99]; // random ra N, nhung pity ep thanh SSR

      const out = await service.roll('u1', 1);

      expect(out.results[0].tier).toBe('N'); // khong co SSR de phat -> tra Astrite
      // Bo dem phai la 100 (99 + lan quay nay), KHONG duoc ve 0: mo lai vat pham
      // la nguoi choi doi thuong SSR ngay lan quay sau.
      expect(written?.pity.SSR).toBe(GACHA_PITY_SSR);
    });
  });

  describe('quay x10', () => {
    it('KHÔNG phát trùng skin trong cùng một lượt (cập nhật sở hữu ngay trong vòng lặp)', async () => {
      // 2 lan dau deu roi vao SSR, luon boc phan tu dau cua danh sach chua so huu
      service.floats = [0.0005, 0.0005, ...Array(8).fill(0.99)];
      service.indexes = [0, 0];

      const out = await service.roll('u1', 10);

      const ssr = out.results.filter((r) => r.tier === 'SSR');
      expect(ssr).toHaveLength(2);
      expect(new Set(ssr.map((r) => r.refId)).size).toBe(2); // 2 skin KHAC nhau
      expect(ssr.every((r) => !r.isDuplicate)).toBe(true);
      expect(written?.unlockedSkins).toEqual(new Set(['1', '2']));
    });

    it('luôn có ÍT NHẤT 1 khung R trong mỗi lượt x10 (pity R = 10)', async () => {
      // Moi lan random deu roi vao bac N; chi co bao hiem moi tao ra vat pham
      service.astriteN = 5;

      const out = await service.roll('u1', 10);

      const tiers = out.results.map((r) => r.tier);
      expect(tiers).toHaveLength(10);
      // 9 lan dau ra N, lan thu 10 cham pity R -> bi ep ra khung
      expect(tiers.slice(0, 9)).toEqual(Array(9).fill('N'));
      expect(tiers[9]).toBe('R');
      expect(written?.pity.R).toBe(0); // bo dem R vua reset
    });

    it('trừ đúng giá gói x10 (1440 = giảm 10%) và cộng lại phần hoàn', async () => {
      service.astriteN = 5;

      const out = await service.roll('u1', 10);

      expect(out.cost).toBe(GACHA_COST_TEN);
      // 9 lan bac N x 5 = 45; lan thu 10 ra khung MOI nen khong hoan
      expect(out.refundTotal).toBe(45);
      expect(out.astriteAfter).toBe(10_000 - GACHA_COST_TEN + 45);
    });
  });

  describe('sổ cái và số dư', () => {
    it('ghi 1 dòng trừ tiền + 1 dòng hoàn, balanceAfter khớp số dư cuối', async () => {
      service.astriteN = 30;

      const out = await service.roll('u1', 1);

      expect(ledger).toHaveLength(2);
      expect(ledger[0]).toMatchObject({
        type: 'GACHA_SPEND',
        amount: -GACHA_COST_SINGLE,
        refId: 'roll-1',
      });
      expect(ledger[1]).toMatchObject({ type: 'GACHA_REFUND', amount: 30, refId: 'roll-1' });
      // Cong don so cai == so du cuoi
      expect(ledger[0].amount + ledger[1].amount + 10_000).toBe(out.astriteAfter);
      expect(ledger[1].balanceAfter).toBe(out.astriteAfter);
    });

    it('không có hoàn -> chỉ ghi 1 dòng trừ tiền', async () => {
      state = makeState({ unlockedSkins: new Set() });
      service.floats = [0.0005]; // SSR moi -> khong hoan

      await service.roll('u1', 1);

      expect(ledger).toHaveLength(1);
      expect(ledger[0].type).toBe('GACHA_SPEND');
    });

    it('lưu lịch sử quay kèm balanceAfter', async () => {
      service.astriteN = 7;
      const out = await service.roll('u1', 1);

      expect(savedRoll).toMatchObject({
        uid: 'u1',
        rollType: 'SINGLE',
        cost: GACHA_COST_SINGLE,
        refundTotal: 7,
        balanceAfter: out.astriteAfter,
      });
    });
  });

  describe('chặn đầu vào', () => {
    it('không đủ Astrite -> báo lỗi, KHÔNG ghi gì', async () => {
      state = makeState({ astrite: GACHA_COST_SINGLE - 1 });

      await expect(service.roll('u1', 1)).rejects.toThrow('Bạn không đủ Astrite.');
      expect(written).toBeUndefined();
      expect(ledger).toHaveLength(0);
    });

    it('số lần quay khác 1 và 10 -> báo lỗi', async () => {
      await expect(service.roll('u1', 5)).rejects.toThrow(BadRequestException);
    });

    it('kho vật phẩm trống -> báo lỗi thay vì trừ tiền', async () => {
      repo.listActiveItems.mockResolvedValue([]);

      await expect(service.roll('u1', 1)).rejects.toThrow(
        'Kho vật phẩm đang trống, chưa thể quay.',
      );
      expect(ledger).toHaveLength(0);
    });
  });

  describe('quản trị kho vật phẩm', () => {
    beforeEach(() => {
      repo.findItemByRef.mockResolvedValue(null);
      repo.findItemById.mockResolvedValue(POOL[0]);
    });

    const newFrame = {
      itemName: 'Khung 9',
      itemType: 'FRAME' as const,
      rarity: 'R' as const,
      refId: 'frame-9',
    };

    it('thêm SKIN/EFFECT -> chặn (asset nằm trong APK, admin không tạo mới được)', async () => {
      await expect(
        service.createItem({ ...newFrame, itemType: 'SKIN', rarity: 'SSR', refId: '3' }),
      ).rejects.toThrow('Chỉ thêm mới được khung ảnh');
      expect(repo.createItem).not.toHaveBeenCalled();
    });

    it('thêm khung có refId không tồn tại -> chặn', async () => {
      framesRepo.findById.mockResolvedValue(null);

      await expect(service.createItem(newFrame)).rejects.toThrow('Không tìm thấy khung ảnh');
      expect(repo.createItem).not.toHaveBeenCalled();
    });

    it('thêm khung ĐÃ có trong kho -> chặn (tránh 1 khung 2 lần = tỉ lệ gấp đôi)', async () => {
      repo.findItemByRef.mockResolvedValue(POOL[0]);

      await expect(service.createItem(newFrame)).rejects.toThrow('đã có trong kho vật phẩm');
      expect(repo.createItem).not.toHaveBeenCalled();
    });

    it('thêm khung hợp lệ -> mặc định bật + lấy ảnh của khung khi không gửi imageUrl', async () => {
      framesRepo.findById.mockResolvedValue({
        frameId: 'frame-9',
        frameName: 'Khung 9',
        imageUrl: 'https://cdn/f9.png',
      } as never);

      const item = await service.createItem(newFrame);

      expect(item.isActive).toBe(true);
      expect(item.imageUrl).toBe('https://cdn/f9.png');
    });

    it('sửa/xoá vật phẩm không tồn tại -> 404', async () => {
      repo.findItemById.mockResolvedValue(null);

      await expect(service.updateItem('bad', { itemName: 'x' })).rejects.toThrow(
        'Không tìm thấy vật phẩm.',
      );
      await expect(service.deleteItem('bad')).rejects.toThrow('Không tìm thấy vật phẩm.');
    });
  });

  describe('lịch sử quay (admin)', () => {
    const roll = (over: Partial<GachaRoll>): GachaRoll => ({
      rollId: 'r',
      uid: 'u1',
      rollType: 'SINGLE',
      cost: GACHA_COST_SINGLE,
      results: [{ tier: 'N', isDuplicate: false, refundAstrite: 0 }],
      refundTotal: 0,
      balanceAfter: 0,
      createdAt: '2026-08-05T10:00:00.000Z',
      ...over,
    });

    beforeEach(() => {
      repo.listAllRolls.mockResolvedValue([
        roll({ rollId: 'r1' }),
        roll({
          rollId: 'r2',
          uid: 'u2',
          results: [{ tier: 'SSR', isDuplicate: false, refundAstrite: 0 }],
        }),
        roll({ rollId: 'r3', createdAt: '2026-08-04T10:00:00.000Z' }),
      ]);
    });

    it('lọc theo bậc: chỉ giữ lượt quay CÓ ít nhất 1 kết quả bậc đó', async () => {
      const rows = await service.listAllRolls({ tier: 'SSR' });
      expect(rows.map((r) => r.rollId)).toEqual(['r2']);
    });

    it('lọc theo uid và theo ngày', async () => {
      expect((await service.listAllRolls({ uid: 'u1' })).map((r) => r.rollId)).toEqual([
        'r1',
        'r3',
      ]);
      expect((await service.listAllRolls({ date: '2026-08-04' })).map((r) => r.rollId)).toEqual([
        'r3',
      ]);
    });

    it('enrich tên người quay; uid không có doc thì hiện luôn uid', async () => {
      const rows = await service.listAllRolls({});
      expect(rows.find((r) => r.uid === 'u1')?.fullName).toBe('Nguyen Van An');
      expect(rows.find((r) => r.uid === 'u2')?.fullName).toBe('u2');
    });
  });

  describe('kho thưởng — tặng vật phẩm', () => {
    it('tặng SKIN ghi vào unlockedSkins dạng SỐ — app so id kiểu Int', async () => {
      repo.findItemById.mockResolvedValueOnce(item({ itemId: 's1', itemType: 'SKIN', refId: '1' }));

      await service.grantItem('s1', 'u1');

      expect(usersRepo.unlockCollectible).toHaveBeenCalledWith('u1', 'unlockedSkins', 1);
    });

    it('tặng FRAME giữ refId dạng chuỗi (frameId Firestore)', async () => {
      repo.findItemById.mockResolvedValueOnce(
        item({ itemId: 'f1', itemType: 'FRAME', rarity: 'R', refId: 'frame-9' }),
      );

      await service.grantItem('f1', 'u1');

      expect(usersRepo.unlockCollectible).toHaveBeenCalledWith('u1', 'unlockedFrames', 'frame-9');
    });

    it('tặng cho uid không tồn tại -> 404, không sinh user doc "ma"', async () => {
      repo.findItemById.mockResolvedValueOnce(item({ itemId: 's1' }));

      await expect(service.grantItem('s1', 'uid-go-nham')).rejects.toThrow(
        'Không tìm thấy người dùng này.',
      );
      expect(usersRepo.unlockCollectible).not.toHaveBeenCalled();
    });

    it('owners: tra cứu đúng field + kiểu giá trị theo loại vật phẩm', async () => {
      repo.findItemById.mockResolvedValueOnce(
        item({ itemId: 'e1', itemType: 'EFFECT', rarity: 'SR', refId: '3' }),
      );

      const { owners } = await service.listItemOwners('e1');

      expect(usersRepo.listByCollectible).toHaveBeenCalledWith('unlockedEffects', 3);
      expect(owners).toEqual([{ uid: 'u1', fullName: 'Nguyen Van An', email: 'an@x.vn' }]);
    });
  });
});
