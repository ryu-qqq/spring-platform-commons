---
title: platform-outbox 2단계 — 트랜스포트 중립 relay 재설계 (Batch + PerItem)
date: 2026-06-08
project: spring-platform-commons
status: 설계 승인됨 (구현 대기)
---

# platform-outbox 2단계 — 트랜스포트 중립 relay 재설계

- **백로그**: vault `wiki/projects/spring-platform-commons/build-out-backlog.md` — P1 `platform-outbox` 2단계
- **선행 근거**: `2026-06-08-platform-sdk-audit-report.md`(6축 감사) + abstraction-critic dogfood 결과(N1 major·S1·C3)

## 1. 배경·문제

1단계는 FF의 generic Queue relay를 승격했으나, abstraction-critic(설계 게이트) dogfood가 **N1(major)**를 잡았다: outbox relay의 본질은 `claim→send→mark` 전이인데 트랜스포트(큐)가 공개 계약에 박혔다 — `QueueOutboxAdapter`·`enqueueBatch`·`OutboxEnqueueCommand`. build.gradle이 'Callback relay 2단계'를 명시해 비(非)큐 채널(HTTP 콜백) 추가가 확정돼 있으므로, **콜백 채널 추가 전인 지금이 개명 최저비용 시점**이다(소비자 0).

또한 critic은 **S1**(저장소+트랜스포트가 한 SPI에 융합)을 지적하되, 단일 소비자 맥락에서 선제 분리는 과추상(YAGNI)이라 info로 강등하고 "콜백 추가 시 재검토"를 권고했다. 본 2단계가 그 시점이다 — 두 트랜스포트(배치 큐, 건별 콜백)를 손에 쥐고 seam을 확정한다.

## 2. 결정 (승인됨)

1. **범위 = 풀 승격**: 중립 개명(N1) + 공유 수명주기 코어 추출 + Callback(건별) relay 추가.
2. **seam = 공유 base `OutboxStore<O>` + 트랜스포트별 relay 템플릿 2개**. dispatch shape(배치 2분기 vs 건별 4분기)가 템플릿 제어흐름 자체를 가르므로 "단일 템플릿+Dispatcher 전략"은 채택하지 않는다 — store에 배치용 아닌 defer/permanent 전이를 강요(ISP 위반)하거나 dispatcher가 발송+영속을 재융합(원점)하기 때문. 수명주기(안 변하는 축)는 공유 base, dispatch(변하는 축)는 트랜스포트별로 정직하게 둔다.
3. **네이밍 축 = dispatch shape**: `Batch*`(배치 발행) / `PerItem*`(건별 발송, HTTP 콜백이 대표). 트랜스포트명(Queue/Callback)을 코어 계약에서 제거.

## 3. 컴포넌트 (단일 `platform-outbox` 모듈, 패키지 `com.ryuqqq.platform.outbox`)

### 3.1 공유 코어

**`spi/OutboxStore<O>`** — 트랜스포트 무관 수명주기 base SPI:
- `String label()` · `String pipeline()` · `String outboxId(O)`
- `List<O> claimPendingMessages(int batchSize)` — PENDING→PROCESSING 원자 claim
- `void bulkMarkSent(List<String> ids, Instant now)`
- `void bulkMarkFailed(List<String> ids, Instant now, String errorMessage)`
- `void bulkReleaseToPending(List<String> ids)` — 인프라 예외 시 무차감 복귀

**`OutboxRetryPolicy`** (record): `int maxRetries`, `Duration maxDeferDuration`. 정적 팩토리 `defaults()`=(5, 6h) 편의값, 소비자 override. (배치 템플릿은 미사용; 건별 템플릿이 `maxDeferDuration`을 defer→FAILED 판정에 사용. `maxRetries`는 소비자 store 구현·문서용 공유 상수.)

**`exception/OutboxDispatchDeferredException`** — 일시 장애(예: CB OPEN). 건별 dispatch에서 던지면 retry 무차감 defer.
**`exception/OutboxDispatchPermanentException`** — 영구 실패(예: 4xx). 던지면 즉시 종결(markFailedPermanently).
(둘 다 `RuntimeException` 상속, 흐름제어 시그널.)

**common-domain: `com.ryuqqq.platform.common.outbox.OutboxStatus`** (enum): `PENDING`·`PROCESSING`·`SENT`·`FAILED`. 건별 템플릿이 deferRetry 후 FAILED 전이 감지에 사용.

### 3.2 배치 트랜스포트

**`dto/OutboxDispatchCommand`** (record) — `businessId`, `idempotencyKey`. (1단계 `OutboxEnqueueCommand` 개명.)
**`dto/OutboxBatchDispatchResult`** (record) — `successIds`, `failedEntries`(`FailedEntry(id, errorMessage)`), `allSuccess()`·`of()`·`hasFailures()` + null 정규화 compact 생성자. (1단계 `OutboxBatchSendResult` 개명.)
**`spi/BatchOutboxAdapter<O> extends OutboxStore<O>`** — 추가: `businessId(O)`·`idempotencyKey(O)`·`OutboxBatchDispatchResult dispatchBatch(List<OutboxDispatchCommand>)`. dispatchBatch 계약(전건 분리 반환·businessId 배치 내 유일)은 1단계 Javadoc 유지.
**`BatchOutboxRelayTemplate`** — `<O> SchedulerBatchProcessingResult relay(int batchSize, BatchOutboxAdapter<O>)`. 흐름·예외안전·errorSummary·메트릭은 1단계 `QueueOutboxRelayTemplate` 로직을 개명 이관(Set 매칭·MeterRegistry optional 유지).

