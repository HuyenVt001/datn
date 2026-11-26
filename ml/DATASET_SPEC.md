# Đặc tả tập dữ liệu Việt Nam — VN-train & VN-test (model y1)

> **Dành cho:** thành viên nhóm đi chụp ảnh, tải ảnh và gán nhãn.
> **Vì sao cần:** COCO/Kaggle gần như toàn ảnh phương Tây → model không biết **ghế nhựa vỉa hè, ly trà sữa, xe số Wave, dù che quán, vở học sinh**… Tập VN dạy model đúng những thứ đó (VN-train) và là **thước đo duy nhất** để chọn lớp nào được ra đề quest (VN-test).
> Kế hoạch tổng: [`Snapget/.claude/QUEST_AI_PLAN.md`](../Snapget/.claude/QUEST_AI_PLAN.md). Script kiểm tra tự động: `scripts/11_merge_external.py` (mọi quy tắc bên dưới đều được script soát — sai là báo lỗi/cảnh báo).

---

## A. Số lượng

| Tập | Mỗi lớp (× 12 lớp) | Negative | Tổng ≈ | Nguồn |
|---|---|---|---|---|
| **VN-train** | **≥ 150 ảnh** (tối thiểu cứng 100) — trong đó **≥ 50 tự chụp**, phần còn lại ảnh Google/Kaggle bối cảnh VN | **300** (trong đó ≥ 150 hard negative) | 2.100 | tự chụp + Google + Kaggle |
| **VN-test** | **50 ảnh** (tối thiểu cứng 20) | **100** (trong đó ≥ 50 hard negative) | 700 | **100% tự chụp bằng điện thoại** — tuyệt đối không dùng ảnh Google/Kaggle |

- "Mỗi lớp N ảnh" = N ảnh **có chứa** lớp đó (1 ảnh có cả cốc lẫn chai tính cho cả 2 lớp).
- Script tự chia VN-test thành **real-calib 40%** (chọn ngưỡng) và **real-test 60%** (báo cáo + chọn lớp quest) — nhóm KHÔNG tự chia.
- Khi train, ảnh VN-train được **lặp ×3** (để không bị ~20k ảnh COCO lấn át) — script tự làm.

### ⚠️ Chống rò rỉ train ↔ test (quan trọng nhất)
Một **vật thể vật lý** hoặc một **địa điểm** chỉ được nằm ở **một** tập. Nếu chụp cùng một cái cốc cho cả train lẫn test, điểm test sẽ đẹp giả tạo.
- Cách đơn giản nhất: **chia người** — vd thành viên A, B chụp VN-test; C, D chụp VN-train. Hoặc chụp test ở **địa điểm khác** hẳn train (nhà khác, quán khác).
- **Không** chụp liên tiếp (burst) rồi chia ảnh cho 2 tập.
- Script còn soát thêm ảnh gần trùng (perceptual hash) giữa train và test, trùng thì loại khỏi train.

---

## B. Định nghĩa lớp cho bối cảnh Việt Nam

Đây là **luật gán nhãn**. Cái gì ở cột ✅ thì vẽ box lớp đó; cái gì ở cột ❌ thì **không** vẽ (và nên chụp làm hard negative).

| Lớp | ✅ TÍNH là lớp này (phải có trong tập) | ❌ KHÔNG tính (hard negative) |
|---|---|---|
| **cup** | cốc sứ, ly thuỷ tinh, **ly nhựa trà sữa/cà phê có nắp + ống hút**, cốc giấy, ly cà phê phin, cốc giữ nhiệt có quai | bát/chén, lon nước, bình giữ nhiệt dáng chai |
| **bottle** | chai Lavie/Aquafina, chai nước ngọt, chai thuỷ tinh, bình nước thể thao, bình giữ nhiệt dáng trụ, chai nước mắm/dầu ăn | lon, lọ hoa, cốc cao |
| **book** | sách, **vở học sinh**, giáo trình, sách photo đóng gáy (mở hoặc gập) | giấy rời, tablet, hộp giấy, menu quán |
| **chair** | **ghế nhựa đỏ/xanh vỉa hè, ghế đẩu nhựa thấp**, ghế gỗ quán cà phê, ghế văn phòng, ghế giảng đường liền bàn (box phần ghế) | sofa, ghế băng công viên, yên xe |
| **potted plant** | cây cảnh / sen đá / kim tiền / lan **trong chậu**, chậu cây ban công, chậu trước quán (cả cây giả trong chậu) | cây trồng dưới đất, hoa cắm lọ, bó hoa |
| **laptop** | laptop mở (≥ 70% số ảnh) và gập | tablet, màn hình rời, TV |
| **keyboard** | bàn phím rời, **cả bàn phím trên laptop** (box vùng phím — ảnh laptop mở phải gán **cả 2 lớp**) | remote, đàn organ, bàn phím điện thoại |
| **backpack** | ba lô học sinh / ba lô laptop **đặt trên ghế, bàn, sàn, xe máy** (≥ 60%) và đang đeo | túi xách, túi tote, vali, túi giao hàng |
| **clock** | đồng hồ treo tường, để bàn, báo thức, đồng hồ điện tử LED treo tường/để bàn | **đồng hồ đeo tay**, màn hình điện thoại/máy tính hiện giờ |
| **umbrella** | ô gấp, ô cán dài (đang mở ≥ 70%), **dù che quán / xe hàng rong** | nón lá, áo mưa, mái hiên, bạt |
| **bicycle** | xe đạp mini, xe đạp thể thao, **xe đạp điện có bàn đạp**, xe đạp công cộng | xe máy điện không bàn đạp |
| **motorcycle** | **xe số (Wave, Dream, Sirius), xe ga (Vision, Lead, SH), xe máy điện**, xe đang chở người, bãi gửi xe | xe đạp điện có bàn đạp, xe ba gác |

