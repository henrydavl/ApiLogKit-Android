# Contributing to ApiLogKit (Android)

Bug reports, feature requests, and pull requests are welcome.

## Reporting bugs

Open an issue and include:
- Android version and device/emulator
- Android Gradle Plugin and Kotlin version
- A minimal reproduction or description of the unexpected behavior

## Suggesting features

Open an issue describing what you'd like and why it's useful. Check existing issues first to avoid duplicates.

## Submitting a pull request

1. Fork the repo and create a branch from `main`
2. Make your changes — keep them focused on a single concern
3. Verify the project builds cleanly: `./gradlew :apilogkit:assembleRelease :sample:assembleDebug`
4. Open a PR with a clear description of what changed and why

## Code style

- Follow the conventions already present in the codebase (`kotlin.code.style=official`)
- Keep parity with the iOS [ApiLogKit](https://github.com/henrydavl/ApiLogKit): the data model,
  export formats, and UX should stay consistent across both platforms
- The library has **no required** third-party dependencies — OkHttp is `compileOnly` and only
  needed by hosts using `ApiLogInterceptor`. Keep it that way
- Jetpack Compose is the UI, but the library must stay consumable from XML/View-based host apps
  (the inspector launches as its own Activity; the Developer Options hook is View-friendly)
- minSdk 24 compatibility must be maintained
