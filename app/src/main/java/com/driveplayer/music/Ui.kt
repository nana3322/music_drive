package com.driveplayer.music

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val BG = Color(0xFF121316)
val Surface1 = Color(0xFF1B1D22)
val Surface2 = Color(0xFF24272E)
val Line = Color(0xFF2C2F37)
val TextC = Color(0xFFF1F2F4)
val Sub = Color(0xFFA3A8B3)
val Accent = Color(0xFF5AD1B0)
val OnAccent = Color(0xFF08201A)
val Danger = Color(0xFFFF9B94)

object Ic {
    private fun mk(name: String, d: String, fill: Boolean = false): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).addPath(
            pathData = addPathNodes(d),
            fill = if (fill) SolidColor(Color.Black) else null,
            stroke = if (fill) null else SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ).build()

    val Folder = mk("folder", "M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z")
    val Note = mk("note", "M9 18V6l10-2v12M5 18a2 2 0 1 0 4 0a2 2 0 1 0-4 0M15 16a2 2 0 1 0 4 0a2 2 0 1 0-4 0")
    val Star = mk("star", "M12 3l2.8 5.7 6.2.9-4.5 4.4 1.1 6.2L12 17.2 6.4 20.2l1.1-6.2L3 9.6l6.2-.9z")
    val StarF = mk("starF", "M12 3l2.8 5.7 6.2.9-4.5 4.4 1.1 6.2L12 17.2 6.4 20.2l1.1-6.2L3 9.6l6.2-.9z", true)
    val More = mk("more", "M12 3.5a1.5 1.5 0 1 0 0 3a1.5 1.5 0 1 0 0-3M12 10.5a1.5 1.5 0 1 0 0 3a1.5 1.5 0 1 0 0-3M12 17.5a1.5 1.5 0 1 0 0 3a1.5 1.5 0 1 0 0-3", true)
    val Play = mk("play", "M7 5l12 7-12 7z", true)
    val Pause = mk("pause", "M6 5h4v14H6zM14 5h4v14h-4z", true)
    val Next = mk("next", "M6 5l10 7-10 7zM17 5h2v14h-2z", true)
    val Prev = mk("prev", "M18 5L8 12l10 7zM5 5h2v14H5z", true)
    val Plus = mk("plus", "M12 5v14M5 12h14")
    val Minus = mk("minus", "M21 12a9 9 0 1 0-18 0a9 9 0 1 0 18 0M8 12h8")
    val Check = mk("check", "M5 12l5 5 9-10")
    val Search = mk("search", "M18 11a7 7 0 1 0-14 0a7 7 0 1 0 14 0M20 20l-3.5-3.5")
    val ChevL = mk("chevL", "M15 6l-6 6 6 6")
    val ChevDown = mk("chevDown", "M6 9l6 6 6-6")
    val Close = mk("close", "M6 6l12 12M18 6L6 18")
    val Trash = mk("trash", "M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3")
    val Pencil = mk("pencil", "M4 20h4L19 9l-4-4L4 16zM13.5 6.5l4 4")
    val ListEdit = mk("list", "M4 6h16M4 12h16M4 18h10")
    val Shuffle = mk("shuffle", "M3 7h3a4 4 0 0 1 3.2 1.6l5.6 6.8A4 4 0 0 0 18 17h3M3 17h3a4 4 0 0 0 3.2-1.6M14.8 8.6A4 4 0 0 1 18 7h3M19 5l2 2-2 2M19 15l2 2-2 2")
    val Repeat = mk("repeat", "M17 2l3 3-3 3M4 11V9a4 4 0 0 1 4-4h12M7 22l-3-3 3-3M20 13v2a4 4 0 0 1-4 4H4")
    val Replay = mk("replay", "M3 12a9 9 0 1 0 3-6.7M3 4v5h5")
    val Queue = mk("queue", "M4 6h12M4 12h12M4 18h7M18 14v6M15 17h6")
    val Sliders = mk("sliders", "M4 7h9M17 7h3M4 17h3M11 17h9M17 7a2 2 0 1 0-4 0a2 2 0 1 0 4 0M11 17a2 2 0 1 0-4 0a2 2 0 1 0 4 0")
    val Heart = mk("heart", "M12 20s-7-4.6-7-10a4 4 0 0 1 7-2.5A4 4 0 0 1 19 10c0 5.4-7 10-7 10z")
}

