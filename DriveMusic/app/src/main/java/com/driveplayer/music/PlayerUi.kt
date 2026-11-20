package com.driveplayer.music

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player

@Composable
fun PlayerScreen(vm: AppVM) {
    var showTimer by remember { mutableStateOf(false) }

    BoxWithConstraints(
        Modifier.fillMaxSize().background(BG)
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onVerticalDrag = { _, d -> total += d },
                    onDragEnd = { if (total > 140f) vm.expanded = false }
                )
            }
    ) {
        val landscape = maxWidth > maxHeight
        if (landscape) {
            // 가로 화면·태블릿: 왼쪽에 플레이어, 오른쪽에 재생 목록
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                    PlayerMain(vm, 140.dp) { showTimer = true }
                }
                QueuePanel(vm, Modifier.weight(1f).fillMaxHeight().padding(top = 12.dp))
            }
        } else {
            val art = (maxHeight.value - 500f).coerceIn(100f, 240f).dp
            Column(Modifier.fillMaxSize()) {
                PlayerMain(vm, art) { showTimer = true }
                QueuePanel(vm, Modifier.weight(1f).fillMaxWidth().padding(top = 12.dp))
            }
        }
    }
    if (showTimer) TimerSheet(vm) { showTimer = false }
}

@Composable
private fun PlayerMain(vm: AppVM, art: Dp, onTimer: () -> Unit) {
    val cur = vm.current
    var drag by remember { mutableStateOf<Float?>(null) }
    val frac = drag ?: if (vm.durMs > 0) (vm.posMs.toFloat() / vm.durMs).coerceIn(0f, 1f) else 0f

    Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBtn(Ic.ChevDown, "미니 플레이어로 내리기", TextC, 26.dp) { vm.expanded = false }
        Txt("재생 중", 13, color = Sub, modifier = Modifier.weight(1f).padding(start = 8.dp))
        if (vm.timerLeftMs > 0) Txt(fmtTime(vm.timerLeftMs), 12, bold = true, color = Accent)
        IconBtn(Ic.Timer, "취침·수업 타이머", if (vm.timerLeftMs > 0) Accent else Sub, 24.dp) { onTimer() }
    }
    Box(Modifier.fillMaxWidth().padding(top = 4.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(art).clip(RoundedCornerShape(16.dp)).background(Surface2),
            contentAlignment = Alignment.Center
        ) { Icon(Ic.Note, null, tint = Color(0xFF6B717E), modifier = Modifier.size(art * 0.37f)) }
    }
    Row(Modifier.fillMaxWidth().padding(start = 28.dp, end = 12.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Txt(titleOf(cur?.name ?: ""), 20, bold = true)
            Txt(vm.artists[cur?.id ?: ""] ?: "", 14, color = Sub)
        }
        IconBtn(Ic.Heart, "재생목록에 저장", Sub, 24.dp) {
            if (cur != null) vm.openAdd(listOf(Item("song", cur.id, cur.name)), 1)
        }
    }
    Column(Modifier.padding(horizontal = 20.dp)) {
        Slider(
            value = frac,
            onValueChange = { drag = it },
            onValueChangeFinished = {
                drag?.let { vm.seekFrac(it) }
                drag = null
            },
            colors = SliderDefaults.colors(
                thumbColor = TextC,
                activeTrackColor = Accent,
                inactiveTrackColor = Line
            )
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            Txt(fmtTime(if (drag != null) (vm.durMs * frac).toLong() else vm.posMs), 12, color = Sub, modifier = Modifier.weight(1f))
            Txt(fmtTime(vm.durMs), 12, color = Sub)
        }
    }
    Row(
        Modifier.fillMaxWidth().height(80.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        IconBtn(Ic.Prev, "이전 곡", TextC, 30.dp) { vm.prev() }
        Spacer(Modifier.width(24.dp))
        Box(
            Modifier.size(68.dp).clip(CircleShape).background(Accent).clickable { vm.playPause() },
            contentAlignment = Alignment.Center
        ) {
            Icon(if (vm.isPlaying) Ic.Pause else Ic.Play, if (vm.isPlaying) "일시정지" else "재생", tint = OnAccent, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.width(24.dp))
        IconBtn(Ic.Next, "다음 곡", TextC, 30.dp) { vm.next() }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
        Pill("처음부터", false, Ic.Replay) { vm.restart() }
        Pill("전체 반복", false, Ic.Repeat, active = vm.repeat == Player.REPEAT_MODE_ALL) { vm.toggleRepeat(Player.REPEAT_MODE_ALL) }
        Pill("한 곡 반복", false, Ic.Repeat, active = vm.repeat == Player.REPEAT_MODE_ONE) { vm.toggleRepeat(Player.REPEAT_MODE_ONE) }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun QueuePanel(vm: AppVM, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)).background(Surface1)
    ) {
        Row(Modifier.fillMaxWidth().height(52.dp).padding(start = 20.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Txt("재생 목록 (${vm.queue.size}곡)", 15, bold = true, modifier = Modifier.weight(1f))
            Pill("셔플", false, Ic.Shuffle, active = vm.shuffle) { vm.toggleShuffle() }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(vm.queue) { i, s ->
                val on = i == vm.curIndex
                Row(
                    Modifier.fillMaxWidth().height(56.dp)
                        .background(if (on) Surface2 else Color.Transparent)
                        .clickable { vm.seekToIndex(i) }
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Txt("${i + 1}", 13, bold = on, color = if (on) Accent else Sub, modifier = Modifier.width(28.dp))
                    Column(Modifier.weight(1f)) {
                        Txt(titleOf(s.name), 15, medium = true, color = if (on) Accent else TextC)
                        Txt(vm.artists[s.id] ?: "", 12, color = Sub)
                    }
                }
            }
        }
    }
}

@Composable
private fun TimerSheet(vm: AppVM, onClose: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Surface1
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Txt("취침·수업 타이머", 17, bold = true, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
            if (vm.timerLeftMs > 0) {
                MenuRow(Ic.Close, "타이머 끄기 (남은 시간 ${fmtTime(vm.timerLeftMs)})", Danger) {
                    vm.setSleepTimer(0)
                    onClose()
                }
            }
            listOf(10, 20, 30, 45, 60, 90).forEach { m ->
                MenuRow(Ic.Timer, "${m}분 뒤 멈추기") {
                    vm.setSleepTimer(m)
                    onClose()
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(vm: AppVM) {
    val ctx = LocalContext.current
    var link by remember { mutableStateOf(vm.store.folderLink) }
    var key by remember { mutableStateOf(vm.store.apiKey) }
    LaunchedEffect(Unit) { vm.refreshCache() }
    val frac = (vm.cacheUsed.toFloat() / CacheManager.MAX).coerceIn(0f, 1f)

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(vm.exportJson().toByteArray(Charsets.UTF_8)) }
                vm.toast = "목록을 내보냈어요"
            } catch (e: Exception) {
                vm.toast = "내보내지 못했어요"
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val text = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                vm.importAll(text)
            } catch (e: Exception) {
                vm.toast = "파일을 읽지 못했어요"
            }
        }
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Txt("설정", 22, bold = true, modifier = Modifier.padding(top = 20.dp, bottom = 12.dp))

                if (!vm.configured) {
                    Box(
                        Modifier.fillMaxWidth().padding(bottom = 16.dp)
                            .clip(RoundedCornerShape(12.dp)).background(Surface2).padding(14.dp)
                    ) {
                        Txt("처음이시네요. 폴더 링크와 API 키를 넣고 저장하면 시작돼요", 14, maxLines = 3)
                    }
                }

                Txt("구글 드라이브", 13, medium = true, color = Sub, modifier = Modifier.padding(bottom = 12.dp))
                FormField("음악 폴더 공유 링크", link, { link = it })
                Spacer(Modifier.height(16.dp))
                FormField("API 키", key, { key = it }, password = true)
                Spacer(Modifier.height(16.dp))
                Box(
                    Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp)).background(Accent)
                        .clickable { vm.applySettings(link, key) },
                    contentAlignment = Alignment.Center
                ) { Txt("저장하고 폴더 불러오기", 15, bold = true, color = OnAccent) }
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(22.dp)).clickable { vm.refreshLibrary() },
                    contentAlignment = Alignment.Center
                ) { Txt("폴더·검색 목록 새로 불러오기", 14, medium = true, color = Accent) }
                Txt(
                    "한 번 열어 본 폴더는 기기에 저장돼서 인터넷 없이도 열려요. 드라이브에 새로 올린 곡은 새로 불러오기를 눌러야 보여요.",
                    12, color = Sub, maxLines = 4
                )

                Txt("목록 백업", 13, medium = true, color = Sub, modifier = Modifier.padding(top = 24.dp, bottom = 12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("목록 내보내기", false) { exportLauncher.launch("drivemusic-lists.json") }
                    Pill("목록 가져오기", false) { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }
                }
                Spacer(Modifier.height(8.dp))
                Txt(
                    "재생목록·폴더 목록·즐겨찾기를 파일로 저장해 다른 기기로 옮길 수 있어요. 가져오면 같은 목록은 가져온 내용으로 바뀌고, 나머지는 그대로 남아요.",
                    12, color = Sub, maxLines = 4
                )

                Txt("저장 공간", 13, medium = true, color = Sub, modifier = Modifier.padding(top = 24.dp, bottom = 12.dp))
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface1).padding(16.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Txt("재생한 곡 저장분", 15, medium = true, modifier = Modifier.weight(1f))
                        Txt(fmtSize(vm.cacheUsed) + " / 1GB", 15, bold = true)
                    }
                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(Line)) {
                        Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(Accent))
                    }
                    Spacer(Modifier.height(12.dp))
                    Txt("한 달(30일)이 지났거나 1GB를 넘으면 가장 오래전에 들은 곡부터 자동으로 지워져요.", 12, color = Sub, maxLines = 3)
                    Spacer(Modifier.height(12.dp))
                    Box(
                        Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(22.dp))
                            .background(Color(0xFF2B2224)).clickable { vm.clearCache() },
                        contentAlignment = Alignment.Center
                    ) { Txt("즉시 비우기", 14, medium = true, color = Danger) }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

fun fmtSize(b: Long): String {
    val mb = b / (1024.0 * 1024.0)
    return if (mb >= 1000) "%.2fGB".format(mb / 1024.0) else "%dMB".format(mb.toLong())
}
