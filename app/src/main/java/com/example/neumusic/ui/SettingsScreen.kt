package com.example.neumusic.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.neumusic.ui.theme.neumorphicShadow
import com.example.neumusic.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val scanPathDisplay by viewModel.scanPathDisplay.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    // 【修改】获取日志列表
    val scanLogs by viewModel.scanLogs.collectAsState()

    // 控制弹窗显示：正在扫描 OR 日志不为空
    val showDialog = isScanning || scanLogs.isNotEmpty()

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.setFolderUri(uri)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NeuBackground)
            .statusBarsPadding()
            .padding(24.dp)
    ) {
        // === 顶部栏 ===
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Back",
                tint = TextPrimary,
                modifier = Modifier
                    .size(32.dp)
                    .clickable { onBack() }
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text("设置", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }

        // === 1. 设置本地音乐目录 ===
        Text("本地音乐目录", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(60.dp)
                    .neumorphicShadow(NeuLightShadow, NeuDarkShadow, 5.dp, 10.dp)
                    .background(NeuBackground, RoundedCornerShape(10.dp))
                    .clickable { folderPickerLauncher.launch(null) }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FolderOpen, null, tint = AccentColor, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (scanPathDisplay == "未设置") "点击选择文件夹" else scanPathDisplay,
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (scanPathDisplay != "未设置") {
                            Text("已授权", color = Color.Gray, fontSize = 10.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            NeuButton(size = 60.dp, onClick = {
                if (scanPathDisplay != "未设置") {
                    viewModel.scanMusic(context)
                } else {
                    Toast.makeText(context, "请先设置目录", Toast.LENGTH_SHORT).show()
                }
            }) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Scan",
                    tint = AccentColor,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }

    // === 扫描日志窗口 ===
    if (showDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isScanning) viewModel.clearLog()
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = AccentColor
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("扫描中...", color = TextPrimary, fontSize = 18.sp)
                    } else {
                        Text("扫描完成", color = TextPrimary, fontSize = 18.sp)
                    }
                }
            },
            text = {
                // 使用 LazyColumn 显示滚动日志
                val listState = rememberLazyListState()

                // 自动滚动到底部
                LaunchedEffect(scanLogs.size) {
                    if (scanLogs.isNotEmpty()) {
                        listState.animateScrollToItem(scanLogs.lastIndex)
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp) // 给日志区域一个固定高度
                        .background(Color.Gray.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    LazyColumn(state = listState) {
                        items(scanLogs) { log ->
                            Text(
                                text = log,
                                fontSize = 12.sp,
                                color = TextPrimary,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                if (!isScanning) {
                    TextButton(onClick = { viewModel.clearLog() }) {
                        Text("确定", color = AccentColor, fontWeight = FontWeight.Bold)
                    }
                }
            },
            containerColor = NeuBackground,
            titleContentColor = TextPrimary,
            textContentColor = TextPrimary,
            shape = RoundedCornerShape(16.dp)
        )
    }
}
