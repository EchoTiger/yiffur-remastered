package com.ugf.yiffur1.util

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

object Formatters {

    private val readableDateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy · HH:mm", Locale.getDefault())

    fun formatCompactCount(count: Int): String {
        val absCount = abs(count)
        val sign = if (count < 0) "-" else ""
        return when {
            absCount >= 1_000_000 -> String.format(Locale.US, "%s%.1fM", sign, absCount / 1_000_000.0)
            absCount >= 10_000 -> String.format(Locale.US, "%s%.0fk", sign, absCount / 1_000.0)
            absCount >= 1_000 -> String.format(Locale.US, "%s%.1fk", sign, absCount / 1_000.0)
            else -> "$sign$absCount"
        }
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0L) return "Unknown"
        return when {
            bytes >= 1_073_741_824L -> String.format(Locale.US, "%.2f GB", bytes / 1_073_741_824.0)
            bytes >= 1_048_576L -> String.format(Locale.US, "%.2f MB", bytes / 1_048_576.0)
            bytes >= 1_024L -> String.format(Locale.US, "%.1f KB", bytes / 1_024.0)
            else -> "$bytes B"
        }
    }

    fun formatDuration(seconds: Double?): String {
        if (seconds == null || seconds <= 0.0) return ""
        val totalSecs = seconds.toInt()
        val mins = totalSecs / 60
        val secs = totalSecs % 60
        return if (mins > 0) {
            String.format(Locale.US, "%d:%02d", mins, secs)
        } else {
            String.format(Locale.US, "0:%02d", secs)
        }
    }

    fun formatIsoDate(isoString: String?): String {
        if (isoString.isNullOrBlank()) return "Unknown"
        return try {
            val odt = OffsetDateTime.parse(isoString)
            odt.atZoneSameInstant(ZoneId.systemDefault()).format(readableDateFormatter)
        } catch (_: Exception) {
            isoString.take(16).replace('T', ' ')
        }
    }

    fun formatRelativeTime(isoString: String?): String {
        if (isoString.isNullOrBlank()) return ""
        return try {
            val instant = OffsetDateTime.parse(isoString).toInstant()
            val now = Instant.now()
            val mins = ChronoUnit.MINUTES.between(instant, now)
            val hours = ChronoUnit.HOURS.between(instant, now)
            val days = ChronoUnit.DAYS.between(instant, now)
            when {
                mins < 1 -> "Just now"
                mins < 60 -> "${mins}m ago"
                hours < 24 -> "${hours}h ago"
                days == 1L -> "Yesterday"
                days < 30 -> "${days}d ago"
                days < 365 -> "${days / 30}mo ago"
                else -> "${days / 365}y ago"
            }
        } catch (_: Exception) {
            isoString.take(10)
        }
    }
}
