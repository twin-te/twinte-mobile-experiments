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

## Shared app navigation

認証後の画面は commonMain の Navigation 3 で管理します。

- Timetable: 通常・特殊の時間割、モジュールの選択、授業の詳細・編集、キャッシュ済み時間割の表示と更新
- Today: 今日の時間割の表示と更新
- Courses: 授業の検索・登録・作成・編集・削除、出欠操作
- Account: 認証の確認、provider の追加・解除、ログアウト、アカウント削除
- Tags: Courses の Manage tags から開くタグ管理
- CourseDetail: 時間割のコマから開く授業詳細。出欠操作、既存の編集画面への遷移、確認付きの削除

画面と年度はヘッダーのメニューから切り替えます。各画面の状態は切替時に保持します。
戻る操作は、配下の画面からその系統のルートへ、ルートからは Timetable へ戻ります。
ログアウトやアカウント変更時にはナビゲーションと入力状態を破棄します。
再生成時には選択年度・モジュール、表示画面、通常・特殊の選択、検索入力、未保存の授業ドラフトを復元します。
セッションや授業データの復元は既存の SessionStore・ローカルキャッシュを利用します。

オフラインでの書き込みキューはありません。キャッシュ済みデータは表示できますが、
作成・編集・出欠変更の保存には通信が必要です。

ナビゲーションの回帰テストは `./gradlew :shared:jvmTest` で実行できます。
UI テストでは画面配線、状態の保存・復元、アカウント間の分離を確認し、
画面キャプチャを `shared/build/reports/navigation/` と `shared/build/reports/timetable-ui/` に出力します。

ホームは既存 Web のデザインを参考にしています。色は `ui/theme`、陰影・ボタンなどの共通部品は
`ui/components`、時間割固有の表示は `feature/timetable` に置き、通信やキャッシュから分離しています。
通常の表は月曜から金曜の1〜6限を表示し、土曜・時間外などの授業は「特殊」の「その他」から参照できます。
空コマを押すと授業検索の先頭へ移動します。初回表示は保存済み・取得済みの日課から現在学期を選び、
休業期は Web と同じ通常学期へ読み替えます。復元済み・手動選択の学期は上書きしません。

単独の授業は詳細画面へ、重複コマは授業選択ダイアログを経由して詳細画面へ移動します。
詳細はローカルキャッシュから表示し、再生成時には授業 ID から復元します。
出欠は保存成功後にキャッシュへ反映し、失敗時は元の値を保持します。削除成功後はホームへ戻ります。
タグは詳細画面で全件を表示し、付け外し・新規作成ができます。新規作成と授業への割り当ては別リクエストで、
割り当てに失敗した場合も作成済みタグを残し、そのタグを押して再試行できます。
メモの変更は、詳細画面右上のメニューから開く既存の編集画面を利用します。
ヘッダーと本文は同じ NavEntry に置き、通常の戻る操作・予測型の戻る操作で共通の横スライドを利用します。

書体は Web の指定に合わせた Noto Sans JP を利用します。静的フォント3ウェイト（計約13.1 MiB）を
`composeResources/font/` に同梱し、オフライン時も Android / iOS で同じ書体を使います。
Android API 24・25 でも表示できるように、可変フォントは使いません。
ライセンスと取得元は `composeResources/files/fonts/noto_sans_jp/` に置いています。
Web 自体は端末の代替フォントにも依存するため、環境ごとの完全なピクセル一致は対象にしていません。

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
