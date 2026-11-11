package com.chethan616.clearpdf.ui

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.overscroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import sh.calvin.reorderable.DragGestureDetector
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.graphics.vector.ImageVector
import com.chethan616.clearpdf.engine.RecentOrder
import com.chethan616.clearpdf.engine.arranged
import com.chethan616.clearpdf.ui.glass.GlassCircle
import com.chethan616.clearpdf.ui.glass.GlassIcon
import com.chethan616.clearpdf.ui.glass.GlassLayer
import com.chethan616.clearpdf.ui.glass.GlassPanel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun JianHome(vm: AppViewModel, onBrowse: () -> Unit) {
    val haptics = com.chethan616.clearpdf.ui.glass.LocalHaptics.current
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var selecting by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf<String?>(null) }
    var moved by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var sorting by remember { mutableStateOf(false) }
    var homeMenu by remember { mutableStateOf(false) }
    var display by remember(vm.recentList, vm.homeRecentOrder) { mutableStateOf(vm.recentList.arranged(vm.homeRecentOrder)) }
    val chosen = display.filter { it.uri in selected }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val platformConfig = LocalViewConfiguration.current
    val recentConfig = remember(platformConfig) {
        object : ViewConfiguration by platformConfig {
            override val longPressTimeoutMillis = minOf(platformConfig.longPressTimeoutMillis, 350L)
        }
    }
    val dragThreshold = platformConfig.touchSlop
    val pullLimit = with(density) { 120.dp.toPx() }
    val overscroll = remember(scope, pullLimit) { RecentOverscroll(scope, pullLimit) }
    val reorder = rememberReorderableLazyListState(list) { from, to ->
        val a = display.indexOfFirst { it.uri == from.key }
        val b = display.indexOfFirst { it.uri == to.key }
        if (a >= 0 && b >= 0 && display[a].pinned == display[b].pinned) {
            display = display.toMutableList().apply { add(b, removeAt(a)) }
            moved = true
        }
    }
    LaunchedEffect(vm.recentList) { selected = selected.intersect(vm.recentList.map { it.uri }.toSet()) }
    BackHandler(selecting) { selecting = false; selected = emptySet() }
    GlassLayer(Modifier.fillMaxSize(), content = {
        val customBackground = com.chethan616.clearpdf.ui.glass.GlassSettings.customWallpaper
        if (com.chethan616.clearpdf.ui.glass.GlassSettings.wallpaper == com.chethan616.clearpdf.ui.glass.Wallpaper.Custom && customBackground != null)
            Image(customBackground, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else com.chethan616.clearpdf.ui.glass.Aurora(com.chethan616.clearpdf.ui.glass.AuroraMoods.Desk, Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.22f)).statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 24.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("EWord", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Color(0xFF182337), modifier = Modifier.weight(1f))
                Box {
                    GlassCircle(onClick = { homeMenu = true }, size = 40.dp, label = "文件与工具菜单") { GlassIcon(LucideIcons.Ellipsis, null) }
                    DropdownMenu(homeMenu, { homeMenu = false }) {
                        DropdownMenuItem(text = { Text("文件管理") }, onClick = { homeMenu = false; vm.homeTab = com.chethan616.clearpdf.ui.home.HomeTab.Files })
                        DropdownMenuItem(text = { Text("文档工具") }, onClick = { homeMenu = false; vm.homeTab = com.chethan616.clearpdf.ui.home.HomeTab.Tools })
                        DropdownMenuItem(text = { Text("图片批量转 PDF") }, onClick = { homeMenu = false; vm.pickPhotosToPdf() })
                        DropdownMenuItem(text = { Text("回收站") }, onClick = { homeMenu = false; vm.toolScreen = ToolScreen.Vault(com.chethan616.clearpdf.engine.VaultKind.Trash) })
                        DropdownMenuItem(text = { Text("恢复编辑草稿") }, onClick = { homeMenu = false; vm.toolScreen = ToolScreen.Vault(com.chethan616.clearpdf.engine.VaultKind.Draft) })
                        DropdownMenuItem(text = { Text("设置") }, onClick = { homeMenu = false; vm.settingsOpen = true })
                    }
                }
            }
            Text("最近使用", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF182337), modifier = Modifier.padding(start = 24.dp, bottom = 16.dp))
            Box(Modifier.weight(1f).clip(RoundedCornerShape(0.dp))) {
            CompositionLocalProvider(LocalViewConfiguration provides recentConfig) {
            LazyColumn(state = list, overscrollEffect = null,
                modifier = Modifier.fillMaxSize().nestedScroll(overscroll.connection).overscroll(overscroll), contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = if (selecting) 220.dp else 112.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(display, key = { it.uri }) { file ->
                ReorderableItem(reorder, key = file.uri) { isDragging ->
                val gesture = remember(file.uri) { RecentGestureGate() }
                var wasSelecting by remember(file.uri) { mutableStateOf(false) }
                var selectedBefore by remember(file.uri) { mutableStateOf(emptySet<String>()) }
                val interactions = remember(file.uri) { MutableInteractionSource() }
                val detector = remember(gesture, dragThreshold, haptics) {
                    object : DragGestureDetector {
                        override suspend fun PointerInputScope.detect(onDragStart: (Offset) -> Unit, onDragEnd: () -> Unit, onDragCancel: () -> Unit, onDrag: (PointerInputChange, Offset) -> Unit) {
                            var beganDrag = false
                            var origin = Offset.Zero
                            var distance = Offset.Zero
                            detectDragGesturesAfterLongPress(onDragStart = {
                                origin = it; distance = Offset.Zero; beganDrag = false
                                wasSelecting = selecting; selectedBefore = selected
                                gesture.start(); moved = false
                                selecting = true; selected = selected + file.uri
                                haptics?.play(com.chethan616.clearpdf.ui.glass.Feel.Bloom)
                                android.util.Log.d("EWordRecentGesture", "hold: selection committed")
                            }, onDragEnd = {
                                android.util.Log.d("EWordRecentGesture", "release: dragged=$beganDrag selected=$selecting")
                                if (beganDrag) onDragEnd() else gesture.finish(android.os.SystemClock.uptimeMillis(), false)
                            }, onDragCancel = {
                                android.util.Log.d("EWordRecentGesture", "cancel: dragged=$beganDrag selected=$selecting")
                                gesture.cancel()
                                if (beganDrag) onDragCancel() else {
                                    gesture.finish(android.os.SystemClock.uptimeMillis(), false)
                                    // Selection was already committed at the hold threshold.
                                    // A layout/gesture cancellation must not undo it.
                                }
                            }) { change, delta ->
                                gesture.move(delta.x, delta.y, dragThreshold)
                                distance += delta
                                if (gesture.hasDragged) {
                                    if (!beganDrag) {
                                        selecting = wasSelecting; selected = selectedBefore
                                        onDragStart(origin); beganDrag = true
                                        onDrag(change, distance)
                                    } else onDrag(change, delta)
                                }
                            }
                        }
                    }
                }
                Surface(shape = RoundedCornerShape(14.dp), color = if (selecting) Color(0xFFE8EDF3).copy(alpha = 0.85f) else Color.White.copy(alpha = 0.88f), shadowElevation = if (isDragging) 8.dp else 0.dp,
                    modifier = Modifier.fillMaxWidth().pointerInput(gesture) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            gesture.press(down.uptimeMillis)
                            try {
                                var pressed: Boolean
                                do {
                                    val event = awaitPointerEvent(PointerEventPass.Final)
                                    pressed = event.changes.any { it.pressed }
                                } while (pressed)
                            } finally { gesture.release(android.os.SystemClock.uptimeMillis()) }
                        }
                    }.draggableHandle(
                        interactionSource = interactions,
                        dragGestureDetector = detector,
                        onDragStarted = { dragging = file.uri; android.util.Log.d("EWordRecentGesture", "drag started") },
                        onDragStopped = {
                            val select = gesture.finish(android.os.SystemClock.uptimeMillis(), moved)
                            if (moved) vm.saveHomeRecentOrder(display.map { it.uri })
                            dragging = null
                            if (select) { selecting = true; selected = selected + file.uri }
                            else { selecting = wasSelecting; selected = selectedBefore }
                        })
                        .clickable(interactionSource = interactions, indication = null, onClick = {
                            if (gesture.allowsClick(android.os.SystemClock.uptimeMillis())) {
                                if (selecting) selected = if (file.uri in selected) selected - file.uri else selected + file.uri else vm.open(Uri.parse(file.uri))
                            }
                        })
                        .semantics { onLongClick("选择文件或拖动排列") { selecting = true; selected = selected + file.uri; haptics?.play(com.chethan616.clearpdf.ui.glass.Feel.Bloom); true } }) {
                    Row(Modifier.padding(18.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(file.name, maxLines = 2, overflow = TextOverflow.Ellipsis, color = if (selecting) Color(0xFF657184) else Color(0xFF182337), fontSize = 16.sp)
                        Text((if (file.pinned) "置顶 · " else "") + (if (file.locked) "已锁定 · " else "") + file.name.substringAfterLast('.', "").uppercase(Locale.ROOT) + "  ·  " + SimpleDateFormat("MM月dd日 HH:mm", Locale.SIMPLIFIED_CHINESE).format(Date(file.opened)), color = Color(0xFF738096), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                    if (selecting) {
                        Spacer(Modifier.width(12.dp))
                        Box(Modifier.size(24.dp).clip(CircleShape).background(if (file.uri in selected) Color(0xFF79A7E8) else Color.Transparent).border(1.5.dp, Color(0xFF8496AF), CircleShape), contentAlignment = androidx.compose.ui.Alignment.Center) {
                            if (file.uri in selected) Icon(LucideIcons.Check, "已选择", tint = Color.White, modifier = Modifier.size(17.dp))
                        }
                    }
                    }
                }
                }
            }
            }
            }
            }
        }
    }, above = {
        if (!selecting && dragging == null) {
        DraggableHomeButton("open", 1f, LucideSet.CloudUpload, Color(0xFFB8DFFF), Color(0xFF315E8C), "打开文件，可拖动", onBrowse)
        DraggableHomeButton("scan", 0f, LucideIcons.ScanText, Color(0xFFDDC9FA), Color(0xFF705093), "扫描文档，可拖动", vm::startScan)
        }
        AnimatedVisibility(visible = selecting && dragging == null, modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter), enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
            GlassPanel(Modifier.navigationBarsPadding().padding(16.dp).fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("已选 ${selected.size} 项", modifier = Modifier.weight(1f), color = Color(0xFF43536B))
                    TextButton(onClick = { selected = if (selected.size == display.size) emptySet() else display.map { it.uri }.toSet() }) { Text(if (selected.size == display.size) "取消全选" else "全选") }
                    TextButton(onClick = { selecting = false; selected = emptySet() }) { Text("完成") }
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Box {
                        RecentAction("排列", LucideIcons.ArrowUpDown, true) { sorting = true }
                        DropdownMenu(expanded = sorting, onDismissRequest = { sorting = false }) {
                            RecentOrder.entries.forEach { order -> DropdownMenuItem(text = { Text((if (order == vm.homeRecentOrder) "✓ " else "") + order.label) }, onClick = { vm.chooseHomeRecentOrder(order); sorting = false }) }
                        }
                    }
                    RecentAction("删除", LucideIcons.Trash2, chosen.isNotEmpty()) {
                        if (chosen.any { it.locked }) vm.tell("所选文件含已锁定项，请先解锁") else deleting = true
                    }
                    RecentAction(if (chosen.isNotEmpty() && chosen.all { it.pinned }) "取消置顶" else "置顶", LucideIcons.Pin, chosen.isNotEmpty()) { vm.setHomeRecentFlags(selected, pinned = !chosen.all { it.pinned }) }
                    RecentAction(if (chosen.isNotEmpty() && chosen.all { it.locked }) "解锁" else "锁定", LucideIcons.Lock, chosen.isNotEmpty()) { vm.setHomeRecentFlags(selected, locked = !chosen.all { it.locked }) }
                    RecentAction("转发", LucideIcons.Share2, chosen.isNotEmpty()) { vm.shareFiles(vm.recentRows().filter { it.key in selected }) }
                }
            }
        }
    })
    if (deleting) {
        var deleteLocal by remember { mutableStateOf(false) }
        AlertDialog(onDismissRequest = { deleting = false }, title = { Text("确认删除 ${chosen.size} 项？") },
            text = { Column {
                Text(chosen.take(3).joinToString("\n") { it.name } + if (chosen.size > 3) "\n等 ${chosen.size} 项" else "")
                Row(Modifier.fillMaxWidth().clickable { deleteLocal = !deleteLocal }.padding(top = 12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(checked = deleteLocal, onCheckedChange = { deleteLocal = it }); Text("同时删除本地文件")
                }
                Text(if (deleteLocal) "本地文件先保存到回收站，再请求删除原件；无删除权限时原件和记录保留。" else "仅删除最近使用记录，本地文件保留。", fontSize = 13.sp)
            } },
            confirmButton = { TextButton(onClick = { vm.deleteHomeRecents(selected, deleteLocal); deleting = false; selecting = false; selected = emptySet() }, enabled = chosen.isNotEmpty()) { Text("确认删除") } },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("取消") } })
    }
}

