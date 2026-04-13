package com.tuozhu.consumablestatistics.ui

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timestampFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")

fun formatTimestamp(timestamp: Long): String {
    return timestampFormatter.format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))
}

fun formatRelativeTime(timestamp: Long?): String {
    if (timestamp == null) return "尚未同步"
    val duration = Duration.between(Instant.ofEpochMilli(timestamp), Instant.now())
    val minutes = duration.toMinutes()
    return when {
        minutes < 1 -> "刚刚"
        minutes < 60 -> "${minutes} 分钟前"
        minutes < 24 * 60 -> "${duration.toHours()} 小时前"
        else -> "${duration.toDays()} 天前"
    }
}

