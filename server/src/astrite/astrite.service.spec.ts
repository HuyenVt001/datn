import { BadRequestException } from '@nestjs/common';
import { SIGNUP_BONUS_ASTRITE } from '../common/constants';
import { AstriteRepository } from './astrite.repository';
import { AstriteService } from './astrite.service';
import { NewAstriteTransaction } from './entities/astrite-transaction.entity';

/**
 * Vi Astrite dung tien that (nap qua PayOS) nen so du va so cai KHONG duoc lech.
 * Cac test duoi kiem: cong/tru dung so, luon ghi kem 1 dong so cai, khong tieu
 * qua so du, va thuong tan thu chi vao dung 1 lan.
 */
describe('AstriteService', () => {
  let service: AstriteService;
  let repo: jest.Mocked<AstriteRepository>;
  let userData: Record<string, unknown> | undefined;
  let written: { balance: number; extra: Record<string, unknown> } | undefined;
  let entries: NewAstriteTransaction[];

  const fakeTx = {} as FirebaseFirestore.Transaction;

  beforeEach(() => {
    userData = { astrite: 500 };
    written = undefined;
    entries = [];

    repo = {
      // Chay thang callback voi 1 tx gia — logic transaction do Firestore lo,
      // o day chi test phan tinh toan cua service.
      runTransaction: jest.fn((fn: (tx: FirebaseFirestore.Transaction) => Promise<unknown>) =>
        fn(fakeTx),
      ),
      getUserInTransaction: jest.fn(async () => userData),
      getBalanceInTransaction: jest.fn(async () => (userData?.astrite as number) ?? 0),
      setBalanceInTransaction: jest.fn((_tx, _uid, balance: number, extra = {}) => {
        written = { balance, extra };
      }),
      addEntryInTransaction: jest.fn((_tx, entry: NewAstriteTransaction) => {
        entries.push(entry);
      }),
      getBalance: jest.fn(),
      listByUid: jest.fn(),
    } as unknown as jest.Mocked<AstriteRepository>;

    service = new AstriteService(repo);
  });

  describe('credit', () => {
    it('cong dung so va ghi 1 dong so cai kem balanceAfter', async () => {
      const balance = await service.credit('u1', 60, 'QUEST_REWARD', '2026-08-05');

      expect(balance).toBe(560);
      expect(written?.balance).toBe(560);
      expect(entries).toEqual([
        {
          uid: 'u1',
          type: 'QUEST_REWARD',
          amount: 60,
          balanceAfter: 560,
          refId: '2026-08-05',
        },
      ]);
    });

    it('so tien <= 0 -> bao loi, khong ghi gi', async () => {
      await expect(service.credit('u1', 0, 'TOPUP')).rejects.toThrow(BadRequestException);
      expect(entries).toHaveLength(0);
      expect(written).toBeUndefined();
    });

    it('user chua co field astrite -> coi nhu 0', async () => {
      userData = {};
      const balance = await service.credit('u1', 1600, 'SIGNUP_BONUS');
      expect(balance).toBe(1600);
    });
  });

  describe('debit', () => {
    it('tru dung so va ghi so cai voi amount AM', async () => {
      const balance = await service.debit('u1', 160, 'GACHA_SPEND', 'roll-1');

      expect(balance).toBe(340);
      expect(written?.balance).toBe(340);
      expect(entries[0]).toMatchObject({ amount: -160, balanceAfter: 340, refId: 'roll-1' });
    });

    it('khong du Astrite -> nem loi va KHONG ghi so cai', async () => {
      await expect(service.debit('u1', 501, 'GACHA_SPEND')).rejects.toThrow(
        'Bạn không đủ Astrite.',
      );
      expect(entries).toHaveLength(0);
      expect(written).toBeUndefined();
    });

    it('tieu dung het so du -> cho phep, con lai 0', async () => {
      const balance = await service.debit('u1', 500, 'GACHA_SPEND');
      expect(balance).toBe(0);
    });
  });

  describe('grantSignupBonusOnce', () => {
    it('lan dau -> cong 1600 + danh dau signupBonusClaimed', async () => {
      userData = { astrite: 0 };

      await service.grantSignupBonusOnce('u1');

      expect(written?.balance).toBe(SIGNUP_BONUS_ASTRITE);
      expect(written?.extra).toEqual({ signupBonusClaimed: true });
      expect(entries[0]).toMatchObject({ type: 'SIGNUP_BONUS', amount: SIGNUP_BONUS_ASTRITE });
    });

    it('da nhan roi -> KHONG cong lan hai (goi lai moi lan mo app van an toan)', async () => {
      userData = { astrite: 1600, signupBonusClaimed: true };

      await service.grantSignupBonusOnce('u1');

      expect(written).toBeUndefined();
      expect(entries).toHaveLength(0);
    });
  });
});
