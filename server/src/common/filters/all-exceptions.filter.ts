import {
  ArgumentsHost,
  Catch,
  ExceptionFilter,
  HttpException,
  HttpStatus,
  Logger,
} from '@nestjs/common';
import { Response } from 'express';

/**
 * Chuan hoa MOI loi ve envelope { success:false, statusCode, message, data:null }.
 * message tra ve cho client la tieng Viet than thien; chi tiet ky thuat cho vao log.
 */
@Catch()
export class AllExceptionsFilter implements ExceptionFilter {
  private readonly logger = new Logger(AllExceptionsFilter.name);

  catch(exception: unknown, host: ArgumentsHost): void {
    const ctx = host.switchToHttp();
    const res = ctx.getResponse<Response>();

    let statusCode = HttpStatus.INTERNAL_SERVER_ERROR;
    let message = 'Da co loi xay ra, vui long thu lai.';

    if (exception instanceof HttpException) {
      statusCode = exception.getStatus();
      const body = exception.getResponse();
      if (typeof body === 'string') {
        message = body;
      } else if (body && typeof body === 'object' && 'message' in body) {
        const raw = (body as { message: string | string[] }).message;
        message = Array.isArray(raw) ? raw.join(', ') : raw;
      }
    } else if (exception instanceof Error) {
      // Loi khong luong truoc — log day du, che giau chi tiet voi client.
      this.logger.error(exception.message, exception.stack);
    }

    res.status(statusCode).json({
      success: false,
      statusCode,
      message,
      data: null,
    });
  }
}
