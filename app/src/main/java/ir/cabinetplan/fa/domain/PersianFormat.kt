package ir.cabinetplan.fa.domain

import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

private val PERSIAN_DIGITS = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')

private val JALALI_MONTHS = arrayOf(
    "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
    "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
)

/** Converts ASCII digits in any value to standard Persian digits. */
fun toPersianDigits(value: Any?): String {
    if (value == null) return ""
    return value.toString().map { ch ->
        when (ch) {
            in '0'..'9' -> PERSIAN_DIGITS[ch - '0']
            in '٠'..'٩' -> PERSIAN_DIGITS[ch - '٠']
            else -> ch
        }
    }.joinToString("")
}

fun Number.fa(): String = toPersianDigits(this)

/** Normalizes Persian and Arabic numerals and decimal separators to ASCII for numeric parsing. */
fun toEnglishDigits(value: String): String = value.trim().map { ch ->
    when (ch) {
        in '۰'..'۹' -> '0' + (ch - '۰')
        in '٠'..'٩' -> '0' + (ch - '٠')
        '٫', '/' -> '.'
        else -> ch
    }
}.joinToString("").replace("٬", "").replace(",", "")

/** Parses a centimeter value entered in Persian, Arabic, or English digits within minCm to maxCm. */
fun parseCentimeters(
    text: String,
    minCm: Float = 1f,
    maxCm: Float = ProjectDocument.MAX_COORDINATE_CM,
): Float? {
    val parsed = toEnglishDigits(text).toFloatOrNull() ?: return null
    return parsed.takeIf { it.isFinite() && it in minCm..maxCm }
}

/** Formats centimeters with up to 1 decimal place in Persian digits (e.g. ۱۲۰ سم or ۱۸٫۵ سم). */
fun formatCm(cm: Float, includeUnit: Boolean = true): String {
    if (!cm.isFinite()) return if (includeUnit) "۰ سم" else "۰"
    val roundedTenths = (cm * 10f).roundToInt()
    val formatted = if (abs(roundedTenths % 10) == 0) {
        (roundedTenths / 10).toString()
    } else {
        val sign = if (roundedTenths < 0) "-" else ""
        val absTenths = abs(roundedTenths)
        "$sign${absTenths / 10}٫${absTenths % 10}"
    }
    val persian = toPersianDigits(formatted)
    return if (includeUnit) "$persian سم" else persian
}

/** Formats running meters from centimeters (e.g. ۲٫۴۰ متر طول). */
fun formatMeters(cm: Float, includeSuffix: Boolean = true): String {
    val safeCm = if (cm.isFinite()) cm else 0f
    val hundredths = safeCm.roundToInt()
    val sign = if (hundredths < 0) "-" else ""
    val absVal = abs(hundredths)
    val whole = absVal / 100
    val frac = (absVal % 100).toString().padStart(2, '0')
    val persian = toPersianDigits("$sign$whole٫$frac")
    return if (includeSuffix) "$persian متر طول" else "$persian متر"
}

/** Formats degree angles normalized to 0..359 in Persian digits. */
fun formatAngle(deg: Float): String {
    val normalized = ((deg.roundToInt() % 360) + 360) % 360
    return "${toPersianDigits(normalized)}°"
}

/** Pure-Kotlin Solar Hijri (Jalali) date-time formatter that works identically on JVM & Android. */
fun formatPersianDate(epochMillis: Long): String {
    return try {
        val zdt = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.of("Asia/Tehran"))
        val (jy, jm, jd) = gregorianToJalali(zdt.year, zdt.monthValue, zdt.dayOfMonth)
        val hh = zdt.hour.toString().padStart(2, '0')
        val mm = zdt.minute.toString().padStart(2, '0')
        val monthName = JALALI_MONTHS.getOrElse(jm - 1) { "" }
        toPersianDigits("$jd $monthName $jy - $hh:$mm")
    } catch (_: Exception) {
        toPersianDigits("۱۴۰۵/۰۸/۲۱")
    }
}

internal fun gregorianToJalali(gy: Int, gm: Int, gd: Int): Triple<Int, Int, Int> {
    val gDaysInMonth = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
    val gy2 = if (gm > 2) gy + 1 else gy
    var days = 355666 + (365 * gy) + ((gy2 + 3) / 4) - ((gy2 + 99) / 100) +
        ((gy2 + 399) / 400) + gd + gDaysInMonth[gm - 1]
    var jy = -1595 + (33 * (days / 12053))
    days %= 12053
    jy += 4 * (days / 1461)
    days %= 1461
    if (days > 365) {
        jy += (days - 1) / 365
        days = (days - 1) % 365
    }
    val jm: Int
    val jd: Int
    if (days < 186) {
        jm = 1 + (days / 31)
        jd = 1 + (days % 31)
    } else {
        jm = 7 + ((days - 186) / 30)
        jd = 1 + ((days - 186) % 30)
    }
    return Triple(jy, jm, jd)
}
