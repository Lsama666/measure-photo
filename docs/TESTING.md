# Verification and limits

## Historical local evidence (2026-09-14, v0.2.1)

The maintainer's original project includes JUnit XML reports with 11 geometry tests and 5 label-placement tests, all passing, plus build/lint/device records. Reports contain local machine metadata and are not republished verbatim.

The original verification notes record successful debug/test APK builds, JVM tests and lint. Six device test cases reported success individually, but the ADB connection dropped before the overall runner completed (exit code 1). This is **not** a clean end-to-end instrumentation run.

The historical v0.1.0 screenshot in the README is a real device capture with synthetic content. It does not demonstrate the current v0.2.1 UI. No current device screenshot was retrieved in that historical run.

## Fresh public-repository checks

See the [Actions runs](https://github.com/Lsama666/measure-photo/actions) for fresh results tied to an exact commit. CI checks unit tests, Android lint, debug assembly and instrumentation-test compilation. It does **not** run device tests or establish human usability.

## Device and load tests

Use a test device without valuable project data. Normal instrumentation:

```sh
./gradlew connectedDebugAndroidTest
```

The load test generates 100 synthetic 4000×3000 JPEGs with 100 annotations each and requires explicit opt-in, after installing both app and test APK:

```sh
adb shell am instrument -w -r -e class com.lsama.measurephoto.LoadTest -e load true com.lsama.measurephoto.test/androidx.test.runner.AndroidJUnitRunner
```

It consumes storage and memory. Historical v0.1.0 load results do not establish performance on other devices or current code. Large PDFs downsample photos; exported image files retain a separate resolution policy.

## Outstanding acceptance

- Complete camera/Photo Picker/export destination and denied-permission flows.
- Human gesture testing, accessibility, landscape, larger fonts and screen sizes.
- Kill/restart recovery, storage exhaustion and interrupted-export fault injection.
- Older/low-memory device coverage and database upgrade migrations.
- Current UI screenshots and complete device test runs without connection failure.

There are no verified public download/adoption metrics at initial publication.
