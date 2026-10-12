package com.cadence.music.ui.screens

import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.RemoveCircle
import androidx.compose.material.icons.rounded.SdCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cadence.music.data.AppSettings
import com.cadence.music.data.FolderInfo
import com.cadence.music.data.prettyPath
import com.cadence.music.ui.components.ScreenList
import com.cadence.music.ui.theme.Cadence

private fun volumeRoot(path: String): String = when {
    path.startsWith("/storage/emulated/0") -> "/storage/emulated/0"
    path.startsWith("/storage/") -> "/storage/" + path.removePrefix("/storage/").substringBefore('/')
    else -> "/" + path.trimStart('/').substringBefore('/')
}

/** Converts a folder picked in the system file picker into a filesystem path. */
fun treeUriToPath(uri: Uri): String? {
    val docId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: return null
    if (docId.startsWith("raw:")) return docId.removePrefix("raw:").trimEnd('/')
    val volume = docId.substringBefore(':')
    val relative = docId.substringAfter(':', "").trim('/')
    val base = when {
        volume.equals("primary", ignoreCase = true) -> Environment.getExternalStorageDirectory().absolutePath
        volume.equals("downloads", ignoreCase = true) ->
            return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
        volume.isNotEmpty() && !volume.contains('/') -> "/storage/$volume"
        else -> return null
    }
    return if (relative.isEmpty()) base else "$base/$relative"
}

/**
 * Choose which folders count as music. Works as onboarding (with a Done button) and from Settings
 * (changes save immediately).
 */
