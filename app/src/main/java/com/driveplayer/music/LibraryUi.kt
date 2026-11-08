package com.driveplayer.music

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(vm: AppVM) {
    val ref = vm.openList
    val data = if (ref != null) vm.getList(ref) else null
    if (ref != null && data != null) {
        if (vm.editing) EditScreen(vm, ref, data) else DetailScreen(vm, ref, data)
    } else {
        ListsScreen(vm)
    }
    ListActionSheet(vm)

    val rr = vm.renameRef
    if (rr != null) {
        val cur = vm.getList(rr)
        if (cur != null) {
            NameDialog("이름 변경", cur.name, { vm.renameRef = null }) {
                vm.renameList(rr, it)
                vm.renameRef = null
            }
        } else vm.renameRef = null
    }
    if (vm.newListDlg) {
        NewListDialog(kindChoice = false, defaultFolderKind = vm.libSeg == 1, onDismiss = { vm.newListDlg = false }) { name, _ ->
            vm.createList(name, vm.libSeg == 1)
            vm.newListDlg = false
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ListsScreen(vm: AppVM) {
    val folderKind = vm.libSeg == 1
    val lists = if (folderKind) vm.folderLists else vm.playlists
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().height(64.dp).padding(start = 20.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Txt("재생목록", 22, bold = true, modifier = Modifier.weight(1f))
            IconBtn(Ic.Plus, if (folderKind) "새 폴더 목록 만들기" else "새 재생목록 만들기", Accent, 24.dp) { vm.newListDlg = true }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 12.dp)
                .clip(RoundedCornerShape(24.dp)).background(Surface1).padding(2.dp)
        ) {
            listOf("재생목록", "폴더 목록").forEachIndexed { i, label ->
                val on = vm.libSeg == i
                Box(
                    Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(22.dp))
                        .background(if (on) Accent else Color.Transparent)
                        .clickable { vm.libSeg = i },
                    contentAlignment = Alignment.Center
                ) { Txt(label, 14, bold = on, medium = !on, color = if (on) OnAccent else Sub) }
            }
        }
        if (lists.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Txt(if (folderKind) "폴더 목록이 없어요. 위의 + 로 만들어 보세요" else "재생목록이 없어요. 위의 + 로 만들어 보세요", 14, color = Sub)
            }
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                items(lists, key = { it.id }) { l ->
                    val ref = ListRef(folderKind, l.id)
                    Row(
                        Modifier.fillMaxWidth().height(72.dp)
                            .combinedClickable(onClick = { vm.openList = ref }, onLongClick = { vm.actionRef = ref })
                            .padding(start = 20.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Tile(if (folderKind) Ic.Folder else Ic.Queue, 52.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Txt(l.name, 16, medium = true)
                            Txt(countText(l.items), 13, color = Sub)
                        }
                        IconBtn(Ic.Play, "재생", Accent, 24.dp) { vm.playList(ref) }
                    }
                }
                item(key = "cap") {
                    Txt("목록을 꾹 누르면 이름 변경·수정·삭제를 할 수 있어요", 12, color = Sub, modifier = Modifier.padding(20.dp))
                }
            }
        }
    }
}

@Composable
private fun DetailScreen(vm: AppVM, ref: ListRef, data: ListData) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Ic.ChevL, "뒤로", TextC, 24.dp) { vm.openList = null }
            Column(Modifier.weight(1f)) {
                Txt((if (ref.folderKind) "폴더 목록 · " else "재생목록 · ") + countText(data.items), 12, color = Sub)
                Txt(data.name, 20, bold = true)
            }
            IconBtn(Ic.Pencil, "목록 수정", Sub) { vm.editing = true }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Pill("전체 재생", true) { vm.playList(ref) }
            Pill("셔플", false, Ic.Shuffle) { vm.playList(ref, shuffleIt = true) }
            Spacer(Modifier.weight(1f))
            IconBtn(Ic.Plus, "폴더·곡 추가", Accent, 24.dp) { vm.startPicker(ref) }
        }
        if (data.items.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Txt("비어 있어요. 오른쪽 위 + 로 폴더·곡을 담아 보세요", 14, color = Sub)
            }
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                itemsIndexed(data.items, key = { _, it -> it.type + it.id }) { idx, item ->
                    Row(
                        Modifier.fillMaxWidth().height(64.dp).clickable { vm.playList(ref, start = idx) }
                            .padding(start = 20.dp, end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Tile(if (item.isFolder) Ic.Folder else Ic.Note, fg = if (item.isFolder) Accent else Sub)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Txt(if (item.isFolder) item.name.ifBlank { "폴더" } else titleOf(item.name), 15, medium = true)
                            Txt(if (item.isFolder) "폴더 · 안의 전체 곡" else (vm.artists[item.id] ?: ""), 13, color = Sub)
                        }
                    }
                }
                item(key = "cap") {
                    Txt("폴더를 재생하면 폴더 안의 곡이 모두 이어서 재생돼요", 12, color = Sub, modifier = Modifier.padding(20.dp))
                }
            }
        }
    }
}

