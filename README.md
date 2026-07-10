This is a Kotlin Multiplatform project targeting Android, iOS.

- [/shared](./shared/src) is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
  - [commonMain](./shared/src/commonMain/kotlin) is for code that’s common for all targets.
  - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
    For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
    the [iosMain](./shared/src/iosMain/kotlin) folder would be the right place for such calls.
    Similarly, if you want to edit the Desktop (JVM) specific part, the [jvmMain](./shared/src/jvmMain/kotlin)
    folder is the appropriate location.

- [/iosApp](./iosApp/iosApp) contains iOS applications. Even if you’re sharing your UI with Compose Multiplatform,
  you need this entry point for your iOS app. This is also where you should add SwiftUI code for your project.

### Build and Run Android Application

To build and run the development version of the Android app, use the run configuration from the run widget
in your IDE’s toolbar or build it directly from the terminal:

- on macOS/Linux
  ```shell
  ./gradlew :androidApp:assembleDebug
  ```
- on Windows
  ```shell
  .\gradlew.bat :androidApp:assembleDebug
  ```

### Build and Run iOS Application

To build and run the development version of the iOS app, use the run configuration from the run widget
in your IDE’s toolbar or open the [/iosApp](./iosApp) directory in Xcode and run it from there.

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…

## Local configuration

このプロジェクトでは、環境ごとの差し替え値をローカル設定ファイルで管理します。
ローカル設定ファイルは各開発者の環境に依存するため、Git には commit しません。

### iOS

`iosApp/Configuration/Local.xcconfig` を作成してください。

```xcconfig
// Apple Developer Team ID
TEAM_ID=XXXXXXXXXX

// Backend base URL
// Debug defaults to staging; Release defaults to production.
// Override when needed:
APP_BASE_URL=https:/$()/app.stg.twinte.net

// Google Sign-In
GOOGLE_IOS_CLIENT_ID=xxxxxxxxxxxx-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx.apps.googleusercontent.com
GOOGLE_IOS_REVERSED_CLIENT_ID=com.googleusercontent.apps.xxxxxxxxxxxx-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
GOOGLE_SERVER_CLIENT_ID=xxxxxxxxxxxx-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx.apps.googleusercontent.coma
```

> [!NOTE]
> `APP_BASE_URL` は `.xcconfig` 上で `//` がコメントとして解釈されるのを避けるため、`https:/$()/app.stg.twinte.net` のように記述する必要があります。

`TEAM_ID` は Xcode の Signing & Capabilities で使用する Apple Developer Team ID です。
Sign in with Apple を動作確認する場合は、Bundle ID と Capability が Apple Developer 側で正しく設定されている必要があります。

### Android

Android では `local.properties` に必要な値を設定できます。

```
twinteAppBaseUrl=https://app.stg.twinte.net
twinteGoogleServerClientId=xxxxxxxxxxxx-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx.apps.googleusercontent.com
```

`twinteAppBaseUrl` を指定しない場合、Debug では staging の `https://app.stg.twinte.net`、Release では production の `https://app.twinte.net` が使われます。
