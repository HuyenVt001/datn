import { Global, Module } from '@nestjs/common';
import { FirebaseService } from './firebase.service';

/**
 * @Global — FirebaseService dung chung toan app, khong can import lai o tung module.
 */
@Global()
@Module({
  providers: [FirebaseService],
  exports: [FirebaseService],
})
export class FirebaseModule {}
