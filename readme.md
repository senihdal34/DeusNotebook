# DeusNotebook

A handwriting notebook for [Onyx Boox](https://www.boox.com/) e-ink tablets, built around a weekly planner start page,
pinned notes, handwriting recognition with a personal word list, markdown export for [Obsidian](https://obsidian.md),
and a reading tab fed by the Boox reader.

Kotlin, Jetpack Compose, the Onyx Pen SDK. Written for a 10.3" e-ink screen: no animation, no shadows, high contrast.

## Build

```bash
./gradlew assembleDebug     # debug APK in app/build/outputs/apk/debug/
./gradlew testDebugUnitTest # unit tests
```

Requires JDK 17 and the Android SDK. The Onyx Maven repository is plain HTTP (`allowInsecureProtocol`), see `settings.gradle`.
Pen input only works on Onyx hardware.

## Credits and license

DeusNotebook is built on [Ethran/notable](https://github.com/Ethran/notable) and
[jdkruzr/aragonite](https://github.com/jdkruzr/aragonite). Their authors wrote most of the editor, the stroke storage and the
export pipeline this app still uses; see their repositories for the full history and contributor list.
The reading tab's badge and board designs follow [BookOrbit](https://github.com/bookorbit/bookorbit) (AGPL-3.0).

Licensed under the GNU General Public License v3.0, see [LICENSE](./LICENSE).
