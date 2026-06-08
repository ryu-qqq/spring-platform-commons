# platform-outbox 설계 — Queue Outbox relay 승격 (1단계)

- **일자:** 2026-06-08
- **프로젝트:** spring-platform-commons (공통 자산 build-out)
- **백로그:** vault `wiki/projects/spring-platform-commons/build-out-backlog.md` — P1 `platform-outbox`
- **상태:** 설계 승인됨 (구현 대기)

## 1. 배경·문제

백로그는 outbox 를 "relay template + adapter SPI; FileFlow Template 참조"로 적었다. 실측 결과:

- **generic relay Template 는 FileFlow 단독 자산이다.** FF 는 `QueueOutboxRelayTemplate<O>` +
  `CallbackOutboxRelayTemplate<O,T,P>` + SPI 어댑터로 잘 추상화했다.
- **MarketPlace 에는 generic 템플릿이 없다.** 도메인별 bespoke 프로세서
  (`OutboundSellerOutboxProcessor`·`QnaOutbox`·`ShipmentOutbox`·`SellerAuthOutbox`·`LegacyConversion` …)가
  제각각이다.

→ 보안(MP·FF 둘 다 동일 필터 보유 → 수렴)과 달리, 본 작업은 **"FF 의 좋은 추상화를 platform 으로 승격"**
하여 향후 재사용 기반을 만드는 것이다. MP·FF 교차 evidence 는 약하다.

또한 FF 추상화는 표면적이 크고 application 레이어 DTO·도메인 VO·예외·메트릭에 얽혀 있으며, Queue relay
(@Component, 단순)와 Callback relay(executor·4분기·예외 시그널, 무거움)의 무게가 크게 다르다.

## 2. 결정 (승인됨)

1. **Queue relay 만 먼저 승격 (1단계).** 최소·증분으로 패턴을 검증하고 diff 를 작게 유지. Callback relay·
   `OutboxStatus`·`OutboxRetryPolicy` 는 2단계로 deferred (Queue 템플릿이 직접 사용하지 않음).
2. **메트릭은 MeterRegistry optional.** platform-scheduler 는 MeterRegistry 필수였으나, relay 는 메트릭
   없이도 실제 일을 하므로 optional 로 둔다 (template 은 null 이면 metric no-op). 메트릭 기록 실패는 swallow.

## 3. 모듈·배치

- 새 모듈 `platform-outbox`, 패키지 `com.ryuqqq.platform.outbox`. **application 레이어 오케스트레이션** 성격.
- 의존성:
  - `api project(':platform-common-application')` — 반환형 `SchedulerBatchProcessingResult`
    (`com.ryuqqq.platform.common.scheduler`, 이미 존재)를 공개 API 로 노출하므로 api.
  - `implementation` spring-boot-dependencies(platform)·spring-boot-autoconfigure·micrometer-core·slf4j-api
  - `testImplementation` junit-bom(platform)·bundles.testing
  - `testRuntimeOnly` junit-platform-launcher
- `settings.gradle`: `include 'platform-outbox'` + projectDir. 루트 `sdkProjects`(`:platform-*`)가
  java-library·maven-publish·sources/javadoc 자동 적용 (platform-scheduler 와 동일).
- web/tx/domain·persistence·SQS 무의존 — 그 구현은 소비측이 SPI 로 제공.

## 4. 컴포넌트

### 4.1 `dto/OutboxEnqueueCommand` (record)
Queue outbox 릴레이 시 발행에 필요한 `businessId` + `idempotencyKey` 쌍.

### 4.2 `dto/OutboxBatchSendResult` (record)
배치 발행 건별 성공/실패 추적. `List<String> successIds`, `List<FailedEntry> failedEntries`
(`FailedEntry(String id, String errorMessage)`). 정적 팩토리 `allSuccess(ids)`·`of(successIds, failedEntries)`,
`boolean hasFailures()`.

### 4.3 `spi/QueueOutboxAdapter<O>`
Queue 릴레이의 도메인 의존부를 추상화한 어댑터 (소비측 구현). `@param <O>` 도메인 Outbox 타입.
- `String label()` — 로그 라벨
- `String pipeline()` — 메트릭 태그용 저카디널리티 식별자
- `String outboxId(O)` — bulkMark 대상 ID
- `String businessId(O)` — 발행 메시지 본문, sendResult 매칭 키
- `String idempotencyKey(O)` — 멱등키
- `List<O> claimPendingMessages(int batchSize)` — PENDING → PROCESSING 원자 claim
- `OutboxBatchSendResult enqueueBatch(List<OutboxEnqueueCommand>)` — 일괄 발행, 성공·실패 분리
- `void bulkMarkSent(List<String> outboxIds, Instant now)`
- `void bulkMarkFailed(List<String> outboxIds, Instant now, String errorMessage)`
- `void bulkReleaseToPending(List<String> outboxIds)` — 인프라 예외 시 무차감 PENDING 복귀

