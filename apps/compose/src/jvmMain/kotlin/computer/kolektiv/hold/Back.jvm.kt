package computer.kolektiv.hold

import androidx.compose.runtime.Composable

@Composable
actual fun HoldBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit

@Composable
actual fun WatchCaptures(onText: (String, String) -> Unit, onAudio: (ByteArray) -> Unit) = Unit
