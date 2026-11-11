package computer.kolektiv.hold

import androidx.compose.runtime.Composable

@Composable
expect fun HoldBackHandler(enabled: Boolean = true, onBack: () -> Unit)

@Composable
expect fun WatchCaptures(onText: (String, String) -> Unit, onAudio: (ByteArray) -> Unit)