@Composable
private fun EditScreen(vm: AppVM, ref: ListRef, data: ListData) {
    var name by remember(data.id) { mutableStateOf(data.name) }
    val items = remember(data.id) { mutableStateListOf<Item>().apply { addAll(data.items) } }
    var confirmDelete by remember { mutableStateOf(false) }

    fun save() = vm.updateList(ref, name.trim().ifBlank { data.name }, items.toList())

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().height(64.dp).padding(start = 20.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Txt("목록 수정", 22, bold = true, modifier = Modifier.weight(1f))
            TextButton(onClick = {
                save()
                vm.editing = false
            }) { Txt("완료", 15, bold = true, color = Accent) }
        }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) { FormField("이름", name, { name = it }) }
        Txt("항목 (꾹 눌러서 끌면 순서가 바뀌어요)", 13, medium = true, color = Sub, modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 4.dp))
        ReorderList(items, vm, Modifier.weight(1f).fillMaxWidth())
        Column(Modifier.padding(horizontal = 20.dp).padding(top = 8.dp)) {
            DashedButton("폴더·곡 추가") {
                save()
                vm.editing = false
                vm.startPicker(ref)
            }
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp)).clickable { confirmDelete = true },
                contentAlignment = Alignment.Center
            ) { Txt(if (ref.folderKind) "폴더 목록 삭제" else "재생목록 삭제", 15, medium = true, color = Danger) }
            Spacer(Modifier.height(8.dp))
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = Surface1,
            title = { Txt("삭제할까요?", 18, bold = true) },
            text = { Txt("'${data.name}' 목록만 지워지고, 드라이브의 파일은 그대로예요.", 14, color = Sub, maxLines = 3) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.deleteList(ref)
                }) { Txt("삭제", 15, bold = true, color = Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Txt("취소", 15, color = Sub) } }
        )
    }
}

/** 항목을 꾹 누른 뒤 끌어서 순서 변경. 오른쪽 ⊖ 는 목록에서 빼기 */
@Composable
private fun ReorderList(items: SnapshotStateList<Item>, vm: AppVM, modifier: Modifier) {
    val rowPx = with(LocalDensity.current) { 64.dp.toPx() }
    var dragKey by remember { mutableStateOf<String?>(null) }
    var dragY by remember { mutableFloatStateOf(0f) }

    LazyColumn(modifier) {
        itemsIndexed(items, key = { _, it -> it.type + it.id }) { index, item ->
            val k = item.type + item.id
            val dragging = dragKey == k
            Row(
                Modifier.fillMaxWidth().height(64.dp)
                    .zIndex(if (dragging) 1f else 0f)
                    .graphicsLayer { translationY = if (dragging) dragY else 0f }
                    .background(if (dragging) Surface2 else Color.Transparent)
                    .pointerInput(k) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                dragKey = k
                                dragY = 0f
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                dragY += amount.y
                                val i = items.indexOfFirst { it.type + it.id == k }
                                if (i >= 0) {
                                    if (dragY > rowPx / 2 && i < items.lastIndex) {
                                        items.add(i + 1, items.removeAt(i))
                                        dragY -= rowPx
                                    } else if (dragY < -rowPx / 2 && i > 0) {
                                        items.add(i - 1, items.removeAt(i))
                                        dragY += rowPx
                                    }
                                }
                            },
                            onDragEnd = {
                                dragKey = null
                                dragY = 0f
                            },
                            onDragCancel = {
                                dragKey = null
                                dragY = 0f
                            }
                        )
                    }
                    .padding(start = 20.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Tile(if (item.isFolder) Ic.Folder else Ic.Note, fg = if (item.isFolder) Accent else Sub)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Txt(if (item.isFolder) item.name.ifBlank { "폴더" } else titleOf(item.name), 15, medium = true)
                    Txt(if (item.isFolder) "폴더 · 안의 전체 곡" else (vm.artists[item.id] ?: ""), 13, color = Sub)
                }
                IconBtn(Ic.Minus, "목록에서 빼기", Danger) { items.removeAt(index) }
            }
        }
    }
}

/** 목록을 꾹 눌렀을 때 나오는 메뉴: 이름 변경 / 목록 수정 / 삭제 */
@Composable
private fun ListActionSheet(vm: AppVM) {
    val ref = vm.actionRef ?: return
    val data = vm.getList(ref)
    if (data == null) {
        vm.actionRef = null
        return
    }
    var confirm by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = { vm.actionRef = null },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Surface1
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Tile(if (ref.folderKind) Ic.Folder else Ic.Queue, 52.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Txt(data.name, 17, bold = true)
                    Txt((if (ref.folderKind) "폴더 목록 · " else "재생목록 · ") + countText(data.items), 13, color = Sub)
                }
            }
            MenuRow(Ic.Pencil, "이름 변경") {
                vm.actionRef = null
                vm.renameRef = ref
            }
            MenuRow(Ic.ListEdit, "목록 수정") {
                vm.actionRef = null
                vm.openList = ref
                vm.editing = true
            }
            MenuRow(Ic.Trash, if (ref.folderKind) "폴더 목록 삭제" else "재생목록 삭제", Danger) { confirm = true }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            containerColor = Surface1,
            title = { Txt("삭제할까요?", 18, bold = true) },
            text = { Txt("'${data.name}' 목록만 지워지고, 드라이브의 파일은 그대로예요.", 14, color = Sub, maxLines = 3) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    vm.actionRef = null
                    vm.deleteList(ref)
                }) { Txt("삭제", 15, bold = true, color = Danger) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Txt("취소", 15, color = Sub) } }
        )
    }
}
