package com.marksnd.foldstandby.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

enum class NightMode { Auto, On, Off }

data class Settings(
    val accent: Int = 0,
    /** 한국어 사용자를 위한 앱이라 기본은 한국어. 설정에서 English를 고를 수 있다. */
    val korean: Boolean = true,
    val nightMode: NightMode = NightMode.Auto,
    val mondayFirst: Boolean = true,
    val alarmGlow: Boolean = true,
    /** 가만히 두면 쉬기: 낮에는 10분 뒤 어둡게(쉬는 화면), 밤에는 1분 뒤 거의 끈다. 움직이거나 만지면 깨어난다. */
    val wakeOnMotion: Boolean = true,
    /** 온도를 화씨로. 날씨는 섭씨로 받아 두고 그릴 때만 바꾼다. */
    val fahrenheit: Boolean = false,
    /** 루틴이 열었는데 잠금이 풀려 있으면(폰을 쓰는 중) 바로 닫는다. 차에서 내비게이션을 밀어내지 않게. */
    val routineOnlyLocked: Boolean = true,
    /** 펼친 화면 오른쪽에 시계 대신 사진을 보여줄지. */
    val unfoldedPhotos: Boolean = true,
    /** 고른 앨범(MediaStore 버킷). 고르지 않았으면 null. */
    val photoAlbumId: Long? = null,
    val photoAlbumName: String = "",
    /** 펼친 화면 오른쪽 스택에서 마지막으로 본 카드. */
    val stackCard: com.marksnd.foldstandby.ui.StackCard = com.marksnd.foldstandby.ui.StackCard.Photos,
)

class SettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("standby", Context.MODE_PRIVATE)

    fun read() = Settings(
        accent = prefs.getInt(ACCENT, 0),
        korean = prefs.getBoolean(KOREAN, true),
        nightMode = NightMode.entries.getOrElse(prefs.getInt(NIGHT, 0)) { NightMode.Auto },
        mondayFirst = prefs.getBoolean(MONDAY_FIRST, true),
        alarmGlow = prefs.getBoolean(ALARM_GLOW, true),
        wakeOnMotion = prefs.getBoolean(WAKE_ON_MOTION, true),
        fahrenheit = prefs.getBoolean(FAHRENHEIT, false),
        routineOnlyLocked = prefs.getBoolean(ROUTINE_ONLY_LOCKED, true),
        unfoldedPhotos = prefs.getBoolean(UNFOLDED_PHOTOS, true),
        photoAlbumId = prefs.getLong(PHOTO_ALBUM_ID, 0L).takeIf { prefs.contains(PHOTO_ALBUM_ID) },
        photoAlbumName = prefs.getString(PHOTO_ALBUM_NAME, "") ?: "",
        stackCard = com.marksnd.foldstandby.ui.StackCard.entries.firstOrNull { it.name == prefs.getString(STACK_CARD, null) }
            ?: com.marksnd.foldstandby.ui.StackCard.Photos,
    )

    fun update(block: SharedPreferences.Editor.() -> Unit) = prefs.edit().apply(block).apply()

    /** 설정 화면을 한 번이라도 열었는지. 처음 실행 때 설정을 자동으로 보여주는 데 쓴다. */
    val setupSeen: Boolean get() = prefs.getBoolean(SETUP_SEEN, false)

    /** "움직이거나 만지면 다시 켜져요" 안내를 이미 보여줬는지. 처음 쉴 때 한 번만 보여준다. */
    val restHintSeen: Boolean get() = prefs.getBoolean(REST_HINT_SEEN, false)

    fun flow(): Flow<Settings> = callbackFlow {
        trySend(read())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(read()) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    companion object {
        const val ACCENT = "accent"
        const val KOREAN = "korean"
        const val NIGHT = "night"
        const val MONDAY_FIRST = "mondayFirst"
        const val ALARM_GLOW = "alarmGlow"
        const val WAKE_ON_MOTION = "wakeOnMotion"
        const val FAHRENHEIT = "fahrenheit"
        const val ROUTINE_ONLY_LOCKED = "routineOnlyLocked"
        const val REST_HINT_SEEN = "restHintSeen"
        const val UNFOLDED_PHOTOS = "unfoldedPhotos"
        const val PHOTO_ALBUM_ID = "photoAlbumId"
        const val PHOTO_ALBUM_NAME = "photoAlbumName"
        const val STACK_CARD = "stackCard"
        const val SETUP_SEEN = "setupSeen"
    }
}

@Composable
fun rememberSettingsStore(): SettingsStore {
    val context = LocalContext.current
    return remember { SettingsStore(context) }
}

@Composable
fun rememberSettings(store: SettingsStore): State<Settings> {
    val flow = remember(store) { store.flow() }
    return flow.collectAsState(store.read())
}
