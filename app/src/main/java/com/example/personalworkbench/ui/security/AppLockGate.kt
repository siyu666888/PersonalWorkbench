package com.example.personalworkbench.ui.security

import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.personalworkbench.ui.components.EmptyState

/**
 * 应用锁生物识别实校验 (Feature 3, PRD 5.1/6.5)。
 *
 * 当 [appLockEnabled] 为 true 且设备支持生物识别/设备凭据时，
 * 启动与每次从后台回到前台都会拦截，要求指纹或 PIN 验证通过后才显示 [content]。
 * 若设备没有任何可用凭证方式，则优雅降级直接放行（不锁死用户）。
 */
@Composable
fun AppLockGate(
    activity: AppCompatActivity,
    appLockEnabled: Boolean,
    content: @Composable () -> Unit
) {
    val biometricManager = remember { BiometricManager.from(activity) }
    val canAuth = remember(appLockEnabled) {
        appLockEnabled && biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    var unlocked by remember(canAuth) { mutableStateOf(!canAuth) }
    var isAuthenticating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val executor = remember { ContextCompat.getMainExecutor(activity) }
    val promptInfo = remember {
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("应用锁验证")
            .setSubtitle("请使用指纹或设备 PIN 解锁个人工作台")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()
    }
    val biometricPrompt = remember(activity) {
        BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                isAuthenticating = false
                unlocked = true
                error = null
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                isAuthenticating = false
                error = errString.toString()
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                error = "验证未通过，请重试"
            }
        })
    }

    fun tryAuth() {
        if (!isAuthenticating && canAuth && !unlocked) {
            isAuthenticating = true
            error = null
            biometricPrompt.authenticate(promptInfo)
        }
    }

    // 首次进入：触发一次
    LaunchedEffect(Unit) { tryAuth() }

    // 从后台回到前台：重新上锁并要求验证
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (canAuth) {
                    unlocked = false
                    tryAuth()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (unlocked || !canAuth) {
        content()
    } else {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            EmptyState(
                icon = Icons.Outlined.Fingerprint,
                title = "已锁定",
                subtitle = error ?: "应用已锁定，请验证身份以继续使用",
                actionLabel = "立即验证",
                onAction = { tryAuth() },
                modifier = Modifier.padding(24.dp)
            )
            if (error != null) {
                Button(onClick = { tryAuth() }, modifier = Modifier.padding(top = 8.dp)) {
                    Text("重试")
                }
            }
        }
    }
}
