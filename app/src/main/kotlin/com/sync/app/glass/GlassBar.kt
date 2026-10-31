package com.sync.app.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule
import com.sync.app.glass.kyant.InteractiveHighlight
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 크기를 t(0..1) 에 따라 (wa×ha) → (wb×hb) 로 레이아웃 단계에서 보간한다. (리컴포지션 없이 매 프레임 갱신)
 */
private fun Modifier.lerpSize(wa: Float, wb: Float, ha: Float, hb: Float, t: () -> Float): Modifier =
    this.layout { measurable, _ ->
        val k = t().coerceIn(0f, 1f)
        val w = (lerp(wa, wb, k) * density).roundToInt().coerceAtLeast(1)
        val h = (lerp(ha, hb, k) * density).roundToInt().coerceAtLeast(1)
        val placeable = measurable.measure(Constraints.fixed(w, h))
        layout(w, h) { placeable.place(0, 0) }
    }

/**
 * Mini player: one Kyant glass capsule.
 *
 *  펼침   : 아트워크 · 제목/아티스트 · 재생(흰색 단색 글리프) · 다음곡
 *  컴팩트 : 내비 행 가운데 슬롯으로 줄어든 상태 — 아티스트 이름을 숨기고 요소 크기를 줄인다.
 *
 * 위치/크기는 DOM(#bar)이 알려 주는 사각형을 스프링으로 따라간다. DOM 은 컴팩트 전환 때 최종 위치로 즉시 이동하고,
 * 네이티브가 약간 오버슈트하는 스프링으로 따라가므로 "블룹" 하고 합쳐지는 모션이 된다.
 * (아래 패딩 96dp 를 둔 RegionRecorder 가 오버슈트 구간까지 배경을 기록한다.)
 */
@Composable
internal fun BarGlass(bar: BarState, backdrop: Backdrop, c: GlassController) {
    val scope = rememberCoroutineScope()
    val highlight = remember(scope) { InteractiveHighlight(scope) }
    val art = rememberRemoteImage(bar.art)

    val r = bar.rect
    val ax = remember { Animatable(r.x) }
    val ay = remember { Animatable(r.y) }
    val aw = remember { Animatable(r.w) }
    val ah = remember { Animatable(r.h) }
    LaunchedEffect(r) {
        val spec = spring<Float>(dampingRatio = 0.62f, stiffness = 380f, visibilityThreshold = 0.05f)
        launch { ax.animateTo(r.x, spec) }
        launch { ay.animateTo(r.y, spec) }
        launch { aw.animateTo(r.w, spec) }
        launch { ah.animateTo(r.h, spec) }
    }

    // 0 = 펼침, 1 = 컴팩트 (내용물 전용 — 오버슈트가 거의 없는 스프링)
    val cp = animateFloatAsState(
        targetValue = if (bar.compact) 1f else 0f,
        animationSpec = spring(0.85f, 460f),
        label = "barCompact"
    )

    Box(
        Modifier
            .liveRect { PxRect(ax.value * density, ay.value * density, aw.value * density, ah.value * density) }
            .drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule() },
                effects = {
                    vibrancy()
                    blur(2f.dp.toPx())
                    lens(24f.dp.toPx(), 24f.dp.toPx())
                },
                onDrawSurface = { drawRect(GlassTheme.Container) }
            )
            .then(highlight.modifier)
            .then(highlight.gestureModifier)
    ) {
        Row(
            Modifier.fillMaxSize().padding(start = 9.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // artwork 40dp → 34dp
            Box(
                Modifier
                    .lerpSize(40f, 34f, 40f, 34f) { cp.value }
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color.White.copy(0.08f))
                    .tap { c.cmd { put("t", "bar"); put("a", "open") } }
            ) {
                if (art != null) Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp)
                    .tap { c.cmd { put("t", "bar"); put("a", "open") } },
                verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically)
            ) {
                BasicText(
                    bar.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = TextStyle(Color.White.copy(0.96f), 13.5f.sp, FontWeight.SemiBold)
                )
                // 아티스트(채널) 이름: 컴팩트로 갈수록 투명해지며 높이도 0 으로 접혀 제목이 세로 가운데로 내려온다.
                BasicText(
                    bar.channel,
                    modifier = Modifier
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            val keep = (1f - cp.value).coerceIn(0f, 1f)
                            val h = (placeable.height * keep).roundToInt()
                            layout(placeable.width, h) { placeable.place(0, 0) }
                        }
                        .graphicsLayer { alpha = (1f - cp.value * 1.8f).coerceIn(0f, 1f) },
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = TextStyle(Color.White.copy(0.55f), 11.sp)
                )
            }
            // play / pause — 배경 없는 흰색 단색 글리프 (다음곡 버튼과 같은 방식)
            Box(
                Modifier
                    .lerpSize(40f, 34f, 40f, 40f) { cp.value }
                    .tap { c.cmd { put("t", "bar"); put("a", "play") } },
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.size(18.dp)) {
                    fillIcon(if (bar.playing) GlassIcons.PauseFill else GlassIcons.PlayFill, 512f, Color.White)
                }
            }
            // NEXT track (the source path is a "previous" glyph, mirrored exactly like the original CSS scaleX(-1))
            Box(
                Modifier
                    .lerpSize(42f, 34f, 40f, 40f) { cp.value }
                    .tap { c.cmd { put("t", "bar"); put("a", "next") } },
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.size(16.dp)) {
                    scale(-1f, 1f, pivot = center) {
                        fillIcon(GlassIcons.NextFill, 512f, Color.White.copy(0.7f))
                    }
                }
            }
        }
    }
}
