# platform-outbox

**Application 레이어 트랜스포트 중립 Outbox relay SDK.**

claim → dispatch → mark 수명주기는 공통 템플릿이 책임지고, 실제 발행/저장(SQS·HTTP·persistence)은
소비측이 SPI 어댑터로 제공한다. 여러 도메인의 Outbox 릴레이 흐름을 한 곳으로 수렴시키는 것이 목표다.

## 역할

Outbox 테이블에 쌓인 PENDING 메시지를 주기적으로 claim 해서 외부로 발행하고, 결과에 따라 상태를
전이(SENT / FAILED / PENDING 복귀)시키는 **릴레이 공통 흐름**을 제공한다. 발행 트랜스포트가 무엇이든
(배치 큐, 건별 HTTP 콜백) 공유되는 수명주기는 base SPI 하나로 묶고, 트랜스포트별로 갈리는 발송 로직만
어댑터로 분리한다.

- **트랜스포트 중립** — 발송 채널을 모듈 밖(소비측 어댑터)으로 밀어낸다. 이 모듈은 SQS·HTTP 등 어떤
  인프라에도 직접 의존하지 않는다.
- **예외 안전** — dispatch 단계만 try 로 좁혀, bulk 마킹 중 예외가 claim 전체를 잘못된 상태로 덮어쓰지
  않게 한다. dispatch 인프라 예외는 retry 무차감으로 PENDING 복귀.
- **메트릭 optional** — `MeterRegistry` 가 없으면 메트릭은 no-op. 로깅/전이는 메트릭에 의존하지 않는다.

## 상태 모델 — `OutboxStatus`

```
PENDING → PROCESSING → SENT | FAILED
```

`platform-common-domain` 의 `com.ryuqqq.platform.common.outbox.OutboxStatus` 에 정의되어 있다.

| 상태 | 의미 |
|------|------|
| `PENDING` | 발행 대기. claim 대상. |
| `PROCESSING` | claim 되어 발송 진행 중. |
| `SENT` | 발송 성공 (종결). |
| `FAILED` | 재시도 한도 초과 또는 영구 실패 (dead-letter). |

claim 은 `PENDING → PROCESSING` 원자 전이여야 한다(동시 릴레이 중복 방지). dispatch 인프라 예외 시에는
`PROCESSING → PENDING` 으로 **retry_count 무차감** 복귀한다.

## 두 릴레이 템플릿

### `BatchOutboxRelayTemplate` — 배치 발행

큐/배치 API 처럼 **한 번에 N건을 발행**하고 성공·실패를 분리 결과로 받는 트랜스포트용.

```
claim → BatchOutboxAdapter.dispatchBatch(commands) → 성공·실패 분리 → bulkMarkSent / bulkMarkFailed
```

- 자동설정 빈으로 등록된다(`PlatformOutboxAutoConfiguration`).
- 사용: `relay(int batchSize, BatchOutboxAdapter<O> adapter)` → `SchedulerBatchProcessingResult`.

### `PerItemOutboxRelayTemplate` — 건별 발송

HTTP 콜백처럼 **항목마다 개별 발송**하고 결과가 4갈래로 갈리는 트랜스포트용.

```
claim → preloadTasks(N+1 회피) → executor 병렬 발송
      → 결과 4분기(success / deferred / permanent / failure)
      → bulkMarkSent / bulkMarkFailed / deferRetry / markFailedPermanently
```

- **자동설정 빈이 아니다.** executor·defer 윈도가 소비자별이라 소비측이 직접 인스턴스화한다.

  ```java
  new PerItemOutboxRelayTemplate(maxDeferDuration, executor, meterRegistry);
  ```

- 발송 실패는 **예외 종류로 신호**한다:
  - `OutboxDispatchDeferredException` → 일시 장애, `deferRetry` 로 재시도 연기
  - `OutboxDispatchPermanentException` → 영구 실패, `markFailedPermanently` 로 종결
  - 그 외 예외 → 일반 실패, `bulkMarkFailed` 로 retry

## 확장점 (SPI) — 소비측이 구현

### `OutboxStore<O>` — 트랜스포트 무관 base SPI

모든 트랜스포트가 공유하는 수명주기 계약.

