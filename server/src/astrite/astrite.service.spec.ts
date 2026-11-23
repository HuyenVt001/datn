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
    it('credits the exact amount and writes 1 ledger row with balanceAfter', async () => {
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

    it('amount <= 0 -> error, writes nothing', async () => {
      await expect(service.credit('u1', 0, 'TOPUP')).rejects.toThrow(BadRequestException);
      expect(entries).toHaveLength(0);
      expect(written).toBeUndefined();
    });

    it('user without astrite field -> treated as 0', async () => {
      userData = {};
      const balance = await service.credit('u1', 1600, 'SIGNUP_BONUS');
      expect(balance).toBe(1600);
    });
  });

  describe('debit', () => {
    it('debits the exact amount and writes a ledger row with NEGATIVE amount', async () => {
      const balance = await service.debit('u1', 160, 'SHOP_PURCHASE', 'purchase-1');

      expect(balance).toBe(340);
      expect(written?.balance).toBe(340);
      expect(entries[0]).toMatchObject({ amount: -160, balanceAfter: 340, refId: 'purchase-1' });
    });

    it('not enough Astrite -> throws and does NOT write ledger', async () => {
      await expect(service.debit('u1', 501, 'SHOP_PURCHASE')).rejects.toThrow(
        "You don't have enough Astrite.",
      );
      expect(entries).toHaveLength(0);
      expect(written).toBeUndefined();
    });

    it('spending the whole balance -> allowed, 0 left', async () => {
      const balance = await service.debit('u1', 500, 'SHOP_PURCHASE');
      expect(balance).toBe(0);
    });
  });

  describe('grantSignupBonusOnce', () => {
    it('first time -> credits 1600 + marks signupBonusClaimed', async () => {
      userData = { astrite: 0 };

      await service.grantSignupBonusOnce('u1');

      expect(written?.balance).toBe(SIGNUP_BONUS_ASTRITE);
      expect(written?.extra).toEqual({ signupBonusClaimed: true });
      expect(entries[0]).toMatchObject({ type: 'SIGNUP_BONUS', amount: SIGNUP_BONUS_ASTRITE });
    });

    it('already claimed -> NOT credited twice (safe to call on every app open)', async () => {
      userData = { astrite: 1600, signupBonusClaimed: true };

      await service.grantSignupBonusOnce('u1');

      expect(written).toBeUndefined();
      expect(entries).toHaveLength(0);
    });
  });
});