@Composable
fun MusicFoldersScreen(
    settings: AppSettings,
    allFolders: List<FolderInfo>,
    onboarding: Boolean,
    onSave: (folders: Set<String>, useAll: Boolean) -> Unit,
    onBack: (() -> Unit)?,
) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(settings.includedFolders) }
    var useAll by remember { mutableStateOf(settings.useAllFolders) }
    var browse by rememberSaveable { mutableStateOf<String?>(null) }

    fun commit(folders: Set<String>, all: Boolean) {
        selected = folders
        useAll = all
        if (!onboarding) onSave(folders, all)
    }

    fun toggle(path: String) {
        when {
            path in selected -> commit(selected - path, useAll)
            selected.any { path.startsWith(it.trimEnd('/') + "/") } ->
                Toast.makeText(context, "Already included through a parent folder", Toast.LENGTH_SHORT).show()
            else -> commit(selected.filterNot { it.startsWith(path.trimEnd('/') + "/") }.toSet() + path, useAll)
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val path = treeUriToPath(uri)
        if (path == null) {
            Toast.makeText(context, "That location can't be used as a music folder", Toast.LENGTH_LONG).show()
        } else {
            commit(selected.filterNot { it.startsWith(path.trimEnd('/') + "/") }.toSet() + path, false)
        }
    }

    // Songs under each candidate folder, counting everything below it.
    fun countUnder(path: String): Int =
        allFolders.filter { it.path == path || it.path.startsWith(path.trimEnd('/') + "/") }.sumOf { it.trackCount }

    val roots = remember(allFolders) { allFolders.map { volumeRoot(it.path) }.distinct().sorted() }
    val children = remember(allFolders, browse) {
        val parent = browse ?: return@remember emptyList<String>()
        val prefix = parent.trimEnd('/') + "/"
        allFolders.map { it.path }.filter { it.startsWith(prefix) }
            .map { prefix + it.removePrefix(prefix).substringBefore('/') }
            .distinct()
            .sortedBy { it.lowercase() }
    }
    val directHere = remember(allFolders, browse) { allFolders.firstOrNull { it.path == browse }?.trackCount ?: 0 }

    Box(Modifier.fillMaxSize().background(Cadence.colors.groupedBackground)) {
        ScreenList(
            "Music Folders",
            backLabel = if (onboarding) null else "Settings",
            onBack = onBack,
            background = Cadence.colors.groupedBackground,
        ) {
            item {
                Text(
                    if (onboarding) "Choose where your music lives. Only audio in these folders will appear in your library, so recordings, ringtones and app sounds stay out."
                    else "Only audio inside these folders appears in your library. Subfolders are included.",
                    fontSize = 15.sp, color = Cadence.colors.secondaryText,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }

            item {
                SettingsGroup(null) {
                    SwitchRow(
                        "Use All Music on This Phone", useAll, Icons.Rounded.LibraryMusic, Color(0xFFFF2D55),
                        subtitle = "Everything Android marks as music, from any folder.",
                        divider = false,
                    ) { commit(selected, it) }
                }
            }

            if (!useAll) {
                item {
                    SettingsGroup("Your Music Folders", footer = if (selected.isEmpty()) "No folders yet. Pick one below or browse your storage." else null) {
                        selected.sorted().forEach { path ->
                            FolderRow(
                                name = path.substringAfterLast('/'),
                                detail = "${prettyPath(path)} · ${countUnder(path)} files",
                                leading = {
                                    Icon(
                                        Icons.Rounded.RemoveCircle, contentDescription = "Remove",
                                        tint = Color(0xFFFF3B30),
                                        modifier = Modifier.size(24.dp).clickable { commit(selected - path, false) },
                                    )
                                },
                            )
                        }
                        Row(
                            Modifier.fillMaxWidth().clickable { picker.launch(null) }.padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.CreateNewFolder, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(14.dp))
                            Text("Add Folder from Storage…", color = MaterialTheme.colorScheme.primary, fontSize = 16.sp)
                        }
                    }
                }

                item {
                    val header = browse?.let { prettyPath(it) } ?: "Folders With Audio"
                    SettingsGroup(header, footer = "Tap a circle to include a folder. Tap a name to look inside.") {
                        if (browse != null) {
                            val parent = browse!!
                            val up = if (parent in roots) null else parent.substringBeforeLast('/')
                            FolderRow(
                                name = "Back",
                                detail = up?.let { prettyPath(it) } ?: "Storage",
                                accent = true,
                                onClick = { browse = up },
                            )
                            if (directHere > 0) {
                                FolderRow(name = "Files in this folder", detail = "$directHere files", dim = true)
                            }
                        }
                        val rows = if (browse == null) roots else children
                        if (rows.isEmpty()) {
                            Text(
                                if (allFolders.isEmpty()) "No audio found yet. Copy music to your phone, then use Rescan Library in Settings."
                                else "No subfolders.",
                                fontSize = 14.sp, color = Cadence.colors.secondaryText,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                        rows.forEach { path ->
                            val isRoot = browse == null
                            val included = path in selected || selected.any { path.startsWith(it.trimEnd('/') + "/") }
                            val hasChildren = allFolders.any { it.path.startsWith(path.trimEnd('/') + "/") }
                            FolderRow(
                                name = if (isRoot) prettyPath(path) else path.substringAfterLast('/'),
                                detail = "${countUnder(path)} files",
                                icon = if (!isRoot) Icons.Rounded.Folder else if (path.startsWith("/storage/emulated")) Icons.Rounded.PhoneAndroid else Icons.Rounded.SdCard,
                                leading = {
                                    Icon(
                                        if (included) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                        contentDescription = if (included) "Included" else "Not included",
                                        tint = if (included) MaterialTheme.colorScheme.primary else Cadence.colors.tertiaryText,
                                        modifier = Modifier.size(26.dp).clip(RoundedCornerShape(13.dp)).clickable { toggle(path) },
                                    )
                                },
                                chevron = hasChildren,
                                onClick = { if (hasChildren) browse = path else toggle(path) },
                            )
                        }
                    }
                }
            }

            if (onboarding) item { Spacer(Modifier.height(96.dp)) }
        }

        if (onboarding) {
            val ready = useAll || selected.isNotEmpty()
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Cadence.colors.groupedBackground)
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (ready) MaterialTheme.colorScheme.primary else Cadence.colors.placeholder)
                        .clickable(enabled = ready) { onSave(selected, useAll) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (ready) "Done" else "Choose at Least One Folder",
                        color = if (ready) Color.White else Cadence.colors.secondaryText,
                        fontWeight = FontWeight.SemiBold, fontSize = 17.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderRow(
    name: String,
    detail: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Rounded.Folder,
    leading: (@Composable () -> Unit)? = null,
    chevron: Boolean = false,
    accent: Boolean = false,
    dim: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(12.dp))
            }
            Icon(
                icon, null,
                tint = if (dim) Cadence.colors.secondaryText else Color(0xFF4FA3F7),
                modifier = Modifier.size(26.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    name, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (accent) MaterialTheme.colorScheme.primary else if (dim) Cadence.colors.secondaryText else MaterialTheme.colorScheme.onSurface,
                )
                Text(detail, fontSize = 12.sp, color = Cadence.colors.secondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (chevron) Icon(Icons.Rounded.ChevronRight, null, tint = Cadence.colors.tertiaryText)
        }
        HorizontalDivider(Modifier.padding(start = 52.dp), thickness = 0.5.dp, color = Cadence.colors.separator)
    }
}
