# platform-scheduler

**Adapter-in 스케줄러 공통 인프라 — `@SchedulerJob` + AOP(TraceId/MDC·로깅·Micrometer 메트릭) + 자동설정.**

스케줄러 메서드 하나에 어노테이션만 붙이면 TraceId 발급·MDC 설정·시작/종료 로깅·실행 메트릭·배치 결과
요약이 횡단 관심사로 자동 적용된다. 각 스케줄러가 로깅·계측 보일러플레이트를 반복하지 않도록 한 곳으로
수렴시키는 것이 목표다.

## 역할

`@Scheduled` 등으로 트리거되는 작업 메서드의 **관측 가능성(observability) 횡단 관심사**를 AOP로 제공한다.

- **TraceId / MDC** — 작업마다 `scheduler-<8hex>` 형태의 TraceId를 발급해 `MdcKeys.TRACE_ID`로 MDC에
  넣고, 작업 종료 시 제거한다. 한 작업 실행에서 나오는 모든 로그가 동일 TraceId로 묶인다.
- **시작/종료 로깅** — 작업 시작·완료(또는 실패)를 `[jobName]` 접두로 표준 로깅한다.
- **메트릭 (optional)** — `MeterRegistry`가 있으면 실행 시간·성공/실패·배치 아이템 메트릭을 기록한다.
  레지스트리가 없으면 메트릭만 no-op이고 **로깅·TraceId는 그대로 동작한다**(핵심 로깅이 메트릭 의존에
  묶이지 않는다).
- **배치 결과 요약** — 작업이 `SchedulerBatchProcessingResult`를 반환하면 total/success/failed를
  요약 로깅하고 아이템 단위 메트릭을 집계한다.

> **불변 핵심:** 로깅·TraceId는 메트릭과 분리된다. 메트릭 인프라가 없는 소비측에서도 작업 추적 로그는
> 보장된다.

## 확장점 — `@SchedulerJob`

작업 메서드에 붙이는 메서드 레벨 어노테이션. `value()`는 작업명으로, **로그 접두·메트릭 태그(`job_name`)**에
쓰인다.

```java
@Scheduled(cron = "0 0 * * * *")
@SchedulerJob("hourly-cleanup")
public SchedulerBatchProcessingResult cleanup() {
    // ... 배치 처리 ...
    return SchedulerBatchProcessingResult.of(total, success, failed);
}
```

- 반환 타입이 `SchedulerBatchProcessingResult`면 배치 요약 로깅·아이템 메트릭이 추가된다.
- 그 외(`void` 포함) 반환 타입은 단순 "작업 완료" 로깅만 적용된다.
- 예외(`Throwable`)는 계측·에러 로깅 후 **재던진다**(삼키지 않음). `Error`(OOM·NoClassDefFoundError 등)도
  동일하게 계측 후 전파된다.

## 동작 — `SchedulerLoggingAspect`

`@annotation(SchedulerJob)`를 포인트컷으로 하는 `@Around` 어드바이스. 자동설정이 `@Bean`으로 등록하므로
`@Component` 스캔에 의존하지 않는다(라이브러리 모범).

실행 흐름:

```
TraceId 발급 → MDC.put(TRACE_ID) → "작업 시작" 로그 → Timer.start(레지스트리 있을 때)
  → joinPoint.proceed()
  → [성공] Timer.stop · executions(success) · 결과 요약 로그 · 배치 아이템 메트릭
  → [실패] Timer.stop · executions(error) · 에러 로그 · 예외 재던짐
  → finally: MDC.remove(TRACE_ID)
```

생성자 인자 `MeterRegistry`는 **nullable** — null이면 모든 메트릭 분기가 no-op으로 빠지고 로깅·TraceId만
동작한다.

## 메트릭 (레지스트리 있을 때)

| 메트릭 | 타입 | 태그 | 의미 |
|--------|------|------|------|
| `scheduler.job.duration` | Timer (percentile histogram) | `job_name` | 작업 실행 시간. |
| `scheduler.job.executions` | Counter | `job_name`, `outcome`(success/error) | 작업 실행 성공/실패 횟수. |
| `scheduler.job.items` | Counter | `job_name`, `result`(success/failed) | 배치 내 개별 아이템 성공/실패 수. `SchedulerBatchProcessingResult` 반환 + `total > 0`일 때만. |

## 배치 결과 — `SchedulerBatchProcessingResult`

`platform-common-application`의 `com.ryuqqq.platform.common.scheduler.SchedulerBatchProcessingResult`
record(`total`, `success`, `failed`). 작업 메서드가 이 타입을 반환하면 aspect가 요약 로깅·아이템 메트릭에
활용한다.

- 팩토리: `of(total, success, failed)`, `empty()`.
- 헬퍼: `hasFailures()`, `merge(other)`(부분 결과 합산).
- `total == 0` → "처리 대상 없음" 로그.
- `hasFailures()` → `warn` 요약, 아니면 `info` 요약.

## 자동 설정 — `PlatformSchedulerAutoConfiguration`

`SchedulerLoggingAspect` 빈을 등록한다.

- `@ConditionalOnClass(ProceedingJoinPoint.class)` — AspectJ(aspectjweaver)가 클래스패스에 있을 때만 활성.
- `@ConditionalOnMissingBean` — 소비측이 동일 빈을 정의하면 양보한다.
- `MeterRegistry`는 `ObjectProvider`로 **optional 주입**(`getIfAvailable()`) — 없으면 null을 넘겨 메트릭만
  no-op.

> AOP가 동작하려면 소비 애플리케이션에서 `@EnableAspectJAutoProxy`(보통 Spring Boot AOP 자동설정으로 충족)와
> `@EnableScheduling`이 활성화되어 있어야 한다.

## 의존성

```groovy
implementation project(':platform-scheduler')
```

`platform-common-application`(→ `SchedulerBatchProcessingResult`, 그리고 transitively `platform-common-domain`의
`MdcKeys`)에 api 의존하며, Micrometer·SLF4J·aspectjweaver를 사용한다. 메트릭 인프라(`MeterRegistry` 구현)는 소비측이 제공하므로 이 모듈은 특정 모니터링
백엔드에 직접 의존하지 않는다.
