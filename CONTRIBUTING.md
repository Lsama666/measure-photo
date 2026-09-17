# Contributing

This is an early-stage project. Report reproducible issues or propose a focused change before a large feature.

1. Describe the Android version, app version, steps, expected/actual behavior.
2. Use synthetic or anonymized photos; omit client names, addresses and private files.
3. Keep original photos intact and geometry independent of entered measurements.
4. Run `./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest`.
5. For editor, camera, storage or export changes, explain device verification and any untested cases.

Add regression tests for meaningful behavior changes. Do not claim manual/device acceptance from compilation alone. Keep API keys and signing material out of the repository. Contributions are under the project's MIT license. Maintainer review is required before merging.
