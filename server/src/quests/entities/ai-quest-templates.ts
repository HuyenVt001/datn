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
 * cau kieu "goc hoc tap" (vat the nho, lan nhieu do). Tieng Viet co dau, bat dau
 * bang "Chụp", <= AI_QUEST_CONTENT_MAX ky tu.
 */
export const AI_QUEST_TEMPLATES: Record<AiQuestClass, readonly string[]> = {
  cup: [
    'Chụp cận cảnh một chiếc cốc trên bàn',
    'Chụp cốc cà phê hoặc cốc trà bạn đang uống',
    'Chụp chiếc cốc bạn thích nhất, đặt giữa khung hình',
    'Chụp một chiếc cốc ở quán quen',
    'Chụp chiếc cốc bạn dùng mỗi sáng',
    'Chụp một chiếc cốc có tay cầm',
    'Chụp cốc nước đang cầm trên tay',
    'Chụp một chiếc cốc trắng hoặc cốc sứ',
  ],
  bottle: [
    'Chụp cận cảnh một chai nước',
    'Chụp chai nước bạn mang theo hôm nay',
    'Chụp một chai nước đứng trên bàn',
    'Chụp chai nước bạn để cạnh giường',
    'Chụp một chai nước ở căng tin',
    'Chụp chai nước đang cầm trên tay',
    'Chụp một chai đồ uống bất kỳ, thấy rõ cả chai',
    'Chụp chai nước lọc còn nguyên nắp',
  ],
  chair: [
    'Chụp trọn một chiếc ghế trống',
    'Chụp cái ghế bạn hay ngồi, nhìn từ phía trước',
    'Chụp một chiếc ghế trong quán cà phê',
    'Chụp chiếc ghế trong lớp học',
    'Chụp một cái ghế ở nơi bạn đang có mặt',
    'Chụp chiếc ghế cạnh bàn học của bạn',
    'Chụp một chiếc ghế gỗ hoặc ghế nhựa',
    'Chụp cái ghế bạn thấy đẹp nhất hôm nay',
  ],
  'potted plant': [
    'Chụp cận cảnh một chậu cây xanh',
    'Chụp chậu cây trong nhà hoặc ngoài ban công',
    'Chụp một chậu cây ở hành lang',
    'Chụp chậu cây để bàn của bạn',
    'Chụp một chậu cây bạn gặp hôm nay',
    'Chụp chậu cây thấy rõ cả chậu lẫn lá',
    'Chụp một chậu cây ở quán cà phê',
    'Chụp chậu cây bạn thích nhất',
  ],
  laptop: [
    'Chụp chiếc laptop đang mở',
    'Chụp laptop của bạn, nhìn thẳng màn hình',
    'Chụp trọn chiếc laptop trên bàn',
    'Chụp laptop bạn đang dùng hôm nay',
    'Chụp một chiếc laptop ở thư viện hoặc quán',
    'Chụp laptop cùng cốc nước bên cạnh',
    'Chụp chiếc laptop nhìn từ trên xuống',
    'Chụp laptop đang chạy dở việc gì đó',
  ],
  clock: [
    'Chụp cận cảnh một chiếc đồng hồ',
    'Chụp đồng hồ treo tường ở nơi bạn đang ở',
    'Chụp chiếc đồng hồ để bàn của bạn',
    'Chụp đồng hồ đang chỉ giờ hiện tại',
    'Chụp chiếc đồng hồ trong lớp học',
    'Chụp đồng hồ báo thức của bạn',
    'Chụp mặt đồng hồ đeo tay, chụp gần',
    'Chụp một chiếc đồng hồ tròn',
  ],
  umbrella: [
    'Chụp một chiếc ô đang mở',
    'Chụp chiếc ô bạn mang theo hôm nay, bung ra',
    'Chụp một cái ô che mưa hoặc che nắng',
    'Chụp chiếc ô của bạn, thấy rõ mái ô',
    'Chụp một chiếc ô để ở góc nhà',
    'Chụp cái ô bạn hay để trong ba lô, mở ra',
    'Chụp một chiếc ô màu bạn thích',
    'Chụp ô đang mở ngoài sân hoặc ngoài đường',
  ],
  bicycle: [
    'Chụp một chiếc xe đạp nhìn từ bên hông',
    'Chụp xe đạp đang dựng trong sân trường',
    'Chụp trọn một chiếc xe đạp',
    'Chụp chiếc xe đạp đang khoá bên đường',
    'Chụp xe đạp của bạn hoặc của người thân',
    'Chụp một chiếc xe đạp bạn gặp hôm nay',
    'Chụp xe đạp thấy rõ cả hai bánh',
    'Chụp một chiếc xe đạp trong bãi xe',
  ],
  motorcycle: [
    'Chụp một chiếc xe máy nhìn từ bên hông',
    'Chụp xe máy của bạn hoặc của người thân',
    'Chụp trọn một chiếc xe máy đang đỗ',
    'Chụp chiếc xe máy trong bãi xe',
    'Chụp một chiếc xe máy trên phố',
    'Chụp xe máy bạn hay đi mỗi ngày',
    'Chụp một chiếc xe máy thấy rõ cả hai bánh',
    'Chụp chiếc xe máy gần bạn nhất',
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
