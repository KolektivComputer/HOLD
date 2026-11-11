package computer.kolektiv.hold

import androidx.compose.ui.window.ComposeUIViewController
import computer.kolektiv.hold.store.JsonHoldRepository
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile

fun MainViewController() = ComposeUIViewController {
    val directory = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).first() as String
    val path = "$directory/hold.json"
    val repository = JsonHoldRepository(
        read = {
            NSString.stringWithContentsOfFile(path, encoding = NSUTF8StringEncoding, error = null) as String?
        },
        write = { value ->
            NSString.create(string = value).writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)
        },
    )
    HoldApp(repository, poster = { _, _, _ -> error("Sync from iOS isn't hooked up yet.") })
}
