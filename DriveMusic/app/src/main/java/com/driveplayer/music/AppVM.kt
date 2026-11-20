package com.driveplayer.music

import android.app.Application
import android.content.ComponentName
import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.random.Random

class AppVM(app: Application) : AndroidViewModel(app) {
    val store = Store(app)

    private fun isConfigured() = store.apiKey.isNotBlank() && parseFolderId(store.folderLink).isNotBlank()

    /** 폴더 링크와 API 키가 저장되어 있는지(처음 실행 안내용) */
    var configured by mutableStateOf(isConfigured())

    var repo: MusicRepo = makeRepo()
        private set

    private fun makeRepo(): MusicRepo {
        val app = getApplication<Application>()
        return DriveRepo(app, store.apiKey.trim(), parseFolderId(store.folderLink), AppSig.headers(app))
    }

    // ---------------------------------------------------------------- 탐색
    var tab by mutableIntStateOf(0)
    var stack by mutableStateOf(listOf<Node>())
    var nodes by mutableStateOf(listOf<Node>())
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var query by mutableStateOf("")
    var results by mutableStateOf<List<Node>?>(null)
    var searching by mutableStateOf(false)
    var showFav by mutableStateOf(false)
    val selected = mutableStateMapOf<String, Item>()
    var picker by mutableStateOf<ListRef?>(null)
    var menuNode by mutableStateOf<Node?>(null)
    var addPayload by mutableStateOf<List<Item>?>(null)
    var addMode by mutableIntStateOf(0)
    var busy by mutableStateOf(false)
    var toast by mutableStateOf<String?>(null)

    private var loadJob: Job? = null
    private var searchJob: Job? = null

    val shownNodes: List<Node>
        get() = when {
            query.isNotBlank() -> results ?: emptyList()
            showFav -> favorites.map { Node(it.id, it.name, true) }
            else -> nodes
        }

    private fun rootNode() = Node(repo.rootId, repo.rootName, true)

    fun reloadRoot() {
        val root = rootNode()
        stack = listOf(root)
        selected.clear()
        query = ""
        results = null
        showFav = false
        loadFolder(root.id)
    }

    fun reload() {
        loadFolder(stack.lastOrNull()?.id ?: repo.rootId)
    }

