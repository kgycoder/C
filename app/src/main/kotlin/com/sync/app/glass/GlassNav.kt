package com.sync.app.glass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.sync.app.glass.kyant.LiquidBottomTab
import com.sync.app.glass.kyant.LiquidBottomTabs
import com.sync.app.glass.kyant.LiquidButton

/*
 * Apple Music 스타일 하단 내비게이션
 *
 *   펼침    [ 홈 | 즐겨찾기 | 대기열 | 플레이리스트 ]  (검색)
 *   컴팩트  (홈)  [ 축소된 미니 플레이어 ]  (검색)
 *
 * DOM 의 #mob-nav 는 여전히 5개 버튼(홈/검색/즐겨찾기/대기열/플레이리스트)을 가지고 있고(app.js 의 gv() 가 원천),
 * 여기서는 그 "행(row)" 의 사각형만 받아서 영역을 나눈다.
 *   - 원형 캡슐 지름 d = 행 높이
 *   - 검색 캡슐 : 행의 오른쪽 끝 (항상 표시)
 *   - 탭 캡슐   : 나머지 왼쪽 영역 (펼침 상태)
 *   - 홈 캡슐   : 행의 왼쪽 끝 (컴팩트 상태 — 탭 캡슐과 크로스페이드 + 크기 스프링)
 * 미니 플레이어(BarGlass)는 DOM 이 알려 주는 사각형을 스프링으로 따라가므로, 컴팩트 전환 때 아래로
 * 미끄러지며 크기가 변하는 블룹 모션이 만들어진다. 각 요소는 서로 겹치지 않는 영역만 차지한다.
 */

/** 탭 캡슐에 들어가는 4개 목적지 */
private val TabLabels = listOf("홈", "즐겨찾기", "대기열", "플레이리스트")
private val TabViews = listOf("home", "fav", "queue", "playlists")

/** DOM #mob-nav 버튼 인덱스(0 홈, 1 검색, 2 즐겨찾기, 3 대기열, 4 플레이리스트) → 탭 캡슐 인덱스(-1 = 탭 아님) */
private val TabOfNav = intArrayOf(0, -1, 1, 2, 3)

/** 탭 캡슐 인덱스 → DOM 버튼 인덱스 */
private val NavOfTab = intArrayOf(0, 2, 3, 4)

private const val GAP = 8f // 탭 캡슐과 검색 캡슐 사이 간격 (CSS px == dp)

