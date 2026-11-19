package com.example.core

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

object DateUtil {
    private fun formatter(): SimpleDateFormat =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }

    fun todayIso(): String = formatter().format(Calendar.getInstance().time)

    fun plusDaysIso(days: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, days)
        return formatter().format(cal.time)
    }

    fun plusMonthsIso(months: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.MONTH, months)
        return formatter().format(cal.time)
    }

    fun daysUntil(dateIso: String): Long? {
        if (dateIso.isBlank()) return null
        return try {
            val targetDate = formatter().parse(dateIso.trim()) ?: return null
            val todayCal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val targetCal = Calendar.getInstance().apply {
                time = targetDate
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val diffMs = targetCal.timeInMillis - todayCal.timeInMillis
            TimeUnit.MILLISECONDS.toDays(diffMs)
        } catch (_: Exception) {
            null
        }
    }
}
