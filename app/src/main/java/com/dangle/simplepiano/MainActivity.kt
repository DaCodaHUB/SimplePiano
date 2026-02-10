package com.dangle.simplepiano

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dangle.simplepiano.audio.PianoEngine
import com.dangle.simplepiano.ui.PianoKeyboard

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PianoScreen() }
    }
}

@Composable
fun PianoScreen() {
    val context = LocalContext.current
    val engine = remember { PianoEngine(context) }

    var loaded by remember { mutableStateOf(false) }
    var sustain by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        loaded = false
        engine.loadAll { loaded = true }
        onDispose { engine.release() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp)
    ) {
        if (loaded) {
            Column(
                modifier = Modifier
                    .fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top controls
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Sustain")
                    Switch(
                        checked = sustain,
                        onCheckedChange = { sustain = it }
                    )
                }

                Spacer(Modifier.height(20.dp))

                // Piano
                PianoKeyboard(
                    engine = engine,
                    sustain = sustain,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("Loading piano…")
            }
        }
    }
}
