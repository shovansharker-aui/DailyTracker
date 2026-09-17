package com.dailytracker.app.miniapps.businesscard

import com.squareup.moshi.JsonClass

/**
 * A brand this business card's owner is a vendor/distributor for.
 * [domain] is a best-effort guess (from Gemini, or entered by hand) at the
 * brand's official website, used only to build a logo lookup URL - it is
 * never shown to the user directly.
 */
@JsonClass(generateAdapter = true)
data class BrandInfo(
    val name: String,
    val domain: String? = null
) {
    /**
     * Clearbit's public logo endpoint - free, keyless, and resolves most
     * well-known brand domains to a logo image. Returns null when there's no
     * domain to look up, so the UI can fall back to a plain text chip.
     */
    val logoUrl: String?
        get() = domain?.trim()?.takeIf { it.isNotEmpty() }?.let { "https://logo.clearbit.com/$it" }
}
