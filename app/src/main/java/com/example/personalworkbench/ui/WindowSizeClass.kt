package com.example.personalworkbench.ui

import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.compositionLocalOf

/**
 * 当前窗口尺寸类别的 CompositionLocal（V1.4.0）。
 * material3-window-size-class 未直接导出 LocalWindowSizeClass，这里自定义一个，
 * 在 MainActivity 顶层用 CompositionLocalProvider 注入，供各页面读取以做自适应布局。
 */
val LocalWindowSizeClass = compositionLocalOf<WindowSizeClass> {
    error("WindowSizeClass 未提供：请在 MainActivity 顶层通过 CompositionLocalProvider 注入")
}