**Hard negative** = ảnh **chỉ** chứa vật ở cột ❌ (bát, lon, đồng hồ đeo tay, nón lá, sofa, tablet, túi xách…) **cộng** ảnh **chụp màn hình / ảnh in / poster** có vật thể của 12 lớp (để model học "ảnh của cái cốc ≠ cái cốc" — chống gian lận). File nhãn **rỗng**.

**Negative thường** = ảnh đời thường không có lớp nào (bầu trời, tường, món ăn, sân trường vắng…). File nhãn rỗng.

---

## C. Độ đa dạng bắt buộc (mỗi lớp, cả train lẫn test)

| Tiêu chí | Yêu cầu |
|---|---|
| Khoảng cách | cận < 1m **~40%** · trung 1–3m **~40%** · xa > 3m **~20%** |
| Vị trí trong khung | ≥ **30%** ảnh vật thể **lệch tâm / sát mép** (đừng lúc nào cũng để giữa) |
| Kích thước | ≥ 20% ảnh vật thể **nhỏ** (chiếm < 10% khung hình) — nhưng người nhìn vẫn nhận ra |
| Ánh sáng | ban ngày · **đèn vàng trong nhà** · đèn huỳnh quang · **tối / ban đêm ≥ 15%** |
| Bối cảnh | ≥ **4** trong: nhà / phòng trọ · giảng đường / thư viện · quán cà phê / trà sữa · vỉa hè / chợ · bãi xe / sân trường · văn phòng |
| Che khuất | ≥ 20% bị che một phần / đang **cầm trên tay** |
| Nhiều vật thể | ≥ 15% ảnh có ≥ 2 lớp. **Bắt buộc có** các cặp dễ nhầm **cùng khung**: laptop + keyboard · bicycle + motorcycle · cup + bottle |
| Đa dạng cá thể | ≤ **3 ảnh / 1 vật thể vật lý** (chụp 10 cái cốc khác nhau tốt hơn 10 ảnh 1 cái cốc); ≥ **3 mẫu điện thoại** khác nhau |
| Hướng ảnh | ảnh tự chụp ≥ **70% ảnh dọc** (giống app). VN-test ưu tiên chụp **bằng chính app Snapget** |

### Checklist khi đi chụp (in ra / để trên điện thoại)
- [ ] Mỗi vật thể chụp tối đa 3 ảnh, mỗi ảnh đổi góc/khoảng cách/ánh sáng
- [ ] Có ảnh xa, ảnh vật nhỏ, ảnh vật ở góc khung
- [ ] Có ảnh buổi tối / đèn vàng
- [ ] Có ảnh cầm trên tay, bị che một phần
- [ ] Có ảnh 2 vật dễ nhầm đứng cạnh nhau (xe đạp cạnh xe máy, laptop có bàn phím, cốc cạnh chai)
- [ ] Chụp hard negative: bát, lon, đồng hồ đeo tay, nón lá, sofa, tablet, túi xách, **màn hình đang hiện ảnh cốc/xe**
- [ ] Ghi vào `metadata.csv` ngay trong ngày (bối cảnh, ánh sáng, khoảng cách, máy)

---

## D. Quy tắc gán nhãn (box, định dạng YOLO)

