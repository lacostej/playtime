package org.example.playtime.config

/**
 * Every value used to talk to Nintendo's servers, in one place.
 * Source: the open-source pynintendoparental / pynintendoauth projects
 * (https://github.com/pantherale0/pynintendoparental), not Nintendo's app.
 */
object NintendoApi {
    // pynintendoauth const.py, KNOWN_NINTENDO_SERVICES["54789befb391a838"]
    const val CLIENT_ID = "54789befb391a838"
    const val REDIRECT_URI = "npf54789befb391a838://auth"
    val SCOPES = listOf(
        "openid",
        "user",
        "moonUser:administration",
        "moonDevice:create",
        "moonOwnedDevice:administration",
        "moonParentalControlSetting",
        "moonParentalControlSetting:update",
        "moonParentalControlSettingState",
        "moonPairingState",
        "moonSmartDevice:administration",
        "moonDailySummary",
        "moonMonthlySummary",
    )

    // pynintendoauth const.py
    const val AUTHORIZE_URL = "https://accounts.nintendo.com/connect/1.0.0/authorize"
    const val SESSION_TOKEN_URL = "https://accounts.nintendo.com/connect/1.0.0/api/session_token"
    const val TOKEN_URL = "https://accounts.nintendo.com/connect/1.0.0/api/token"
    const val GRANT_TYPE = "urn:ietf:params:oauth:grant-type:jwt-bearer-session-token"

    // pynintendoparental const.py BASE_URL
    const val API_BASE = "https://app.lp1.znma.srv.nintendo.net"

    /**
     * Required minimum-version gate (lower values get 400 invalid_headers; on 2026-11-18, 650 was rejected, 660 accepted).
     * Same value as pynintendoparental const.py MOBILE_APP_BUILD (commit 7bf3dee, 2026-09-15).
     * When Nintendo starts rejecting it, check upstream's const.py and bump.
     */
    const val APP_INTERNAL_VERSION = "700"

    /** Our own identity, sent honestly. */
    const val APP_ID = "org.example.playtime"
    const val USER_AGENT = "playtime/0.1 (unofficial parental-controls client)"
}
