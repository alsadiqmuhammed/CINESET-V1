package com.madar.launcher.command

private val DIACRITICS = Regex("[\\u064B-\\u065F\\u0670\\u0640]")
private val SPACES = Regex("\\s+")

/**
 * Folds Arabic spelling variants (hamza forms, taa marbuta, Iraqi/Persian letters, Arabic-Indic digits)
 * so "تليگرام", "تلغرام" and "تليكرام" all compare equal-ish.
 */
fun normalize(input: String): String {
    val sb = StringBuilder(input.length)
    for (ch in input.lowercase().replace(DIACRITICS, "")) {
        sb.append(
            when (ch) {
                'أ', 'إ', 'آ', 'ٱ' -> 'ا'
                'ة' -> 'ه'
                'ى', 'ئ' -> 'ي'
                'ؤ' -> 'و'
                'گ', 'ک' -> 'ك'
                'غ' -> 'ك'
                'چ' -> 'ج'
                'پ' -> 'ب'
                'ڤ' -> 'ف'
                in '٠'..'٩' -> '0' + (ch - '٠')
                in '۰'..'۹' -> '0' + (ch - '۰')
                '،' -> ','
                '٫' -> '.'
                else -> ch
            },
        )
    }
    return sb.toString().replace(SPACES, " ").trim()
}