### 3.3 건별 트랜스포트

**`spi/PerItemOutboxAdapter<O, T, P> extends OutboxStore<O>`** — 추가:
- `String taskId(O)` · `OutboxStatus outboxStatus(O)` · `String callbackUrl(O)` · `Instant createdAt(O)` · `String idempotencyKey(O)`
- `Map<String, T> preloadTasks(List<String> taskIds)` — N+1 회피 batch 조회
- `P buildPayload(O outbox, T task)`
- `void notify(String url, P payload, String idempotencyKey)` — 외부 통지. 실패 시 `OutboxDispatchDeferredException`/`OutboxDispatchPermanentException`/기타 예외로 신호
- `void deferRetry(O outbox, Instant now)` — 일시 장애 시 도메인 deferRetry + persist
- `void markFailedPermanently(O outbox, String errorMessage, Instant now)`

**`PerItemOutboxRelayTemplate`** — `<O,T,P> SchedulerBatchProcessingResult relay(int batchSize, PerItemOutboxAdapter<O,T,P>)`. 흐름: claim → preloadTasks → `ExecutorService` 병렬 dispatch, 4분기(success/deferred/permanent/failure) 수집 → applyResults(success·failure는 bulk, deferred는 deferRetry[+FAILED 전이 로깅], permanent는 markFailedPermanently). 예외 안전: dispatch 단계만 try로 좁혀 bulk 마킹 후 예외가 SENT를 덮지 않게. 생성자: `Duration maxDeferDuration`·`ExecutorService`·nullable `MeterRegistry`. (FF `CallbackOutboxRelayTemplate` 로직을 중립 개명 이관.)

## 4. 와이어링·메트릭

- `PlatformOutboxAutoConfiguration`: `BatchOutboxRelayTemplate` 빈을 `@ConditionalOnMissingBean`+`ObjectProvider<MeterRegistry>`로 등록(현행 개명).
- `PerItemOutboxRelayTemplate`은 **소비자 인스턴스화** — executor·defer 윈도가 소비자별이라 autoconfig가 강제하지 않는다(FF 동일). 자동설정은 건드리지 않음.
- 메트릭 `outbox.relay{pipeline, result}` 공유. 건별 result 값에 `deferred`·`permanent_failure` 추가.

## 5. 테스트 (피라미드)

- **공유**: `OutboxStore` 계약은 두 어댑터 fake가 행사(별도 단위 없음).
- **배치**: 기존 7 테스트 개명 이관(빈 claim·전건성공·부분실패·인프라예외 release·errorSummary·메트릭·null MeterRegistry).
- **건별** (fake `PerItemOutboxAdapter` + `SimpleMeterRegistry` + 직접 실행 executor): 4분기 각각(success→bulkMarkSent / failure→bulkMarkFailed / deferred→deferRetry / permanent→markFailedPermanently) · preload 호출·N+1 회피 · deferRetry 후 OutboxStatus.FAILED 감지 로깅 · dispatch 인프라 예외→bulkReleaseToPending · 메트릭 4 result · null MeterRegistry.
- **DTO**: `OutboxBatchDispatchResult` allSuccess/of/hasFailures/null 정규화.
- **자동설정 슬라이스**: `BatchOutboxRelayTemplate` 빈 등록·`@ConditionalOnMissingBean` 양보·MeterRegistry 없이 동작.

## 6. 마이그레이션

1단계 `Queue*`·`OutboxEnqueueCommand`·`OutboxBatchSendResult`는 **소비자 0**이라 파괴적 개명을 그대로 적용(deprecation 별칭 불필요). 기존 테스트는 개명 이관.

## 7. 검증·완료 기준

- [ ] `./gradlew :platform-outbox:test` green (배치 개명 + 건별 신규 + DTO + 자동설정)
- [ ] `./gradlew build` 전체 green — archrules·기존 모듈 포함
- [ ] `Queue`·`enqueue` 토큰이 공개 계약(spi·template·dto·메서드명)에서 제거됨
- [ ] **abstraction-critic 재실행 → CLEAN 수렴** (N1 해소·seam 분리 확인; dogfood 루프 닫기)
- [ ] 입양 스케치: FF Download/Transform의 Queue·Callback 서비스가 각 어댑터 구현만으로 수렴 가능함을 확인

## 8. 비목표

- 서버(FF·MP) 입양 마이그레이션 — build-out 완료 후.
- persistence(claim/mark SQL)·SQS 발행·HTTP notify 구현 — 소비자가 SPI로 제공.
- reactive 변형 / OutboxStatus 기반 모니터링 조회.

## 9. 변경 이력

| 날짜 | 변경 내용 | 작성자 |
|------|----------|--------|
| 2026-06-08 | 초안: 트랜스포트 중립 개명(N1) + 공유 OutboxStore seam + Batch/PerItem 2 템플릿 | ryu-qqq |

*최종 갱신: 2026-06-08*