@Composable
fun Txt(
    s: String,
    sp: Int = 15,
    bold: Boolean = false,
    medium: Boolean = false,
    color: Color = TextC,
    maxLines: Int = 1,
    modifier: Modifier = Modifier
) {
    Text(
        s,
        color = color,
        fontSize = sp.sp,
        fontWeight = if (bold) FontWeight.Bold else if (medium) FontWeight.Medium else FontWeight.Normal,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

@Composable
fun IconBtn(icon: ImageVector, desc: String, tint: Color = Sub, size: Dp = 22.dp, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(44.dp)) {
        Icon(icon, desc, tint = tint, modifier = Modifier.size(size))
    }
}

@Composable
fun Tile(icon: ImageVector, size: Dp = 44.dp, fg: Color = Sub, bg: Color = Surface2) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(8.dp)).background(bg),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = fg, modifier = Modifier.size(22.dp)) }
}

@Composable
fun CheckBox(on: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        if (on) {
            Box(
                Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).background(Accent),
                contentAlignment = Alignment.Center
            ) { Icon(Ic.Check, null, tint = OnAccent, modifier = Modifier.size(16.dp)) }
        } else {
            Box(Modifier.size(22.dp).border(2.dp, Color(0xFF6B717E), RoundedCornerShape(6.dp)))
        }
    }
}

@Composable
fun Pill(label: String, filled: Boolean, icon: ImageVector? = null, active: Boolean = false, onClick: () -> Unit) {
    val bg = if (filled) Accent else Color.Transparent
    val fg = if (filled) OnAccent else if (active) Accent else TextC
    val stroke = BorderStroke(1.dp, if (active) Accent else Color(0xFF3A3E48))
    Row(
        Modifier
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(bg)
            .then(if (filled) Modifier else Modifier.border(stroke, RoundedCornerShape(22.dp)))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (icon != null) Icon(icon, null, tint = fg, modifier = Modifier.size(16.dp))
        Txt(label, 14, bold = filled, medium = !filled, color = fg)
    }
}

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier) {
    Row(
        modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).background(Surface1).padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Ic.Search, null, tint = Sub, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Txt(hint, 15, color = Sub)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = TextC, fontSize = 15.sp),
                cursorBrush = SolidColor(Accent),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (value.isNotEmpty()) IconBtn(Ic.Close, "지우기", Sub, 18.dp) { onChange("") }
        else Spacer(Modifier.width(10.dp))
    }
}

@Composable
fun FormField(label: String, value: String, onChange: (String) -> Unit, password: Boolean = false) {
    Column {
        Txt(label, 14, medium = true)
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier.fillMaxWidth().height(48.dp)
                .clip(RoundedCornerShape(12.dp)).background(Surface1)
                .border(1.dp, Line, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = TextC, fontSize = 15.sp),
                cursorBrush = SolidColor(Accent),
                visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun DashedButton(label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .drawBehind {
                drawRoundRect(
                    color = Color(0xFF4A4F5A),
                    cornerRadius = CornerRadius(12.dp.toPx()),
                    style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
                )
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(Ic.Plus, null, tint = Accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Txt(label, 15, medium = true, color = Accent)
    }
}

@Composable
fun MenuRow(icon: ImageVector, label: String, color: Color = TextC, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).clickable(onClick = onClick).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Txt(label, 16, color = color)
    }
}

@Composable
fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface1,
        title = { Txt(title, 18, bold = true) },
        text = { FormField("이름", text, { text = it }) },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onOk(text.trim()) }) { Txt("확인", 15, bold = true, color = Accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Txt("취소", 15, color = Sub) } }
    )
}

