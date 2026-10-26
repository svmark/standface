package com.marksnd.foldstandby.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.delay
import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 현재 시각(밀리초). smooth면 매 프레임, 아니면 매초 갱신한다.
 * 그리는 단계(Canvas)에서만 읽으면 다시 구성하지 않고 다시 그리기만 한다.
 */
@Composable
fun rememberNow(smooth: Boolean): State<Long> = produceState(System.currentTimeMillis(), smooth) {
    while (true) {
        if (smooth) {
            withFrameMillis { value = System.currentTimeMillis() }
        } else {
            value = System.currentTimeMillis()
            delay(1000 - value % 1000)
        }
    }
}

/** 오늘 날짜. 1분마다 확인해 자정과 시간대 변경을 따라간다. */
@Composable
fun rememberToday(): State<LocalDate> = produceState(LocalDate.now()) {
    while (true) {
        delay(60_000 - System.currentTimeMillis() % 60_000)
        value = LocalDate.now()
    }
}

/** 가로·세로 흐름의 주기(분). 서로 나누어떨어지지 않아 같은 경로를 오래 반복하지 않는다. */
private const val SHIFT_PERIOD_X = 97.0
private const val SHIFT_PERIOD_Y = 131.0

/**
 * 번인 방지: [minute]분째에 화면을 옮길 거리. ±[rangePx] 안에서 천천히 흐르고, 1분에 많아야 범위의 7%쯤 움직인다.
 * 큰 숫자 획(18–57dp)보다 넓게 움직여야 획 가운데도 쉰다.
 */
fun pixelShiftAt(minute: Long, rangePx: Int): IntOffset {
    val x = sin(2 * PI * minute / SHIFT_PERIOD_X)
    val y = sin(2 * PI * minute / SHIFT_PERIOD_Y + 1.0)
    return IntOffset((x * rangePx).roundToInt(), (y * rangePx).roundToInt())
}

/** 번인 방지: 1분마다 화면 전체를 [pixelShiftAt]만큼 옮긴다. */
@Composable
fun rememberPixelShift(rangePx: Int): State<IntOffset> {
    fun minuteNow() = System.currentTimeMillis() / 60_000
    return produceState(pixelShiftAt(minuteNow(), rangePx), rangePx) {
        while (true) {
            delay(60_000 - System.currentTimeMillis() % 60_000)
            value = pixelShiftAt(minuteNow(), rangePx)
        }
    }
}
