package com.cadence.music

import android.app.Application
import com.cadence.music.data.LibraryRepository
import com.cadence.music.data.MediaScanner
import com.cadence.music.data.SettingsRepository
import com.cadence.music.data.UserDataRepository
import com.cadence.music.playback.PlayerConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class CadenceApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    lateinit var settings: SettingsRepository
        private set
    lateinit var scanner: MediaScanner
        private set
    lateinit var library: LibraryRepository
        private set
    lateinit var userData: UserDataRepository
        private set
    lateinit var player: PlayerConnection
        private set

    override fun onCreate() {
        super.onCreate()
        settings = SettingsRepository(this)
        scanner = MediaScanner(this, appScope)
        library = LibraryRepository(scanner, settings, appScope)
        userData = UserDataRepository(this, appScope)
        player = PlayerConnection(this, appScope, userData, library, settings)
        scanner.start()
    }
}
