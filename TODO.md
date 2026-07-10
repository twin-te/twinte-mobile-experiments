# TODO

## Mobile 認証の強化

- `feat/auth-challenge-mobile` に作業途中のものがある（予定）
- Backend については Codex が取り組んだやつ `codex/harden-mobile-auth` があるので、先にこれをレビュー＆マージする
  - staging に `000004_create_auth_challenges` と `000005_create_apple_credentials` を適用
  - 問題として、かなり差分がデカい
  - 破壊的変更は無いため、おそらく現行リリースが動かなくなることはないが、stg にデプロイしてチェックする
  - （安定してきたら、破壊的変更のフォールバックとなるものは消してもいいね）
- [ ] staging で `POST /auth/v4/google/challenge` と `POST /auth/v4/apple/challenge` が利用できることを確認する
- [ ] challenge なしの既存 Google/Apple ログインと `GET /auth/v4/logout` が、リリース済みアプリから引き続き利用できることを確認する
- マージ後の KMP 実装との競合を解消する

### Challenge と nonce を用いたログイン

- [ ] one-time challenge を作成する Mobile API を復元する。
- [ ] Backend が発行した nonce を Android/iOS の Google Sign-In に渡す。
- [ ] Backend が発行した nonce を iOS の Sign in with Apple に渡す。
- [ ] Google/Apple の ID token とともに `challenge_id` を送信する。
- [ ] Apple の ID token、`authorization_code`、`challenge_id` をまとめて送信する。
- [ ] challenge の欠落、期限切れ、再利用、provider 不一致を処理し、既存のローカルセッションを上書きしない。
- [ ] 新しいセッションは `getMe` に成功してからローカルへ保存する。

### Logout とエラー処理

- [ ] 更新済み Backend のデプロイ後、Mobile の logout を GET から POST に切り替える。
- [ ] リモート logout に失敗してもローカルセッションは削除し、ローカルのみの logout だったことを表示する。
- [ ] Google/Apple のユーザーキャンセルを認証エラーと区別する。
- [ ] provider 追加操作のキャンセルや失敗時に、ログイン済み UI 状態を維持する。
- [ ] timeout、オフライン、未認証、予期しないエラーの表示を両プラットフォームで確認する。

### テストと動作確認

- [ ] challenge response の解析と、Google/Apple の challenge 付きリクエストに対するテストを復元する。
- [ ] `getMe` 失敗時に以前のセッションを上書きしないことを repository test で確認する。
- [ ] `./gradlew check --continue` を実行する。
- [ ] Android の Debug/Release をビルドし、それぞれ staging/production URL が使われることを確認する。
- [ ] iOS の Debug/Release をビルドし、それぞれ staging/production URL が使われることを確認する。
- [ ] staging に対して、実機で Google/Apple ログイン、provider 追加、logout、アカウント削除を確認する。
- [ ] 更新済み staging Backend に対して、リリース済みの旧アプリが動作することを確認する。

## Backend の追加対応

- [ ] Apple refresh token を長期的に production で保存する前に、保存時暗号化の方針を決める。
- [ ] Apple authorization code の交換と token revoke の integration test を追加する。
- [ ] challenge の作成、消費、期限切れ、replay、provider 不一致に対する handler test を追加する。
- [ ] session 削除を指定ユーザーに限定する既存 PR を完了する。この TODO では同じ修正を重複して実装しない。

## 後回しにした保守作業

- [ ] protobuf の source/generated code 同期と CI 検証を再検討する。
- [ ] `App.kt` の認証状態と副作用を ViewModel 相当の state holder に分離することを検討する。
