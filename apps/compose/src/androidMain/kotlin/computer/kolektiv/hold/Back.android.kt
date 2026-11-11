package computer.kolektiv.hold

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

@Composable
actual fun HoldBackHandler(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled = enabled, onBack = onBack)
}

@Composable
actual fun WatchCaptures(onText: (String, String) -> Unit, onAudio: (ByteArray) -> Unit) = Unit
