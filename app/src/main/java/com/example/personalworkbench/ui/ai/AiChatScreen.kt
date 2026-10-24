package com.example.personalworkbench.ui.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.personalworkbench.di.AppContainer
import com.example.personalworkbench.ui.theme.LocalSemanticColors
import com.example.personalworkbench.viewmodel.AiAssistantViewModel
import com.example.personalworkbench.viewmodel.ChatMessage
import com.example.personalworkbench.viewmodel.ChatRole

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen(nav: NavHostController, container: AppContainer, initialQuery: String = "") {
    val vm: AiAssistantViewModel = viewModel(factory = AiAssistantViewModel.Factory(container))
    val messages by vm.messages.collectAsStateWithLifecycle(emptyList())
    val typing by vm.typing.collectAsStateWithLifecycle(false)
    val ctx by vm.context.collectAsStateWithLifecycle(AiAssistantViewModel.ContextState())
    val pendingNetwork by vm.pendingNetwork.collectAsStateWithLifecycle(null)
    val networkEndpoint by vm.networkEndpoint.collectAsStateWithLifecycle("")
    var input by remember { mutableStateOf(TextFieldValue(initialQuery)) }
    var showCtx by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // 进入页面即汇总一次本机数据
    LaunchedEffect(Unit) { vm.refreshContext() }

    // 从首页命令栏带指令进入（文字/语音）时，自动发送首条指令
    LaunchedEffect(initialQuery) {
        if (initialQuery.isNotBlank()) {
            vm.submit(initialQuery)
            input = TextFieldValue("")
        }
    }

    // 新消息到达时自动滚动到底部
    LaunchedEffect(messages.size, typing) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    // V1.6.2：首次联网发送前的二次确认
    if (pendingNetwork != null) {
        AlertDialog(
            onDismissRequest = { vm.dismissNetworkConfirm() },
            title = { Text("确认联网调用") },
            text = {
                Text(
                    "你已配置 API Key，本条提问将离开本机、发送至：\n${networkEndpoint.ifBlank { "（未设置端点）" }}\n\n" +
                        "若在「我的 - AI 智能助手」中开启了「注入本机数据上下文」，待办/日程/笔记/目标/工程/日志的快照也会一并发送。" +
                        "该开关默认关闭，可随时在设置里关闭。",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.confirmNetworkAndSend() }) { Text("确认发送") }
            },
            dismissButton = {
                TextButton(onClick = { vm.dismissNetworkConfirm() }) { Text("取消") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = LocalSemanticColors.current.ai)
                        Text("AI 助手", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 8.dp))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 本机数据上下文状态条（V1.4.1）
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (ctx.enabled) {
                            LocalSemanticColors.current.aiContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                        modifier = Modifier.padding(end = 8.dp).clickable { showCtx = !showCtx }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Outlined.DataObject,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = if (ctx.enabled) LocalSemanticColors.current.ai else MaterialTheme.colorScheme.outline
                            )
                            Text(
                                text = if (ctx.enabled) "数据 ${ctx.totalItems}" else "数据 关",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (ctx.enabled) LocalSemanticColors.current.ai else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 2.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp, 8.dp, 12.dp, 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        decorationBox = { inner ->
                            Box(modifier = Modifier.fillMaxWidth()) {
                                if (input.text.isEmpty()) {
                                    Text("问问你的工作台…", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                inner()
                            }
                        }
                    )
                    IconButton(
                        onClick = {
                            if (input.text.isNotBlank()) {
                                vm.submit(input.text)
                                input = TextFieldValue("")
                            }
                        },
                        enabled = input.text.isNotBlank()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送", tint = LocalSemanticColors.current.ai)
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // 本机数据上下文详情（V1.4.1）
            androidx.compose.animation.AnimatedVisibility(visible = showCtx) {
                Surface(
                    color = LocalSemanticColors.current.aiContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "本机数据上下文",
                                style = MaterialTheme.typography.titleSmall,
                                color = LocalSemanticColors.current.ai
                            )
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { vm.refreshContext() }) { Text("重新汇总", style = MaterialTheme.typography.labelSmall) }
                        }
                        Text(
                            ctx.summary + if (ctx.hasApiKey) "，提问时会随对话发送给云端模型" else "；当前未配置 API Key，使用本地问答",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        if (ctx.sections.isEmpty()) {
                            Text(
                                "暂无可注入的数据，去待办/笔记/日程里添加内容后点「重新汇总」。",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        } else {
                            ctx.sections.forEach { (name, count) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "· $name",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        "$count 条",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = LocalSemanticColors.current.ai
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "提示：上下文内容仅用于本次提问的 system 提示，可在「我的-设置-AI 助手」关闭。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().weight(1f).padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp, 12.dp, 8.dp, 12.dp)
            ) {
                items(messages) { msg -> MessageBubble(msg) }
                if (typing) {
                    item { TypingBubble() }
                }
                if (messages.size == 1 && !typing) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                            Text("快捷提问", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            vm.suggestions.forEach { s ->
                                Surface(
                                    onClick = { vm.submit(s) },
                                    shape = RoundedCornerShape(50),
                                    color = LocalSemanticColors.current.aiContainer,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(s, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), style = MaterialTheme.typography.bodyMedium, color = LocalSemanticColors.current.ai)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(msg: ChatMessage) {
    val isUser = msg.role == ChatRole.USER
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        if (!isUser) {
            Surface(
                shape = RoundedCornerShape(50),
                color = LocalSemanticColors.current.ai,
                modifier = Modifier.size(32.dp).padding(end = 4.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (isUser) MaterialTheme.colorScheme.primaryContainer else LocalSemanticColors.current.aiContainer,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Text(
                msg.text,
                modifier = Modifier.padding(12.dp, 10.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun TypingBubble() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = RoundedCornerShape(50),
            color = LocalSemanticColors.current.ai,
            modifier = Modifier.size(32.dp).padding(end = 4.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(18.dp))
            }
        }
        Surface(shape = RoundedCornerShape(16.dp), color = LocalSemanticColors.current.aiContainer) {
            Text("正在思考…", modifier = Modifier.padding(12.dp, 10.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
