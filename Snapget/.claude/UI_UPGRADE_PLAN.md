# UI_UPGRADE_PLAN.md — Nâng cấp UI/UX "Playful Dark" (NHÁP 2026-11-15)

> Mục tiêu: app sinh động, vui, có cảm giác gamified (mỗi chạm có phản hồi, mỗi thành tựu được ăn mừng).
> Quy chuẩn thiết kế: `DESIGN.md` **mục 0**. Danh sách lỗi hiện tại: `UI_REVIEW.md`.
> Trạng thái: **chờ user trả lời câu hỏi** → chốt các mục ⏳ → mới code.

## Lộ trình đề xuất (thứ tự có thể đổi theo câu trả lời)

| Phase | Nội dung | Phụ thuộc |
|---|---|---|
| **P0 — Nền móng** | Motion tokens (0.2), haptic helper (0.4), reduced-motion check, token màu mới (0.7), Type.kt đầy đủ (0.8), `ChunkyButton` (0.3) | Chốt màu / font / kiểu nút |
| **P1 — Sửa lỗi UX** | P0 của `UI_REVIEW.md`: touch target, Loading/Error/Empty + Retry, pull-to-refresh, không mất nội dung, dropdown Camera, icon Photo Library | Chốt câu hỏi Camera |
| **P2 — Phản hồi tương tác** | Press feedback mọi nút, haptic, stagger list, Snackbar thay Toast, footer có ripple/semantics | P0 |
| **P3 — Gamification hiển thị** | Thanh tiến độ streak, vòng quest, thưởng hiện trước, CTA trên quest card, nhắc giữ streak | P0 |
| **P4 — Khoảnh khắc ăn mừng** | Overlay streak +1, mốc streak + khung, quest xong, mua item, kết bạn | P0, P3 (+ server nếu cần cờ "streak vừa tăng") |
| **P5 — Đánh bóng** | Copy mới (0.9), skeleton, onboarding/empty state minh họa, âm thanh (nếu chọn) | — |

## Quyết định cần chốt
_(điền sau khi user trả lời)_

## Việc user cần làm (checklist vận hành)
_(điền sau — vd: chọn/duyệt asset minh họa, thêm font vào `res/font`, test trên máy thật)_
