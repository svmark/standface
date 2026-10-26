package com.marksnd.foldstandby.ui

import androidx.compose.ui.unit.dp
import com.marksnd.foldstandby.data.pixelShiftAt
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.roundToInt

/**
 * 번인 측정: 화면 스냅숏(app/screenshots/)을 하루 동안 앱이 옮기는 자리들로 옮겨 겹쳐 보고,
 * 그래도 계속 켜져 있는 픽셀이 밝은 픽셀의 얼마인지 본다. 몇 시간씩 떠 있는 쉬는 화면(이름에 rested)은 [RESTED_LIMIT],
 * 만지거나 움직인 뒤 10분만 떠 있는 깨어 있는 화면은 [AWAKE_LIMIT]까지.
 * 큰 숫자처럼 획이 굵으면 화면을 옮겨도 획 가운데는 계속 켜진다. 그런 디자인이 다시 들어오면 여기서 드러난다.
 * 스냅숏은 recordRoborazziDebug로 만든 기준 그림을 쓴다. 기준 그림을 바꾸면 이 테스트도 다시 돈다.
 */
class BurnInTest {

    @Test fun nothingStaysLitWhereverTheScreenMoves() {
        val dir = File("screenshots")
        val shots = dir.listFiles { f -> f.extension == "png" && SKIP.none { it in f.name } }.orEmpty().sortedBy { it.name }
        assertTrue("스냅숏이 없다: ${dir.absolutePath}", shots.isNotEmpty())
        val results = shots.associate { it.nameWithoutExtension to stuckShare(it) }
        results.forEach { (name, share) -> println("%-40s 계속 켜진 픽셀 %5.1f%%".format(name, share * 100)) }
        val over = results.filter { (name, share) -> share > if ("rested" in name) RESTED_LIMIT else AWAKE_LIMIT }
        assertTrue("계속 켜진 픽셀이 기준(쉬는 화면 5%, 깨어 있는 화면 10%)을 넘는 화면: $over", over.isEmpty())
    }

    /** 밝은 픽셀 가운데, 옮긴 모든 자리를 평균해도 90% 넘게 밝은 픽셀의 비율. */
    private fun stuckShare(file: File): Double {
        val img = ImageIO.read(file)
        val w = img.width
        val h = img.height
        val lum = IntArray(w * h) { i ->
            val rgb = img.getRGB(i % w, i / w)
            maxOf(rgb shr 16 and 0xFF, rgb shr 8 and 0xFF, rgb and 0xFF)
        }
        val bright = lum.count { it > 128 }
        if (bright == 0) return 0.0

        val shortSideDp = minOf(w, h) / PX_PER_DP
        val rangePx = (shiftRange(shortSideDp.dp).value * PX_PER_DP).roundToInt()
        val offsets = (0 until 24 * 60 step 10).map { pixelShiftAt(it.toLong(), rangePx) }.distinct()

        val sum = LongArray(w * h)
        for (o in offsets) {
            for (y in 0 until h) {
                val sy = y - o.y
                if (sy !in 0 until h) continue
                for (x in 0 until w) {
                    val sx = x - o.x
                    if (sx in 0 until w) sum[y * w + x] += lum[sy * w + sx].toLong()
                }
            }
        }
        val stuck = sum.count { it > 0.9 * 255 * offsets.size }
        return stuck.toDouble() / bright
    }

    private companion object {
        /** 화면 스냅숏 밀도 450dpi. */
        const val PX_PER_DP = 450f / 160f

        const val RESTED_LIMIT = 0.05
        const val AWAKE_LIMIT = 0.10

        /** 사진(30초마다 바뀜)과 알람 전 빛(15분만 켜짐)은 오래 같은 그림이 아니라서 뺀다. */
        val SKIP = listOf("photos", "alarm_glow")
    }
}
