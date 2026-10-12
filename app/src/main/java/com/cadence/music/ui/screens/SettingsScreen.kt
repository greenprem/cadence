package com.cadence.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PhotoSizeSelectLarge
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cadence.music.BuildConfig
import com.cadence.music.data.AppSettings
import com.cadence.music.data.DecoderMode
import com.cadence.music.data.SettingsKeys
import com.cadence.music.data.ThemeMode
import com.cadence.music.ui.MainViewModel
import com.cadence.music.ui.Nav
import com.cadence.music.ui.Routes
import com.cadence.music.ui.components.ScreenList
import com.cadence.music.ui.nowplaying.openEqualizer
import com.cadence.music.ui.theme.AccentColors
import com.cadence.music.ui.theme.AccentNames
import com.cadence.music.ui.theme.Cadence

private val Red = Color(0xFFFF3B30)
private val Orange = Color(0xFFFF9500)
private val Green = Color(0xFF34C759)
private val Blue = Color(0xFF007AFF)
private val Indigo = Color(0xFF5856D6)
private val Purple = Color(0xFFAF52DE)
private val Gray = Color(0xFF8E8E93)
private val Teal = Color(0xFF30B0C7)
private val Pink = Color(0xFFFF2D55)

@Composable
fun SettingsScreen(settings: AppSettings, vm: MainViewModel, trackCount: Int, scanning: Boolean, nav: Nav) {
    val context = LocalContext.current
    var dialog by remember { mutableStateOf<String?>(null) }

    ScreenList("Settings", background = Cadence.colors.groupedBackground) {
        item {
            SettingsGroup(
                "Library",
                footer = "Only audio inside your music folders shows up, so voice notes, ringtones and app sounds stay out of your library.",
            ) {
                SettingRow(
                    "Music Folders", icon = Icons.Rounded.Folder, iconColor = Blue,
                    value = when {
                        settings.useAllFolders -> "All"
                        settings.includedFolders.size == 1 -> "1 folder"
                        else -> "${settings.includedFolders.size} folders"
                    },
                    onClick = { nav.go(Routes.MUSIC_FOLDERS) },
                )
                SettingRow(
                    "Skip Short Audio", icon = Icons.Rounded.FilterAlt, iconColor = Orange,
                    value = minDurationLabel(settings.minDurationSec),
                    onClick = { dialog = "duration" },
                )
                SettingRow(
                    "Rescan Library", icon = Icons.Rounded.Refresh, iconColor = Green,
                    value = if (scanning) "Scanning…" else "$trackCount songs",
                    chevron = false, divider = false,
                    onClick = { vm.rescan() },
                )
            }
        }

        item {
            SettingsGroup("Playback") {
                SwitchRow(
                    "Pause When Headphones Disconnect", settings.pauseOnDisconnect, Icons.Rounded.Headphones, Red,
                ) { vm.set(SettingsKeys.PAUSE_ON_DISCONNECT, it) }
                SwitchRow(
                    "Pause for Other Audio", settings.handleAudioFocus, Icons.Rounded.VolumeOff, Orange,
                    subtitle = "Pause or duck during calls, navigation and other apps.",
                ) { vm.set(SettingsKeys.AUDIO_FOCUS, it) }
                SwitchRow(
                    "Skip Silence", settings.skipSilence && !settings.hiResOutput, Icons.Rounded.SkipNext, Teal,
                    subtitle = if (settings.hiResOutput) "Unavailable while Hi-Res Output is on." else "Trim silent gaps between and inside songs.",
                    enabled = !settings.hiResOutput,
                ) { vm.set(SettingsKeys.SKIP_SILENCE, it) }
                SwitchRow(
                    "Restore Queue on Launch", settings.resumeQueue, Icons.Rounded.History, Indigo,
                ) { vm.set(SettingsKeys.RESUME_QUEUE, it) }
                SettingRow(
                    "Equalizer", icon = Icons.Rounded.Tune, iconColor = Gray, divider = false,
                    onClick = { openEqualizer(context) },
                )
            }
        }

        item {
            SettingsGroup(
                "Audio Quality",
                footer = "Hi-Res Output and Decoder changes apply once playback fully stops (pause, then swipe Cadence away from Recents).",
            ) {
                SwitchRow(
                    "Hi-Res Output", settings.hiResOutput, Icons.Rounded.HighQuality, Red,
                    subtitle = "Plays 24-bit FLAC and ALAC at full resolution using 32-bit float output instead of reducing to 16-bit.",
                ) { vm.set(SettingsKeys.HI_RES, it) }
                SettingRow(
                    "Decoder", icon = Icons.Rounded.Memory, iconColor = Purple,
                    value = settings.decoderMode.label,
                    subtitle = settings.decoderMode.description,
                    onClick = { dialog = "decoder" },
                )
                SwitchRow(
                    "Audio Offload", settings.audioOffload, Icons.Rounded.BatterySaver, Green,
                    subtitle = "Lets the phone's audio chip decode for longer battery life. Some phones lose gapless playback or effects with it.",
                ) { vm.set(SettingsKeys.OFFLOAD, it) }
                SwitchRow(
                    "Show Quality Badges", settings.showQualityBadges, Icons.Rounded.GraphicEq, Blue,
                    subtitle = "Lossless, Hi-Res Lossless and bitrate on Now Playing.",
                    divider = false,
                ) { vm.set(SettingsKeys.BADGES, it) }
            }
        }

        item {
            SettingsGroup("Appearance") {
                SettingRow(
                    "Theme", icon = Icons.Rounded.DarkMode, iconColor = Indigo,
                    value = settings.themeMode.label,
                    onClick = { dialog = "theme" },
                )
                AccentRow(settings.accentIndex) { vm.set(SettingsKeys.ACCENT, it) }
                SwitchRow(
                    "Color-Matched Now Playing", settings.dynamicBackground, Icons.Rounded.Wallpaper, Teal,
                    subtitle = "Tint Now Playing with colors from the artwork.",
                ) { vm.set(SettingsKeys.DYNAMIC_BG, it) }
                SwitchRow(
                    "Shrink Artwork When Paused", settings.shrinkArtworkWhenPaused, Icons.Rounded.PhotoSizeSelectLarge, Orange,
                ) { vm.set(SettingsKeys.SHRINK_ART, it) }
                SwitchRow(
                    "Show Volume Bar", settings.showVolumeBar, Icons.Rounded.VolumeUp, Blue,
                    subtitle = "The volume slider on Now Playing.",
                ) { vm.set(SettingsKeys.SHOW_VOLUME, it) }
                SwitchRow(
                    "Find Lyrics Online", settings.onlineLyrics, Icons.Rounded.FormatQuote, Pink,
                    subtitle = "When a song has no synced lyrics in its tags, look them up on LRCLIB (lrclib.net). Only the artist, title, album and length are sent.",
                    divider = false,
                ) { vm.set(SettingsKeys.ONLINE_LYRICS, it) }
            }
        }

        item {
            SettingsGroup("Privacy") {
                SettingRow(
                    "Clear Listening History", icon = Icons.Rounded.DeleteSweep, iconColor = Red,
                    subtitle = "Resets Recently Played and Top Songs.",
                    chevron = false, divider = false,
                    onClick = { dialog = "history" },
                )
            }
        }

        item {
            SettingsGroup("About", footer = "Cadence plays only files stored on this phone. Nothing leaves your device.") {
                SettingRow("Supported Formats", icon = Icons.Rounded.Audiotrack, iconColor = Red, onClick = { dialog = "formats" })
                SettingRow("Version", icon = Icons.Rounded.Info, iconColor = Gray, value = BuildConfig.VERSION_NAME, chevron = false, divider = false)
            }
        }
    }

    when (dialog) {
        "duration" -> ChoiceDialog(
            "Skip Audio Shorter Than",
            listOf(0, 10, 30, 60, 120).map { minDurationLabel(it) },
            listOf(0, 10, 30, 60, 120).indexOf(settings.minDurationSec),
            onPick = { vm.set(SettingsKeys.MIN_DURATION, listOf(0, 10, 30, 60, 120)[it]) },
            onDismiss = { dialog = null },
        )
        "decoder" -> ChoiceDialog(
            "Decoder",
            DecoderMode.entries.map { it.label },
            settings.decoderMode.ordinal,
            descriptions = DecoderMode.entries.map { it.description },
            onPick = { vm.set(SettingsKeys.DECODER, DecoderMode.entries[it].name) },
            onDismiss = { dialog = null },
        )
        "theme" -> ChoiceDialog(
            "Theme",
            ThemeMode.entries.map { it.label },
            settings.themeMode.ordinal,
            onPick = { vm.set(SettingsKeys.THEME, ThemeMode.entries[it].name) },
            onDismiss = { dialog = null },
        )
        "history" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Clear Listening History?") },
            text = { Text("Recently Played and Top Songs will start over. Playlists and favorites are kept.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.user.clearHistory()
                    dialog = null
                }) { Text("Clear", color = Red) }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
        )
        "formats" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Supported Formats") },
            text = {
                Text(
                    "Lossless: FLAC (including 24-bit / 192 kHz and higher), ALAC (.m4a), WAV\n\n" +
                        "Lossy: MP3, AAC / M4A, Opus, Ogg Vorbis, AMR, AC-3 / E-AC-3\n\n" +
                        "Containers: M4A / MP4, MKA / WebM, Ogg, 3GP, ADTS\n\n" +
                        "Not supported: AIFF, APE, WavPack, DSD (DSF/DFF) and WMA. Android's player has no reader for these files.",
                    fontSize = 14.sp,
                )
            },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text("Done") } },
        )
    }
}

