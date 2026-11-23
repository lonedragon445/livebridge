package com.appsfolder.livebridge.bridge

data class BridgePayload(
    val id: Int,
    val sourcePackage: String,
    val title: String,
    val body: String,
    val category: String = "general",
    val progress: Int? = null,
    val maxProgress: Int = 100,
    val indeterminate: Boolean = false,
    val otpCode: String? = null,
    val ongoing: Boolean = true
) {
    companion object {
        private val OTP_REGEX = Regex("""\b(\d{4,8})\b""")

        fun extractOtp(text: String): String? {
            val lower = text.lowercase()
            if (!lower.contains("otp") && !lower.contains("code") && !lower.contains("pin") && !lower.contains("verification")) {
                return null
            }
            return OTP_REGEX.find(text)?.value
        }

        fun detectCategory(pkg: String, title: String, body: String): String {
            val combined = "$pkg $title $body".lowercase()
            return when {
                extractOtp(combined) != null -> "otp"
                listOf("eta", "min away", "arriving", "driver", "rider", "delivery", "order").any { it in combined } -> "delivery_or_ride"
                listOf("turn", "km", "m ", "route", "maps", "navigation").any { it in combined } -> "navigation"
                listOf("°c", "°f", "rain", "forecast", "humidity", "weather").any { it in combined } -> "weather"
                else -> "general"
            }
        }
    }
}
