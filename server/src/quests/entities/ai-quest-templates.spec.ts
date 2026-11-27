import {
  AI_EXTRA_CLASSES,
  AI_MODEL_CLASSES,
  AI_QUEST_CLASSES,
  AI_QUEST_CONTENT_MAX,
  AI_QUEST_GROUPS,
  aiQuestTargetClasses,
} from '../../common/constants';
import { AI_QUEST_TEMPLATES, pickFallbackQuest, seededIndex } from './ai-quest-templates';

/**
 * Bo mau quest AI la NGUON CHINH cua noi dung quest (2026-08-16) — khoa chat luong
 * de them cau ve sau khong lam vo rang buoc phia server/app.
 */
describe('AI_QUEST_TEMPLATES', () => {
  it('each quest has >= 6 sentences, 56 total, no duplicates within a quest', () => {
    let total = 0;
    for (const cls of AI_QUEST_CLASSES) {
      const list = AI_QUEST_TEMPLATES[cls];
      expect(list.length).toBeGreaterThanOrEqual(6);
      expect(new Set(list).size).toBe(list.length);
      total += list.length;
    }
    expect(total).toBe(56);
  });

  it('every sentence starts with "Take/Snap a photo/selfie", <= AI_QUEST_CONTENT_MAX characters, no extra whitespace', () => {
    for (const cls of AI_QUEST_CLASSES) {
      for (const content of AI_QUEST_TEMPLATES[cls]) {
        expect(content).toMatch(/^(Take|Snap) a (group )?(photo|selfie) /);
        expect(content.length).toBeLessThanOrEqual(AI_QUEST_CONTENT_MAX);
        expect(content.trim()).toBe(content);
      }
    }
  });

  it('every class in a quest group is known by y1 or an extra model; dropped classes stay in the model', () => {
    const known: readonly string[] = [...AI_MODEL_CLASSES, ...AI_EXTRA_CLASSES];
    const used = new Set<string>();
    for (const cls of AI_QUEST_CLASSES) {
      for (const c of AI_QUEST_GROUPS[cls]) {
        expect(known).toContain(c);
        used.add(c);
      }
    }
    // 2026-11-27: 3 lop da bo khoi ca quest lan model y2
    for (const dropped of ['backpack', 'clock', 'potted plant']) {
      expect(known).not.toContain(dropped);
      expect(used.has(dropped)).toBe(false);
    }
    // y2 chi hoc dung cac lop cua quest -> moi lop model deu thuoc 1 quest
    for (const c of AI_MODEL_CLASSES) expect(used.has(c)).toBe(true);
  });

  it('grouped quests accept every member; legacy quest keys fall back to a single class', () => {
    expect(aiQuestTargetClasses('cup')).toEqual(['cup', 'bottle']);
    expect(aiQuestTargetClasses('bicycle')).toEqual(['bicycle', 'motorcycle']);
    expect(aiQuestTargetClasses('person')).toEqual(['person']);
    expect(aiQuestTargetClasses('clock')).toEqual(['clock']); // doc quest cu truoc 2026-11-27
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
    expect(seen.size).toBeGreaterThanOrEqual(6); // 7 quest, avoid 3 — hau het quest deu ra trong 1 thang
  });

  it('seededIndex is always within [0, size)', () => {
    for (const seed of ['a', '2026-08-16', '2026-08-16:cup', '', 'ü']) {
      const i = seededIndex(seed, 8);
      expect(i).toBeGreaterThanOrEqual(0);
      expect(i).toBeLessThan(8);
    }
  });
});
