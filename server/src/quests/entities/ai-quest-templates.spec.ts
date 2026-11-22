import { AI_MODEL_CLASSES, AI_QUEST_CLASSES, AI_QUEST_CONTENT_MAX } from '../../common/constants';
import { AI_QUEST_TEMPLATES, pickFallbackQuest, seededIndex } from './ai-quest-templates';

/**
 * Bo mau quest AI la NGUON CHINH cua noi dung quest (2026-08-16) — khoa chat luong
 * de them cau ve sau khong lam vo rang buoc phia server/app.
 */
describe('AI_QUEST_TEMPLATES', () => {
  it('each quest class has >= 6 sentences, 72 total, no duplicates within a class', () => {
    let total = 0;
    for (const cls of AI_QUEST_CLASSES) {
      const list = AI_QUEST_TEMPLATES[cls];
      expect(list.length).toBeGreaterThanOrEqual(6);
      expect(new Set(list).size).toBe(list.length);
      total += list.length;
    }
    expect(total).toBe(72);
  });

  it('every sentence starts with "Take a photo of", <= AI_QUEST_CONTENT_MAX characters, no extra whitespace', () => {
    for (const cls of AI_QUEST_CLASSES) {
      for (const content of AI_QUEST_TEMPLATES[cls]) {
        expect(content.startsWith('Take a photo of')).toBe(true);
        expect(content.length).toBeLessThanOrEqual(AI_QUEST_CONTENT_MAX);
        expect(content.trim()).toBe(content);
      }
    }
  });

  it('quest classes are a subset of the model classes; the 3 excluded classes remain in the model', () => {
    for (const cls of AI_QUEST_CLASSES) {
      expect(AI_MODEL_CLASSES).toContain(cls);
    }
    // 3 lop bi loai khoi vong quay (QUEST_AI_PLAN 2.3) — van co trong output model
    for (const dropped of ['book', 'backpack', 'keyboard']) {
      expect(AI_MODEL_CLASSES).toContain(dropped);
      expect(AI_QUEST_CLASSES as readonly string[]).not.toContain(dropped);
    }
  });
});

describe('pickFallbackQuest', () => {
  it('deterministic per day: same input -> same output (2 server instances pick the same)', () => {
    const a = pickFallbackQuest('2026-08-16', ['cup']);
    const b = pickFallbackQuest('2026-08-16', ['cup']);
    expect(a).toEqual(b);
    expect(AI_QUEST_CLASSES).toContain(a.targetClass);
    expect(AI_QUEST_TEMPLATES[a.targetClass]).toContain(a.content);
  });

  it('never picks a class in avoid; if all are avoided it still picks one', () => {
    for (let d = 1; d <= 28; d++) {
      const date = `2026-09-${String(d).padStart(2, '0')}`;
      const picked = pickFallbackQuest(date, ['cup', 'bottle', 'chair']);
      expect(['cup', 'bottle', 'chair']).not.toContain(picked.targetClass);
    }
    const all = pickFallbackQuest('2026-09-01', [...AI_QUEST_CLASSES]);
    expect(AI_QUEST_CLASSES).toContain(all.targetClass);
  });

  it('30 consecutive days (avoiding the previous 3 days like the server) -> no object repeats within 3 adjacent days', () => {
    const recent: string[] = [];
    const seen = new Set<string>();
    for (let d = 1; d <= 30; d++) {
      const date = `2026-10-${String(d).padStart(2, '0')}`;
      const picked = pickFallbackQuest(date, recent.slice(-3));
      expect(recent.slice(-3)).not.toContain(picked.targetClass);
      recent.push(picked.targetClass);
      seen.add(picked.targetClass);
    }
    expect(seen.size).toBeGreaterThanOrEqual(7); // 9 lop, avoid 3 — hau het lop deu ra trong 1 thang
  });

  it('seededIndex is always within [0, size)', () => {
    for (const seed of ['a', '2026-08-16', '2026-08-16:cup', '', 'ü']) {
      const i = seededIndex(seed, 8);
      expect(i).toBeGreaterThanOrEqual(0);
      expect(i).toBeLessThan(8);
    }
  });
});
