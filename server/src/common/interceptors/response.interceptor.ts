import { CallHandler, ExecutionContext, Injectable, NestInterceptor } from '@nestjs/common';
import { Response } from 'express';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { ApiResponseDto } from '../dto/api-response.dto';

/**
 * Boc moi response thanh cong vao envelope chuan { success, statusCode, message, data }.
 * Neu controller tra { message, data } thi lay message do, nguoc lai mac dinh 'OK'.
 */
@Injectable()
export class ResponseInterceptor<T> implements NestInterceptor<T, ApiResponseDto<T>> {
  intercept(context: ExecutionContext, next: CallHandler): Observable<ApiResponseDto<T>> {
    const res = context.switchToHttp().getResponse<Response>();
    return next.handle().pipe(
      map((payload) => {
        // Cho phep controller/service tra { message, data } de custom message.
        const hasCustomMessage =
          payload && typeof payload === 'object' && 'data' in payload && 'message' in payload;
        const data = hasCustomMessage ? (payload as { data: T }).data : (payload as T);
        const message = hasCustomMessage ? (payload as { message: string }).message : 'OK';

        return {
          success: true,
          statusCode: res.statusCode,
          message,
          data: data ?? (null as T),
        };
      }),
    );
  }
}