/** 새 목록 만들기. kindChoice=true면 재생목록/폴더 목록을 고르게 한다 */
@Composable
fun NewListDialog(kindChoice: Boolean, defaultFolderKind: Boolean, onDismiss: () -> Unit, onOk: (String, Boolean) -> Unit) {
    var text by remember { mutableStateOf("") }
    var folderKind by remember { mutableStateOf(defaultFolderKind) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface1,
        title = { Txt(if (kindChoice) "새 목록 만들기" else if (defaultFolderKind) "새 폴더 목록" else "새 재생목록", 18, bold = true) },
        text = {
            Column {
                FormField("이름", text, { text = it })
                if (kindChoice) {
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("재생목록", false, active = !folderKind) { folderKind = false }
                        Pill("폴더 목록", false, active = folderKind) { folderKind = true }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onOk(text.trim(), folderKind) }) { Txt("만들기", 15, bold = true, color = Accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Txt("취소", 15, color = Sub) } }
    )
}

// ------------------------------------------------------------------ 앱 틀

@Composable
fun App(vm: AppVM) {
    val ctx = LocalContext.current
    LaunchedEffect(vm.toast) {
        vm.toast?.let {
            Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show()
            vm.toast = null
        }
    }
    BackHandler(enabled = vm.tab == 0 && (vm.stack.size > 1 || vm.showFav || vm.query.isNotBlank())) { vm.backBrowse() }
    BackHandler(enabled = vm.tab == 1 && vm.openList != null) {
        if (vm.editing) vm.editing = false else vm.openList = null
    }
    BackHandler(enabled = vm.picker != null) { vm.cancelPicker() }
    BackHandler(enabled = vm.expanded) { vm.expanded = false }

    Box(Modifier.fillMaxSize().background(BG).systemBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                when (vm.tab) {
                    0 -> BrowseScreen(vm)
                    1 -> LibraryScreen(vm)
                    else -> SettingsScreen(vm)
                }
            }
            if (vm.queue.isNotEmpty()) MiniPlayer(vm)
            NavBar(vm)
        }
        if (vm.expanded) PlayerScreen(vm)
        if (vm.menuNode != null) NodeMenuSheet(vm)
        if (vm.addPayload != null) AddSheet(vm)
        if (vm.busy) {
            Box(
                Modifier.fillMaxSize().background(Color(0x88000000)).clickable(enabled = true) {},
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = Accent) }
        }
    }
}

@Composable
fun NavBar(vm: AppVM) {
    Column(Modifier.fillMaxWidth().background(BG)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
        Row(Modifier.fillMaxWidth().height(60.dp)) {
            val tabs = listOf(Ic.Folder to "탐색", Ic.Queue to "재생목록", Ic.Sliders to "설정")
            tabs.forEachIndexed { i, (icon, label) ->
                val c = if (vm.tab == i) Accent else Sub
                Column(
                    Modifier.weight(1f).fillMaxHeight().clickable { vm.tab = i },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(icon, label, tint = c, modifier = Modifier.size(22.dp))
                    Txt(label, 11, medium = true, color = c)
                }
            }
        }
    }
}

@Composable
fun MiniPlayer(vm: AppVM) {
    val cur = vm.current
    val frac = if (vm.durMs > 0) (vm.posMs.toFloat() / vm.durMs).coerceIn(0f, 1f) else 0f
    Column(
        Modifier.fillMaxWidth().background(Surface1)
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onVerticalDrag = { change, d ->
                        change.consume()
                        total += d
                    },
                    onDragEnd = { if (total < -30f) vm.expanded = true }
                )
            }
            .clickable { vm.expanded = true }
    ) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(Line)) {
            Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(Accent))
        }
        Row(
            Modifier.fillMaxWidth().height(64.dp).padding(start = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Tile(Ic.Note)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Txt(titleOf(cur?.name ?: ""), 15, medium = true)
                Txt(vm.artists[cur?.id ?: ""] ?: "", 13, color = Sub)
            }
            IconBtn(if (vm.isPlaying) Ic.Pause else Ic.Play, if (vm.isPlaying) "일시정지" else "재생", TextC, 26.dp) { vm.playPause() }
            IconBtn(Ic.Next, "다음 곡", TextC, 24.dp) { vm.next() }
        }
    }
}

// ------------------------------------------------------------------ 시트

@Composable
fun NodeMenuSheet(vm: AppVM) {
    val n = vm.menuNode ?: return
    ModalBottomSheet(
        onDismissRequest = { vm.menuNode = null },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Surface1
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Tile(if (n.isFolder) Ic.Folder else Ic.Note, 52.dp, if (n.isFolder) Accent else Sub)
                Spacer(Modifier.width(14.dp))
                Column {
                    Txt(if (n.isFolder) n.name else titleOf(n.name), 17, bold = true)
                    Txt(if (n.isFolder) "폴더" else "곡", 13, color = Sub)
                }
            }
            if (n.isFolder) MenuRow(Ic.Play, "재생") {
                vm.menuNode = null
                vm.playFolders(listOf(n.id))
            }
            MenuRow(Ic.Queue, "재생목록에 담기") { vm.openAdd(listOf(n.toItem()), 1) }
            if (n.isFolder) MenuRow(Ic.Folder, "폴더 목록에 담기") { vm.openAdd(listOf(n.toItem()), 2) }
        }
    }
}

