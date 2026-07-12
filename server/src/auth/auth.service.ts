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
   */
  async adminLogin(idToken: string): Promise<{ accessToken: string; email?: string }> {
    let decoded;
    try {
      decoded = await this.firebase.auth().verifyIdToken(idToken);
    } catch {
      throw new UnauthorizedException('Firebase token khong hop le.');
    }

    if (decoded.admin !== true) {
      throw new UnauthorizedException('Tai khoan nay khong co quyen admin.');
    }

    const accessToken = await this.jwt.signAsync({
      sub: decoded.uid,
      email: decoded.email,
      role: 'admin',
    });

    return { accessToken, email: decoded.email };
  }
}