1. Vẽ box cho **MỌI** vật thể thuộc 12 lớp có trong ảnh — **không chỉ** lớp của ảnh đó. Ảnh "cup" có chai bên cạnh → vẽ cả bottle. **Thiếu box = dạy model sai** (nó học rằng cái chai đó "không phải chai").
2. Box **ôm sát phần nhìn thấy được**. Vật bị che / cắt mép vẫn vẽ nếu thấy ≥ ~20% và người nhìn nhận ra.
3. Bãi xe / dãy ghế đông: vẽ từng cái phân biệt được, tối đa ~20 box/ảnh; phần quá xa, chồng chéo thì bỏ.
4. Vật thể **trong màn hình, ảnh in, gương, poster** → **KHÔNG** vẽ.
5. Theo đúng bảng định nghĩa lớp ở mục B (đặc biệt: xe đạp điện có bàn đạp = bicycle; đồng hồ đeo tay = không vẽ; laptop mở = vẽ cả laptop lẫn keyboard).
6. **Duyệt 100% ảnh** (kể cả box do công cụ tự gợi ý). Mỗi ảnh **VN-test** phải được **người thứ 2** kiểm lại.

### Quy trình gán nhãn (Roboflow — miễn phí, khuyến nghị)
1. Gom ảnh chưa nhãn vào 1 thư mục (vd `self_test/…`).
2. Roboflow → *Create Project* (Object Detection) → tạo sẵn đủ 12 lớp đúng tên → *Upload* ảnh.
3. Vẽ box từng ảnh (có thể bật gợi ý box tự động của Roboflow cho nhanh, nhưng vẫn phải soát từng box).
4. Lỗi hay gặp khi soát: box lệch, **box sai lớp** (xe đạp điện thành motorcycle, nón lá thành umbrella, đồng hồ đeo tay thành clock), **box bị sót** (ly trà sữa, ghế nhựa thấp hay bị sót).
5. *Generate version* → **bỏ hết Preprocessing** (đặc biệt *Resize* — Roboflow mặc định thu về 640px, làm mất chi tiết) và **không bật Augmentation** (script train tự augment) · *Train/Test split*: dồn **100% vào train** (việc chia train/test do nhóm quyết theo mục A, script chia calib/test). *Export* → định dạng **YOLOv8** (hoặc YOLOv5 PyTorch) → tải zip → chép `images/` + `labels/` vào `vn/train/` hoặc `vn/test/` (mục E), giữ `data.yaml`.
   - Label Studio / CVAT cũng được, miễn export ra YOLO **kèm file tên lớp** (`classes.txt` hoặc `data.yaml`) đúng thứ tự 12 lớp.

> Thứ tự 12 lớp (class id 0 → 11): `cup, bottle, book, chair, potted plant, laptop, keyboard, backpack, clock, umbrella, bicycle, motorcycle`. Nếu Roboflow đổi thứ tự thì không sao — script đọc `data.yaml` và tự map theo **tên**.

---

## E. Yêu cầu file & cấu trúc thư mục

**Ảnh**: JPEG/PNG, cạnh dài **1024–4032 px**. Không dùng: ảnh chụp màn hình (trừ hard negative chống gian lận), ảnh ghép (collage), ảnh có watermark lớn / chữ chèn, ảnh đã qua filter mạnh. Ảnh Google ưu tiên bật bộ lọc *Công cụ → Quyền sử dụng → Giấy phép Creative Commons*.

**Tên file**: `<nguồn>_<lớp>_<4 số>.jpg`
- `nguồn` ∈ `self` (tự chụp) · `gg` (ảnh Internet) · `kg` (Kaggle)
- `lớp` = tên lớp, dấu cách → `_` (`potted_plant`); negative thường = `neg`; hard negative = `hardneg`
- Ví dụ: `self_cup_0001.jpg`, `gg_potted_plant_0042.jpg`, `self_hardneg_0007.jpg`
- Ảnh có nhiều lớp: đặt theo lớp **chính** (lớp bạn định chụp) — các lớp khác thể hiện qua box.

**Cấu trúc** (zip rồi kéo lên Drive `snapget-ml/data/vn.zip`):
```
vn/
  data.yaml  hoặc  classes.txt        ← file tên lớp (Roboflow export có sẵn)
  train/images/*.jpg   train/labels/*.txt
  test/images/*.jpg    test/labels/*.txt     ← negative: file .txt RỖNG (hoặc không có file)
  metadata.csv
  data_sources.md                     ← link + giấy phép từng dataset Kaggle / nguồn ảnh Google
```

**`metadata.csv`** (mỗi ảnh 1 dòng, UTF-8; mở bằng Google Sheets rồi *Download → CSV*):

| cột | giá trị hợp lệ |
|---|---|
| `filename` | tên file ảnh, vd `self_cup_0001.jpg` |
| `split` | `train` · `test` |
| `source` | `self` · `gg` · `kg` |
| `url` | link gốc (ảnh Google/Kaggle), để trống nếu tự chụp |
| `scene` | `home` · `classroom` · `cafe` · `street` · `parking` · `office` · `other` |
| `lighting` | `day` · `warm` (đèn vàng) · `fluorescent` · `dark` (tối/ban đêm) |
| `distance` | `near` (< 1m) · `mid` (1–3m) · `far` (> 3m) |
| `phone` | mẫu máy, vd `iPhone 12`, `Galaxy A54` |

