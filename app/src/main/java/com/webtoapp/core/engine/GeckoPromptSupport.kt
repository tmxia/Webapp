package com.webtoapp.core.engine









internal object GeckoPromptSupport {

    




    data class PromptChoice(
        val label: String,
        val selected: Boolean = false,
        val disabled: Boolean = false,
        val separator: Boolean = false,
        val children: List<PromptChoice>? = null,
        val source: Any? = null
    )

    



    data class ChoiceRow(
        val choice: PromptChoice?,
        val label: String,
        val depth: Int,
        val enabled: Boolean,
        val checked: Boolean
    )

    




    fun flattenChoices(
        choices: List<PromptChoice>,
        depth: Int = 0,
        out: MutableList<ChoiceRow> = mutableListOf()
    ): List<ChoiceRow> {
        for (c in choices) {
            if (!c.children.isNullOrEmpty()) {
                out += ChoiceRow(c, c.label, depth, enabled = false, checked = false)
                flattenChoices(c.children, depth + 1, out)
            } else {
                val enabled = !c.disabled && !c.separator
                out += ChoiceRow(c, c.label, depth, enabled, checked = c.selected)
            }
        }
        return out
    }

    

    private val DATE_RE = Regex("""^(\d{4})-(\d{1,2})-(\d{1,2})""")
    private val TIME_RE = Regex("""^(\d{1,2}):(\d{2})""")
    private val MONTH_RE = Regex("""^(\d{4})-(\d{1,2})$""")
    private val WEEK_RE = Regex("""^(\d{4})-W(\d{1,2})$""")
    private val DATETIME_LOCAL_RE = Regex("""^(\d{4})-(\d{1,2})-(\d{1,2})[T ](\d{1,2}):(\d{2})""")

    
    fun parseDateValue(s: String?): Triple<Int, Int, Int>? =
        s?.let { DATE_RE.find(it) }?.destructured?.let { (y, m, d) ->
            Triple(y.toInt(), m.toInt(), d.toInt())
        }

    
    fun parseTimeValue(s: String?): Pair<Int, Int>? =
        s?.let { TIME_RE.find(it) }?.destructured?.let { (h, m) ->
            Pair(h.toInt(), m.toInt())
        }

    data class DateTimeParts(val y: Int, val mo: Int, val d: Int, val h: Int, val mi: Int)

    
    fun parseDateTimeLocal(s: String?): DateTimeParts? =
        s?.let { DATETIME_LOCAL_RE.find(it) }?.destructured?.let { (y, mo, d, h, mi) ->
            DateTimeParts(y.toInt(), mo.toInt(), d.toInt(), h.toInt(), mi.toInt())
        }

    
    fun parseMonthValue(s: String?): Pair<Int, Int>? =
        s?.let { MONTH_RE.find(it) }?.destructured?.let { (y, m) ->
            Pair(y.toInt(), m.toInt())
        }

    
    fun parseWeekValue(s: String?): Pair<Int, Int>? =
        s?.let { WEEK_RE.find(it) }?.destructured?.let { (y, w) ->
            Pair(y.toInt(), w.toInt())
        }

    fun formatDate(y: Int, m: Int, d: Int) = "%04d-%02d-%02d".format(y, m, d)
    fun formatTime(h: Int, m: Int) = "%02d:%02d".format(h, m)
    fun formatDateTimeLocal(y: Int, mo: Int, d: Int, h: Int, mi: Int) =
        "%04d-%02d-%02dT%02d:%02d".format(y, mo, d, h, mi)
    fun formatMonth(y: Int, m: Int) = "%04d-%02d".format(y, m)
    fun formatWeek(y: Int, w: Int) = "%04d-W%02d".format(y, w)

    private fun dayOrdinal(y: Int, m: Int, d: Int) = y * 10_000 + m * 100 + d
    private fun timeOrdinal(h: Int, m: Int) = h * 100 + m

    
    fun clampDate(y: Int, m: Int, d: Int, min: String?, max: String?): Triple<Int, Int, Int> {
        var v = dayOrdinal(y, m, d)
        parseDateValue(min)?.let { v = v.coerceAtLeast(dayOrdinal(it.first, it.second, it.third)) }
        parseDateValue(max)?.let { v = v.coerceAtMost(dayOrdinal(it.first, it.second, it.third)) }
        return Triple(v / 10_000, (v / 100) % 100, v % 100)
    }

    
    fun clampTime(h: Int, m: Int, min: String?, max: String?): Pair<Int, Int> {
        var v = timeOrdinal(h, m)
        parseTimeValue(min)?.let { v = v.coerceAtLeast(timeOrdinal(it.first, it.second)) }
        parseTimeValue(max)?.let { v = v.coerceAtMost(timeOrdinal(it.first, it.second)) }
        return Pair(v / 100, v % 100)
    }

    

    




    fun normalizeHexColor(raw: String?): String? {
        var s = raw?.trim() ?: return null
        if (s.startsWith("#")) s = s.substring(1)
        s = when (s.length) {
            3 -> s.flatMap { listOf(it, it) }.joinToString("")
            4 -> s.flatMap { listOf(it, it) }.joinToString("").substring(0, 6)
            6 -> s
            8 -> s.substring(2)
            else -> return null
        }
        if (!s.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
        return "#${s.lowercase()}"
    }
}
