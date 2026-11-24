# finlight Java·Kotlin 클라이언트

*[English](README.md) | [简体中文](README.zh-CN.md) | [日本語](README.ja.md) | 한국어*

> 이 번역은 영어 README를 바탕으로 하며 업데이트가 늦을 수 있습니다. 내용이 다르면 [README.md](README.md)가 우선합니다.

> **호환되지 않는 메이저 릴리스.** 1.0.0은 finlight API v3만 지원합니다. 0.1.x용 코드는 1.x에서 컴파일되지 않습니다.
> API v2를 계속 쓰려면 `me.finlight:finlight-client:0.1.0`을 유지하세요. 0.1.x는 더 이상 릴리스되지 않습니다.
> [0.1.x에서 마이그레이션](README.md#migrating-from-01x)을 참고하세요.

[finlight](https://finlight.me) 뉴스 API의 JVM 클라이언트입니다. REST로 금융 뉴스, 기업, 소스를 검색·조회하고, WebSocket으로
기사를 실시간 수신하며, Webhook을 검증합니다. Java 17로 작성되었고 Kotlin도 같은 API를 사용합니다(JSpecify null 표시, 빌더, sealed 타입).

## 버전

<<TABLE 0>>

SDK 버전과 API 버전은 서로 독립적입니다. 서버에 추가되는 필드와 값은 호환되는 1.x 릴리스로 제공됩니다. API v3를 지원하는
다른 finlight SDK: TypeScript 4.x, Python 3.x, PHP 2.x, Go 모듈 `/v2`, .NET 2.x, Java 1.x, Ruby 1.x, Rust 0.2.

## 요구 사항

- Java 17 이상(17과 21에서 테스트)
- Jackson `jackson-databind` 2.13 이상(2.13.5와 2.18.3으로 테스트)
- JSpecify 1.0(어노테이션만)

REST와 WebSocket은 JDK의 `java.net.http` 클라이언트를 사용하며 다른 런타임 의존성은 없습니다.

## 설치

Gradle(Kotlin DSL):

<<CODE 0>>

Maven:

<<CODE 1>>

## 빠른 시작

<<CODE 2>>

Kotlin:

<<CODE 3>>

REST 호출은 호출한 스레드를 블록합니다. 빌더의 각 메서드는 API 키 이름과 같으며, 설정한 키만 전송됩니다.

## 기사 검색

<<CODE 4>>

`searchAll`은 페이지를 자동으로 넘깁니다. 호출 시점에 요청 본문을 고정하고 이후 페이지에서는 커서만 추가합니다.
페이지마다 할당량을 쓰므로 `maxPages` 페이지(기본 10)에서 멈춥니다.

<<CODE 5>>

결과는 한 번만 순회할 수 있습니다. 형태 오류(예: `limit(0)`이나 빈 `languages`)는 전송 전에 `FinlightValidationException`이 됩니다.
알 수 없는 키는 컴파일되지 않습니다. 어휘 값은 로컬에서 검증하지 않으므로 서버의 새 값은 SDK 업데이트 없이 동작합니다.
날짜·시간은 밀리초를 포함한 UTC(`2026-11-01T00:00:00.000Z`), 날짜만은 `YYYY-MM-DD`, 문자열은 그대로 전송됩니다.

## 기사, 기업, 소스 조회

<<CODE 6>>

- 찾지 못하면 `NotFoundException`이 발생합니다(404는 빈 결과가 아니라 오류입니다). id 도입 전에 색인된 기사는 링크로만 조회됩니다.
- 기업 조회에는 `entityAccess` 권한이 필요합니다(없으면 `ForbiddenException`).
- `companies().get(id)`는 레코드를 ETag와 함께 보관하고 재검증합니다. 바뀌지 않았으면 캐시에서 반환하며
  `cacheInfo().notModified()`가 true입니다. 직접 `ifNoneMatch`를 주면 304는 null을 반환합니다.
- 목록(`CompanyList`, `SourceList`, `ArticlePage.articles()`)은 읽을 수 없는 항목을 건너뛰고 경고를 남기며,
  인덱스와 원본 JSON을 `skipped()`에 보관합니다.

## 키 없음, null, 빈 값

요금제나 `include`에 따라 달라지는 키는 세 가지 상태를 가지며 `Gated<T>`로 표현됩니다:

<<TABLE 1>>

<<CODE 7>>

`content`와 `sentiment`는 키 없음, null, 값 중 하나입니다. `companies`, `events`, `events[].companyIds`, `Source.hasContent`는
키 없음 또는 값입니다(`[]`도 값입니다). `companyIds`는 같은 페이로드에 `companies`가 있을 때만 전송됩니다. 키 없음은 절대
`[]`, `false`, `0`으로 바뀌지 않습니다.

타임스탬프는 전송된 문자열 그대로 유지됩니다(`publishedAt()`). `publishedAtTime()` 등 `*Time()` 메서드는 RFC 3339로 엄격하게 파싱하고
읽을 수 없으면 null을 반환합니다. 새 서버의 알 수 없는 키는 무시되며, `Article`, `CompanyRecord`, `Source`, `StreamEvent`,
`WebhookEvent`와 API 오류의 `rawJson()`은 받은 JSON을 반환합니다. 어휘 값은 열려 있습니다. 상수와 비교하세요
(`CompanyRole.ACQUIRER`, `switch`에서는 `EventTypeValues.MNA`). 알 수 없는 값은 받은 그대로 유지됩니다.

## 실시간 스트림

<<CODE 8>>

Kotlin에서는 `onEvent`에 람다를 넘길 수 있습니다: `client.stream(params) { e -> println(e.event()) }`.

- `client.rawStream(RawStreamParams, listener)`는 보강 전 기사를 전달합니다(`<wssUrl>/raw`). `include`, content, sentiment,
  companies, events가 없고 `availableAt`은 항상 null입니다.
- 리스너 메서드는 스트림의 데몬 스레드 하나에서 순서대로 실행됩니다. 느린 리스너가 keep-alive를 오작동시키지 않으며, 리스너의 예외는
  기록되고 스트림은 계속됩니다. `close()` 후에는 이벤트가 오지 않고 마지막으로 `onClosed`가 호출됩니다. `stream.awaitTermination()`은
  끝날 때까지 블록합니다.
- 스트림은 25초마다 ping을 보내고, 60초 동안 pong이 없으면 연결을 교체하며, 115분 후 연결을 갱신하고, 재연결 때마다 같은 구독을 보냅니다.
  중복은 버려집니다. 같은 기사의 `article.created`와 `article.updated`는 별개의 이벤트입니다.
- 누락분을 자동으로 채우지 않습니다. `onReconnect`에는 마지막으로 본 `availableAt`(raw는 `firstSeenAt`)이 전달됩니다. 필요하면 그 시각부터
  `orderBy` `availableAt`(raw는 `firstSeenAt`)으로 `searchAll`을 호출하세요.
- 종료성 문제는 `onClosed(terminal)`로 스트림을 끝냅니다: 잘못된 키(`UnauthorizedException`), WebSocket 권한 없음(`ForbiddenException`),
  구독 거부(`ValidationFailedException`), 다른 연결이 넘겨받은 경우 이유가 `connection_limit`인 `PreemptedException`.
  그 밖의 문제는 재연결하고 `onError`로 전달됩니다.
- 월간 WebSocket 할당량을 다 쓰면(연결이 "quota exceeded"로 거부되거나 이유가 `quota_exceeded`인 `PreemptedException`) 스트림은 끝나지 않고
  할당량이 허용될 때까지 1시간마다 다시 시도합니다. 대기할 때마다 경고를 남기고 `onQuotaWait`를 호출합니다. `quotaReconnect(false)`로 오류 종료로
  바꿀 수 있고 `quotaReconnectInterval`로 간격을 바꿀 수 있습니다.

알아 둘 제한:

- 요금제의 연결 수 제한은 보강, raw, 이전 SDK 연결이 함께 씁니다. 제한에 걸린 연결은 거부되고 SDK는 60초 후 다시 시도합니다.
  `StreamOptions.takeover(true)`를 쓰면 대신 가장 오래된 연결을 교체합니다.
- 구독할 때마다 서버는 일치하는 최신 기사 한 건을 먼저 보냅니다. 오래된 기사일 수 있습니다.
- 게이트웨이는 2시간 후 모든 연결을 끊습니다. SDK는 그 전에 갱신합니다.
- 32,000바이트를 넘는 라이브 프레임은 서버에서 알림 없이 버려집니다. 긴 기사에 content를 포함하면 생길 수 있습니다.
  `articles().get(id)`로 가져오세요.
- `connection.admitted`(`onAdmitted`)는 서버가 구독을 확인하기 전에 도착합니다. 구독이 수락되었다는 뜻이 아닙니다.

## Webhook

finlight는 모든 Webhook에 서명합니다. 프레임워크가 파싱하기 전에 **원본** 요청 본문으로 검증하세요:

<<CODE 9>>

- `headers`는 문자열, 컬렉션, 배열 값을 가진 아무 `Map<String, ?>`입니다(Servlet, Spring, JDK `HttpExchange.getRequestHeaders()`).
  이름은 대소문자를 구분하지 않습니다. `constructEvent(body, signature, timestamp, secret)` 오버로드는 두 헤더 값을 직접 받고,
  `verifySignature`는 검증만 합니다.
- 타임스탬프는 앞뒤 300초(`WebhookVerifyOptions.tolerance`) 이내여야 합니다.
- 대시보드 테스트 Webhook은 `event.isTest()`가 true이며 요금제에 따라 잘리지 않습니다.
- 410으로 응답하지 마세요. Webhook이 즉시 비활성화됩니다. 2xx가 아닌 응답이 10번 연속되어도 비활성화됩니다.

원본 본문 얻기:

<<TABLE 2>>

Webhook을 v3로 전환하기: 먼저 이 SDK를 배포하고 `WrongPayloadVersion`에 2xx로 응답하며 `raw()`를 보관합니다. 그다음 대시보드에서
Webhook을 `payloadVersion` 3으로 바꾸고 약 2시간 기다립니다. 전환 전에 대기열에 들어간 재시도는 이전 형식으로 도착하기 때문입니다.

## 오류와 재시도

모든 오류는 `FinlightException`(`RuntimeException`)을 상속하며 `kind()`를 가집니다:

<<CODE 10>>

- 5xx, 네트워크 오류, 시도 타임아웃은 지터를 넣어 간격을 늘리며 최대 3번 재시도합니다(POST 검색 포함).
- 400, 401, 403, 404는 재시도하지 않습니다. 월간 429는 즉시 실패하며 `searchAll`에서도 같습니다. REST는 새 할당량 기간을 기다리지 않습니다
  (기다리는 것은 스트림뿐입니다).
- 버스트 429는 정확히 `Retry-After`만큼(헤더가 없으면 10초) 기다린 뒤 한 번 더 시도합니다. 기본으로 켜져 있고 `maxRetries`에 포함되지 않습니다.
  `retryOnBurst(false)`는 즉시 예외를 던집니다.
- 호출마다 자체 `X-Request-Id`를 보내며 재시도에서도 같은 값을 씁니다. `requestId()`로 확인할 수 있습니다.
- 호출 스레드를 인터럽트하면 호출이 취소됩니다. 대기는 즉시 끝나고 재시도하지 않으며, 인터럽트 플래그를 복원한 뒤
  `java.util.concurrent.CancellationException`을 던집니다.

## 설정

<<CODE 11>>

<<TABLE 3>>

`httpClient(java.net.http.HttpClient)`는 직접 만든 클라이언트를 REST와 WebSocket에 사용합니다(프록시, TLS, Executor). 그 연결 타임아웃과
프록시 설정이 적용되고 SDK는 이를 닫지 않습니다. 자체 재시도로 감싸지 마세요. 시도마다 할당량을 씁니다. `client.close()`는 그 클라이언트의
모든 스트림을 끝내고 SDK가 만든 HTTP 클라이언트를 종료합니다.

## 로깅

SDK는 플랫폼 로거 `System.getLogger("finlight")`로 기록합니다(`slf4j-jdk-platform-logging` 같은 다른 백엔드를 설치하지 않으면
java.util.logging). 세부 내용은 DEBUG, 문제는 WARNING이며 기본으로는 그 외에 아무것도 출력하지 않고, SDK가 직접 stdout이나 stderr에 쓰지
않습니다. API 키, Webhook 시크릿, 서명, 본문은 로그나 오류 메시지에 절대 나오지 않으며 `FinlightConfig.toString()`도 키를 숨깁니다.

## 데이터 참고

- 오래된 기사의 `publishedAt`이 `"1970-01-01T00:00:00.000Z"`일 수 있습니다. 받은 그대로 전달됩니다.
- 빈 텍스트는 null로 전달됩니다(`summary`, `content`). `""`가 되지 않습니다.
- 역할 도입 전에 태깅된 기사는 `role`과 `relevance`가 null이고 `events`는 `[]`입니다.
- id 도입 전에 색인된 기사는 `articles().get(id)`에서 404가 됩니다. `getByLink`를 쓰세요.
- 오래된 기사의 `availableAt`은 null이며 raw 스트림에서는 항상 null입니다.

## 0.1.x에서 마이그레이션

전체 가이드는 [docs.finlight.me](https://docs.finlight.me)에 있습니다. 요약:

<<TABLE 4>>

0.1.x와 1.x는 같은 패키지를 쓰므로 하나의 클래스패스에 함께 둘 수 없습니다. 같은 키로 0.1.x와 1.x 스트림을 동시에 실행하지도 마세요.
연결 수 제한을 공유합니다.

Webhook 전환: 1.x를 배포하고 `WrongPayloadVersion`에 2xx로 응답하며 `raw()`를 보관한 뒤, 대시보드에서 `payloadVersion` 3으로 바꾸고
대기 중인 재시도를 위해 약 2시간 기다립니다.

## 개발

<<CODE 12>>

계약 키트(`conformance/`, 버전은 `conformance/CONTRACT_VERSION`)는 저장소에 포함되어 sha256 `MANIFEST`로 검증됩니다.
`CONFORMANCE.md`는 키트의 각 케이스를 해당 테스트에 연결합니다. `examples/`의 다섯 예제는 테스트 스위트에서 컴파일되고 로컬 목 서버에서
실행됩니다. 라이브 점검은 dev 환경만 대상으로 하며 `./gradlew test`에 포함되지 않고 `FINLIGHT_API_KEY`, `FINLIGHT_BASE_URL`,
`FINLIGHT_WSS_URL`이 필요합니다: `./gradlew liveTest`.

## 라이선스

MIT