`metadata.csv` dùng để kiểm tra độ đa dạng (mục C) **và** để báo cáo độ chính xác theo điều kiện (ảnh tối vs sáng, gần vs xa) — một mục phân tích đắt giá trong chương AI.

---

## F. Script tự kiểm tra gì (`11_merge_external.py`)

| Lỗi → **DỪNG** (phải sửa) | Cảnh báo → in ra + ghi `merge_report.json` |
|---|---|
| lớp nào dưới mức tối thiểu cứng (train 100 / test 20) | lớp dưới mức khuyến nghị (train 150 / test 50), thiếu negative / hard negative |
| class id ngoài 12 lớp, box toạ độ ngoài [0,1] / w,h ≤ 0 | tên file sai quy ước |
| ảnh không phải neg mà thiếu file nhãn | ảnh tên lớp X nhưng không có box X; ảnh neg mà có box; ảnh không box mà tên không phải neg |
| ảnh `gg_`/`kg_` nằm trong **test** | ảnh dọc < 70%, cạnh dài < 1024px |
| ảnh không mở được | thiếu cặp dễ nhầm cùng khung; tỉ lệ tối/khoảng cách/bối cảnh/máy dưới mục C (từ `metadata.csv`) |
| | ảnh train gần trùng với ảnh test → tự loại khỏi train |

---

## G0. Tập train bổ sung dữ liệu bản địa (đã có sẵn)

`snapget-ml/data/snapget_vn_supplement.zip` trên Drive — dataset YOLO 12 lớp **chỉ dùng cho train** (ảnh bối cảnh Việt Nam, gán nhãn bán tự động; số lượng, nguồn, giấy phép: `README.md` + `stats.json` trong zip). Notebook train tự giải nén và đưa vào script 11 bằng `--vn`. Nhóm chỉ cần bổ sung phần **tự chụp** (≥ 50 ảnh/lớp cho VN-train) và **toàn bộ VN-test** theo các mục A–F.

## G. Ảnh Google & dataset Kaggle — lưu ý riêng

**Ảnh Google** (chỉ cho VN-train):
- Từ khoá tiếng Việt cho ra ảnh đúng bối cảnh: "ghế nhựa vỉa hè", "ly trà sữa trên bàn", "bãi gửi xe máy", "xe đạp điện", "dù che quán cà phê", "vở học sinh", "chậu cây ban công"…
- Ưu tiên ảnh **đời thường** chụp bằng điện thoại; tránh ảnh sản phẩm nền trắng (studio) — model học sai phân bố.
- Ghi link vào cột `url` của `metadata.csv`. Ảnh tải về dùng cho mục đích học thuật (đồ án), **không** đưa lên repo public.

**Dataset Kaggle**:
- Nên chọn dataset **có box** (YOLO / COCO JSON / Pascal VOC). Dataset chỉ có nhãn cấp ảnh (thư mục theo lớp) → phải qua bước pre-label + sửa tay như ảnh Google.
- Tên lớp lạ (`mug`, `motorbike`, `houseplant`…) → khai báo trong [`ml/yolo_class_map.yaml`](yolo_class_map.yaml) (đã có sẵn nhiều tên phổ biến). Lớp không khai báo sẽ bị bỏ và script cảnh báo.
- Cẩn thận dataset chỉ gán **một số** lớp: ảnh "cốc" của dataset đó có thể có chai **không được gán** → nhãn thiếu. Ưu tiên dataset ảnh tập trung vào đúng vật thể.
- Ghi tên dataset + link + giấy phép vào `data_sources.md` (báo cáo cần trích nguồn).
- Dataset Kaggle **không** dùng làm test.

---

## H. Câu hỏi thường gặp

- **Ảnh xe máy có người ngồi?** → vẫn vẽ box motorcycle (người không phải lớp của mình, bỏ qua).
- **Ly nhựa trà sữa không nắp?** → vẫn là cup.
- **Bình giữ nhiệt (tumbler) có quai, dáng cốc?** → cup. Dáng trụ cao như chai, nắp vặn → bottle.
- **Laptop gập, không thấy bàn phím?** → chỉ vẽ laptop.
- **Ô gập đang cụp để trong giỏ xe?** → umbrella nếu nhận ra được; nhưng đa số ảnh ô nên là ô **đang mở**.
- **Đồng hồ trên lò vi sóng / điện thoại?** → không vẽ clock (không phải đồng hồ độc lập).
- **Chụp chung 1 cái cốc ở 2 quán khác nhau?** → vẫn là 1 vật thể vật lý → chỉ được ở 1 tập.
