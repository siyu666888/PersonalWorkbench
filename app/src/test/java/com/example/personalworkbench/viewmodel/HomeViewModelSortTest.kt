package com.example.personalworkbench.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 最新动态按时间倒序排序（V1.4.0）。抽成纯函数后可直接单测，无需数据库。
 */
class HomeViewModelSortTest {
    private fun act(id: String, ts: Long) = RecentActivity(
        id = id, type = ActivityType.TASK, refId = 0L, title = id, subtitle = "", timestamp = ts
    )

    @Test
    fun sortRecentActivities_ordersByTimestampDescending() {
        val items = listOf(act("old", 1000L), act("new", 3000L), act("mid", 2000L))
        val sorted = HomeViewModel.sortRecentActivities(items)
        assertEquals(listOf("new", "mid", "old"), sorted.map { it.id })
    }

    @Test
    fun sortRecentActivities_keepsStableOrderForSameTimestamp() {
        val items = listOf(act("a", 2000L), act("b", 2000L), act("c", 1000L))
        val sorted = HomeViewModel.sortRecentActivities(items)
        assertEquals(listOf("a", "b", "c"), sorted.map { it.id })
    }

    @Test
    fun sortRecentActivities_emptyReturnsEmpty() {
        assertEquals(emptyList<RecentActivity>(), HomeViewModel.sortRecentActivities(emptyList()))
    }
}
