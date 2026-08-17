import { ConfigService } from '@nestjs/config';
import { AI_QUEST_CLASSES } from '../common/constants';
import { AiService } from './ai.service';

describe('AiService', () => {
  const configWith = (values: Record<string, string | undefined>) =>
    ({ get: (key: string) => values[key] }) as unknown as ConfigService;

  const fetchMock = jest.fn();

  beforeEach(() => {
    fetchMock.mockReset();
    global.fetch = fetchMock as unknown as typeof fetch;
  });

  describe('enabled (fail-safe env)', () => {
    it('thieu URL hoac API key -> enabled=false, goi verify thi nem loi (khong goi mang)', async () => {
      const service = new AiService(configWith({ AI_SERVICE_URL: '', AI_SERVICE_API_KEY: '' }));
      expect(service.enabled).toBe(false);
      await expect(service.verify('https://x/y.jpg', 'cup')).rejects.toThrow('chua duoc cau hinh');
      expect(fetchMock).not.toHaveBeenCalled();
    });

    it('du 2 bien -> enabled=true (bo dau / cuoi URL)', () => {
      const service = new AiService(
        configWith({ AI_SERVICE_URL: 'https://x.hf.space/', AI_SERVICE_API_KEY: 'k' }),
      );
      expect(service.enabled).toBe(true);
    });
  });

  describe('verify', () => {
    const service = () =>
      new AiService(
        configWith({ AI_SERVICE_URL: 'https://x.hf.space', AI_SERVICE_API_KEY: 'secret' }),
      );

    it('POST /verify voi X-API-Key + body {imageUrl,targetClass}, tra JSON', async () => {
      const payload = {
        matched: true,
        score: 0.9,
        threshold: 0.35,
        scores: { cup: 0.9 },
        modelVersion: 'v0',
        latencyMs: 50,
      };
      fetchMock.mockResolvedValue({ ok: true, json: () => Promise.resolve(payload) });

      const result = await service().verify('https://img/a.jpg', 'cup');

      expect(result).toEqual(payload);
      const [url, init] = fetchMock.mock.calls[0];
      expect(url).toBe('https://x.hf.space/verify');
      expect(init.method).toBe('POST');
      expect(init.headers['X-API-Key']).toBe('secret');
      expect(JSON.parse(init.body)).toEqual({ imageUrl: 'https://img/a.jpg', targetClass: 'cup' });
    });

    it('Space tra khong 2xx -> nem loi kem status (khong lo key)', async () => {
      fetchMock.mockResolvedValue({ ok: false, status: 503, json: () => Promise.resolve({}) });
      await expect(service().verify('https://img/a.jpg', 'cup')).rejects.toThrow('/verify tra 503');
    });

    it('timeout (AbortError) -> nem loi "timeout"', async () => {
      const abortErr = Object.assign(new Error('aborted'), { name: 'AbortError' });
      fetchMock.mockRejectedValue(abortErr);
      await expect(service().verify('https://img/a.jpg', 'cup')).rejects.toThrow('timeout');
    });
  });

  describe('generate', () => {
    it('gui dung danh sach lop ra de (AI_QUEST_CLASSES) + avoid', async () => {
      const service = new AiService(
        configWith({ AI_SERVICE_URL: 'https://x.hf.space', AI_SERVICE_API_KEY: 'secret' }),
      );
      fetchMock.mockResolvedValue({
        ok: true,
        json: () => Promise.resolve({ targetClass: 'cup', content: 'Chụp một chiếc cốc' }),
      });

      const result = await service.generate(['book']);

      expect(result).toEqual({ targetClass: 'cup', content: 'Chụp một chiếc cốc' });
      const body = JSON.parse(fetchMock.mock.calls[0][1].body);
      expect(body.classes).toEqual([...AI_QUEST_CLASSES]);
      expect(body.avoid).toEqual(['book']);
    });
  });

  describe('toVerifyImageUrl', () => {
    it('chen transform 224 vao URL Cloudinary chua transform', () => {
      expect(
        AiService.toVerifyImageUrl(
          'https://res.cloudinary.com/demo/image/upload/v1712/snapget/abc.jpg',
        ),
      ).toBe(
        'https://res.cloudinary.com/demo/image/upload/w_224,h_224,c_fill,f_jpg,q_auto/v1712/snapget/abc.jpg',
      );
    });

    it('URL Cloudinary DA co transform -> giu nguyen', () => {
      const url = 'https://res.cloudinary.com/demo/image/upload/w_500/v1/abc.jpg';
      expect(AiService.toVerifyImageUrl(url)).toBe(url);
    });

    it('URL khong phai Cloudinary image -> giu nguyen', () => {
      expect(AiService.toVerifyImageUrl('https://cdn.example.com/a.jpg')).toBe(
        'https://cdn.example.com/a.jpg',
      );
      const video = 'https://res.cloudinary.com/demo/video/upload/v1/abc.mp4';
      expect(AiService.toVerifyImageUrl(video)).toBe(video);
    });
  });
});
