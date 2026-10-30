package com.marksnd.foldstandby.data

import android.app.AlarmManager
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.LocalTime

data class BatteryInfo(val percent: Int, val plugged: Int, val status: Int = BatteryManager.BATTERY_STATUS_UNKNOWN) {
    val charging get() = plugged != 0

    /** 지금 실제로 채우는 중인지. 꽂혀 있어도 배터리 보호로 멈췄거나 가득 찼으면 false. */
    val filling get() = charging && status == BatteryManager.BATTERY_STATUS_CHARGING
    val wireless get() = plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS
}

private fun Intent.toBattery(): BatteryInfo {
    val level = getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
    val scale = getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
    return BatteryInfo(level * 100 / scale, getIntExtra(BatteryManager.EXTRA_PLUGGED, 0), getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN))
}

@Composable
fun rememberBattery(): State<BatteryInfo> {
    val context = LocalContext.current
    return produceState(BatteryInfo(0, 0)) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                value = intent.toBattery()
            }
        }
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )?.let { value = it.toBattery() }
        awaitDispose { context.unregisterReceiver(receiver) }
    }
}

/** 다음 알람 시각(epoch ms). 없으면 null. */
@Composable
fun rememberNextAlarm(): State<Long?> {
    val context = LocalContext.current
    return produceState<Long?>(null) {
        val am = context.getSystemService(AlarmManager::class.java)
        value = am?.nextAlarmClock?.triggerTime
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                value = am?.nextAlarmClock?.triggerTime
            }
        }
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        awaitDispose { context.unregisterReceiver(receiver) }
    }
}

/**
 * 주변이 어두운지. 조도 센서로 판단하고(3 lx 아래면 어둡다, 10 lx 위면 밝다),
 * 센서가 없으면 22시~6시를 어둡다고 본다.
 */
@Composable
fun rememberIsDark(): State<Boolean> {
    val context = LocalContext.current
    return produceState(false) {
        val sm = context.getSystemService(SensorManager::class.java)
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_LIGHT)
        if (sm == null || sensor == null) {
            var running = true
            while (running) {
                val h = LocalTime.now().hour
                value = h >= 22 || h < 6
                delay(60_000)
                running = isActive
            }
            return@produceState
        }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val lux = event.values[0]
                if (lux < 3f) value = true else if (lux > 10f) value = false
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        awaitDispose { sm.unregisterListener(listener) }
    }
}

/**
 * 폰이 잠겨 있는지(PIN·패턴·지문 같은 보안 잠금). 잠긴 동안에는 일정 제목·사진처럼 남이 보면 안 되는 것을 숨긴다.
 * 밀어서 열기만 있는 폰은 누구나 열 수 있으니 잠기지 않은 것으로 본다.
 * 잠금 상태가 바뀔 때 알려 주는 기능(addKeyguardLockedStateListener)은 시스템 앱 권한이 필요해서 1초마다 확인한다.
 */
@Composable
fun rememberLocked(): State<Boolean> {
    val context = LocalContext.current
    return produceState(isLocked(context)) {
        while (isActive) {
            value = isLocked(context)
            delay(1000)
        }
    }
}

fun isLocked(context: Context): Boolean =
    context.getSystemService(KeyguardManager::class.java)?.let { it.isKeyguardLocked && it.isDeviceSecure } ?: false

/**
 * 폰을 쓰는 중인지: 화면이 켜져 있고 잠금화면이 떠 있지 않다. 밀어서 열기 잠금화면도 '쓰지 않는 중'으로 본다.
 * 루틴이 충전 때 앱을 열었을 때, 쓰는 중이면 쓰던 앱(내비게이션 등)을 밀어내지 않으려고 쓴다.
 */
fun inUse(context: Context): Boolean {
    val screenOn = context.getSystemService(PowerManager::class.java)?.isInteractive ?: true
    val keyguard = context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked ?: false
    return screenOn && !keyguard
}

/** 폰에 설정된 화면 밝기(0~1). 읽지 못하면 null. 자동 밝기일 때는 마지막으로 정해진 값이다. */
fun systemBrightness(context: Context): Float? = runCatching {
    Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
}.getOrNull()?.coerceIn(0f, 1f)
