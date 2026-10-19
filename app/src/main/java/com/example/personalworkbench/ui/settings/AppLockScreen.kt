package com.example.personalworkbench.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.personalworkbench.di.AppContainer
import com.example.personalworkbench.ui.components.EmptyState
import com.example.personalworkbench.viewmodel.SettingsViewModel
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Fingerprint

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLockScreen(nav: NavHostController, container: AppContainer) {
    val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(container))
    val enabled by vm.appLockEnabled.collectAsStateWithLifecycle(false)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("应用锁", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            EmptyState(
                icon = androidx.compose.material.icons.Icons.Outlined.Fingerprint,
                title = if (enabled) "应用锁已开启" else "应用锁未开启",
                subtitle = "在「我的-应用锁」中开启后，启动 APP 将需要指纹或 PIN 验证",
                actionLabel = if (enabled) null else "去开启",
                onAction = if (enabled) null else { { nav.popBackStack() } },
                modifier = Modifier.padding(padding)
            )
        }
    }
}
