# platform-outbox MdcPropagating dogfood — 설계

> **스냅샷:** 2026-06-17

> PerItem outbox relay의 병렬 발송에서 유실되던 traceId를 MdcPropagating으로 전파한다(dogfood).
> 작성: 2026-06-17 · 상태: 승인됨(설계) → 구현 계획 단계

## 배경·동기

`PerItemOutboxRelayTemplate.dispatchAll`은 `CompletableFuture.runAsync(() -> dispatchOne(...), executor)`로
건별 병렬 발송한다. relay 스레드의 MDC(`traceId` 등)가 워커 스레드(`executor`)로 전파되지 않아 **발송 로그에서
traceId가 유실**된다. 같은 세션에서 추가한 `MdcPropagating`(PR #48, main 머지됨)을 outbox에 적용해 이 통증을
직접 해소한다(dogfood — platform이 자기 유틸을 소비).

## 범위 (작은 동작 변경 + 의존 추가)

**변경 1 — 적용** (`PerItemOutboxRelayTemplate.java`의 `dispatchAll`):
```java
CompletableFuture.runAsync(
    MdcPropagating.wrap(() -> dispatchOne(outbox, taskById, adapter, results)),
    executor)
```
relay 스레드 MDC 스냅샷이 워커에서 복원되어 `dispatchOne`·어댑터 발송 로그에 traceId가 보존된다.

**변경 2 — 의존** (`platform-outbox/build.gradle`): `implementation project(':platform-observability')` 추가.
`MdcPropagating`은 outbox 내부 구현에서만 쓰여 공개 API에 노출되지 않으므로 **`implementation`**(outbox의
`api`는 `platform-common-application`만 유지).

## 비목표

- **`BatchOutboxRelayTemplate`** — 단일 배치 호출 구조라 워커 스레드 병렬 없음(전파 불필요).
- outbox의 다른 리팩터링·메트릭 변경.

## 의존 방향 정합

`platform-observability`는 횡단 관측성 모듈(런타임 의존 slf4j-api뿐)이라 application 레이어(outbox)가 의존해도
헥사고날/의존 방향 위반이 아니다. observability는 outbox를 모르므로 **순환 없음**.

## 테스트

`PerItemOutboxRelayTemplateTest`에 전파 검증 추가:
- relay 호출 전 제출 스레드에서 `MDC.put("traceId", "T-OUTBOX")` 세팅.
- `dispatchOne`이 실제 워커 스레드에서 보는 `MDC.get("traceId")`를 어댑터/store stub이 기록(`AtomicReference`
  또는 수집 리스트)하도록 구성.
- dispatch 실행 후, 워커가 기록한 traceId가 제출 시점 값(`T-OUTBOX`)과 일치하는지 단언.
- slf4j 바인딩: outbox test가 MDC를 실제로 쓰므로 바인딩(logback-classic) `testRuntimeOnly`가 없으면 추가
  (NOP adapter면 전파 검증 불가). 기존 outbox test 의존 확인은 구현 계획에서.
- 기존 PerItem 테스트(성공/deferred/permanent 분기) 회귀 없음 확인.

## 영향 범위

- 변경: `platform-outbox` — `PerItemOutboxRelayTemplate`(runAsync 1줄 wrap), `build.gradle`(의존 1줄),
  `PerItemOutboxRelayTemplateTest`(전파 테스트). 다른 모듈·소비처 무영향(추가 의존은 transitive하지만
  observability는 경량).

---

*최종 갱신: 2026-06-17 — 초판(설계 승인). PerItem relay runAsync에 MdcPropagating.wrap 적용, Batch 제외.*
