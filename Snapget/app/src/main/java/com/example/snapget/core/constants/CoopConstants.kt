package com.example.snapget.core.constants

/**
 * Han loi moi chup chung (giay). Khop COOP_INVITE_TTL_MINUTES = 5 trong
 * server/src/common/constants.ts. Chi dung DU PHONG khi response thieu
 * `remainingSeconds` — han that do server quyet.
 */
const val COOP_INVITE_TTL_SECONDS = 5 * 60L

/** Han phien chup sau khi accept (giay) — khop COOP_SESSION_TTL_MINUTES = 10 cua server. */
const val COOP_SESSION_TTL_SECONDS = 10 * 60L
