package computer.kolektiv.hold

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.dateWithTimeIntervalSince1970

actual fun formatStamp(millis: Long): String {
    val formatter = NSDateFormatter()
    formatter.dateFormat = "MMM d, yyyy · h:mm a"
    return formatter.stringFromDate(NSDate.dateWithTimeIntervalSince1970(millis / 1000.0)) ?: ""
}
