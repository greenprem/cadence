package com.cadence.music.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore by preferencesDataStore(name = "settings")

enum class ThemeMode(val label: String) { SYSTEM("Automatic"), LIGHT("Light"), DARK("Dark") }

enum class SongSort(val label: String) {
    TITLE("Title"), ARTIST("Artist"), ALBUM("Album"), DATE_ADDED("Recently Added"), DURATION("Duration")
}

enum class AlbumSort(val label: String) {
    TITLE("Title"), ARTIST("Artist"), YEAR("Year"), DATE_ADDED("Recently Added")
}

/** 0 = never use FFmpeg, 1 = only when the device has no decoder, 2 = always prefer FFmpeg. */
enum class DecoderMode(val label: String, val description: String) {
    PLATFORM_ONLY("Device only", "Use only the phone's built-in decoders."),
    FALLBACK("Automatic", "Use FFmpeg when the phone can't decode a file (for example ALAC)."),
    PREFER_FFMPEG("Prefer FFmpeg", "Decode everything with FFmpeg for consistent output."),
}

data class AppSettings(
    val includedFolders: Set<String> = emptySet(),
    val useAllFolders: Boolean = false,
    val foldersConfigured: Boolean = false,
    val minDurationSec: Int = 30,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accentIndex: Int = 0,
    val showQualityBadges: Boolean = true,
    val dynamicBackground: Boolean = true,
    val shrinkArtworkWhenPaused: Boolean = true,
    val showVolumeBar: Boolean = true,
    val onlineLyrics: Boolean = true,
    val skipSilence: Boolean = false,
    val pauseOnDisconnect: Boolean = true,
    val handleAudioFocus: Boolean = true,
    val resumeQueue: Boolean = true,
    val hiResOutput: Boolean = true,
    val audioOffload: Boolean = false,
    val decoderMode: DecoderMode = DecoderMode.FALLBACK,
    val songSort: SongSort = SongSort.TITLE,
    val albumSort: AlbumSort = AlbumSort.TITLE,
)

object SettingsKeys {
    val FOLDERS = stringSetPreferencesKey("included_folders")
    val USE_ALL = booleanPreferencesKey("use_all_folders")
    val CONFIGURED = booleanPreferencesKey("folders_configured")
    val MIN_DURATION = intPreferencesKey("min_duration_sec")
    val THEME = stringPreferencesKey("theme_mode")
    val ACCENT = intPreferencesKey("accent_index")
    val BADGES = booleanPreferencesKey("show_quality_badges")
    val DYNAMIC_BG = booleanPreferencesKey("dynamic_background")
    val SHRINK_ART = booleanPreferencesKey("shrink_artwork")
    val SHOW_VOLUME = booleanPreferencesKey("show_volume_bar")
    val ONLINE_LYRICS = booleanPreferencesKey("online_lyrics")
    val SKIP_SILENCE = booleanPreferencesKey("skip_silence")
    val PAUSE_ON_DISCONNECT = booleanPreferencesKey("pause_on_disconnect")
    val AUDIO_FOCUS = booleanPreferencesKey("audio_focus")
    val RESUME_QUEUE = booleanPreferencesKey("resume_queue")
    val HI_RES = booleanPreferencesKey("hi_res_output")
    val OFFLOAD = booleanPreferencesKey("audio_offload")
    val DECODER = stringPreferencesKey("decoder_mode")
    val SONG_SORT = stringPreferencesKey("song_sort")
    val ALBUM_SORT = stringPreferencesKey("album_sort")
}

private inline fun <reified T : Enum<T>> Preferences.enumOf(key: Preferences.Key<String>, default: T): T =
    this[key]?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: default

class SettingsRepository(private val context: Context) {

    val settings: Flow<AppSettings> = context.dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p ->
            val d = AppSettings()
            AppSettings(
                includedFolders = p[SettingsKeys.FOLDERS] ?: d.includedFolders,
                useAllFolders = p[SettingsKeys.USE_ALL] ?: d.useAllFolders,
                foldersConfigured = p[SettingsKeys.CONFIGURED] ?: d.foldersConfigured,
                minDurationSec = p[SettingsKeys.MIN_DURATION] ?: d.minDurationSec,
                themeMode = p.enumOf(SettingsKeys.THEME, d.themeMode),
                accentIndex = p[SettingsKeys.ACCENT] ?: d.accentIndex,
                showQualityBadges = p[SettingsKeys.BADGES] ?: d.showQualityBadges,
                dynamicBackground = p[SettingsKeys.DYNAMIC_BG] ?: d.dynamicBackground,
                shrinkArtworkWhenPaused = p[SettingsKeys.SHRINK_ART] ?: d.shrinkArtworkWhenPaused,
                showVolumeBar = p[SettingsKeys.SHOW_VOLUME] ?: d.showVolumeBar,
                onlineLyrics = p[SettingsKeys.ONLINE_LYRICS] ?: d.onlineLyrics,
                skipSilence = p[SettingsKeys.SKIP_SILENCE] ?: d.skipSilence,
                pauseOnDisconnect = p[SettingsKeys.PAUSE_ON_DISCONNECT] ?: d.pauseOnDisconnect,
                handleAudioFocus = p[SettingsKeys.AUDIO_FOCUS] ?: d.handleAudioFocus,
                resumeQueue = p[SettingsKeys.RESUME_QUEUE] ?: d.resumeQueue,
                hiResOutput = p[SettingsKeys.HI_RES] ?: d.hiResOutput,
                audioOffload = p[SettingsKeys.OFFLOAD] ?: d.audioOffload,
                decoderMode = p.enumOf(SettingsKeys.DECODER, d.decoderMode),
                songSort = p.enumOf(SettingsKeys.SONG_SORT, d.songSort),
                albumSort = p.enumOf(SettingsKeys.ALBUM_SORT, d.albumSort),
            )
        }
        .distinctUntilChanged()

    suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        context.dataStore.edit { it[key] = value }
    }

    suspend fun setFolders(folders: Set<String>, useAll: Boolean) {
        context.dataStore.edit {
            it[SettingsKeys.FOLDERS] = folders
            it[SettingsKeys.USE_ALL] = useAll
            it[SettingsKeys.CONFIGURED] = true
        }
    }
}