@Composable
internal fun NavGlass(nav: NavState, bar: BarState?, backdrop: Backdrop, c: GlassController) {
    val r = nav.rect
    val d = r.h                              // 원형 캡슐 지름 = 행 높이
    val accent = LocalGlassAccent.current

    val compact = bar != null && bar.compact
    // 0 = 펼침, 1 = 컴팩트. 살짝 오버슈트하는 스프링 → 홈 캡슐이 톡 튀어나오는 느낌
    val cp = animateFloatAsState(
        targetValue = if (compact) 1f else 0f,
        animationSpec = spring(0.72f, 380f),
        label = "navCompact"
    )
    val showTabs by remember { derivedStateOf { cp.value < 0.995f } }
    val showHome by remember { derivedStateOf { cp.value > 0.005f } }

    val activeNav = rememberUpdatedState(nav.active)
    val searchActive = nav.active == 1

    // 검색 화면에서는 어느 탭도 선택되지 않지만(인디케이터 숨김), 인디케이터 위치는 마지막 탭에 남겨 둬야
    // 다시 돌아올 때 자연스럽게 이어진다. (탭 캡슐이 컴팩트 상태에서 사라졌다 돌아와도 유지되도록 바깥에 둔다)
    val lastTab = remember { intArrayOf(0) }
    val navTab = TabOfNav[nav.active.coerceIn(0, TabOfNav.size - 1)]
    if (navTab >= 0) lastTab[0] = navTab

    // ── 탭 캡슐 ───────────────────────────────────────────────
    if (showTabs) {
        val tabIndex = rememberUpdatedState(if (navTab >= 0) navTab else lastTab[0])
        val selected = remember { { tabIndex.value } }

        val onSelected = remember(c) {
            { index: Int ->
                // 비교 기준은 "선택된 탭"이 아니라 "실제 DOM 뷰": 검색 화면에서 마지막 탭을 다시 눌러도 이동해야 한다.
                if (index in TabViews.indices && activeNav.value != NavOfTab[index]) {
                    c.cmd { put("t", "nav"); put("v", TabViews[index]) }
                }
            }
        }
        val indicatorAlpha = animateFloatAsState(
            targetValue = if (searchActive) 0f else 1f,
            animationSpec = spring(1f, 500f),
            label = "tabIndicator"
        )

        Box(
            Modifier
                .cssRect(CssRect(r.x, r.y, r.w - d - GAP, d))
                .graphicsLayer { alpha = (1f - cp.value * 1.7f).coerceIn(0f, 1f) },
            contentAlignment = Alignment.Center
        ) {
            LiquidBottomTabs(
                selectedTabIndex = selected,
                onTabSelected = onSelected,
                backdrop = backdrop,
                tabsCount = TabLabels.size,
                accentColor = accent,
                modifier = Modifier.fillMaxWidth(),
                height = d.dp,
                indicatorAlpha = { indicatorAlpha.value }
            ) {
                repeat(TabLabels.size) { index ->
                    LiquidBottomTab({ onSelected(index) }) {
                        Canvas(Modifier.size(22.dp)) {
                            strokeIcon(GlassIcons.Nav[NavOfTab[index]], 20f, GlassTheme.Content.copy(0.8f), 1.6f)
                        }
                        BasicText(
                            TabLabels[index],
                            style = TextStyle(GlassTheme.Content.copy(0.8f), 10.sp, FontWeight.Medium),
                            maxLines = 1, softWrap = false, overflow = TextOverflow.Clip
                        )
                    }
                }
            }
        }
    }

    // ── 홈 캡슐 (컴팩트 상태에서만) ───────────────────────────────
    if (showHome) {
        Box(
            Modifier
                .liveRect {
                    // 중심은 고정, 크기만 0.55 → 1.0 으로 자란다 (블룹)
                    val f = (0.55f + 0.45f * cp.value).coerceIn(0.3f, 1.12f)
                    val full = d * density
                    val side = full * f
                    PxRect(r.x * density + (full - side) / 2f, r.y * density + (full - side) / 2f, side, side)
                }
                .graphicsLayer { alpha = cp.value.coerceIn(0f, 1f) }
        ) {
            GlassCircleButton(
                backdrop = backdrop,
                active = nav.active == 0,
                accent = accent,
                icon = GlassIcons.Home,
                onClick = { c.cmd { put("t", "nav"); put("v", "home") } }
            )
        }
    }

    // ── 검색 캡슐 (항상) ────────────────────────────────────────
    Box(Modifier.cssRect(CssRect(r.x + r.w - d, r.y, d, d))) {
        GlassCircleButton(
            backdrop = backdrop,
            active = searchActive,
            accent = accent,
            icon = GlassIcons.Search,
            onClick = { c.cmd { put("t", "nav"); put("v", "search") } }
        )
    }
}

/** Kyant `LiquidButton` 기반 원형 단일 캡슐(아이콘 하나). 활성 상태 = 흰 아이콘 + 강조색 면. */
@Composable
private fun GlassCircleButton(
    backdrop: Backdrop,
    active: Boolean,
    accent: Color,
    icon: List<Path>,
    onClick: () -> Unit
) {
    LiquidButton(
        onClick = onClick,
        backdrop = backdrop,
        modifier = Modifier.fillMaxSize(),
        surfaceColor = if (active) accent.copy(alpha = 0.30f) else GlassTheme.Container
    ) {
        Canvas(Modifier.size(22.dp)) {
            strokeIcon(icon, 20f, if (active) Color.White else GlassTheme.Content.copy(0.85f), 1.8f)
        }
    }
}
