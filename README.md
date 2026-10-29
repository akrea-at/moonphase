# Moon Phase

Android app and home-screen widget showing the current moon phase, rendered from NASA lunar maps with the dark side faintly visible in earthshine.

## Features

- Photo-realistic moon with correct terminator, libration and axis tilt; flipped for the southern hemisphere.
- Phase name, illumination, age, and the next new and full moon with local date and time.
- Month calendar (swipe or arrows) with the phase for each day and the exact local time of new and full moon.
- Location by device position (one-shot, rounded to about 1 km) or an offline city search over ~34,000 places with their time zones.
- Resizable widget rendered at its actual pixel size, with an optional phase label (off by default).
- English and German: follows the phone's language, or pick one in the app's settings.

## Build

Requires the Android SDK and a JDK 17 to 23 for Gradle 8.11 (newer JDKs are not supported). If your default Java is newer, point Gradle at another JDK in `~/.gradle/gradle.properties`, for example Android Studio's bundled one:

```properties
org.gradle.java.home=/opt/android-studio/jbr
```

```sh
./gradlew testDebugUnitTest
./gradlew assembleRelease
```

The minified release APK is written unsigned to `app/build/outputs/apk/release/app-release-unsigned.apk`. To sign it with your own key, create a `keystore.properties` next to `settings.gradle.kts` (it is gitignored):

```properties
storeFile=/path/to/release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

The signed APK is then `app/build/outputs/apk/release/app-release.apk`. For a quick install, `./gradlew installDebug` uses the debug key.

Asset regeneration is described in [tools/README.md](tools/README.md). Store listing text and screenshots for F-Droid are in `fastlane/metadata/android/`.

## Resource use

- No internet permission and no background service.
- Location is read only when you tap *Update location*, or once a day when the app is opened in device-location mode.
- The widget redraws on a non-wakeup, inexact alarm every three hours, and when the time zone, clock, language or app settings change. It never wakes the device.
- Astronomy is computed locally (Meeus, *Astronomical Algorithms*); phase times agree with an independent ephemeris to within seconds.

## Attribution

- Moon imagery: NASA's Scientific Visualization Studio / LRO (LROC & LOLA teams)
- City data: GeoNames (geonames.org), CC BY 4.0

## Licence

Moon Phase is free software, licensed under the GNU General Public License v3.0 or later (GPL-3.0-or-later). See [LICENSE](LICENSE).