@Composable
fun AddSheet(vm: AppVM) {
    val items = vm.addPayload ?: return
    val mode = vm.addMode
    val allFolders = items.all { it.isFolder }
    val showPl = mode != 2
    val showFl = allFolders && mode != 1
    var q by remember { mutableStateOf("") }
    var newDlg by remember { mutableStateOf(false) }

    fun containsAll(l: ListData) = items.all { i -> l.items.any { it.type == i.type && it.id == i.id } }
    val plChecked = remember { mutableStateListOf<String>().apply { addAll(vm.playlists.filter { containsAll(it) }.map { it.id }) } }
    val flChecked = remember { mutableStateListOf<String>().apply { addAll(vm.folderLists.filter { containsAll(it) }.map { it.id }) } }

    val summary = if (items.size == 1) (if (items[0].isFolder) items[0].name else titleOf(items[0].name))
    else if (allFolders) "폴더 ${items.size}개" else "항목 ${items.size}개"

    ModalBottomSheet(
        onDismissRequest = { vm.addPayload = null },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Surface1
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Tile(if (allFolders) Ic.Folder else Ic.Note, 48.dp, if (allFolders) Accent else Sub)
                Spacer(Modifier.width(12.dp))
                Column {
                    Txt("목록에 담기", 12, color = Sub)
                    Txt(summary, 17, bold = true)
                }
            }
            SearchField(q, { q = it }, "재생목록·폴더 목록 검색", Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                if (showPl) {
                    Txt(
                        if (allFolders) "재생목록 (폴더 안 전체 곡이 들어가요)" else "재생목록", 13, medium = true, color = Sub,
                        modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp)
                    )
                    val list = vm.playlists.filter { it.name.contains(q, ignoreCase = true) }
                    if (list.isEmpty()) Txt("재생목록이 없어요", 14, color = Sub, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
                    list.forEach { l ->
                        val on = l.id in plChecked
                        Row(
                            Modifier.fillMaxWidth().height(56.dp).clickable { if (on) plChecked.remove(l.id) else plChecked.add(l.id) }
                                .padding(start = 8.dp, end = 20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CheckBox(on) { if (on) plChecked.remove(l.id) else plChecked.add(l.id) }
                            Txt(l.name, 16, modifier = Modifier.weight(1f).padding(start = 4.dp))
                            Txt(countText(l.items), 13, color = Sub)
                        }
                    }
                }
                if (showFl) {
                    Txt("폴더 목록", 13, medium = true, color = Sub, modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp))
                    val list = vm.folderLists.filter { it.name.contains(q, ignoreCase = true) }
                    if (list.isEmpty()) Txt("폴더 목록이 없어요", 14, color = Sub, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
                    list.forEach { l ->
                        val on = l.id in flChecked
                        Row(
                            Modifier.fillMaxWidth().height(56.dp).clickable { if (on) flChecked.remove(l.id) else flChecked.add(l.id) }
                                .padding(start = 8.dp, end = 20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CheckBox(on) { if (on) flChecked.remove(l.id) else flChecked.add(l.id) }
                            Txt(l.name, 16, modifier = Modifier.weight(1f).padding(start = 4.dp))
                            Txt(countText(l.items), 13, color = Sub)
                        }
                    }
                }
            }
            Column(Modifier.padding(horizontal = 20.dp).padding(top = 8.dp)) {
                DashedButton("새 목록 만들기") { newDlg = true }
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp)).background(Accent)
                        .clickable {
                            vm.applyAdd(items, plChecked.toSet(), flChecked.toSet())
                            vm.addPayload = null
                        },
                    contentAlignment = Alignment.Center
                ) { Txt("완료", 16, bold = true, color = OnAccent) }
            }
        }
    }
    if (newDlg) {
        NewListDialog(
            kindChoice = showPl && showFl,
            defaultFolderKind = !showPl,
            onDismiss = { newDlg = false },
            onOk = { name, folderKind ->
                val l = vm.createList(name, folderKind)
                if (folderKind) flChecked.add(l.id) else plChecked.add(l.id)
                newDlg = false
            }
        )
    }
}
