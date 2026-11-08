package com.driveplayer.music

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun BrowseScreen(vm: AppVM) {
    val picking = vm.picker != null
    val folderOnly = vm.picker?.folderKind == true
    val list = vm.shownNodes
    val title = when {
        vm.query.isNotBlank() -> "검색 결과"
        vm.showFav -> "즐겨찾기"
        else -> vm.stack.lastOrNull()?.name ?: ""
    }
    val base = "구글 드라이브"
    val crumb = when {
        picking -> if (folderOnly) "목록에 담을 폴더를 선택하세요" else "목록에 담을 폴더·곡을 선택하세요"
        vm.query.isNotBlank() || vm.showFav -> base
        vm.stack.size > 1 -> vm.stack.dropLast(1).joinToString(" › ") { it.name }
        else -> base
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (vm.stack.size > 1 && vm.query.isBlank() && !vm.showFav) {
                IconBtn(Ic.ChevL, "상위 폴더로", TextC, 24.dp) { vm.back() }
            } else {
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Txt(crumb, 12, color = Sub)
                Txt(title, 20, bold = true)
            }
        }
        SearchField(vm.query, { vm.onQuery(it) }, "곡·폴더 검색", Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
        Row(Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("전체 폴더", filled = !vm.showFav) { vm.showFav = false }
            Pill("즐겨찾기", filled = vm.showFav, icon = Ic.Star) { vm.showFav = true }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                vm.searching -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Accent)
                    Spacer(Modifier.height(12.dp))
                    Txt("검색 목록을 만드는 중이에요 (처음 한 번만)", 13, color = Sub)
                }
                vm.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Accent)
                vm.error != null && vm.query.isBlank() && !vm.showFav -> Column(
                    Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Txt(vm.error ?: "", 14, color = Danger, maxLines = 6)
                    Spacer(Modifier.height(16.dp))
                    Pill("다시 시도", false) { vm.reload() }
                }
                list.isEmpty() -> Txt(
                    if (vm.showFav) "즐겨찾기한 폴더가 없어요" else if (vm.query.isNotBlank()) "검색 결과가 없어요" else "비어 있어요",
                    14, color = Sub, modifier = Modifier.align(Alignment.Center)
                )
                else -> {
                    val folders = list.filter { it.isFolder }
                    val songs = list.filter { !it.isFolder }
                    LazyColumn(Modifier.fillMaxSize()) {
                        if (folders.isNotEmpty()) {
                            item(key = "hf") {
                                Row(
                                    Modifier.fillMaxWidth().height(32.dp).padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Txt("폴더", 13, medium = true, color = Sub, modifier = Modifier.weight(1f))
                                    Txt("전체 선택", 13, medium = true, color = Accent, modifier = Modifier.clickable { vm.selectAllFolders() })
                                }
                            }
                            items(folders, key = { "f" + it.id }) { n -> FolderRow(vm, n, picking) }
                        }
                        if (songs.isNotEmpty()) {
                            item(key = "hs") {
                                Row(Modifier.fillMaxWidth().height(36.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Txt("곡", 13, medium = true, color = Sub)
                                }
                            }
                            items(songs, key = { "s" + it.id }) { n -> SongRow(vm, n, list, picking, folderOnly) }
                        }
                    }
                }
            }
        }

        SelectionBar(vm, picking)
    }
}

@Composable
private fun FolderRow(vm: AppVM, n: Node, picking: Boolean) {
    val sel = vm.selected.containsKey(n.id)
    val fav = vm.isFav(n.id)
    Row(
        Modifier.fillMaxWidth().height(56.dp)
            .background(if (sel) Surface1 else Color.Transparent)
            .clickable { vm.openFolder(n) }
            .padding(start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CheckBox(sel) { vm.toggleSelect(n) }
        Icon(Ic.Folder, null, tint = Sub, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Txt(n.name, 16, modifier = Modifier.weight(1f))
        if (!picking) {
            IconBtn(if (fav) Ic.StarF else Ic.Star, if (fav) "즐겨찾기 해제" else "즐겨찾기에 추가", if (fav) Accent else Sub) { vm.toggleFav(n) }
            IconBtn(Ic.More, "메뉴", Sub) { vm.menuNode = n }
        }
    }
}

@Composable
private fun SongRow(vm: AppVM, n: Node, all: List<Node>, picking: Boolean, folderOnly: Boolean) {
    LaunchedEffect(n.id) { vm.loadArtist(n.id) }
    val playingNow = vm.current?.id == n.id
    val sel = vm.selected.containsKey(n.id)
    Row(
        Modifier.fillMaxWidth().height(60.dp)
            .background(if (sel) Surface1 else Color.Transparent)
            .clickable { if (picking) { if (!folderOnly) vm.toggleSelect(n) } else vm.playFromList(all, n) }
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Tile(Ic.Note, fg = if (playingNow) Accent else Sub)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Txt(titleOf(n.name), 15, medium = true, color = if (playingNow) Accent else TextC)
            Txt(vm.artists[n.id] ?: "", 13, color = Sub)
        }
        if (picking) {
            if (!folderOnly) CheckBox(sel) { vm.toggleSelect(n) }
        } else {
            IconBtn(Ic.More, "메뉴", Sub) { vm.menuNode = n }
        }
    }
}

@Composable
private fun SelectionBar(vm: AppVM, picking: Boolean) {
    val n = vm.selected.size
    if (n == 0 && !picking) return
    Row(
        Modifier.fillMaxWidth().height(60.dp).background(Surface2).padding(start = 4.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBtn(Ic.Close, "선택 해제", TextC, 18.dp) { vm.selected.clear() }
        Txt("${n}개 선택됨", 14, modifier = Modifier.weight(1f))
        if (picking) {
            Pill("취소", false) { vm.cancelPicker() }
            Spacer(Modifier.width(8.dp))
            Pill("담기", true) { vm.finishPicker() }
        } else {
            Pill("목록에 담기", false) { vm.openAddForSelection() }
            Spacer(Modifier.width(8.dp))
            Pill("재생", true) { vm.playSelected() }
        }
    }
}
