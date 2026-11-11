package computer.kolektiv.hold

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

actual fun formatStamp(millis: Long): String =
    SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()).format(Date(millis))
