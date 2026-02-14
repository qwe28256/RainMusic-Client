package com.example.neumusic.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// 自定义 Modifier 实现新拟态阴影
fun Modifier.neumorphicShadow(
    lightColor: Color = Color.White,
    darkColor: Color = Color(0xFFA3B1C6), // 默认深色阴影
    elevation: Dp = 10.dp,
    cornerRadius: Dp = 20.dp, // 圆角半径
    isPressed: Boolean = false // 是否按下（凹陷效果暂未实现，目前主要做凸起）
) = this.drawBehind {
    val shadowColorDark = darkColor.toArgb()
    val shadowColorLight = lightColor.toArgb()
    val shadowRadius = elevation.toPx()
    val offset = shadowRadius / 2
    val radiusPx = cornerRadius.toPx()

    drawIntoCanvas {
        val paint = Paint()
        val frameworkPaint = paint.asFrameworkPaint()

        if (!isPressed) {
            // 1. 绘制右下角暗阴影
            frameworkPaint.color = shadowColorDark
            frameworkPaint.setShadowLayer(shadowRadius, offset, offset, shadowColorDark)
            it.drawRoundRect(0f, 0f, size.width, size.height, radiusPx, radiusPx, paint)

            // 2. 绘制左上角亮高光
            frameworkPaint.color = shadowColorLight
            frameworkPaint.setShadowLayer(shadowRadius, -offset, -offset, shadowColorLight)
            it.drawRoundRect(0f, 0f, size.width, size.height, radiusPx, radiusPx, paint)
        }
    }
}
