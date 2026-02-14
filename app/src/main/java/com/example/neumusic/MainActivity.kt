package com.example.neumusic

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.neumusic.ui.MainScreen
import com.example.neumusic.viewmodel.PlayerViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    PermissionManager()
                }
            }
        }
    }
}

@Composable
fun PermissionManager() {
    val context = LocalContext.current
    // 状态：是否拥有存储权限
    var hasStoragePermission by remember {
        mutableStateOf(checkStoragePermission(context))
    }

    // 监听生命周期，当用户从设置页面回来时，重新检查权限
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasStoragePermission = checkStoragePermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (hasStoragePermission) {
        // 有权限，显示主界面
        MainContent()
    } else {
        // 无权限，显示申请界面
        RequestPermissionScreen()
    }
}

@OptIn(ExperimentalPermissionsApi::class) // 【修复】添加 OptIn 注解
@Composable
fun MainContent() {
    val viewModel: PlayerViewModel = viewModel()

    // 即使有了文件权限，对于 Android 13+，我们还需要申请一下通知权限
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val notificationPermission = rememberMultiplePermissionsState(
            permissions = listOf(Manifest.permission.POST_NOTIFICATIONS)
        )
        LaunchedEffect(Unit) {
            notificationPermission.launchMultiplePermissionRequest()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.initializeController()
    }

    MainScreen(viewModel)
}

@Composable
fun RequestPermissionScreen() {
    val context = LocalContext.current

    // 用于跳转到“管理所有文件”设置页面的 Launcher
    val settingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        // 结果在 onResume 中统一检查
    }

    // 用于请求旧版权限的 Launcher
    val oldPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // 结果在 onResume 中统一检查
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text("需要所有文件访问权限")
            Spacer(modifier = Modifier.height(16.dp))
            Text("为了读取 .lrc 歌词文件和音频元数据，请授予“管理所有文件”权限。")
            Spacer(modifier = Modifier.height(24.dp))

            Button(onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // Android 11+ (API 30+) 跳转到专用设置页面
                    try {
                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                        intent.addCategory("android.intent.category.DEFAULT")
                        intent.data = Uri.parse(String.format("package:%s", context.packageName))
                        settingsLauncher.launch(intent)
                    } catch (e: Exception) {
                        // 备用方案：跳转到通用的文件访问设置列表
                        val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        settingsLauncher.launch(intent)
                    }
                } else {
                    // Android 10及以下请求普通读写权限
                    oldPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                        )
                    )
                }
            }) {
                Text("授予权限")
            }
        }
    }
}

// 检查权限的辅助函数
fun checkStoragePermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        // Android 11+: 检查 MANAGE_EXTERNAL_STORAGE
        Environment.isExternalStorageManager()
    } else {
        // Android 10-: 检查 READ_EXTERNAL_STORAGE
        val result = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE)
        result == PackageManager.PERMISSION_GRANTED
    }
}
