package com.example.snapget.core.constants

/**
 * Gioi han media (business rule tu PDF — server enforce that, client chi chan UX).
 * Khop MAX_VIDEO_SECONDS trong server/src/common/constants.ts.
 *
 * "Anh GIF" (chot 2026-08-03): GIU nut chup = quay clip ngan <=3s, phat LAP VO HAN
 * va KHONG tieng — coi nhu 1 tam anh biet chuyen dong, phan biet voi anh thuong.
 * File thuc te van la .mp4 (PostType.VIDEO / contentType VIDEO o server) — khong
 * doi ten enum de khong pha contract API.
 */
const val MAX_VIDEO_SECONDS = 3
