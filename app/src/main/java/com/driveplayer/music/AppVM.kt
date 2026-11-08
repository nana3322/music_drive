package com.driveplayer.music

import android.app.Application
import android.content.ComponentName
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
import androidx.media3.common.Player
import androidx.media3.session.MediaController
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

class AppVM(app: Application) : AndroidViewModel(app) {
    val store = Store(app)
    var repo: MusicRepo = makeRepo()
        private set

    private fun makeRepo(): MusicRepo =
        DriveRepo(store.apiKey.trim(), parseFolderId(store.folderLink))

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

    val current: Song? get() = queue.getOrNull(curIndex)

    private var controller: MediaController? = null
    private var connecting = false
    private val pending = ArrayList<(MediaController) -> Unit>()

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            this@AppVM.isPlaying = isPlaying
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            controller?.let {
                curIndex = it.currentMediaItemIndex
                posMs = 0L
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
        }
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
                }
            })
            .buildAsync()
        f.addListener({
            connecting = false
            try {
                val mc = f.get()
                mc.addListener(playerListener)
                controller = mc
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

    fun playSongs(list: List<Song>, start: Int = 0) {
        if (list.isEmpty()) {
            toast = "재생할 곡이 없어요"
            return
        }
        val r = repo
        val s0 = start.coerceIn(0, list.lastIndex)
        withController { c ->
            val items = list.map { s ->
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
            c.setMediaItems(items, s0, 0L)
            c.prepare()
            c.play()
            queue = list
            curIndex = s0
            posMs = 0L
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
                if (shuffleIt) playSongs(songs.shuffled(), 0) else playSongs(songs, startIdx)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toast = e.message ?: "재생 목록을 만들지 못했어요"
            }
            busy = false
        }
    }

    fun playPause() = withController { if (it.isPlaying) it.pause() else it.play() }
    fun next() = withController { it.seekToNextMediaItem() }
    fun prev() = withController { it.seekToPrevious() }
    fun restart() = withController { it.seekTo(0L) }
    fun seekToIndex(i: Int) = withController {
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

    // ---------------------------------------------------------------- 설정 / 캐시
    fun applySettings(link: String, key: String) {
        if (key.isBlank() || parseFolderId(link).isBlank()) {
            toast = "폴더 링크와 API 키를 입력해 주세요"
            return
        }
        store.folderLink = link.trim()
        store.apiKey = key.trim()
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
        super.onCleared()
    }

    init {
        artists.putAll(store.loadArtists())
        viewModelScope.launch(Dispatchers.IO) { CacheManager.purgeOld(getApplication<Application>()) }
        withController { }
        reloadRoot()
        viewModelScope.launch {
            while (isActive) {
                delay(500)
                val c = controller
                if (c != null && c.isConnected && isPlaying) {
                    posMs = c.currentPosition
                    durMs = c.duration.coerceAtLeast(0L)
                }
            }
        }
    }
}
