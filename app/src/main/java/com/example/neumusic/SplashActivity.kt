package com.example.neumusic

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.example.neumusic.data.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL

@SuppressLint("CustomSplashScreen")
class SplashActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. 热启动终极判断：如果不是任务栈根，直接结束
        // 这样从后台切回来时，用户会直接看到 MainActivity，完全绕过 Splash
        if (!isTaskRoot) {
            finish()
            return
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)

        // 【关键修复】：移除了 AppContainer.preloadData()
        // 不要在这里扫描音乐！会让 Splash 动画卡顿。放到 MainActivity 加载完之后再做。

        // 仅在后台静默更新图片，不影响 UI
        checkUpdateSplashInBackground()

        setContent {
            SplashScreenContent(
                onTimeout = { goToMain() },
                onSaveImage = { saveImageToSdCard() }
            )
        }
    }

    private fun goToMain() {
        val intent = Intent(this, MainActivity::class.java)
        // 确保 MainActivity 是栈顶唯一的实例，防止重复创建
        intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        startActivity(intent)

        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, android.R.anim.fade_in, android.R.anim.fade_out)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
        finish()
    }

    private fun checkUpdateSplashInBackground() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = NetworkClient.splashApi.getSplashConfig()
                val imageUrl = response.imageUrl
                if (!imageUrl.isNullOrEmpty() && response.state == "0") {
                    val url = URL(imageUrl)
                    val bitmap = BitmapFactory.decodeStream(url.openConnection().getInputStream())
                    val cacheFile = File(filesDir, "splash_cache.jpg")
                    FileOutputStream(cacheFile).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun saveImageToSdCard() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val cacheFile = File(filesDir, "splash_cache.jpg")
                if (!cacheFile.exists()) return@launch

                val bitmap = BitmapFactory.decodeFile(cacheFile.absolutePath)
                val path = Environment.getExternalStorageDirectory().absolutePath + "/歌词适配开屏.png"
                val file = File(path)

                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SplashActivity, "已保存: $path", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SplashActivity, "保存失败，请检查权限", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

@Composable
fun SplashScreenContent(
    onTimeout: () -> Unit,
    onSaveImage: () -> Unit
) {
    val context = LocalContext.current
    var splashBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }

    LaunchedEffect(Unit) {
        // 使用 IO 线程加载图片，不卡顿
        withContext(Dispatchers.IO) {
            val cacheFile = File(context.filesDir, "splash_cache.jpg")
            if (cacheFile.exists()) {
                val bitmap = BitmapFactory.decodeFile(cacheFile.absolutePath)
                splashBitmap = bitmap?.asImageBitmap()
            }
        }
        delay(2500)
        onTimeout()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { onSaveImage() })
            },
        contentAlignment = Alignment.Center
    ) {
        if (splashBitmap != null) {
            Image(
                bitmap = splashBitmap!!,
                contentDescription = "Splash",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        // 如果没有图片，就显示 Theme 里的背景，这里留白即可
    }
}