private fun minDurationLabel(sec: Int): String = when {
    sec <= 0 -> "Off"
    sec < 60 -> "$sec seconds"
    sec == 60 -> "1 minute"
    else -> "${sec / 60} minutes"
}

@Composable
fun SettingsGroup(header: String?, footer: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        if (header != null) {
            Text(
                header.uppercase(), fontSize = 13.sp, color = Cadence.colors.secondaryText,
                modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 6.dp),
            )
        } else Spacer(Modifier.padding(top = 16.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Cadence.colors.groupedCell),
            content = content,
        )
        if (footer != null) {
            Text(
                footer, fontSize = 13.sp, color = Cadence.colors.secondaryText,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp),
            )
        }
    }
}

@Composable
private fun IconTile(icon: ImageVector, color: Color) {
    Box(Modifier.size(30.dp).clip(RoundedCornerShape(7.dp)).background(color), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(19.dp))
    }
}

@Composable
fun SettingRow(
    title: String,
    icon: ImageVector? = null,
    iconColor: Color = Gray,
    value: String? = null,
    subtitle: String? = null,
    chevron: Boolean = true,
    divider: Boolean = true,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier)
                .heightIn(min = 48.dp)
                .padding(start = 14.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                IconTile(icon, if (enabled) iconColor else iconColor.copy(alpha = 0.4f))
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title, fontSize = 16.sp,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else Cadence.colors.secondaryText,
                )
                if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = Cadence.colors.secondaryText)
            }
            if (value != null) {
                Text(value, fontSize = 15.sp, color = Cadence.colors.secondaryText, modifier = Modifier.padding(start = 8.dp))
            }
            trailing?.invoke(this)
            if (onClick != null && chevron) Icon(Icons.Rounded.ChevronRight, null, tint = Cadence.colors.tertiaryText)
        }
        if (divider) {
            HorizontalDivider(
                Modifier.padding(start = if (icon != null) 58.dp else 14.dp),
                thickness = 0.5.dp, color = Cadence.colors.separator,
            )
        }
    }
}

