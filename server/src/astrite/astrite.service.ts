import { BadRequestException, Injectable, Logger } from '@nestjs/common';
import { SIGNUP_BONUS_ASTRITE } from '../common/constants';
import { AstriteRepository } from './astrite.repository';
import { AstriteTransaction, AstriteTxType } from './entities/astrite-transaction.entity';

/**
 * Vi Astrite: cong/tru so du va ghi so cai trong CUNG mot transaction.
 *
 * Luat bat di bat dich: KHONG service nao duoc tu sua `users.astrite`. Muon
 * doi so du thi goi service nay — nho vay so cai luon khop so du.
 * Rieng gacha tu chay transaction cua no (vi con phai tru tien + mo khoa vat
 * pham + ghi lich su quay cung luc) va goi thang AstriteRepository.
 */
@Injectable()
export class AstriteService {
  private readonly logger = new Logger(AstriteService.name);

  constructor(private readonly repo: AstriteRepository) {}

  async getBalance(uid: string): Promise<number> {
    return this.repo.getBalance(uid);
  }

  async listTransactions(uid: string, limit = 50): Promise<AstriteTransaction[]> {
    return this.repo.listByUid(uid, limit);
  }

  /**
   * Cong Astrite (transaction — chong race khi 2 request cung luc).
   * Tra ve so du moi.
   */
  async credit(uid: string, amount: number, type: AstriteTxType, refId?: string): Promise<number> {
    if (amount <= 0) {
      throw new BadRequestException('So Astrite cong vao phai lon hon 0.');
    }
    return this.repo.runTransaction(async (tx) => {
      const balance = (await this.repo.getBalanceInTransaction(tx, uid)) + amount;
      this.repo.setBalanceInTransaction(tx, uid, balance);
      this.repo.addEntryInTransaction(tx, { uid, type, amount, balanceAfter: balance, refId });
      return balance;
    });
  }

  /**
   * Tru Astrite. Nem 400 neu khong du — kiem tra ben TRONG transaction nen
   * khong the bam 2 lan de tieu qua so du.
   */
  async debit(uid: string, amount: number, type: AstriteTxType, refId?: string): Promise<number> {
    if (amount <= 0) {
      throw new BadRequestException('So Astrite tru phai lon hon 0.');
    }
    return this.repo.runTransaction(async (tx) => {
      const current = await this.repo.getBalanceInTransaction(tx, uid);
      if (current < amount) {
        throw new BadRequestException('Bạn không đủ Astrite.');
      }
      const balance = current - amount;
      this.repo.setBalanceInTransaction(tx, uid, balance);
      this.repo.addEntryInTransaction(tx, {
        uid,
        type,
        amount: -amount,
        balanceAfter: balance,
        refId,
      });
      return balance;
    });
  }

  /**
   * Tang Astrite tan thu — CHI MOT LAN cho moi tai khoan.
   * Co `signupBonusClaimed` doc/ghi trong cung transaction nen 2 request dang
   * nhap dong thoi cung chi cong duoc 1 lan.
   */
  async grantSignupBonusOnce(uid: string): Promise<void> {
    const granted = await this.repo.runTransaction(async (tx) => {
      const data = await this.repo.getUserInTransaction(tx, uid);
      if (data?.signupBonusClaimed) {
        return false;
      }
      const balance = ((data?.astrite as number | undefined) ?? 0) + SIGNUP_BONUS_ASTRITE;
      this.repo.setBalanceInTransaction(tx, uid, balance, { signupBonusClaimed: true });
      this.repo.addEntryInTransaction(tx, {
        uid,
        type: 'SIGNUP_BONUS',
        amount: SIGNUP_BONUS_ASTRITE,
        balanceAfter: balance,
      });
      return true;
    });

    if (granted) {
      this.logger.log(`Tang ${SIGNUP_BONUS_ASTRITE} Astrite tan thu cho ${uid}`);
    }
  }
}
