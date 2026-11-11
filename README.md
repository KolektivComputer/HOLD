# HOLD

Offline pocket log. Notes, numbers, voice, Flipper dumps, health readings, places. Tags, categories, saved views. Syncs to a server you host, when you say so.

| Path | What |
| --- | --- |
| `apps/web` | Browser pocket. Works offline in the device that opens it. |
| `modules/shared` | Kotlin model, Flipper and health parsers, sync merge. Android, iOS, JVM. |
| `apps/compose` | Compose UI for Android (min SDK 26), desktop, and iOS. |
| `apps/apple` | Xcode project for the iPhone app and the Apple Watch companion. |
| `services/sync` | Server contract. The server itself comes later. |

Nothing is uploaded until a client is pointed at a base URL and you hit sync. Use the WireGuard address of your own host. The browser also needs that host to allow the page's origin.

```
./gradlew :modules:shared:jvmTest
./gradlew :apps:compose:run          # desktop
cd apps/web && npm install && npm run dev
```

Android and iOS need a machine with the SDK / Xcode. Open `apps/apple/HOLD.xcodeproj`, pick your team, and run HOLD. The iPhone target builds the Compose framework. The Watch target sends a note or a recording to the phone.
