import { AI_QUEST_CLASSES, AiQuestClass } from '../../common/constants';

/**
 * Bo cau quest AI — NGUON CHINH (user chot 2026-08-16 bo LLM sinh quest: AI chi lam
 * nhiem vu XAC MINH ANH, noi dung quest lay tu bo mau nay). Van dong thoi la duong
 * FALLBACK neu sau nay bat lai LLM (`source: 'LLM' | 'FALLBACK'` van giu).
 *
 * 9 lop x 8 cau = 72 cau (9 lop ra de — xem AI_QUEST_CLASSES; AI_QUEST_AVOID_RECENT
 * tranh lap vat the 3 ngay gan nhat => moi lop ~3-5 lan/thang, 8 cau du de gan nhu
 * khong lap cau chu trong 1 thang).
 *
 * Cau chu VIET DE ANH DE NHAN DIEN (don bay do chinh xac that): goi y chup CAN,
 * vat the O GIUA khung hinh, mot vat the la chinh, du sang, o mo (umbrella) — tranh
 * cau kieu "goc hoc tap" (vat the nho, lan nhieu do). Tieng Anh, bat dau
 * bang "Take a photo of", <= AI_QUEST_CONTENT_MAX ky tu.
 */
export const AI_QUEST_TEMPLATES: Record<AiQuestClass, readonly string[]> = {
  cup: [
    'Take a photo of a cup up close on the table',
    'Take a photo of the coffee or tea cup you are drinking',
    'Take a photo of your favorite cup in the center of the frame',
    'Take a photo of a cup at your favorite cafe',
    'Take a photo of the cup you use every morning',
    'Take a photo of a cup with a handle',
    'Take a photo of the cup you are holding',
    'Take a photo of a white cup or ceramic mug',
  ],
  bottle: [
    'Take a photo of a water bottle up close',
    'Take a photo of the water bottle you brought today',
    'Take a photo of a water bottle standing on the table',
    'Take a photo of the water bottle by your bed',
    'Take a photo of a water bottle in the cafeteria',
    'Take a photo of the water bottle in your hand',
    'Take a photo of any beverage bottle, clearly visible',
    'Take a photo of a sealed water bottle with its cap on',
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
  'potted plant': [
    'Take a photo of a green potted plant up close',
    'Take a photo of a potted plant indoors or on the balcony',
    'Take a photo of a potted plant in the hallway',
    'Take a photo of your desktop potted plant',
    'Take a photo of a potted plant you come across today',
    'Take a photo of a potted plant showing both the pot and leaves',
    'Take a photo of a potted plant in a cafe',
    'Take a photo of your favorite potted plant',
  ],
  laptop: [
    'Take a photo of an open laptop',
    'Take a photo of your laptop facing the screen directly',
    'Take a photo of the entire laptop on the desk',
    'Take a photo of the laptop you are using today',
    'Take a photo of a laptop in the library or a cafe',
    'Take a photo of a laptop with a cup next to it',
    'Take a photo of a laptop from a top-down view',
    'Take a photo of a laptop while working on something',
  ],
  clock: [
    'Take a photo of a clock up close',
    'Take a photo of the wall clock where you are staying',
    'Take a photo of your desk clock',
    'Take a photo of a clock showing the current time',
    'Take a photo of the clock in your classroom',
    'Take a photo of your alarm clock',
    'Take a photo of a watch face up close',
    'Take a photo of a round clock',
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
  bicycle: [
    'Take a photo of a bicycle from the side',
    'Take a photo of a bicycle parked in the schoolyard',
    'Take a photo of a complete bicycle',
    'Take a photo of a bicycle locked by the street',
    "Take a photo of your bicycle or a relative's bicycle",
    'Take a photo of a bicycle you see today',
    'Take a photo of a bicycle showing both wheels clearly',
    'Take a photo of a bicycle in the parking lot',
  ],
  motorcycle: [
    'Take a photo of a motorcycle from the side',
    "Take a photo of your motorcycle or a relative's motorcycle",
    'Take a photo of a parked motorcycle, fully visible',
    'Take a photo of a motorcycle in the parking lot',
    'Take a photo of a motorcycle on the street',
    'Take a photo of the motorcycle you ride every day',
    'Take a photo of a motorcycle showing both wheels clearly',
    'Take a photo of the motorcycle closest to you',
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
