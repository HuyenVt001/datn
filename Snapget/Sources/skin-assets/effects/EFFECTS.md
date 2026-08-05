# EFFECTS.md — Phiếu tham số 5 hiệu ứng touch

> Đặc tả kỹ thuật đầy đủ: `.claude/SKIN_PLAN.md` mục **2.5.6**.
> Các giá trị dưới đây là **đề xuất khởi đầu** khớp với ảnh hạt placeholder. Bạn sửa số nào thì tôi chỉnh theo số đó — không phải vẽ lại ảnh.
> **Cả 5 hiệu ứng đều là vật phẩm bậc SR** trong gacha (theo spec: hiệu ứng touch = SR), nên không cần chọn bậc hiếm.

---

## effectId 1 — Snowfall

```
effectId:            1
displayName:         Snowfall
Dạng:                1 (ảnh hạt tĩnh)
File:                effect1_particle.png   (192×192)
Số hạt mỗi lần chạm: 8
Cỡ hạt hiển thị:     22dp
Thời lượng:          1200ms
Hướng bay:           rơi xuống, lắc ngang nhẹ (biên độ ±12dp)
Quãng đường:         90dp
Xoay:                có, 60°/giây
Scale theo thời gian: 1.0 → 0.7
Fade:                mờ dần từ mốc 60% thời lượng
Ăn màu skin:         có (ảnh vẽ trắng)
```

## effectId 2 — Leaf

```
effectId:            2
displayName:         Leaf
Dạng:                1
File:                effect2_particle.png   (192×192)
Số hạt mỗi lần chạm: 6
Cỡ hạt hiển thị:     26dp
Thời lượng:          1400ms
Hướng bay:           rơi xuống, đảo qua lại như lá rụng (biên độ ±20dp)
Quãng đường:         100dp
Xoay:                có, 120°/giây
Scale theo thời gian: 1.0 → 0.8
Fade:                mờ dần từ mốc 65% thời lượng
Ăn màu skin:         có
```

## effectId 3 — Sparkle

```
effectId:            3
displayName:         Sparkle
Dạng:                1
File:                effect3_particle.png   (192×192)
Số hạt mỗi lần chạm: 10
Cỡ hạt hiển thị:     20dp
Thời lượng:          600ms
Hướng bay:           toả đều 360°
Quãng đường:         55dp
Xoay:                có, 180°/giây
Scale theo thời gian: 1.0 → 0.3
Fade:                mờ dần từ mốc 50% thời lượng
Ăn màu skin:         có
```

## effectId 4 — Bubble

```
effectId:            4
displayName:         Bubble
Dạng:                1
File:                effect4_particle.png   (192×192)
Số hạt mỗi lần chạm: 7
Cỡ hạt hiển thị:     28dp
Thời lượng:          1100ms
Hướng bay:           bay lên, lắc ngang nhẹ (biên độ ±10dp)
Quãng đường:         80dp
Xoay:                không
Scale theo thời gian: 0.6 → 1.1  (phồng dần rồi vỡ)
Fade:                mờ dần từ mốc 70% thời lượng
Ăn màu skin:         có
```

## effectId 5 — Ember

```
effectId:            5
displayName:         Ember
Dạng:                1
File:                effect5_particle.png   (192×192)
Số hạt mỗi lần chạm: 10
Cỡ hạt hiển thị:     18dp
Thời lượng:          900ms
Hướng bay:           bắn ra rồi rơi xuống (có trọng lực)
Quãng đường:         70dp
Xoay:                có, 90°/giây
Scale theo thời gian: 1.0 → 0.4
Fade:                mờ dần từ mốc 55% thời lượng
Ăn màu skin:         có
```

---

## Ghi chú chung

- **Tên concept đang là tạm** (Snowfall / Leaf / Sparkle / Bubble / Ember) — đổi thoải mái, chỉ cần giữ `effectId` và tên file.
- `Ăn màu skin = có` nghĩa hạt được tô theo `accent` của skin đang dùng → cùng một hiệu ứng sẽ **xanh băng** ở skin Snow và **vàng be** ở skin Forest. Muốn giữ màu cố định thì đổi thành `không` và gửi ảnh có màu sẵn.
- Trần kỹ thuật: tối đa **8 hiệu ứng sống cùng lúc** trên màn hình; hiệu ứng **tắt khi đang quay GIF** ở màn camera; có toggle tắt hẳn trong Settings.