@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    icon: ImageVector?,
    iconColor: Color,
    subtitle: String? = null,
    enabled: Boolean = true,
    divider: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    SettingRow(
        title, icon = icon, iconColor = iconColor, subtitle = subtitle, divider = divider, enabled = enabled,
        chevron = false, onClick = { onChange(!checked) },
    ) {
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedTrackColor = Color(0xFF34C759),
                checkedThumbColor = Color.White,
                checkedBorderColor = Color.Transparent,
                uncheckedTrackColor = Cadence.colors.placeholder,
                uncheckedThumbColor = Color.White,
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

@Composable
private fun AccentRow(selected: Int, onSelect: (Int) -> Unit) {
    Column {
        Row(Modifier.padding(start = 14.dp, end = 12.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Rounded.Palette, AccentColors.getOrElse(selected) { AccentColors[0] })
            Spacer(Modifier.width(14.dp))
            Text("Accent Color", fontSize = 16.sp, modifier = Modifier.weight(1f))
            Text(AccentNames.getOrElse(selected) { "Red" }, fontSize = 15.sp, color = Cadence.colors.secondaryText)
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 58.dp, end = 12.dp, top = 10.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AccentColors.forEachIndexed { i, color ->
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(color)
                        .then(if (i == selected) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), CircleShape) else Modifier)
                        .clickable { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (i == selected) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
        HorizontalDivider(Modifier.padding(start = 58.dp), thickness = 0.5.dp, color = Cadence.colors.separator)
    }
}

@Composable
fun ChoiceDialog(
    title: String,
    options: List<String>,
    selected: Int,
    descriptions: List<String>? = null,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEachIndexed { i, label ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                onPick(i)
                                onDismiss()
                            }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(label, fontSize = 16.sp)
                            descriptions?.getOrNull(i)?.let {
                                Text(it, fontSize = 12.sp, color = Cadence.colors.secondaryText)
                            }
                        }
                        if (i == selected) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
