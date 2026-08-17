import { AI_MODEL_CLASSES, AI_QUEST_CLASSES, AI_QUEST_CONTENT_MAX } from '../../common/constants';
import { AI_QUEST_TEMPLATES, pickFallbackQuest, seededIndex } from './ai-quest-templates';

/**
 * Bo mau quest AI la NGUON CHINH cua noi dung quest (2026-08-16) — khoa chat luong
 * de them cau ve sau khong lam vo rang buoc phia server/app.
 */
describe('AI_QUEST_TEMPLATES', () => {
  it('moi lop ra de co >= 6 cau, tong 72, khong trung cau trong 1 lop', () => {
    let total = 0;
    for (const cls of AI_QUEST_CLASSES) {
      const list = AI_QUEST_TEMPLATES[cls];
      expect(list.length).toBeGreaterThanOrEqual(6);
      expect(new Set(list).size).toBe(list.length);
      total += list.length;
    }
    expect(total).toBe(72);
  });

  it('cau nao cung bat dau bang "Chụp", <= AI_QUEST_CONTENT_MAX ky tu, khong thua khoang trang', () => {
    for (const cls of AI_QUEST_CLASSES) {
      for (const content of AI_QUEST_TEMPLATES[cls]) {
        expect(content.startsWith('Chụp')).toBe(true);
        expect(content.length).toBeLessThanOrEqual(AI_QUEST_CONTENT_MAX);
        expect(content.trim()).toBe(content);
      }
    }
  });

  it('lop ra de la tap con cua lop model hoc; 3 lop bi loai van nam trong model', () => {
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
  it('xac dinh theo ngay: cung input -> cung output (2 instance server chon giong nhau)', () => {
    const a = pickFallbackQuest('2026-08-16', ['cup']);
    const b = pickFallbackQuest('2026-08-16', ['cup']);
    expect(a).toEqual(b);
    expect(AI_QUEST_CLASSES).toContain(a.targetClass);
    expect(AI_QUEST_TEMPLATES[a.targetClass]).toContain(a.content);
  });

  it('khong chon lop trong avoid; avoid het thi van chon duoc 1 lop', () => {
    for (let d = 1; d <= 28; d++) {
      const date = `2026-09-${String(d).padStart(2, '0')}`;
      const picked = pickFallbackQuest(date, ['cup', 'bottle', 'chair']);
      expect(['cup', 'bottle', 'chair']).not.toContain(picked.targetClass);
    }
    const all = pickFallbackQuest('2026-09-01', [...AI_QUEST_CLASSES]);
    expect(AI_QUEST_CLASSES).toContain(all.targetClass);
  });

  it('30 ngay lien tiep (avoid 3 ngay truoc nhu server) -> khong lap vat the trong 3 ngay canh nhau', () => {
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

  it('seededIndex luon trong [0, size)', () => {
    for (const seed of ['a', '2026-08-16', '2026-08-16:cup', '', 'ữ']) {
      const i = seededIndex(seed, 8);
      expect(i).toBeGreaterThanOrEqual(0);
      expect(i).toBeLessThan(8);
    }
  });
});
