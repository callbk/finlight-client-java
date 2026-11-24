# finlight クライアント（Java / Kotlin）

*[English](README.md) | [简体中文](README.zh-CN.md) | 日本語 | [한국어](README.ko.md)*

> この翻訳は英語版 README をもとにしており、更新が遅れる場合があります。内容が異なる場合は [README.md](README.md) が優先です。

> **互換性のないメジャーリリース。** 1.0.0 は finlight API v3 のみに対応します。0.1.x 向けのコードは 1.x ではコンパイルできません。
> API v2 を使い続ける場合は `me.finlight:finlight-client:0.1.0` のままにしてください。0.1.x の新しいリリースはありません。
> [0.1.x からの移行](README.md#migrating-from-01x) を参照してください。

[finlight](https://finlight.me) ニュース API の JVM クライアントです。REST で金融ニュース・企業・ソースを検索・参照し、
WebSocket で記事をリアルタイムに受信し、Webhook を検証します。Java 17 で書かれており、Kotlin も同じ API を使えます
（JSpecify の null 注釈、ビルダー、sealed 型）。

## バージョン

<<TABLE 0>>

SDK のバージョンと API のバージョンは独立しています。サーバー側で追加されるフィールドや値は互換性のある 1.x リリースで提供されます。
API v3 に対応する他の finlight SDK：TypeScript 4.x、Python 3.x、PHP 2.x、Go モジュール `/v2`、.NET 2.x、Java 1.x、Ruby 1.x、Rust 0.2。

## 動作要件

- Java 17 以上（17 と 21 でテスト）
- Jackson `jackson-databind` 2.13.5 以上（2.13.5 と 2.18.3 でテスト）。公開 POM は 2.13.5 を下限とし、Jackson BOM を
  インポートしないため、ビルド側の Jackson バージョンがそのまま使われます。
- JSpecify 1.0（注釈のみ）

REST と WebSocket には JDK 標準の `java.net.http` クライアントを使い、その他の実行時依存はありません。

## インストール

Gradle（Kotlin DSL）：

<<CODE 0>>

Maven：

<<CODE 1>>

## クイックスタート

<<CODE 2>>

Kotlin：

<<CODE 3>>

REST 呼び出しは呼び出し元スレッドをブロックします。ビルダーの各メソッドは API のキー名と一致し、設定したキーだけが送信されます。

## 記事の検索

<<CODE 4>>

`sourceTypes` と `excludeSourceTypes` はソースの種類（`SourceType`：`newswire`、`press_release`、`financial_media`、
`general_media`、`aggregator`、`blog`、`government`）で絞り込みます。未分類のソースの記事は `sourceTypes` に一致せず、
`excludeSourceTypes` では残ります。どちらもソースプールの中で絞り込むだけで、ソースを追加することはありません。
未分類のソースでは `source().type()` と `Source.type()` は null です。

`searchAll` はページを自動でたどります。呼び出し時にリクエスト本文を固定し、以降のページではカーソルだけを追加します。
各ページがクォータを消費するため、`maxPages` ページ（既定 10）で停止します。

<<CODE 5>>

結果は一度だけ反復できます。形の誤り（例：`limit(0)` や空の `languages`）は送信前に `FinlightValidationException` になります。
未知のキーはコンパイルできません。語彙の値はローカルで検証しないため、サーバーの新しい値は SDK の更新なしで使えます。
日時はミリ秒付き UTC（`2026-11-01T00:00:00.000Z`）、日付のみは `YYYY-MM-DD`、文字列はそのまま送信されます。

## 記事・企業・ソースの参照

<<CODE 6>>

- 見つからない場合は `NotFoundException` になります（404 は空の結果ではなくエラーです）。id 導入前に登録された記事はリンクでのみ参照できます。
- 企業の参照には `entityAccess` 権限が必要です（ない場合は `ForbiddenException`）。
- `companies().get(id)` はレコードを ETag とともに保持して再検証します。変更がなければキャッシュから返し、
  `cacheInfo().notModified()` が true になります。独自の `ifNoneMatch` を渡した場合、304 は null を返します。
- リスト（`CompanyList`、`SourceList`、`ArticlePage.articles()`）は読めない要素をスキップして警告を記録し、
  インデックスと生の JSON を `skipped()` に残します。

## キーなし、null、空リスト

プランや `include` に依存するキーには 3 つの状態があり、`Gated<T>` で表します：

<<TABLE 1>>

<<CODE 7>>

`content` と `sentiment` は「キーなし」「null」「値」のいずれかです。`companies`、`events`、`events[].companyIds`、
`Source.hasContent` は「キーなし」か「値」です（`[]` も値です）。`companyIds` は同じペイロードに `companies` がある場合のみ送られます。
キーなしが `[]`、`false`、`0` に変換されることはありません。

タイムスタンプは送られた文字列のまま保持されます（`publishedAt()`）。`publishedAtTime()` などの `*Time()` は RFC 3339 で厳密に解析し、
読めない値には null を返します。新しいサーバーの未知のキーは無視され、`Article`、`CompanyRecord`、`Source`、`StreamEvent`、
`WebhookEvent`、API エラーの `rawJson()` は受信した JSON を返します。語彙の値はオープンです。定数と比較してください
（`CompanyRole.ACQUIRER`、`switch` では `EventTypeValues.MNA`）。未知の値は送られたまま保持されます。

## リアルタイムストリーム

<<CODE 8>>

Kotlin では `onEvent` にラムダを渡せます：`client.stream(params) { e -> println(e.event()) }`。

- `client.rawStream(RawStreamParams, listener)` はエンリッチ前の記事を配信します（`<wssUrl>/raw`）。`include`、content、
  sentiment、companies、events はなく、`availableAt` は常に null です。
- リスナーのメソッドはストリームの 1 つのデーモンスレッドで順番に実行されます。遅いリスナーがキープアライブを誤検知させることはなく、
  例外は記録されてストリームは継続します。`close()` の後はイベントが届かず、最後に `onClosed` が呼ばれます。
  `stream.awaitTermination()` は終了までブロックします。
- ストリームは 25 秒ごとに ping を送り、60 秒 pong がなければ接続を置き換え、115 分後に接続を更新し、再接続のたびに同じ購読を送ります。
  重複は破棄されます。同じ記事の `article.created` と `article.updated` は別のイベントです。
- 欠落分の自動取得は行いません。`onReconnect` には最後に見た `availableAt`（raw では `firstSeenAt`）が渡されます。
  必要ならその時刻から `orderBy` `availableAt`（raw では `firstSeenAt`）で `searchAll` してください。
- 終了を伴う問題は `onClosed(terminal)` でストリームを終わらせます：無効なキー（`UnauthorizedException`）、WebSocket 権限なし
  （`ForbiddenException`）、購読の拒否（`ValidationFailedException`）、別の接続に引き継がれた場合の理由 `connection_limit` の
  `PreemptedException`。それ以外の問題は再接続し、`onError` に渡されます。
- 月間の WebSocket クォータを使い切った場合（接続が "quota exceeded" で拒否される、または理由 `quota_exceeded` の
  `PreemptedException`）、ストリームは終了せず、クォータが戻るまで 1 時間ごとに再試行します。待機のたびに警告を記録し
  `onQuotaWait` を呼びます。`quotaReconnect(false)` でエラー終了に切り替え、`quotaReconnectInterval` で間隔を変更できます。

知っておくべき制限：

- プランの接続数上限は、エンリッチ、raw、旧 SDK の接続で共有されます。上限時の接続は拒否され、SDK は 60 秒後に再試行します。
  `StreamOptions.takeover(true)` を使うと、代わりに最も古い接続を置き換えます。
- 購読のたびに、サーバーは一致する最新記事を 1 件送ります。古い記事の場合もあります。
- ゲートウェイは 2 時間で接続を終了します。SDK はその前に更新します。
- 32,000 バイトを超えるライブフレームはサーバーで通知なく破棄されます。長い記事で content を含めると起こり得ます。
  `articles().get(id)` で取得してください。
- `connection.admitted`（`onAdmitted`）はサーバーが購読を検証する前に届きます。購読が受け入れられたことを意味しません。

## Webhook

finlight はすべての Webhook に署名します。フレームワークが解析する前に、**生の**リクエスト本文で検証してください：

<<CODE 9>>

- `headers` は文字列・コレクション・配列を値に持つ任意の `Map<String, ?>` です（Servlet、Spring、JDK の
  `HttpExchange.getRequestHeaders()`）。名前は大文字小文字を区別しません。`constructEvent(body, signature, timestamp, secret)`
  は 2 つのヘッダー値を直接受け取り、`verifySignature` は検証のみ行います。
- タイムスタンプは前後 300 秒（`WebhookVerifyOptions.tolerance`）以内である必要があります。
- ダッシュボードのテスト Webhook では `event.isTest()` が true になり、プランによる削減はありません。
- 410 で応答しないでください。Webhook が即座に無効になります。2xx 以外が 10 回続いても無効になります。

生の本文の取得方法：

<<TABLE 2>>

Webhook を v3 に切り替える手順：まずこの SDK をデプロイし、`WrongPayloadVersion` に 2xx で応答して `raw()` を保持します。
次にダッシュボードで Webhook を `payloadVersion` 3 に切り替え、約 2 時間待ちます。切り替え前にキューに入った再送は旧形式で届くためです。

## エラーと再試行

すべてのエラーは `FinlightException`（`RuntimeException`）を継承し、`kind()` を持ちます：

<<CODE 10>>

- 5xx、ネットワークエラー、試行のタイムアウトは、ジッター付きで間隔を伸ばしながら最大 3 回再試行します（POST の検索も含む）。
- 400、401、403、404 は再試行しません。月間の 429 は即座に失敗し、`searchAll` でも同様です。REST は新しいクォータ期間を待ちません（待つのはストリームのみ）。
- バーストの 429 は `Retry-After` ちょうど（ヘッダーがなければ 10 秒）待って 1 回だけ再試行します。既定で有効で、`maxRetries` には含まれません。
  `retryOnBurst(RetryOnBurst.OFF)` で即座に例外になります。
- 各呼び出しは独自の `X-Request-Id` を送り、再試行でも同じ値を使います。`requestId()` で参照できます。
- JDK の `HttpClient` は、接続できなかったときやプール内の接続が閉じていたときに、GET を自ら再送することがあります。この再送は
  `X-Request-Id` を保ち、`maxRetries` には含まれません。`-Djdk.httpclient.disableRetryConnect=true` で接続失敗後の再送を
  JVM 全体で無効にできます。閉じたプール接続での再送は無効にできません。
- 呼び出しスレッドを割り込むと呼び出しはキャンセルされます。待機は即座に終わり、再試行せず、割り込みフラグを戻したうえで
  `java.util.concurrent.CancellationException` を投げます。

## 設定

<<CODE 11>>

<<TABLE 3>>

`httpClient(java.net.http.HttpClient)` で独自のクライアントを REST と WebSocket に使えます（プロキシ、TLS、Executor）。
その接続タイムアウトとプロキシ設定が適用され、SDK がそれを閉じることはありません。独自の再試行で包まないでください。試行ごとにクォータを消費します。JDK 自身の GET 再送（「エラーと再試行」を参照）もこのクライアントに当てはまります。
`client.close()` はそのクライアントのすべてのストリームを終了し、SDK が作成した HTTP クライアントを停止します。

## ログ

SDK はプラットフォームロガー `System.getLogger("finlight")` でログを出します（別のバックエンド、例えば
`slf4j-jdk-platform-logging` を入れない限り java.util.logging）。詳細は DEBUG、問題は WARNING で、既定ではそれ以外は出力されず、
SDK 自身が stdout や stderr に書くことはありません。API キー、Webhook シークレット、署名、本文はログやエラーメッセージに出ず、
`FinlightConfig.toString()` もキーを隠します。

## データについて

- 古い記事の `publishedAt` が `"1970-01-01T00:00:00.000Z"` の場合があります。送られたまま配信されます。
- 空のテキストは null で配信されます（`summary`、`content`）。`""` にはなりません。
- ロール導入前にタグ付けされた記事は `role` と `relevance` が null、`events` が `[]` です。
- id 導入前に登録された記事は `articles().get(id)` で 404 になります。`getByLink` を使ってください。
- 古い記事の `availableAt` は null で、raw ストリームでは常に null です。

## 0.1.x からの移行

詳しいガイドは [docs.finlight.me](https://docs.finlight.me) にあります。概要：

<<TABLE 4>>

0.1.x と 1.x は同じパッケージ名のため、1 つのクラスパスに同居できません。また、同じキーで 0.1.x と 1.x のストリームを並行して動かさないでください。
接続数上限を共有します。

Webhook の切り替え：1.x をデプロイし、`WrongPayloadVersion` に 2xx で応答して `raw()` を保持し、ダッシュボードで
`payloadVersion` 3 に切り替え、キュー内の再送のために約 2 時間待ちます。

## 開発

<<CODE 12>>

コントラクトキット（`conformance/`、バージョンは `conformance/CONTRACT_VERSION`）はリポジトリに同梱され、sha256 の `MANIFEST`
で検証されます。`CONFORMANCE.md` は各ケースと対応するテストを対応付けます。`examples/` の 5 つのサンプルはテストスイートで
コンパイルされ、ローカルのモックに対して実行されます。ライブチェックは dev 環境のみを対象とし、`./gradlew test` には含まれず、
`FINLIGHT_API_KEY`、`FINLIGHT_BASE_URL`、`FINLIGHT_WSS_URL` が必要です：`./gradlew liveTest`。

## ライセンス

MIT
