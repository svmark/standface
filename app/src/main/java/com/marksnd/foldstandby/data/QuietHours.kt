package com.marksnd.foldstandby.data

import java.util.Calendar
import java.util.TimeZone

/** 잠자는 시간에 화면을 만졌을 때 시각을 보여주는 시간. */
const val QUIET_PEEK_MS = 10_000L

/** 잠자는 시간의 기본 시작(0:00)과 끝(6:00). 하루 0시부터 센 분. */
const val QUIET_DEFAULT_START = 0
const val QUIET_DEFAULT_END = 6 * 60

/**
 * [nowMinute](하루 0시부터 센 분)가 잠자는 시간 [startMinute] 이상 [endMinute] 미만인지.
 * 22:00~06:00처럼 자정을 넘기는 시간대도 된다. 시작과 끝이 같으면 잠자는 시간이 없는 것으로 본다
 * (하루 종일 꺼지는 실수를 막는다).
 */
fun isQuiet(nowMinute: Int, startMinute: Int, endMinute: Int): Boolean = when {
    startMinute == endMinute -> false
    startMinute < endMinute -> nowMinute in startMinute until endMinute
    else -> nowMinute >= startMinute || nowMinute < endMinute
}

/** 시각(밀리초)을 그 날 0시부터 센 분으로. 저장하지 않고 매번 지금 시각에서 계산한다. */
fun minuteOfDay(millis: Long, zone: TimeZone = TimeZone.getDefault()): Int {
    val c = Calendar.getInstance(zone).apply { timeInMillis = millis }
    return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
}

fun formatMinute(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)
