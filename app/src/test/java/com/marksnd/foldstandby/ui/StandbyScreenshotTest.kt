package com.marksnd.foldstandby.ui

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.marksnd.foldstandby.data.MediaState
import com.marksnd.foldstandby.data.SettingsStore
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale
import java.util.TimeZone

/*
 * 기기 크기·자세별 스탠바이 화면 스냅숏. 값은 FixedSources로 고정한다. 표시 언어는 기본값(한국어), 이름이 _en인 것만 영어.
 *   기준 이미지 만들기:  ./gradlew recordRoborazziDebug
 *   기준과 비교:         ./gradlew verifyRoborazziDebug
 * 이미지는 app/screenshots/에 있다. 화면을 일부러 바꿨으면 기준을 다시 만들고 바뀐 그림을 눈으로 확인한 뒤 커밋한다.
 *
 * 크기(450dpi 기준 dp): 폴드8 커버 701×444, 폴드8 펼침 870×657, 플립8 메인 896×384, 플립8 커버 337×373
 *   좁은 세로: 21:9(폴드7·폴드8 울트라 커버, 플립8 메인) 384×896, 19.5:9(S23 FE·S24 울트라) 384×832
 */
private const val FOLD8_COVER_LAND = "w701dp-h444dp-land-450dpi"
private const val FOLD8_COVER_PORT = "w444dp-h701dp-port-450dpi"
private const val FOLD8_INNER_LAND = "w870dp-h657dp-land-450dpi"
private const val FOLD8_INNER_PORT = "w657dp-h870dp-port-450dpi"
private const val FLIP8_LAND = "w896dp-h384dp-land-450dpi"
private const val FLIP8_PORT = "w384dp-h896dp-port-450dpi"
private const val FLIP8_COVER = "w337dp-h373dp-port-450dpi"
private const val GALAXY_S_PORT = "w384dp-h832dp-port-450dpi"

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class StandbyScreenshotTest {
    @get:Rule val composeRule = createComposeRule()

    @Before fun setUp() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"))
        Locale.setDefault(Locale.US)
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("standby", Context.MODE_PRIVATE).edit().clear().commit()
        SettingsStore(context).update {
            putBoolean(SettingsStore.SETUP_SEEN, true)
            putLong(SettingsStore.PHOTO_ALBUM_ID, 1L)
            putString(SettingsStore.PHOTO_ALBUM_NAME, "Demo")
            // 처음 쉴 때 한 번 뜨는 안내는 찍지 않는다.
            putBoolean(SettingsStore.REST_HINT_SEEN, true)
        }
    }

    private fun shoot(
        name: String,
        posture: Posture = Posture.Flat,
        sources: FixedSources = FixedSources(),
        english: Boolean = false,
        fahrenheit: Boolean = false,
        /** 동작을 마친 뒤 아무도 만지지 않고 15분이 지난 화면(낮에는 쉬는 화면)을 찍는다. */
        rested: Boolean = false,
        gesture: (() -> Unit)? = null,
    ) {
        // 기본은 한국어. 영어 화면도 몇 장 함께 지킨다.
        if (english) SettingsStore(ApplicationProvider.getApplicationContext()).update { putBoolean(SettingsStore.KOREAN, false) }
        if (fahrenheit) SettingsStore(ApplicationProvider.getApplicationContext()).update { putBoolean(SettingsStore.FAHRENHEIT, true) }
        composeRule.setContent {
            CompositionLocalProvider(LocalStandbySources provides sources) {
                StandbyScreen(posture = posture, onOpenSettings = {}, onBrightness = {})
            }
        }
        composeRule.waitForIdle()
        gesture?.invoke()
        composeRule.waitForIdle()
        if (rested) {
            sources.minutesLater.longValue = 15
            composeRule.waitForIdle()
        }
        composeRule.onRoot().captureRoboImage("screenshots/$name.png")
    }

    /**
     * 시계 스타일 넘기기(위로 밀기). 반접기면 윗면 안에서 민다.
     * 화면 가장자리에는 번인 방지로 옮겨 다닐 여백이 있어서, 손가락처럼 가장자리 조금 안쪽에서 시작한다.
     */
    private fun nextStyle(tabletop: Boolean = false) = composeRule.onRoot().performTouchInput {
        if (tabletop) swipeUp(startY = height * 0.4f, endY = height * 0.05f) else swipeUp(startY = height * 0.9f, endY = height * 0.1f)
    }

    private fun nextPage() = composeRule.onRoot().performTouchInput { swipeLeft(startX = width * 0.9f, endX = width * 0.1f) }

    // 폴드8 커버 화면
    @Config(qualifiers = FOLD8_COVER_LAND) @Test fun fold8CoverClock() = shoot("fold8_cover_land_clock")
    @Config(qualifiers = FOLD8_COVER_LAND) @Test fun fold8CoverDigital() = shoot("fold8_cover_land_digital") { nextStyle() }
    @Config(qualifiers = FOLD8_COVER_LAND) @Test fun fold8CoverCalendar() = shoot("fold8_cover_land_calendar") { nextStyle(); composeRule.waitForIdle(); nextStyle() }
    @Config(qualifiers = FOLD8_COVER_LAND) @Test fun fold8CoverWeather() = shoot("fold8_cover_land_weather") { nextPage() }
    @Config(qualifiers = FOLD8_COVER_LAND) @Test fun fold8CoverMusic() = shoot("fold8_cover_land_music_noaccess", sources = FixedSources(media = MediaState.NoAccess)) { nextPage(); composeRule.waitForIdle(); nextPage() }
    @Config(qualifiers = FOLD8_COVER_LAND) @Test fun fold8CoverNight() = shoot("fold8_cover_land_night", sources = FixedSources(dark = true))
    @Config(qualifiers = FOLD8_COVER_LAND) @Test fun fold8CoverAlarmGlow() = shoot("fold8_cover_land_alarm_glow", sources = FixedSources(alarmInMinutes = 5))
    @Config(qualifiers = FOLD8_COVER_PORT) @Test fun fold8CoverPortrait() = shoot("fold8_cover_port_clock")

    // 폴드8 펼친 화면
    @Config(qualifiers = FOLD8_INNER_LAND) @Test fun fold8InnerPhotos() = shoot("fold8_inner_land_photos")
    @Config(qualifiers = FOLD8_INNER_PORT) @Test fun fold8InnerTabletop() = shoot("fold8_inner_tabletop", Posture.Tabletop)

    // 플립8
    @Config(qualifiers = FLIP8_LAND) @Test fun flip8Clock() = shoot("flip8_land_clock")
    @Config(qualifiers = FLIP8_LAND) @Test fun flip8Calendar() = shoot("flip8_land_calendar") { nextStyle(); composeRule.waitForIdle(); nextStyle() }
    @Config(qualifiers = FLIP8_PORT) @Test fun flip8Tabletop() = shoot("flip8_tabletop_clock", Posture.Tabletop)
    @Config(qualifiers = FLIP8_PORT) @Test fun flip8TabletopDigital() = shoot("flip8_tabletop_digital", Posture.Tabletop) { nextStyle(tabletop = true) }
    @Config(qualifiers = FLIP8_COVER) @Test fun flip8Cover() = shoot("flip8_cover_clock")

    // 좁은 세로 화면: 달력 스타일의 큰 날짜와 시각이 한 줄에 들어가야 한다.
    @Config(qualifiers = FLIP8_PORT) @Test fun narrow21x9Calendar() = shoot("narrow_21x9_port_calendar") { nextStyle(); composeRule.waitForIdle(); nextStyle() }
    @Config(qualifiers = GALAXY_S_PORT) @Test fun galaxySPortraitCalendar() = shoot("galaxy_s_port_calendar") { nextStyle(); composeRule.waitForIdle(); nextStyle() }

    // 낮에 10분 넘게 가만히 두면 쉬는 화면: 한 단계 어둡고 큰 숫자는 외곽선(번인 방지)
    @Config(qualifiers = FOLD8_COVER_LAND) @Test fun fold8CoverRestedClock() = shoot("fold8_cover_land_rested_clock", rested = true)
    @Config(qualifiers = FOLD8_COVER_LAND) @Test fun fold8CoverRestedDigital() = shoot("fold8_cover_land_rested_digital", rested = true) { nextStyle() }
    @Config(qualifiers = FOLD8_COVER_LAND) @Test fun fold8CoverRestedCalendar() = shoot("fold8_cover_land_rested_calendar", rested = true) { nextStyle(); composeRule.waitForIdle(); nextStyle() }
    @Config(qualifiers = FOLD8_COVER_LAND) @Test fun fold8CoverRestedWeather() = shoot("fold8_cover_land_rested_weather", rested = true) { nextPage() }
    @Config(qualifiers = FOLD8_INNER_PORT) @Test fun fold8InnerRestedTabletop() = shoot("fold8_inner_rested_tabletop", Posture.Tabletop, rested = true)
    @Config(qualifiers = FLIP8_LAND) @Test fun flip8RestedCalendar() = shoot("flip8_land_rested_calendar", rested = true) { nextStyle(); composeRule.waitForIdle(); nextStyle() }

    // 잠긴 동안: 일정 제목은 "일정", 사진 카드는 빠진다
    @Config(qualifiers = FOLD8_COVER_LAND) @Test fun fold8CoverLockedCalendar() = shoot("fold8_cover_land_locked_calendar", sources = FixedSources(locked = true)) { nextStyle(); composeRule.waitForIdle(); nextStyle() }
    @Config(qualifiers = FOLD8_INNER_LAND) @Test fun fold8InnerLocked() = shoot("fold8_inner_land_locked", sources = FixedSources(locked = true))

    // 화씨: 세 자리 기온도 잘리지 않아야 한다
    @Config(qualifiers = FOLD8_COVER_LAND) @Test fun fold8CoverWeatherFahrenheit() = shoot("fold8_cover_land_weather_f", sources = FixedSources(hotter = 23), fahrenheit = true) { nextPage() }

    // 영어 표시
    @Config(qualifiers = FOLD8_COVER_LAND) @Test fun fold8CoverCalendarEnglish() = shoot("fold8_cover_land_calendar_en", english = true) { nextStyle(); composeRule.waitForIdle(); nextStyle() }
    @Config(qualifiers = FOLD8_COVER_LAND) @Test fun fold8CoverDigitalEnglish() = shoot("fold8_cover_land_digital_en", english = true) { nextStyle() }
}
