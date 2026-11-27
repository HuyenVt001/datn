import { AI_QUEST_CLASSES, AiQuestClass } from '../../common/constants';

/**
 * Bo cau quest AI — NGUON CHINH (user chot 2026-08-16 bo LLM sinh quest: AI chi lam
 * nhiem vu XAC MINH ANH, noi dung quest lay tu bo mau nay). Van dong thoi la duong
 * FALLBACK neu sau nay bat lai LLM (`source: 'LLM' | 'FALLBACK'` van giu).
 *
 * 7 quest x 8 cau = 56 cau (2026-11-27 — 7 quest GOP NHOM, xem AI_QUEST_GROUPS;
 * AI_QUEST_AVOID_RECENT tranh lap quest 3 ngay gan nhat => moi quest ~4 lan/thang).
 * Cau cua quest gop nhom NEU DU CAC LOP duoc chap nhan ("a cup or a bottle") de user
 * biet chup gi cung qua. Quest `person` = anh co >= 2 nguoi (chup cung ban be).
 *
 * Cau chu VIET DE ANH DE NHAN DIEN (don bay do chinh xac that): goi y chup CAN,
 * vat the O GIUA khung hinh, mot vat the la chinh, du sang, o mo (umbrella) — tranh
 * cau kieu "goc hoc tap" (vat the nho, lan nhieu do). Tieng Anh, bat dau bang
 * "Take a photo/selfie" hoac "Snap a photo/selfie", <= AI_QUEST_CONTENT_MAX ky tu.
 */
export const AI_QUEST_TEMPLATES: Record<AiQuestClass, readonly string[]> = {
  bicycle: [
    'Take a photo of a bicycle or motorbike from the side',
    'Take a photo of your bicycle or motorbike',
    'Take a photo of a parked bike or motorbike, fully visible',
    'Take a photo of a bicycle or motorbike in the parking lot',
    'Take a photo of a bike or motorbike on the street',
    'Take a photo of the bike or motorbike you ride every day',
    'Take a photo of a bicycle or motorbike showing both wheels',
    'Take a photo of the bike or motorbike closest to you',
  ],
  laptop: [
    'Take a photo of an open laptop',
    'Take a photo of the laptop or keyboard you are using today',
    'Take a photo of a laptop on your desk',
    'Take a photo of a keyboard up close',
    'Take a photo of a laptop in the library or a cafe',
    'Take a photo of your keyboard from a top-down view',
    'Take a photo of the entire laptop facing the screen',
    'Take a photo of a laptop or keyboard while working on something',
  ],
  umbrella: [
    'Take a photo of an open umbrella',
    'Take a photo of the umbrella you brought today, opened up',
    'Take a photo of a rain or sun umbrella',
    'Take a photo of your umbrella showing the canopy clearly',
    'Take a photo of an umbrella in the corner of the room',
    'Take a photo of the umbrella from your backpack, opened up',
    'Take a photo of an umbrella in a color you like',
    'Take a photo of an open umbrella outdoors or on the street',
  ],
  chair: [
    'Take a photo of an empty chair, fully visible',
    'Take a photo of the chair you usually sit on from the front',
    'Take a photo of a chair in a cafe',
    'Take a photo of a chair in the classroom',
    'Take a photo of a chair wherever you are right now',
    'Take a photo of the chair next to your study desk',
    'Take a photo of a wooden or plastic chair',
    'Take a photo of the nicest chair you see today',
  ],
  cup: [
    'Take a photo of a cup or a bottle up close on the table',
    'Take a photo of the coffee, tea or water you are drinking',
    'Take a photo of your favorite cup or water bottle',
    'Take a photo of a drink at your favorite cafe',
    'Take a photo of the cup or bottle you use every day',
    'Take a photo of the cup or bottle you are holding',
    'Take a photo of a water bottle standing on the table',
    'Take a photo of a mug, a glass or a bottle',
  ],
  book: [
    'Take a photo of a book up close',
    'Take a photo of the book you are reading',
    'Take a photo of an open book on the table',
    'Take a photo of a notebook or textbook from your bag',
    'Take a photo of a book in the library',
    'Take a photo of a book cover in the center of the frame',
    'Take a photo of a stack of books',
    'Take a photo of your favorite book',
  ],
  person: [
    'Snap a photo with your friend',
    'Take a photo of you and a friend together',
    'Take a selfie with a friend',
    'Snap a group photo with your classmates',
    'Take a photo of two or more friends hanging out',
    'Snap a selfie with someone you met today',
    'Take a photo with your friends at lunch',
    'Snap a photo with your study buddy',
  ],
};

/** Hash xac dinh (djb2) — chon lop/template theo ngay ma khong can luu state. */
export function seededIndex(seed: string, size: number): number {
  let hash = 5381;
  for (let i = 0; i < seed.length; i++) {
    hash = ((hash << 5) + hash + seed.charCodeAt(i)) | 0;
  }
  return Math.abs(hash) % size;
}

/**
 * Chon quest cho 1 ngay: lop seed theo ngay (bo qua `avoid` — vat the cua cac ngay
 * gan nhat), template seed theo ngay + lop. Cung input -> cung output (2 instance
 * server goi dong thoi van chon giong nhau).
 */
export function pickFallbackQuest(
  date: string,
  avoid: readonly string[] = [],
): { targetClass: AiQuestClass; content: string } {
  const candidates = AI_QUEST_CLASSES.filter((c) => !avoid.includes(c));
  const pool = candidates.length > 0 ? candidates : [...AI_QUEST_CLASSES];
  const targetClass = pool[seededIndex(date, pool.length)];
  const templates = AI_QUEST_TEMPLATES[targetClass];
  const content = templates[seededIndex(`${date}:${targetClass}`, templates.length)];
  return { targetClass, content };
}
