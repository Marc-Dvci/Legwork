package app.legwork.core

import app.legwork.BuildConfig

/** Build-time defaults. Everything network-specific (mints, program id, cluster) comes from the verifier's /config. */
object Config {
    val VERIFIER_URL: String = BuildConfig.VERIFIER_URL
    const val IDENTITY_URI = "https://legwork.app"
    const val IDENTITY_NAME = "Legwork"
    const val IDENTITY_ICON = "favicon.ico"
    const val SIWS_STATEMENT = "Sign in to Legwork to find paid missions near you."

    /** Reservation length mirrors the program constant. */
    const val RESERVATION_MINUTES = 45

    /** Metres of slack added to the mission radius to absorb GPS accuracy. */
    const val ARRIVAL_SLACK_M = 25.0
}

object Categories {
    data class Cat(val id: Int, val name: String, val emoji: String)
    val all = listOf(
        Cat(0, "Retail", "🛒"),
        Cat(1, "Events", "🎪"),
        Cat(2, "Infrastructure", "🔌"),
        Cat(3, "Community", "🤝"),
        Cat(4, "Crypto", "◎"),
        Cat(5, "Local business", "☕"),
        Cat(6, "Research", "🔬"),
        Cat(7, "Seeker", "📱"),
    )
    fun byId(id: Int) = all.getOrNull(id) ?: all[0]
}

object Tiers {
    data class Tier(val name: String, val minApproved: Int, val minSkr: Long, val perk: String)
    /** SKR amounts are in base units (6 decimals). */
    val all = listOf(
        Tier("Explorer", 0, 0, "Standard missions"),
        Tier("Verified", 3, 0, "Verified badge, priority verification"),
        Tier("Trusted", 10, 100_000_000, "Higher-value missions, two reservations at once"),
        Tier("Expert", 40, 1_000_000_000, "Premium campaigns, dispute reviewer"),
    )
    fun of(approved: Int, skr: Long, seekerVerified: Boolean): Tier {
        var t = all[0]
        for (tier in all) if (approved >= tier.minApproved && skr >= tier.minSkr) t = tier
        if (seekerVerified && t == all[0]) t = all[1]
        return t
    }
    fun next(current: Tier): Tier? = all.getOrNull(all.indexOf(current) + 1)
}