@Composable
private fun RecentAction(label: String, icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    Column(Modifier.widthIn(min = 62.dp).padding(horizontal = 4.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
        GlassCircle(onClick = { if (enabled) onClick() }, size = 40.dp, label = label) {
            GlassIcon(icon, null, tint = Color(0xFF52637D).copy(alpha = if (enabled) 1f else 0.35f), modifier = Modifier.size(20.dp))
        }
        Text(label, fontSize = 12.sp, color = Color(0xFF52637D), modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun DraggableHomeButton(id: String, initialX: Float, icon: ImageVector, color: Color, ink: Color, label: String, onOpen: () -> Unit) {
    var x by rememberSaveable(id) { mutableFloatStateOf(initialX) }
    var y by rememberSaveable(id) { mutableFloatStateOf(1f) }
    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
        val density = LocalDensity.current
        val margin = with(density) { 16.dp.toPx() }
        val diameter = with(density) { 64.dp.toPx() }
        val rangeX = (constraints.maxWidth - diameter - margin * 2).coerceAtLeast(0f)
        val rangeY = (constraints.maxHeight - diameter - margin * 2).coerceAtLeast(0f)
        GlassCircle(onClick = onOpen, size = 64.dp, surface = color.copy(alpha = 0.42f), ink = ink, label = label,
            modifier = Modifier.offset { IntOffset((margin + rangeX * x).roundToInt(), (margin + rangeY * y).roundToInt()) }
                .pointerInput(rangeX, rangeY) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        x = (x + drag.x / rangeX.coerceAtLeast(1f)).coerceIn(0f, 1f)
                        y = (y + drag.y / rangeY.coerceAtLeast(1f)).coerceIn(0f, 1f)
                    }
                }) {
            GlassIcon(icon, null, tint = ink, modifier = Modifier.size(28.dp))
        }
    }
}