### 4.4 `QueueOutboxRelayTemplate`
`<O> SchedulerBatchProcessingResult relay(int batchSize, QueueOutboxAdapter<O> adapter)`.

흐름:
1. `claimPendingMessages(batchSize)` → 비면 `SchedulerBatchProcessingResult.empty()`.
2. claim 한 항목으로 `OutboxEnqueueCommand` 목록 빌드 후 **try** `enqueueBatch`.
   - 인프라 예외 시: `bulkReleaseToPending(claimedOutboxIds)` 후 `of(claimed.size(), 0, 0)` 반환
     (retry_count 무차감). **try 는 enqueueBatch 만 감싼다.**
3. `applySendResults`: success ids → `bulkMarkSent`, 실패 있으면 errorSummary 만들어 `bulkMarkFailed`.
   bulk 마킹은 try 밖에서 실행되어 markSent 예외가 claim 전체를 markFailed 로 덮어쓰지 않는다.
4. `outbox.relay` 카운터 `{pipeline, result=success|failure}` 기록 후
   `of(total, success, failed)` 반환.

세부:
- 에러요약: `failedEntries` 의 errorMessage 를 `distinct().limit(3)` 후 `"; "` join (DB 컬럼 길이 보호).
- 메트릭: 생성자 `MeterRegistry`(null 허용). null 이면 no-op. 기록 중 예외는 warn 로그 후 swallow.

### 4.5 `config/PlatformOutboxAutoConfiguration`
`@AutoConfiguration` + `@ConditionalOnClass(SchedulerBatchProcessingResult.class)`.
- `@Bean @ConditionalOnMissingBean QueueOutboxRelayTemplate queueOutboxRelayTemplate(ObjectProvider<MeterRegistry> registries)`
  → `new QueueOutboxRelayTemplate(registries.getIfAvailable())`. MeterRegistry 없어도 빈 등록.
- `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 등록.

## 5. 테스트 (피라미드)

- **단위** (`QueueOutboxRelayTemplate`, fake `QueueOutboxAdapter` + `SimpleMeterRegistry`):
  - 빈 claim → `empty()`, enqueueBatch 미호출
  - 전건 성공 → `bulkMarkSent` 전체 ids, `bulkMarkFailed` 미호출, 결과 `(n, n, 0)`
  - 부분 실패 → success 는 markSent, failed 는 markFailed(errorSummary 포함), 결과 `(n, s, f)`
  - `enqueueBatch` 예외 → `bulkReleaseToPending` 전체, markSent/markFailed 미호출, 결과 `(n, 0, 0)`
  - errorSummary dedup·최대 3건
  - 메트릭: `outbox.relay{pipeline,result}` 카운터 증가 검증
  - `MeterRegistry == null` → NPE 없이 정상 동작
- **DTO** 경량: `OutboxBatchSendResult.allSuccess/of/hasFailures`
- **자동설정 슬라이스** (`ApplicationContextRunner`): 템플릿 빈 등록, 소비측 동일 타입 빈 정의 시
  `@ConditionalOnMissingBean` 양보, MeterRegistry 없이도 빈 등록.

## 6. 검증·완료 기준

- [ ] `./gradlew :platform-outbox:test` green
- [ ] `./gradlew build` 전체 green — archrules·기존 모듈 포함
- [ ] `com.ryuqqq.platform.outbox` 패키지·기존 SDK 컨벤션 준수
- [ ] 범위 준수: Queue-only, platform-outbox 는 persistence/SQS/web 무의존, 서버 코드 미변경
- [ ] 입양 스케치로 FF Download/Transform Queue 서비스가 SPI 구현만으로 수렴 가능함을 확인

## 7. 입양 적합성 (deferred — 본 작업 범위 아님, 설계 검증용)

FF 의 `ProcessDownloadQueueOutboxService`·`ProcessTransformQueueOutboxService` 가 각각
`QueueOutboxAdapter` 를 구현하면 두 서비스의 claim→enqueue→bulkMark 흐름이 platform 템플릿으로 수렴한다
(동작 변경 0). MP 는 추후 도메인별 adapter 구현 시 합류 가능.

## 8. 비목표

- **Callback relay** (executor 병렬발송·4분기·`ExternalServiceUnavailableException`/`PermanentCallbackFailureException`
  시그널)·`OutboxStatus`·`OutboxRetryPolicy` — 2단계 별도 작업.
- 서버(FF·MP) 입양 마이그레이션 — build-out 완료 후.
- persistence(JPA·QueryDSL)·SQS 발행 구현 — 소비측이 SPI 로 제공.
- reactive 변형.
