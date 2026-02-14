package com.example.neumusic.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object SleepTimerManager {
    private val scope = CoroutineScope(Dispatchers.Main)
    private var timerJob: Job? = null

    // 剩余时间字符串 (例如 "14:59")，null 表示没开启
    private val _timeLeft = MutableStateFlow<String?>(null)
    val timeLeft = _timeLeft.asStateFlow()

    // 定时结束时的回调
    private var onTimerFinished: (() -> Unit)? = null

    // 设置回调 (通常由 Service 注册)
    fun setOnTimerFinishedListener(listener: () -> Unit) {
        onTimerFinished = listener
    }

    fun startTimer(minutes: Int) {
        stopTimer()
        if (minutes <= 0) return

        val totalSeconds = minutes * 60L

        timerJob = scope.launch {
            var remaining = totalSeconds

            while (remaining > 0) {
                // 更新时间文本
                val min = remaining / 60
                val sec = remaining % 60
                _timeLeft.value = String.format("%02d:%02d", min, sec)

                delay(1000) // 等待 1 秒
                remaining--
            }

            // 倒计时结束
            _timeLeft.value = null
            onTimerFinished?.invoke()
        }
    }

    fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
        _timeLeft.value = null
    }
}
