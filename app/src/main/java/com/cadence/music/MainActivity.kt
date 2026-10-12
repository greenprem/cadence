package com.cadence.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.cadence.music.ui.CadenceRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { CadenceRoot() }
    }

    override fun onStart() {
        super.onStart()
        (application as CadenceApp).player.connect()
    }
}
