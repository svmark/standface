package com.marksnd.foldstandby

import android.app.KeyguardManager
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.window.layout.WindowInfoTracker
import com.marksnd.foldstandby.data.SettingsStore
import com.marksnd.foldstandby.data.inUse
import com.marksnd.foldstandby.data.isLocked
import com.marksnd.foldstandby.data.rememberBattery
import com.marksnd.foldstandby.ui.Posture
import com.marksnd.foldstandby.ui.StandbyScreen
import com.marksnd.foldstandby.ui.applyBrightness
import com.marksnd.foldstandby.ui.postureOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 손으로 여는 스탠바이 화면. 전체화면이고, 충전 중일 때만 화면을 켜 둔다. */
class StandbyActivity : ComponentActivity() {

    private var posture by mutableStateOf(Posture.Flat)

    /** 화면이 다시 보일 때마다 늘어난다. 스탠바이는 이때 달력+시계로 돌아간다. */
    private var openCount by mutableIntStateOf(0)

    /** 쉬는 화면(낮)이나 거의 꺼진 화면(밤)인지. 이때는 제스처 막대도 숨긴다. */
    private var resting = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 루틴이 열었는데 폰을 쓰는 중(잠금 풀림)이면 열지 않고 쓰던 앱으로 돌려보낸다.
        // 차에서 무선 충전할 때 내비게이션을 밀어내던 문제. 아이폰 스탠바이도 잠긴 폰에서만 켜진다.
        if (savedInstanceState == null && skipRoutineLaunch(intent)) {
            finish()
            return
        }
        enableEdgeToEdge()
        hideSystemBars()
        // 잠금화면 위에 바로 보인다. 잠긴 동안 일정 제목·사진은 화면에서 숨긴다(LocalLocked).
        setShowWhenLocked(true)
        if (openedByRoutine(intent)) setTurnScreenOn(true)

        // 아이콘이 하나뿐이라, 처음 실행할 때는 설정(권한·화면 보호기 안내)을 먼저 보여준다.
        if (savedInstanceState == null && !SettingsStore(this).setupSeen) {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                WindowInfoTracker.getOrCreate(this@StandbyActivity)
                    .windowLayoutInfo(this@StandbyActivity)
                    .collect { posture = debugPosture() ?: postureOf(it) }
            }
        }

        setContent {
            val battery by rememberBattery()
            var wasCharging by remember { mutableStateOf<Boolean?>(null) }
            LaunchedEffect(battery.charging) {
                // 충전 중이던 폰을 충전기에서 떼면 아이폰 스탠바이처럼 닫는다.
                // 충전 없이 직접 연 경우(처음부터 충전 중 아님)는 그대로 둔다.
                // 차량 무선 충전기는 충전을 잠깐씩 끊으므로 몇 초 기다렸다가, 그사이 다시 붙으면 닫지 않는다
                // (충전 상태가 바뀌면 이 블록이 취소되고 새로 시작한다).
                if (wasCharging == true && !battery.charging) {
                    delay(UNPLUG_CLOSE_MS)
                    finish()
                }
                wasCharging = battery.charging
                if (battery.charging) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
            StandbyScreen(
                posture = posture,
                onOpenSettings = ::openSettings,
                onBrightness = { level -> applyBrightness(window, level) },
                openCount = openCount,
                onRest = { rest ->
                    resting = rest
                    hideSystemBars()
                },
            )
        }
    }

    override fun onStart() {
        super.onStart()
        openCount++
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // 이미 떠 있는 화면을 루틴이 다시 열 때도 같은 규칙. 화면은 그대로 두고 쓰던 앱이 앞에 남게 한다.
        if (skipRoutineLaunch(intent)) {
            moveTaskToBack(true)
            return
        }
        setIntent(intent)
        debugPosture()?.let { posture = it }
    }

    /**
     * 개발 빌드에서만: 에뮬레이터가 반접기를 알려주지 않을 때 화면 확인용으로 자세를 강제한다.
     * adb shell am start -n com.marksnd.foldstandby/.StandbyActivity --es debugPosture tabletop
     */
    private fun debugPosture(): Posture? {
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return null
        return Posture.entries.firstOrNull { it.name.equals(intent.getStringExtra(EXTRA_DEBUG_POSTURE), ignoreCase = true) }
    }

    /** 길게 눌러 설정을 연다. 잠겨 있으면 잠금 해제(지문·PIN)를 먼저 받는다. 취소하면 스탠바이에 그대로 남는다. */
    private fun openSettings() {
        val open = { startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        if (!isLocked(this)) return open()
        getSystemService(KeyguardManager::class.java)?.requestDismissKeyguard(
            this,
            object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() = open()
            },
        )
    }

    /** 삼성 루틴(모드 및 루틴)이 연 것인지. 앱을 연 쪽 패키지(referrer)로 구별한다. */
    private fun openedByRoutine(intent: Intent): Boolean {
        val from = referrer?.host ?: intent.getStringExtra(Intent.EXTRA_REFERRER_NAME)
        Log.i(TAG, "opened by $from")
        return from != null && "routine" in from
    }

    private fun skipRoutineLaunch(intent: Intent): Boolean =
        openedByRoutine(intent) && SettingsStore(this).read().routineOnlyLocked && inUse(this)

    private fun hideSystemBars() {
        // 위 상태 표시줄은 늘 숨긴다. 아래는 내비게이션 방식에 따라 다르다.
        // 3버튼(◁ ○ ▢)은 몇 시간씩 같은 자리에 켜져 있어 숨기고, 가장자리를 밀면 잠깐 나온다.
        // 제스처 막대는 둔다. 둘 다 숨기면 첫 번째 위로 밀기는 막대만 꺼내고 두 번째에야 홈으로 나가서
        // 앱이 잘 안 닫힌다. 다만 쉬는 화면에서는 막대도 숨긴다(번인 방지).
        val insets = ViewCompat.getRootWindowInsets(window.decorView)
        val threeButton = (insets?.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.tappableElement())?.bottom ?: 0) > 0
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.statusBars())
            if (threeButton || resting) hide(WindowInsetsCompat.Type.navigationBars())
            else show(WindowInsetsCompat.Type.navigationBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    companion object {
        /** 화면 보호기가 회전 때문에 이 화면으로 넘겼을 때 붙는 표시. */
        const val EXTRA_FROM_DREAM = "fromDream"
        private const val EXTRA_DEBUG_POSTURE = "debugPosture"
        private const val TAG = "Standface"

        /** 충전이 끊긴 뒤 이만큼 다시 붙지 않으면 닫는다. */
        private const val UNPLUG_CLOSE_MS = 5_000L
    }
}
