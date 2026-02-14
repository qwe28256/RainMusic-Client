package com.example.neumusic.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.neumusic.ui.theme.neumorphicShadow
import com.example.neumusic.viewmodel.PlayerViewModel

enum class MainTab {
    Discover,
    Mine
}

@Composable
fun MainScreen(viewModel: PlayerViewModel) {
    var currentTab by remember { mutableStateOf(MainTab.Discover) }
    var showFullPlayer by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    val currentSong by viewModel.currentSong.collectAsState()

    BackHandler(enabled = showFullPlayer || showSettings) {
        when {
            showFullPlayer -> showFullPlayer = false
            showSettings -> showSettings = false
        }
    }

    if (showSettings) {
        SettingsScreen(onBack = { showSettings = false })
    } else {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            val screenHeightPx = constraints.maxHeight.toFloat()

            // 动画配置
            val openProgress by animateFloatAsState(
                targetValue = if (showFullPlayer) 1f else 0f,
                animationSpec = tween(
                    durationMillis = 400,
                    easing = CubicBezierEasing(0.2f, 0.0f, 0.2f, 1.0f)
                ),
                label = "player_anim"
            )

            // === 1. 主页面 (背景) ===
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // 【核心修改】：移除了 scale (缩放)
                        // 背景只做变暗处理，位置和大小绝对不变
                        alpha = 1f - (0.3f * openProgress)

                        // 只有完全打开时才裁剪圆角
                        if (openProgress > 0.99f) {
                            clip = true
                            shape = RoundedCornerShape(20.dp)
                        }
                    }
                    .background(NeuBackground)
            ) {
                when (currentTab) {
                    MainTab.Discover -> DiscoverPage()
                    MainTab.Mine -> MinePage(onSettingsClick = { showSettings = true })
                }
            }

            // === 2. 浮动导航栏 ===
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 40.dp, start = 30.dp, end = 30.dp)
                    .zIndex(1f) // 层级比背景高
                // 【核心修改】：移除了 graphicsLayer
                // 导航栏现在不做任何动画 (不位移、不缩放、不透明度变化)
                // 它就静止在那里，等着被播放器盖住
            ) {
                FloatingNeumorphicNavBar(
                    currentTab = currentTab,
                    onTabSelected = { currentTab = it },
                    onPlayerClick = { showFullPlayer = true },
                    currentSongImage = currentSong?.albumArtUri
                )
            }

            // === 3. 全屏播放器 ===
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(2f) // 层级最高，盖住导航栏
                    .graphicsLayer {
                        // 唯一在动的元素：播放器本身
                        translationY = screenHeightPx * (1f - openProgress)
                    }
            ) {
                MusicPlayerScreen(
                    viewModel = viewModel,
                    onCollapse = { showFullPlayer = false }
                )
            }
        }
    }
}

// ... 以下组件保持不变 ...

@Composable
fun FloatingNeumorphicNavBar(
    currentTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    onPlayerClick: () -> Unit,
    currentSongImage: Any?
) {
    val barHeight = 70.dp
    val barShape = RoundedCornerShape(35.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(barHeight)
            .neumorphicShadow(
                lightColor = NeuLightShadow,
                darkColor = NeuDarkShadow,
                elevation = 10.dp,
                cornerRadius = 35.dp
            )
            .background(NeuBackground, barShape)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { onTabSelected(MainTab.Discover) }) {
            Icon(
                imageVector = if (currentTab == MainTab.Discover) Icons.Filled.Explore else Icons.Outlined.Explore,
                contentDescription = "Discover",
                tint = if (currentTab == MainTab.Discover) AccentColor else Color.Gray,
                modifier = Modifier.size(28.dp)
            )
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(50.dp)
                .neumorphicShadow(
                    lightColor = NeuLightShadow,
                    darkColor = NeuDarkShadow,
                    elevation = 6.dp,
                    cornerRadius = 25.dp
                )
                .background(NeuBackground, CircleShape)
                .clip(CircleShape)
                .clickable { onPlayerClick() }
        ) {
            if (currentSongImage != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(currentSongImage)
                        .crossfade(false)
                        .build(),
                    contentDescription = "Playing",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        IconButton(onClick = { onTabSelected(MainTab.Mine) }) {
            Icon(
                imageVector = if (currentTab == MainTab.Mine) Icons.Filled.Person else Icons.Outlined.Person,
                contentDescription = "Mine",
                tint = if (currentTab == MainTab.Mine) AccentColor else Color.Gray,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
fun DiscoverPage() {
    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            modifier = Modifier.padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("发现", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.padding(end = 16.dp))
            Text("订阅", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Color.Gray)
        }
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(">.< 还没有开发", fontSize = 18.sp, color = Color.Gray.copy(alpha = 0.7f), fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun MinePage(onSettingsClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Box(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
            Icon(
                Icons.Default.Settings,
                contentDescription = "Settings",
                tint = TextPrimary,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(28.dp)
                    .clickable { onSettingsClick() }
            )
        }
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("我的页面\n(待开发)", textAlign = TextAlign.Center, fontSize = 18.sp, color = Color.Gray.copy(alpha = 0.7f))
        }
    }
}