    private fun loadFolder(id: String) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            loading = true
            error = null
            try {
                nodes = repo.list(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                nodes = emptyList()
                error = e.message ?: "불러오지 못했어요"
            }
            loading = false
        }
    }

    fun openFolder(n: Node) {
        if (showFav || query.isNotBlank()) {
            stack = listOf(rootNode(), n)
            showFav = false
            query = ""
            results = null
        } else {
            stack = stack + n
        }
        loadFolder(n.id)
    }

    fun back() {
        if (stack.size > 1) {
            stack = stack.dropLast(1)
            loadFolder(stack.last().id)
        }
    }

    fun backBrowse() {
        when {
            query.isNotBlank() -> onQuery("")
            showFav -> showFav = false
            else -> back()
        }
    }

    fun onQuery(q: String) {
        query = q
        searchJob?.cancel()
        if (q.isBlank()) {
            results = null
            searching = false
            return
        }
        searchJob = viewModelScope.launch {
            delay(350)
            searching = true
            try {
                results = repo.search(q)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                results = emptyList()
                toast = e.message
            }
            searching = false
        }
    }

    fun toggleSelect(n: Node) {
        if (selected.containsKey(n.id)) selected.remove(n.id) else selected[n.id] = n.toItem()
    }

    fun selectAllFolders() {
        val folders = shownNodes.filter { it.isFolder }
        if (folders.isNotEmpty() && folders.all { selected.containsKey(it.id) }) {
            folders.forEach { selected.remove(it.id) }
        } else {
            folders.forEach { selected[it.id] = it.toItem() }
        }
    }

    // ---------------------------------------------------------------- 즐겨찾기 / 목록
    var favorites by mutableStateOf(store.loadItems("favs"))
    var playlists by mutableStateOf(store.loadLists("playlists"))
    var folderLists by mutableStateOf(store.loadLists("folderlists"))
    var libSeg by mutableIntStateOf(0)
    var openList by mutableStateOf<ListRef?>(null)
    var editing by mutableStateOf(false)
    var actionRef by mutableStateOf<ListRef?>(null)
    var renameRef by mutableStateOf<ListRef?>(null)
    var newListDlg by mutableStateOf(false)

    fun isFav(id: String) = favorites.any { it.id == id }

    fun toggleFav(n: Node) {
        favorites = if (isFav(n.id)) favorites.filterNot { it.id == n.id }
        else favorites + Item("folder", n.id, n.name)
        store.saveItems("favs", favorites)
    }

    private fun newId() = UUID.randomUUID().toString().take(8)

    fun getList(ref: ListRef): ListData? =
        (if (ref.folderKind) folderLists else playlists).firstOrNull { it.id == ref.id }

    private fun setLists(folderKind: Boolean, l: List<ListData>) {
        if (folderKind) {
            folderLists = l
            store.saveLists("folderlists", l)
        } else {
            playlists = l
            store.saveLists("playlists", l)
        }
    }

    fun createList(name: String, folderKind: Boolean, items: List<Item> = emptyList()): ListData {
        val d = ListData(newId(), name, items)
        setLists(folderKind, (if (folderKind) folderLists else playlists) + d)
        return d
    }

    fun renameList(ref: ListRef, name: String) {
        val src = if (ref.folderKind) folderLists else playlists
        setLists(ref.folderKind, src.map { if (it.id == ref.id) it.copy(name = name) else it })
    }

    fun deleteList(ref: ListRef) {
        val src = if (ref.folderKind) folderLists else playlists
        setLists(ref.folderKind, src.filterNot { it.id == ref.id })
        if (openList == ref) {
            openList = null
            editing = false
        }
    }

    fun updateList(ref: ListRef, name: String, items: List<Item>) {
        val src = if (ref.folderKind) folderLists else playlists
        setLists(ref.folderKind, src.map { if (it.id == ref.id) it.copy(name = name, items = items) else it })
    }

    fun addToList(ref: ListRef, items: List<Item>) {
        val l = getList(ref) ?: return
        val add = items.filter { i -> (!ref.folderKind || i.isFolder) && l.items.none { it.type == i.type && it.id == i.id } }
        updateList(ref, l.name, l.items + add)
    }

    fun openAdd(items: List<Item>, mode: Int) {
        menuNode = null
        addMode = mode
        addPayload = items
    }

    fun openAddForSelection() {
        val items = selected.values.toList()
        if (items.isEmpty()) return
        openAdd(items, if (items.all { it.isFolder }) 0 else 1)
    }

    /** 시트에서 체크한 대로 목록에 담거나 뺀다 */
    fun applyAdd(items: List<Item>, plChecked: Set<String>, flChecked: Set<String>) {
        fun has(l: ListData) = items.all { i -> l.items.any { it.type == i.type && it.id == i.id } }
        fun upd(src: List<ListData>, checked: Set<String>, onlyFolders: Boolean): List<ListData> = src.map { l ->
            val h = has(l)
            if (l.id in checked && !h) {
                val add = items.filter { i ->
                    (!onlyFolders || i.isFolder) && l.items.none { it.type == i.type && it.id == i.id }
                }
                l.copy(items = l.items + add)
            } else if (l.id !in checked && h) {
                l.copy(items = l.items.filterNot { x -> items.any { it.type == x.type && it.id == x.id } })
            } else l
        }
        setLists(false, upd(playlists, plChecked, false))
        setLists(true, upd(folderLists, flChecked, true))
        selected.clear()
        toast = "목록에 반영했어요"
    }

    fun startPicker(ref: ListRef) {
        picker = ref
        tab = 0
        selected.clear()
        query = ""
        results = null
        showFav = false
    }

    fun finishPicker() {
        val ref = picker ?: return
        if (selected.isEmpty()) {
            toast = "담을 항목을 선택하세요"
            return
        }
        addToList(ref, selected.values.toList())
        picker = null
        selected.clear()
        tab = 1
        toast = "목록에 담았어요"
    }

    fun cancelPicker() {
        picker = null
        selected.clear()
        tab = 1
    }

    // ---------------------------------------------------------------- 목록 내보내기 / 가져오기
    fun exportJson(): String = store.exportAll(playlists, folderLists, favorites)

    /** 같은 목록(같은 ID)은 가져온 것으로 바뀌고, 나머지는 그대로 두고 추가한다 */
    fun importAll(text: String) {
        val r = store.parseAll(text)
        if (r == null) {
            toast = "가져올 수 없는 파일이에요"
            return
        }
        fun merge(cur: List<ListData>, inc: List<ListData>): List<ListData> {
            val ids = inc.map { it.id }.toSet()
            return cur.filter { it.id !in ids } + inc
        }
        setLists(false, merge(playlists, r.first))
        setLists(true, merge(folderLists, r.second))
        favorites = favorites + r.third.filter { f -> favorites.none { it.id == f.id } }
        store.saveItems("favs", favorites)
        toast = "재생목록 ${r.first.size}개, 폴더 목록 ${r.second.size}개를 가져왔어요"
    }

    // ---------------------------------------------------------------- 가수(ID3)
    val artists = mutableStateMapOf<String, String>()
    private val artistSem = Semaphore(3)
    private val artistTried = HashSet<String>()
    private var artistDirty = 0

    fun loadArtist(id: String) {
        if (artists.containsKey(id) || !artistTried.add(id)) return
        viewModelScope.launch {
            val a = artistSem.withPermit {
                try { repo.artist(id) } catch (e: Exception) { null }
            }
            if (!a.isNullOrBlank()) {
                artists[id] = a
                artistDirty++
                if (artistDirty % 10 == 0) store.saveArtists(artists.toMap())
            }
        }
    }

    // ---------------------------------------------------------------- 재생
    var queue by mutableStateOf(listOf<Song>())
    var curIndex by mutableIntStateOf(0)
    var isPlaying by mutableStateOf(false)
    var posMs by mutableLongStateOf(0L)
    var durMs by mutableLongStateOf(0L)
    var repeat by mutableIntStateOf(Player.REPEAT_MODE_OFF)
    var shuffle by mutableStateOf(false)
    var expanded by mutableStateOf(false)
    var cacheUsed by mutableLongStateOf(0L)

    /** 취침·수업 타이머: 끝나는 시각(0이면 꺼짐)과 남은 시간 */
    var timerEndMs by mutableLongStateOf(0L)
    var timerLeftMs by mutableLongStateOf(0L)

    val current: Song? get() = queue.getOrNull(curIndex)

    private var controller: MediaController? = null
    private var connecting = false
    private val pending = ArrayList<(MediaController) -> Unit>()

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            this@AppVM.isPlaying = isPlaying
            if (!isPlaying) {
                controller?.let { store.saveLastPos(it.currentMediaItemIndex, it.currentPosition) }
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            controller?.let {
                curIndex = it.currentMediaItemIndex
                posMs = 0L
                if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                    store.saveLastPos(it.currentMediaItemIndex, 0L)
                }
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            controller?.let { durMs = it.duration.coerceAtLeast(0L) }
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            this@AppVM.repeat = repeatMode
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            this@AppVM.shuffle = shuffleModeEnabled
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            val a = mediaMetadata.artist?.toString()
            val id = controller?.currentMediaItem?.mediaId
            if (!a.isNullOrBlank() && !id.isNullOrBlank()) artists[id] = a
        }

        override fun onPlayerError(error: PlaybackException) {
            toast = "재생할 수 없는 곡은 건너뛰어요"
        }
    }

    private fun buildItems(list: List<Song>): List<MediaItem> {
        val r = repo
        return list.map { s ->
            MediaItem.Builder()
                .setMediaId(s.id)
                .setUri(r.streamUri(s.id))
                .setCustomCacheKey(s.id)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(titleOf(s.name))
                        .setArtist(artists[s.id])
                        .build()
                )
                .build()
        }
    }

    private fun syncFrom(c: MediaController) {
        isPlaying = c.isPlaying
        repeat = c.repeatMode
        shuffle = c.shuffleModeEnabled
        if (c.mediaItemCount > 0) {
            queue = (0 until c.mediaItemCount).map {
                val m = c.getMediaItemAt(it)
                Song(m.mediaId, m.mediaMetadata.title?.toString() ?: "")
            }
            curIndex = c.currentMediaItemIndex
            posMs = c.currentPosition
            durMs = c.duration.coerceAtLeast(0L)
        } else {
            restoreLast(c)
        }
    }

    /** 앱이 꺼졌다 켜져도 마지막에 듣던 목록·곡·위치를 다시 불러온다(재생 버튼을 누르기 전까진 데이터를 쓰지 않음) */
    private fun restoreLast(c: MediaController) {
        if (!configured) return
        val q = store.loadLastQueue()
        if (q.isEmpty()) return
        val idx = store.lastIndex.coerceIn(0, q.lastIndex)
        val pos = store.lastPos
        c.setMediaItems(buildItems(q), idx, pos)
        queue = q
        curIndex = idx
        posMs = pos
    }

    private fun withController(block: (MediaController) -> Unit) {
        val c = controller
        if (c != null && c.isConnected) {
            block(c)
            return
        }
        pending.add(block)
        if (connecting) return
        connecting = true
        val app = getApplication<Application>()
        val token = SessionToken(app, ComponentName(app, PlayerService::class.java))
        val f = MediaController.Builder(app, token)
            .setListener(object : MediaController.Listener {
                override fun onDisconnected(controller: MediaController) {
                    this@AppVM.controller = null
                    queue = emptyList()
                    isPlaying = false
                    expanded = false
                    timerEndMs = 0L
                }

                override fun onExtrasChanged(controller: MediaController, extras: Bundle) {
                    timerEndMs = extras.getLong("timerEnd", 0L)
                }
            })
            .buildAsync()
        f.addListener({
            connecting = false
            try {
                val mc = f.get()
                mc.addListener(playerListener)
                controller = mc
                timerEndMs = mc.sessionExtras.getLong("timerEnd", 0L)
                syncFrom(mc)
                val run = ArrayList(pending)
                pending.clear()
                run.forEach { it(mc) }
            } catch (e: Exception) {
                pending.clear()
                toast = "플레이어에 연결하지 못했어요"
            }
        }, ContextCompat.getMainExecutor(app))
    }

    /** shuffleMode=true면 목록 순서는 그대로 두고 재생 순서만 섞는다(플레이어의 셔플 기능) */
    fun playSongs(list: List<Song>, start: Int = 0, shuffleMode: Boolean = false) {
        if (list.isEmpty()) {
            toast = "재생할 곡이 없어요"
            return
        }
        val s0 = start.coerceIn(0, list.lastIndex)
        withController { c ->
            c.setMediaItems(buildItems(list), s0, 0L)
            c.shuffleModeEnabled = shuffleMode
            c.prepare()
            c.play()
            queue = list
            curIndex = s0
            posMs = 0L
            store.saveLastQueue(list)
            store.saveLastPos(s0, 0L)
        }
    }

    fun playFromList(list: List<Node>, tapped: Node) {
        val songs = list.filter { !it.isFolder }.map { Song(it.id, it.name) }
        playSongs(songs, songs.indexOfFirst { it.id == tapped.id }.coerceAtLeast(0))
    }

    fun playFolders(ids: List<String>, shuffleIt: Boolean = false) =
        playItems(ids.map { Item("folder", it, "") }, shuffleIt)

    fun playSelected() {
        val items = selected.values.toList()
        selected.clear()
        playItems(items, false)
    }

    fun playList(ref: ListRef, shuffleIt: Boolean = false, start: Int = 0) {
        val l = getList(ref) ?: return
        if (l.items.isEmpty()) {
            toast = "목록이 비어 있어요"
            return
        }
        playItems(l.items, shuffleIt, start)
    }

    /** 폴더는 안의 모든 곡(하위 폴더 포함)으로 펼쳐서 하나의 재생 목록으로 합친다 */
    fun playItems(items: List<Item>, shuffleIt: Boolean, startItem: Int = 0) {
        viewModelScope.launch {
            busy = true
            try {
                val songs = ArrayList<Song>()
                var startIdx = 0
                items.forEachIndexed { idx, item ->
                    if (idx == startItem) startIdx = songs.size
                    if (item.type == "song") songs.add(Song(item.id, item.name))
                    else songs.addAll(repo.songsIn(item.id))
                }
                if (shuffleIt && songs.isNotEmpty()) playSongs(songs, Random.nextInt(songs.size), true)
                else playSongs(songs, startIdx, false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toast = e.message ?: "재생 목록을 만들지 못했어요"
            }
            busy = false
        }
    }

    fun playPause() = withController {
        if (it.isPlaying) {
            it.pause()
        } else {
            if (it.playbackState == Player.STATE_IDLE) it.prepare()
            it.play()
        }
    }

    fun next() = withController { it.seekToNextMediaItem() }
    fun prev() = withController { it.seekToPrevious() }
    fun restart() = withController { it.seekTo(0L) }

    fun seekToIndex(i: Int) = withController {
        if (it.playbackState == Player.STATE_IDLE) it.prepare()
        it.seekToDefaultPosition(i)
        it.play()
    }

    fun seekFrac(f: Float) = withController {
        val d = it.duration
        if (d > 0) {
            it.seekTo((d * f).toLong())
            posMs = (d * f).toLong()
        }
    }

    fun toggleRepeat(mode: Int) = withController {
        it.repeatMode = if (it.repeatMode == mode) Player.REPEAT_MODE_OFF else mode
    }

    fun toggleShuffle() = withController { it.shuffleModeEnabled = !it.shuffleModeEnabled }

    /** 0이면 타이머 끄기. 타이머는 재생 서비스에서 돌아서 화면을 꺼도 동작한다 */
    fun setSleepTimer(min: Int) {
        timerEndMs = if (min > 0) System.currentTimeMillis() + min * 60_000L else 0L
        withController { c ->
            c.sendCustomCommand(
                SessionCommand(PlayerService.CMD_TIMER, Bundle.EMPTY),
                Bundle().apply { putInt("min", min) }
            )
        }
        toast = if (min > 0) "${min}분 뒤에 멈춰요" else "타이머를 껐어요"
    }

    // ---------------------------------------------------------------- 설정 / 캐시
    fun applySettings(link: String, key: String) {
        if (key.isBlank() || parseFolderId(link).isBlank()) {
            toast = "폴더 링크와 API 키를 입력해 주세요"
            return
        }
        store.folderLink = link.trim()
        store.apiKey = key.trim()
        configured = true
        repo = makeRepo()
        reloadRoot()
        tab = 0
        toast = "폴더를 불러오는 중이에요"
    }

    fun refreshLibrary() {
        repo.refresh()
        reloadRoot()
        toast = "새로 불러왔어요"
    }

    fun refreshCache() {
        viewModelScope.launch {
            val u = withContext(Dispatchers.IO) { CacheManager.used(getApplication<Application>()) }
            cacheUsed = u
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { CacheManager.clear(getApplication<Application>()) }
            refreshCache()
            toast = "저장된 곡을 비웠어요"
        }
    }

    override fun onCleared() {
        store.saveArtists(artists.toMap())
        controller?.let { store.saveLastPos(it.currentMediaItemIndex, it.currentPosition) }
        super.onCleared()
    }

    init {
        artists.putAll(store.loadArtists())
        viewModelScope.launch(Dispatchers.IO) { CacheManager.purgeOld(getApplication<Application>()) }
        withController { }
        reloadRoot()
        // 처음 실행(링크·키 없음)이면 설정 탭부터 연다
        if (!configured) tab = 2
        viewModelScope.launch {
            var tick = 0
            while (isActive) {
                delay(500)
                tick++
                val c = controller
                if (c != null && c.isConnected && isPlaying) {
                    posMs = c.currentPosition
                    durMs = c.duration.coerceAtLeast(0L)
                    if (tick % 10 == 0) store.saveLastPos(c.currentMediaItemIndex, c.currentPosition)
                }
                timerLeftMs = if (timerEndMs > 0L) (timerEndMs - System.currentTimeMillis()).coerceAtLeast(0L) else 0L
                if (timerEndMs > 0L && timerLeftMs == 0L) timerEndMs = 0L
            }
        }
    }
}
