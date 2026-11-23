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
    it('missing URL or API key -> enabled=false, verify throws (no network call)', async () => {
      const service = new AiService(configWith({ AI_SERVICE_URL: '', AI_SERVICE_API_KEY: '' }));
      expect(service.enabled).toBe(false);
      await expect(service.verify('https://x/y.jpg', 'cup')).rejects.toThrow('is not configured');
      expect(fetchMock).not.toHaveBeenCalled();
    });

    it('both vars set -> enabled=true (trailing / stripped from URL)', () => {
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

    it('POST /verify with X-API-Key + body {imageUrl,targetClass}, returns JSON', async () => {
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

    it('Space returns non-2xx -> throws with status (does not leak key)', async () => {
      fetchMock.mockResolvedValue({ ok: false, status: 503, json: () => Promise.resolve({}) });
      await expect(service().verify('https://img/a.jpg', 'cup')).rejects.toThrow(
        '/verify returned 503',
      );
    });

    it('timeout (AbortError) -> throws "timeout"', async () => {
      const abortErr = Object.assign(new Error('aborted'), { name: 'AbortError' });
      fetchMock.mockRejectedValue(abortErr);
      await expect(service().verify('https://img/a.jpg', 'cup')).rejects.toThrow('timeout');
    });
  });

  describe('generate', () => {
    it('sends the quest class list (AI_QUEST_CLASSES) + avoid', async () => {
      const service = new AiService(
        configWith({ AI_SERVICE_URL: 'https://x.hf.space', AI_SERVICE_API_KEY: 'secret' }),
      );
      fetchMock.mockResolvedValue({
        ok: true,
        json: () => Promise.resolve({ targetClass: 'cup', content: 'Take a photo of a cup' }),
      });

      const result = await service.generate(['book']);

      expect(result).toEqual({ targetClass: 'cup', content: 'Take a photo of a cup' });
      const body = JSON.parse(fetchMock.mock.calls[0][1].body);
      expect(body.classes).toEqual([...AI_QUEST_CLASSES]);
      expect(body.avoid).toEqual(['book']);
    });
  });

  describe('toVerifyImageUrl', () => {
    it('inserts the 640 c_limit transform (keeps aspect ratio, no crop) into a Cloudinary URL without one', () => {
      expect(
        AiService.toVerifyImageUrl(
          'https://res.cloudinary.com/demo/image/upload/v1712/snapget/abc.jpg',
        ),
      ).toBe(
        'https://res.cloudinary.com/demo/image/upload/w_640,h_640,c_limit,f_jpg,q_auto/v1712/snapget/abc.jpg',
      );
    });

    it('Cloudinary URL that ALREADY has a transform -> unchanged', () => {
      const url = 'https://res.cloudinary.com/demo/image/upload/w_500/v1/abc.jpg';
      expect(AiService.toVerifyImageUrl(url)).toBe(url);
    });

    it('non-Cloudinary image URL -> unchanged', () => {
      expect(AiService.toVerifyImageUrl('https://cdn.example.com/a.jpg')).toBe(
        'https://cdn.example.com/a.jpg',
      );
      const video = 'https://res.cloudinary.com/demo/video/upload/v1/abc.mp4';
      expect(AiService.toVerifyImageUrl(video)).toBe(video);
    });
  });
});
