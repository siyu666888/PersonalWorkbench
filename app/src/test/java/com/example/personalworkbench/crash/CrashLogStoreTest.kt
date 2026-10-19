package com.example.personalworkbench.crash

import org.junit.Assert.assertEquals
import org.junit.Test

/** 崩溃日志文件名的展示转换 (V1.3.3) */
class CrashLogStoreTest {

    @Test fun `标准文件名转成可读时间`() {
        assertEquals(
            "2026/10/14 12:35:19",
            CrashLogStore.displayName("crash-20261014-123519-559.txt")
        )
    }

    @Test fun `毫秒部分不影响展示`() {
        assertEquals(
            "2026/01/02 03:04:05",
            CrashLogStore.displayName("crash-20260102-030405-000.txt")
        )
    }

    @Test fun `非标准名原样返回`() {
        assertEquals("readme.txt", CrashLogStore.displayName("readme.txt"))
        assertEquals("crash-.txt", CrashLogStore.displayName("crash-.txt"))
        assertEquals("", CrashLogStore.displayName(""))
    }

    @Test fun `保留份数上限为10`() {
        assertEquals(10, CrashLogStore.MAX_KEEP)
    }
}
