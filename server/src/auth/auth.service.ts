import { Injectable, UnauthorizedException } from '@nestjs/common';
import { JwtService } from '@nestjs/jwt';
import { FirebaseService } from '../firebase/firebase.service';

@Injectable()
export class AuthService {
  constructor(
    private readonly firebase: FirebaseService,
    private readonly jwt: JwtService,
  ) {}

  /**
   * Verify Firebase ID token cua admin -> kiem tra custom claim { admin: true }
   * -> phat JWT rieng cua server cho phien admin.
   * Quyen admin doc tu getUser() (trang thai HIEN TAI) chu khong tin claim trong
   * token cu — admin vua bi thu quyen thi token ID con han cung khong doi duoc JWT.
   */
  async adminLogin(idToken: string): Promise<{ accessToken: string; uid: string; email?: string }> {
    let decoded;
    try {
      decoded = await this.firebase.auth().verifyIdToken(idToken);
    } catch {
      throw new UnauthorizedException('Invalid Firebase token.');
    }

    const userRecord = await this.firebase.auth().getUser(decoded.uid);
    if (userRecord.customClaims?.admin !== true) {
      throw new UnauthorizedException('This account does not have admin rights.');
    }
    if (userRecord.disabled) {
      throw new UnauthorizedException('This account has been disabled.');
    }

    const accessToken = await this.jwt.signAsync({
      sub: decoded.uid,
      email: decoded.email,
      role: 'admin',
    });

    return { accessToken, uid: decoded.uid, email: decoded.email };
  }
}
