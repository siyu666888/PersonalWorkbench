package com.example.personalworkbench.crash

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * 崩溃日志捕获 (V1.3.3)
 *
 * 在 Application 启动时安装一个 [Thread.UncaughtExceptionHandler]：
 *  1. 先把堆栈写进 `filesDir/crash/`（见 [CrashLogStore]）；
 *  2. 再交回**原来**的默认处理器，保证系统原有的崩溃行为（弹「已停止」/ 进程退出）不变。
 *
 * 也就是说：崩溃体验与系统默认完全一致，只是多留下一份可事后诊断的本地报告。
 */
object CrashReporter {

    /**
     * 是否记录崩溃日志。默认 true——DataStore 的首个值到达前就按「开」处理：
     * 崩溃随时可能发生，宁可多存一份纯本地日志，也不要漏掉。
     * 设置页的开关通过 [observeEnabled] 同步进来。
     */
    @Volatile
    var enabled: Boolean = true
        internal set

    @Volatile
    private var installed = false

    fun install(app: Application) {
        if (installed) return
        installed = true

        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            // 写日志自身出错也不能吞掉崩溃，这里不做任何 try 之外的假设
            if (enabled) {
                runCatching { CrashLogStore.save(app, thread, error) }
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** 把设置页的开关同步进来 */
    fun observeEnabled(scope: CoroutineScope, enabledFlow: Flow<Boolean>) {
        scope.launch { enabledFlow.collect { enabled = it } }
    }
}
