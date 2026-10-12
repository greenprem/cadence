# Cadence

A local-only Android music player with an Apple Music–style interface, built with Kotlin, Jetpack Compose and Media3 (ExoPlayer). It is tuned for high-quality files such as FLAC and ALAC.

## Features

**Library**
- Music folder selector: only audio inside the folders you choose appears, so voice notes, ringtones and app sounds stay out. Browse your storage folder by folder, or add any folder with the system picker. Subfolders are included. There is also an "all music on this phone" switch.
- Skip short audio (off, 10 s, 30 s, 1 min, 2 min).
- Home: Play / Shuffle all, Recently Played, Top Songs, Recently Added, Lossless albums, Favorite songs.
- Library: Playlists, Artists, Albums, Songs, Genres, Folders, Favorites, plus a Recently Added grid. Opening a category shows a pill bar of all categories on top; swipe left or right (or tap a pill) to switch between them.
- Album pages with large artwork, Play / Shuffle buttons, disc grouping, track numbers, total length and "More by" the same artist.
- Search across artists, albums and songs, with genre tiles when the search box is empty. Tap the Search tab again to jump to the search field with the keyboard open (once more closes it).
- Playlists: create, rename, delete, reorder and remove songs. Favorites (★).
- Sorting for Songs and Albums, remembered between launches.
- The library updates by itself when files change, and Rescan Library forces Android to re-index your folders.

**Playback**
- Media3 ExoPlayer in a background service, with a notification, lock-screen and Bluetooth controls.
- Hi-Res Output (default on): 32-bit float output, so 24-bit FLAC/ALAC isn't reduced to 16-bit.
- FFmpeg decoder (Jellyfin's Media3 build) for ALAC and as a fallback when the phone has no decoder. You can set it to Automatic, Device only or Prefer FFmpeg.
- Gapless playback, shuffle that always starts from the chosen song, Repeat All / One, Play Next / Play Last.
- The queue and position are restored when you reopen the app.
- Sleep timer (minutes, or end of current song), the system equalizer, pause when headphones disconnect, pause for other audio, skip silence and optional audio offload.

**Speed**
- Every tab keeps its own screen stack alive, so switching tabs is instant and each tab remembers where you were (tap the tab again to go back to its first screen).
- Now Playing is built ahead of time and only slides into view, following your finger when you drag the mini player up or the player down.
- Compose baseline profiles are installed with the app (profileinstaller). For the smoothest result install the **release** APK; debug builds of Compose apps are several times slower.

**Apple Music touches**
- Large titles that collapse into a small bar as you scroll, and iOS-style back buttons that name the previous screen.
- A floating mini player above the tab bar with a thin progress line. Tap it or drag it up to open Now Playing (the artwork flies up and grows into place), swipe it left / right to change song, and drag Now Playing down to close.
- On Now Playing, tap the artwork to play / pause and swipe it left / right to change song.
- Lyrics: synced lyrics follow the song (tap a line to jump to it). Sources, best first: synced lyrics in the file's tags (FLAC/Vorbis LYRICS, MP3 USLT, M4A ©lyr) or a matching .lrc file, then LRCLIB (lrclib.net), then plain lyrics. Online lookup can be turned off in Settings.
- Now Playing uses the artwork's colors and a blurred artwork backdrop (Android 12+). The artwork shrinks with a spring when paused.
- A thin scrubber and an even thinner volume bar (can be hidden in Settings) that thicken while dragged, and a Lossless / Hi-Res Lossless / bitrate badge. Tap the badge for bit depth, sample rate and file details.
- An animated equalizer marker on the playing song, the Playing Next queue with Shuffle and Repeat toggles, and long-press menus with haptics.
- Grouped settings with colored icon tiles, seven accent colors (Apple Music red by default), and Light / Dark / Automatic theme.

## Formats

| Plays | Notes |
|---|---|
| FLAC | Any bit depth and sample rate, including 24-bit/192 kHz |
| ALAC (.m4a) | Through the FFmpeg decoder |
| WAV | PCM |
| MP3, AAC/M4A, Opus, Ogg Vorbis, AMR, AC-3/E-AC-3 | |
| MKA/WebM, MP4, 3GP, ADTS | Containers |

AIFF, APE, WavPack, DSD (DSF/DFF) and WMA don't play because Media3 has no reader for those files.

## Build

You need Android Studio (Ladybug or newer) or JDK 17+ with the Android SDK (platform 35).

```bash
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease      # minified, signed with the debug key for side-loading
```

Or open the folder in Android Studio and press Run.

**Without a local setup:** push this folder to a GitHub repository. The included workflow (`.github/workflows/build.yml`) builds both APKs on every push, and you can download them from the run's **Artifacts** section.

Requires Android 8.0 (API 26) or newer.

## Project layout

```
app/src/main/java/com/cadence/music/
  CadenceApp.kt            app-wide singletons
  MainActivity.kt
  data/                    MediaStore scan, folder filter, settings (DataStore),
                           playlists/favorites/history, artwork + audio-quality readers
  playback/                PlaybackService (MediaSessionService + ExoPlayer), PlayerConnection
  ui/                      CadenceRoot (tabs, navigation), theme, components,
                           nowplaying/, screens/
```