| 메서드 | 책임 |
|--------|------|
| `label()` | 로그 라벨 (예: "다운로드"). |
| `pipeline()` | 메트릭 태그용 저카디널리티 파이프라인 식별자. |
| `outboxId(O)` | outbox 자체 ID — bulkMark 대상. |
| `claimPendingMessages(int)` | `PENDING → PROCESSING` 원자 claim 후 도메인 객체 목록. |
| `bulkMarkSent(ids, now)` | 성공분 `PROCESSING → SENT` 일괄 마킹. |
| `bulkMarkFailed(ids, now, err)` | 실패분 일괄 마킹 (재시도 증가/dead-letter 는 구현 정책). |
| `bulkReleaseToPending(ids)` | 인프라 예외 시 `PROCESSING → PENDING` 무차감 복귀. |

### `BatchOutboxAdapter<O> extends OutboxStore<O>`

base 수명주기에 배치 dispatch 를 더한다.

| 메서드 | 책임 |
|--------|------|
| `businessId(O)` | 발행 메시지 본문이자 dispatchResult 매칭 키. |
| `idempotencyKey(O)` | 발행 메시지 멱등키. |
| `dispatchBatch(commands)` | 배치 발행 후 `OutboxBatchDispatchResult` 반환. |

> **dispatchBatch 계약:** 입력 command 의 모든 business id 는 결과의 `successIds` 또는 `failedEntries`
> 중 **정확히 한 곳**에 포함되어야 한다. 어느 쪽에도 없으면 PROCESSING 으로 남아 stuck 된다. 또한
> `businessId` 는 한 배치 내 **유일**해야 한다(매칭 상관키). 유일성이 보장되지 않으면 outbox 별 유일
> 값(예: `idempotencyKey`)을 businessId 로 싣는다.

### `PerItemOutboxAdapter<O, T, P> extends OutboxStore<O>`

base 수명주기에 건별 dispatch·4분기 전이를 더한다. 타입 파라미터: `O` = Outbox, `T` = 페이로드 빌드용
부모 작업, `P` = 발송 페이로드.

| 메서드 | 책임 |
|--------|------|
| `taskId(O)` / `preloadTasks(ids)` | 부모 작업 N+1 회피 batch 조회. |
| `outboxStatus(O)` | defer 후 FAILED 전이 감지용. |
| `callbackUrl(O)` / `createdAt(O)` | 발송 URL · 외부 생성 시각 (로그·경과시간). |
| `idempotencyKey(O)` | 발송 멱등키. |
| `buildPayload(O, T)` | 발송 페이로드 빌드. |
| `notify(url, payload, key)` | 외부 건별 통지 — 실패는 위 예외로 신호. |
| `deferRetry(O, now, max)` | 일시 장애 재시도 연기 + persist. |
| `markFailedPermanently(O, err, now)` | 영구 실패 종결 + persist. |

## DTO

- **`OutboxDispatchCommand`** — `businessId` + `idempotencyKey` 쌍. 배치 발행 입력.
- **`OutboxBatchDispatchResult`** — `successIds` + `failedEntries(id, errorMessage)`. null 입력은 빈
  리스트로 정규화하고 방어적 복사로 불변. 팩토리: `allSuccess(ids)`, `of(successIds, failedEntries)`.

## 재시도 정책 — `OutboxRetryPolicy`

소비자 구성 가능한 record.

| 필드 | 의미 | 적용 주체 |
|------|------|-----------|
| `maxRetries` | 일반 실패 재시도 최대 횟수 (초과 시 FAILED). | 소비자 store 구현 |
| `maxDeferDuration` | 일시 장애 defer 최대 시간. | `PerItemOutboxRelayTemplate` 가 `deferRetry` 에 주입 |

```java
OutboxRetryPolicy.defaults();              // 5회 · 6시간
OutboxRetryPolicy.of(3, Duration.ofHours(2));
```

## 자동 설정

`PlatformOutboxAutoConfiguration` 이 `BatchOutboxRelayTemplate` 빈을 등록한다.

- `@ConditionalOnClass(SchedulerBatchProcessingResult.class)` — `platform-common-application` 존재 시.
- `@ConditionalOnMissingBean` — 소비자 재정의 가능.
- `MeterRegistry` 는 `ObjectProvider` 로 optional 주입.
- `PerItemOutboxRelayTemplate` 는 여기서 **등록하지 않는다** (소비자별 executor·defer 윈도 → 소비측 인스턴스화).

## 의존성

```groovy
implementation project(':platform-outbox')
```

`platform-common-application`(→ `SchedulerBatchProcessingResult`, 그리고 transitively `platform-common-domain`
의 `OutboxStatus`) 에 의존한다. 발행/저장
구현은 소비측이 SPI 로 제공하므로 이 모듈은 트랜스포트 인프라(SQS·HTTP·JPA)에 직접 의존하지 않는다.
