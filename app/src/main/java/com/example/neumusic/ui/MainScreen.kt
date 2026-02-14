package com.example.neumusic.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Input
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
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
import androidx.compose.ui.text.style.TextOverflow
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
    // 控制本地音乐页面显示
    var showLocalMusic by remember { mutableStateOf(false) }

    val currentSong by viewModel.currentSong.collectAsState()

    // 统一处理返回键：优先级从上到下
    BackHandler(enabled = showFullPlayer || showSettings || showLocalMusic) {
        when {
            showFullPlayer -> showFullPlayer = false
            showSettings -> showSettings = false
            showLocalMusic -> showLocalMusic = false
        }
    }

    // 页面路由逻辑
    if (showSettings) {
        SettingsScreen(onBack = { showSettings = false })
    } else if (showLocalMusic) {
        LocalMusicScreen(
            viewModel = viewModel,
            onBack = { showLocalMusic = false }
        )
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
                        alpha = 1f - (0.3f * openProgress)
                    }
                    .background(NeuBackground)
            ) {
                when (currentTab) {
                    MainTab.Discover -> DiscoverPage()
                    MainTab.Mine -> MinePage(
                        onSettingsClick = { showSettings = true },
                        onLocalMusicClick = { showLocalMusic = true }
                    )
                }
            }

            // === 2. 浮动导航栏 ===
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 40.dp, start = 30.dp, end = 30.dp)
                    .zIndex(1f)
            ) {
                FloatingNeumorphicNavBar(
                    currentTab = currentTab,
                    onTabSelected = { currentTab = it },
                    onPlayerClick = { showFullPlayer = true },
                    currentSongImage = currentSong?.albumArtUri
                )
            }

            // === 3. 全屏播放器 ===
            if (openProgress > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(2f)
                        .graphicsLayer {
                            translationY = screenHeightPx * (1f - openProgress)
                            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                            clip = true
                            shadowElevation = 20.dp.toPx()
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
}

// === 底部导航栏 ===
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

// === 发现页面 ===
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

// === 我的页面 ===
@Composable
fun MinePage(
    onSettingsClick: () -> Unit,
    onLocalMusicClick: () -> Unit
) {
    // 假数据
    val dummyPlaylists = listOf(
        "华语流行精选", "深夜emo时刻", "工作学习背景音", "周杰伦全集", "欧美金曲", "抖音热歌"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // 1. 顶部设置
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable { onSettingsClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = TextPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // 2. 用户信息
        ProfileHeaderSection()

        Spacer(modifier = Modifier.height(32.dp))

        // 3. 功能网格
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            NeuGridItem(icon = Icons.Default.Favorite, title = "我喜欢的", color = Color(0xFFF85D5D))
            NeuGridItem(icon = Icons.Default.History, title = "最近播放", color = AccentColor)
            NeuGridItem(
                icon = Icons.Default.PhoneAndroid,
                title = "本地音乐",
                color = Color(0xFF4CAF50),
                onClick = onLocalMusicClick
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // 4. 操作按钮
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            NeuWideButton(
                icon = Icons.Default.Add,
                text = "新建歌单",
                modifier = Modifier.weight(1f)
            )
            NeuWideButton(
                icon = Icons.Default.Input,
                text = "导入歌单",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // 5. 收藏歌单标题
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "收藏的歌单",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                "(${dummyPlaylists.size})",
                fontSize = 14.sp,
                color = Color.Gray
            )
        }

        // 6. 歌单横向列表
        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            items(dummyPlaylists.size) { index ->
                PlaylistCardItem(title = dummyPlaylists[index], count = (10..100).random())
            }
        }

        // 底部留白
        Spacer(modifier = Modifier.height(100.dp))
    }
}

// === 组件：歌单卡片 (横向) ===
@Composable
fun PlaylistCardItem(title: String, count: Int) {
    Column(
        modifier = Modifier
            .width(110.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { /* TODO */ },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(110.dp)
                .neumorphicShadow(
                    lightColor = NeuLightShadow,
                    darkColor = NeuDarkShadow,
                    elevation = 6.dp,
                    cornerRadius = 16.dp
                )
                .background(NeuBackground, RoundedCornerShape(16.dp))
        ) {
            Icon(
                Icons.Default.List,
                contentDescription = null,
                tint = Color.Gray.copy(0.5f),
                modifier = Modifier.size(40.dp)
            )
            // 右下角播放小按钮
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(24.dp)
                    .background(NeuBackground.copy(alpha = 0.9f), CircleShape)
                    .neumorphicShadow(NeuLightShadow, NeuDarkShadow, 2.dp, 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = AccentColor,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = "$count 首",
            fontSize = 11.sp,
            color = Color.Gray,
            modifier = Modifier.padding(top = 2.dp),
            textAlign = TextAlign.Center
        )
    }
}

// === 组件：用户信息头 ===
@Composable
fun ProfileHeaderSection() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(70.dp)
                .neumorphicShadow(
                    lightColor = NeuLightShadow,
                    darkColor = NeuDarkShadow,
                    elevation = 6.dp,
                    cornerRadius = 35.dp
                )
                .background(NeuBackground, CircleShape)
        ) {
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.width(20.dp))

        Column(
            modifier = Modifier.weight(1f).clickable { /* TODO: 跳转登录 */ }
        ) {
            Text(
                text = "立即登录",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "登录同步你的音乐世界 >",
                fontSize = 14.sp,
                color = Color.Gray
            )
        }
    }
}

// === 组件：功能网格 ===
@Composable
fun NeuGridItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, color: Color, onClick: () -> Unit = {} ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(60.dp)
                .neumorphicShadow(
                    lightColor = NeuLightShadow,
                    darkColor = NeuDarkShadow,
                    elevation = 6.dp,
                    cornerRadius = 16.dp
                )
                .background(NeuBackground, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .clickable { onClick() }
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = color,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary
        )
    }
}

// === 组件：宽按钮 ===
@Composable
fun NeuWideButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(50.dp)
            .neumorphicShadow(
                lightColor = NeuLightShadow,
                darkColor = NeuDarkShadow,
                elevation = 5.dp,
                cornerRadius = 12.dp
            )
            .background(NeuBackground, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .clickable { /* TODO */ }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = TextPrimary, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, fontSize = 14.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
    }
}
