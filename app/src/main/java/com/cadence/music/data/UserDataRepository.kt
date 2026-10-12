package com.cadence.music.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class UserData(
    val favorites: Set<Long> = emptySet(),
    val playlists: List<Playlist> = emptyList(),
    /** Most recent first. */
    val recentlyPlayed: List<Long> = emptyList(),
    val playCounts: Map<Long, Int> = emptyMap(),
)

data class SavedQueue(val trackIds: List<Long>, val index: Int, val positionMs: Long, val shuffle: Boolean, val repeatMode: Int)

/** Loved songs, playlists, listening history and the last queue, stored in the app's private files. */
class UserDataRepository(context: Context, private val scope: CoroutineScope) {

    private val file = File(context.filesDir, "userdata.json")
    private val queuePrefs = context.getSharedPreferences("queue", Context.MODE_PRIVATE)
    private val writeLock = Mutex()

    private val _state = MutableStateFlow(load())
    val state: StateFlow<UserData> = _state.asStateFlow()

    fun toggleFavorite(id: Long) = mutate {
        it.copy(favorites = if (id in it.favorites) it.favorites - id else it.favorites + id)
    }

    fun createPlaylist(name: String, initial: List<Long> = emptyList()): Long {
        val id = System.currentTimeMillis()
        mutate { it.copy(playlists = it.playlists + Playlist(id, name.trim().ifEmpty { "New Playlist" }, initial, id)) }
        return id
    }

    fun renamePlaylist(id: Long, name: String) = mutatePlaylist(id) { it.copy(name = name.trim().ifEmpty { it.name }) }

    fun deletePlaylist(id: Long) = mutate { d -> d.copy(playlists = d.playlists.filterNot { it.id == id }) }

    fun addToPlaylist(id: Long, trackIds: List<Long>) = mutatePlaylist(id) { it.copy(trackIds = it.trackIds + trackIds) }

    fun removeFromPlaylist(id: Long, position: Int) = mutatePlaylist(id) {
        if (position !in it.trackIds.indices) it
        else it.copy(trackIds = it.trackIds.toMutableList().apply { removeAt(position) })
    }

    fun movePlaylistItem(id: Long, from: Int, to: Int) = mutatePlaylist(id) {
        if (from !in it.trackIds.indices || to !in it.trackIds.indices) it
        else it.copy(trackIds = it.trackIds.toMutableList().apply { add(to, removeAt(from)) })
    }

    fun recordPlay(trackId: Long) = mutate {
        it.copy(
            recentlyPlayed = (listOf(trackId) + it.recentlyPlayed.filter { r -> r != trackId }).take(200),
            playCounts = it.playCounts + (trackId to ((it.playCounts[trackId] ?: 0) + 1)),
        )
    }

    fun clearHistory() = mutate { it.copy(recentlyPlayed = emptyList(), playCounts = emptyMap()) }

    fun saveQueue(q: SavedQueue) {
        queuePrefs.edit()
            .putString("ids", q.trackIds.joinToString(","))
            .putInt("index", q.index)
            .putLong("position", q.positionMs)
            .putBoolean("shuffle", q.shuffle)
            .putInt("repeat", q.repeatMode)
            .apply()
    }

    fun loadQueue(): SavedQueue? {
        val ids = queuePrefs.getString("ids", null)?.split(',')?.mapNotNull { it.toLongOrNull() }
        if (ids.isNullOrEmpty()) return null
        return SavedQueue(
            ids,
            queuePrefs.getInt("index", 0),
            queuePrefs.getLong("position", 0L),
            queuePrefs.getBoolean("shuffle", false),
            queuePrefs.getInt("repeat", 0),
        )
    }

    private fun mutatePlaylist(id: Long, f: (Playlist) -> Playlist) = mutate { d ->
        d.copy(playlists = d.playlists.map { if (it.id == id) f(it) else it })
    }

    private fun mutate(f: (UserData) -> UserData) {
        _state.update(f)
        scope.launch(Dispatchers.IO) {
            writeLock.withLock {
                // Always write the newest state, so out-of-order launches can't save stale data.
                runCatching {
                    val tmp = File(file.path + ".tmp")
                    tmp.writeText(toJson(_state.value).toString())
                    tmp.renameTo(file)
                }
            }
        }
    }

    private fun load(): UserData {
        if (!file.exists()) return UserData()
        return runCatching { parse(JSONObject(file.readText())) }.getOrDefault(UserData())
    }

    private fun parse(o: JSONObject): UserData =
        UserData(
            favorites = o.optJSONArray("favorites").longs().toSet(),
            playlists = o.optJSONArray("playlists")?.let { arr ->
                (0 until arr.length()).map { i ->
                    val p = arr.getJSONObject(i)
                    Playlist(p.getLong("id"), p.getString("name"), p.optJSONArray("tracks").longs(), p.optLong("created"))
                }
            }.orEmpty(),
            recentlyPlayed = o.optJSONArray("recent").longs(),
            playCounts = o.optJSONObject("counts")?.let { c ->
                c.keys().asSequence().mapNotNull { k -> k.toLongOrNull()?.let { it to c.getInt(k) } }.toMap()
            }.orEmpty(),
        )

    private fun toJson(d: UserData) = JSONObject().apply {
        put("favorites", JSONArray(d.favorites.toList()))
        put("playlists", JSONArray().apply {
            d.playlists.forEach { p ->
                put(JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("tracks", JSONArray(p.trackIds))
                    put("created", p.createdAt)
                })
            }
        })
        put("recent", JSONArray(d.recentlyPlayed))
        put("counts", JSONObject().apply { d.playCounts.forEach { (k, v) -> put(k.toString(), v) } })
    }

    private fun JSONArray?.longs(): List<Long> =
        if (this == null) emptyList() else (0 until length()).map { getLong(it) }
}
