package com.example.snapget.core.util

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.example.snapget.core.constants.Month
import com.example.snapget.core.model.Post
import com.example.snapget.feature.profile.DayPostGroup
import com.example.snapget.feature.profile.MonthPosts
import java.time.LocalDate

fun calculateDaysOfMonthInYear(
    month: Month,
    year: Int,
): Int = when (month) {
    Month.JANUARY, Month.MARCH, Month.MAY, Month.JULY, Month.AUGUST, Month.OCTOBER, Month.DECEMBER -> 31
    Month.APRIL, Month.JUNE, Month.SEPTEMBER, Month.NOVEMBER -> 30
    Month.FEBRUARY -> if ((year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)) 29 else 28
}

// Helper function to group posts by month and year
// @RequiresApi(Build.VERSION_CODES.O)
// fun groupPostsByMonthYear(posts: List<Post>): List<MonthPosts> {
//    val currentDate = LocalDate.now()
//
//    // Create a more realistic distribution that demonstrates badge functionality
//    // Some posts will be on the same day to show the badge feature
//    return listOf(
//        MonthPosts(
//            month = Month.AUGUST,
//            year = 2025,
//            posts = posts // All posts in August to show grouping
//        ),
//        MonthPosts(
//            month = Month.JULY,
//            year = 2025,
//            posts = posts.take(2) // Some posts in July
//        )
//    ).filter { it.posts.isNotEmpty() }
// }

@RequiresApi(Build.VERSION_CODES.O)
fun groupPostsByMonthYear(posts: List<Post>): List<MonthPosts> {
    // Group posts by month and year
    val groupedByMonthYear = posts.groupBy { post ->
        try {
            // Parse the ISO date from post createdAt field
            val dateTime = java.time.OffsetDateTime.parse(post.createdAt)
            // Extract month and year as key
            Pair(Month.values()[dateTime.monthValue - 1], dateTime.year)
        } catch (e: Exception) {
            // Fallback in case of parsing error
            Log.e("DateUtils", "Error parsing date: ${post.createdAt}", e)
            Pair(Month.JANUARY, 2025) // Default fallback
        }
    }

    // Convert the grouped map to a list of MonthPosts
    return groupedByMonthYear.map { (monthYear, postsInMonth) ->
        val (month, year) = monthYear
        MonthPosts(
            month = month,
            year = year,
            posts = postsInMonth,
        )
    }.sortedByDescending { monthPost ->
        // Sort by year and month (newest first)
        monthPost.year * 100 + monthPost.month.ordinal
    }
}

@RequiresApi(Build.VERSION_CODES.O)
fun groupPostsByDay(posts: List<Post>, daysInMonth: Int): Map<Int, DayPostGroup> {
    // Group posts by day of month
    val postsByDay = posts.groupBy { post ->
        try {
            // Parse the date from post timestamp
            val dateTime = java.time.OffsetDateTime.parse(post.createdAt)
            dateTime.dayOfMonth
        } catch (e: Exception) {
            Log.e("DateUtils", "Error parsing day from date: ${post.createdAt}", e)
            1 // Default fallback day
        }
    }

    // Convert to map of DayPostGroup objects
    return postsByDay.mapValues { (dayNumber, postsOnDay) ->
        DayPostGroup(
            dayNumber = dayNumber,
            posts = postsOnDay,
        )
    }
}

enum class StartDateStyle {
    MONDAY,
    SUNDAY,
}

/**
 * Thoi gian tuong doi NGAN kieu Locket cho hang tac gia duoi post:
 * "Just now" / "5m" / "3h" / "1d". Nhan ISO string tu server (postTime).
 */
@RequiresApi(Build.VERSION_CODES.O)
fun relativeTimeShort(iso: String): String = try {
    val instant = java.time.Instant.parse(iso)
    val mins = java.time.Duration.between(instant, java.time.Instant.now()).toMinutes()
    when {
        mins < 1 -> "Just now"
        mins < 60 -> "${mins}m"
        mins < 24 * 60 -> "${mins / 60}h"
        else -> "${mins / (24 * 60)}d"
    }
} catch (e: Exception) {
    Log.e("DateUtils", "Error parsing relative time: $iso", e)
    ""
}

@RequiresApi(Build.VERSION_CODES.O)
fun getStartDayOffset(style: StartDateStyle = StartDateStyle.MONDAY, month: Month, year: Int): Int {
    val firstDay = LocalDate.of(year, month.ordinal + 1, 1)
    if (style == StartDateStyle.SUNDAY) {
        // If style is Sunday, we need to adjust the first day of the week
        return firstDay.dayOfWeek.value % 7
    } else {
        return (firstDay.dayOfWeek.value - 1) % 7
    }
}
