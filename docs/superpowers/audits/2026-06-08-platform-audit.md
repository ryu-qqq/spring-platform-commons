---
title: 플랫폼 감사 스코어카드 (Phase 1 첫 sweep)
date: 2026-06-08
source: platform-audit-sweep (autoconfig-auditor + observability-auditor)
tracks: [Paved Road, Observability, Structure]
---

# spring-platform-commons 감사 스코어카드 — 2026-06-08

> 자가 감사 fleet Phase 1 첫 전체 sweep. 읽기전용 auditor 2종(autoconfig·observability)이 모듈×차원 12건 감사 + 결정론적 스코어카드. 강제 아님(가시성 기반). 기준 근거: `docs/superpowers/specs/2026-06-08-platform-audit-fleet-phase1-design.md`, taxonomy: vault `platform-team-taxonomy.md`.

## 요약
- **관측성: 전 모듈 Gold** — MDC/메트릭 build-out(P2-3·scheduler·outbox·resilient-client) 성과. 카디널리티·MdcKeys SSOT·MeterRegistry 옵셔널 전부 충족.
- **Paved Road: autoconfig 슬라이스 테스트가 유일 갭** — security·outbox·web Gold(ApplicationContextRunner 보유), redis·scheduler는 `context-runner-test` **major fail**(슬라이스 테스트 부재), jpa는 minor(positive만).
- **Structure: 11모듈 전부 README 부재**(우아한 5계층 기준 위반, minor·체계적).

## Structure track (결정론적 — README 존재)
| 모듈 | README | 비고 |
|---|---|---|
| platform-redis | ❌ 없음 | 역할·확장점 문서 필요 (minor) |
| platform-scheduler | ❌ 없음 | 역할·확장점 문서 필요 (minor) |
| platform-security | ❌ 없음 | 역할·확장점 문서 필요 (minor) |
| platform-outbox | ❌ 없음 | 역할·확장점 문서 필요 (minor) |
| platform-web | ❌ 없음 | 역할·확장점 문서 필요 (minor) |
| platform-persistence-jpa | ❌ 없음 | 역할·확장점 문서 필요 (minor) |
| platform-common-domain | ❌ 없음 | 역할·확장점 문서 필요 (minor) |
| platform-common-application | ❌ 없음 | 역할·확장점 문서 필요 (minor) |
| platform-bootstrap | ❌ 없음 | 역할·확장점 문서 필요 (minor) |
| platform-archrules | ❌ 없음 | 역할·확장점 문서 필요 (minor) |
| resilient-client | ❌ 없음 | 역할·확장점 문서 필요 (minor) |

> Structure 판정: 전 모듈 README 부재 = 체계적 minor. escalation(major) 아님 — Phase 2 일괄 보강 후보.

## 🚨 중요(escalation)

| 모듈 | track | check | evidence | direction |
|---|---|---|---|---|
| platform-redis | Paved Road | context-runner-test | `platform-redis/src/test` 하위에 RedissonCacheAdapterTest.java, RedissonDistributedLockAdapterTest.java(Mockito 단위테스트)만 존재; ApplicationContextRunner 미사용(grep 0건). 자동설정 backs-off/property/FilteredClassLoader 테스트 부재 | ApplicationContextRunner 기반 테스트 추가: RedissonClient 빈 부재 시 어댑터 backs-off, 빈 존재 시 등록, FilteredClassLoader(Redisson 제외) 가드 검증 |
| platform-scheduler | Paved Road | context-runner-test | 테스트는 SchedulerLoggingAspectTest.java(aspect 단위 테스트, SimpleMeterRegistry·null 직접 주입)만 존재. ApplicationContextRunner 기반 자동설정 테스트(backs-off/property/FilteredClassLoader) 부재 | ApplicationContextRunner로 빈 등록·@ConditionalOnMissingBean backs-off·@ConditionalOnClass(ProceedingJoinPoint) FilteredClassLoader·MeterRegistry 부재 시나리오 검증 테스트 추가 필요 |

## 스코어카드 (모듈 × track)

| 모듈 | Paved Road | Observability |
|---|---|---|
| platform-redis | 🥈 Silver (3/4) | — |
| platform-scheduler | 🥈 Silver (3/4) | 🥇 Gold (4/4) |
| platform-security | 🥇 Gold (4/4) | 🥇 Gold (1/1) |
| platform-outbox | 🥇 Gold (4/4) | 🥇 Gold (3/3) |
| platform-web | 🥇 Gold (3/3) | 🥇 Gold (1/1) |
| platform-persistence-jpa | 🥈 Silver (3/4) | — |
| resilient-client | — | 🥇 Gold (4/4) |
| platform-common-domain | — | 🥇 Gold (1/1) |

## check 상세

### Paved Road

#### platform-redis (🥈 Silver, 3/4)

| check | status | evidence | direction |
|---|---|---|---|
| imports-registered | ✅ pass | PlatformRedisAutoConfiguration.java:20 @AutoConfiguration; .../AutoConfiguration.imports:1 한 줄로 등록; spring.factories 없음 | 자동설정 등록 정상 — 변경 불필요 |
| conditional-override | ✅ pass | PlatformRedisAutoConfiguration.java:26 distributedLockPort @ConditionalOnMissingBean, :33 cachePort @ConditionalOnMissingBean — 공개 기본 빈 모두 가드됨 | 무조건 빈 등록 없음 — 변경 불필요 |
| context-runner-test | ❌ fail | `platform-redis/src/test` 하위에 RedissonCacheAdapterTest.java, RedissonDistributedLockAdapterTest.java(Mockito 단위테스트)만 존재; ApplicationContextRunner 미사용(grep 0건). 자동설정 backs-off/property/FilteredClassLoader 테스트 부재 | ApplicationContextRunner 기반 테스트 추가: RedissonClient 빈 부재 시 어댑터 backs-off, 빈 존재 시 등록, FilteredClassLoader(Redisson 제외) 가드 검증 |
| conditional-on-class | ✅ pass | PlatformRedisAutoConfiguration.java:21 @ConditionalOnClass(RedissonClient.class), :25/:32 각 빈에 @ConditionalOnBean(RedissonClient.class) — optional 의존성 가드됨 | optional 의존성 가드 정상 — 변경 불필요 |

#### platform-scheduler (🥈 Silver, 3/4)

| check | status | evidence | direction |
|---|---|---|---|
| imports-registered | ✅ pass | PlatformSchedulerAutoConfiguration.java:18 @AutoConfiguration; .../AutoConfiguration.imports:1 한 줄 한 클래스 등록, spring.factories 없음 | 자동설정 표준 등록 충족 |
| conditional-override | ✅ pass | PlatformSchedulerAutoConfiguration.java:23 schedulerLoggingAspect 빈에 @ConditionalOnMissingBean — 소비측 동일 빈 정의 시 양보 | 무조건 등록 아님, 소비측 오버라이드 가능 |
| context-runner-test | ❌ fail | 테스트는 SchedulerLoggingAspectTest.java(aspect 단위 테스트, SimpleMeterRegistry·null 직접 주입)만 존재. ApplicationContextRunner 기반 자동설정 테스트(backs-off/property/FilteredClassLoader) 부재 | ApplicationContextRunner로 빈 등록·@ConditionalOnMissingBean backs-off·@ConditionalOnClass(ProceedingJoinPoint) FilteredClassLoader·MeterRegistry 부재 시나리오 검증 테스트 추가 필요 |
| conditional-on-class | ✅ pass | PlatformSchedulerAutoConfiguration.java:19 @ConditionalOnClass(ProceedingJoinPoint.class) aspectj 가드; :25-26 optional MeterRegistry를 ObjectProvider.getIfAvailable()로 가드 주입 | optional 의존성 무가드 직접 주입 없음 |

#### platform-security (🥇 Gold, 4/4)

| check | status | evidence | direction |
|---|---|---|---|
| imports-registered | ✅ pass | PlatformSecurityAutoConfiguration.java:21 @AutoConfiguration; .../AutoConfiguration.imports:1 한 줄로 클래스 등록; spring.factories 미존재 | 자동설정 클래스가 @AutoConfiguration 표시 + imports 파일에 한 줄 한 클래스로 등록됨, 레거시 spring.factories 잔존 없음 |
| conditional-override | ✅ pass | PlatformSecurityAutoConfiguration.java:27,34,41 — 세 공개 빈 모두 @ConditionalOnMissingBean | 모든 공개 기본 빈에 @ConditionalOnMissingBean 적용, 무조건 등록 없음 |
| context-runner-test | ✅ pass | PlatformSecurityAutoConfigurationTest.java:16-21 ApplicationContextRunner; :34 backsOffWhenUserDefinesOwnFilter; :21 withPropertyValues | ApplicationContextRunner 기반 테스트 존재 + backs-off + property 시나리오 커버 |
| conditional-on-class | ✅ pass | PlatformSecurityAutoConfiguration.java:22 @ConditionalOnClass(OncePerRequestFilter.class); 주입 의존성은 ServiceTokenProperties와 ObjectMapper(Boot 보장 빈)로 optional 의존 직접주입 없음 | 서블릿 의존(OncePerRequestFilter)에 @ConditionalOnClass 가드, MeterRegistry 등 무가드 optional 주입 없음 |

#### platform-outbox (🥇 Gold, 4/4)

| check | status | evidence | direction |
|---|---|---|---|
| imports-registered | ✅ pass | PlatformOutboxAutoConfiguration.java:18 @AutoConfiguration; .../AutoConfiguration.imports:1 등록(단일 클래스); src 내 spring.factories 부재 | 유지 |
| conditional-override | ✅ pass | PlatformOutboxAutoConfiguration.java:23 공개 빈 batchOutboxRelayTemplate 에 @ConditionalOnMissingBean 적용 | 유지 |
| context-runner-test | ✅ pass | PlatformOutboxAutoConfigurationTest.java:15-18 ApplicationContextRunner; :37 backsOffWhenUserDefinesOwnTemplate(backs-off); :29 MeterRegistry 시나리오 | 유지 |
| conditional-on-class | ✅ pass | PlatformOutboxAutoConfiguration.java:19 @ConditionalOnClass(SchedulerBatchProcessingResult.class); :25-26 optional MeterRegistry 를 ObjectProvider.getIfAvailable() 로 가드 | 유지 |

#### platform-web (🥇 Gold, 3/3)

| check | status | evidence | direction |
|---|---|---|---|
| imports-registered | ✅ pass | PlatformWebAutoConfiguration.java:26 has @AutoConfiguration; .../AutoConfiguration.imports:1 registers com.ryuqqq.platform.web.config.PlatformWebAutoConfiguration (one class per line); no spring.factories present. | none |
| conditional-override | ✅ pass | All three public beans guarded with @ConditionalOnMissingBean: PlatformWebAutoConfiguration.java:31 (errorMapperRegistry), :37 (globalExceptionHandler), :43 (requestContextFilter). | none |
| context-runner-test | ✅ pass | PlatformWebAutoConfigurationTest.java:15-17 uses WebApplicationContextRunner with AutoConfigurations.of; :21 asserts bean registration; :30-37 asserts back-off via withBean + ConditionalOnMissingBean. | none |
| conditional-on-class | ⚪ N/A | No optional/third-party dependency (e.g. MeterRegistry) is injected; only collection injection List<ErrorMapper> (PlatformWebAutoConfiguration.java:32) and internal types. grep for MeterRegistry/ObjectProvider/ConditionalOnClass/ConditionalOnBean in src/main/java returns nothing. No unguarded optional injection exists, so the guard requirement does not apply. | none |

#### platform-persistence-jpa (🥈 Silver, 3/4)

| check | status | evidence | direction |
|---|---|---|---|
| imports-registered | ✅ pass | PlatformJpaAutoConfiguration.java:21 @AutoConfiguration; .../AutoConfiguration.imports:1 등록 (한 줄 한 클래스); spring.factories 잔존 없음 | 유지 |
| conditional-override | ✅ pass | PlatformJpaAutoConfiguration.java:28 @ConditionalOnMissingBean on jpaQueryFactory() 공개 기본 빈 | 유지 |
| context-runner-test | ⚠️ warn | PlatformJpaAutoConfigurationTest.java:15-34 ApplicationContextRunner 존재하나 positive 등록(hasSingleBean) 한 케이스뿐 — backs-off(@ConditionalOnMissingBean 사용자 빈 우선)/property/FilteredClassLoader 검증 부재 | backs-off/FilteredClassLoader(@ConditionalOnClass 미존재) 시나리오 테스트 추가 |
| conditional-on-class | ✅ pass | PlatformJpaAutoConfiguration.java:23 @ConditionalOnClass({EntityManagerFactory.class, JPAQueryFactory.class}); :29 @ConditionalOnBean(EntityManagerFactory.class) — optional 의존성 가드됨 | 유지 |

### Observability

#### platform-scheduler (🥇 Gold, 4/4)

| check | status | evidence | direction |
|---|---|---|---|
| metric-naming | ✅ pass | SchedulerLoggingAspect.java:77 "scheduler.job.duration", :87 "scheduler.job.executions", :99/:104 "scheduler.job.items" — 모두 일관 dot-네임스페이스 scheduler.job.*, 하드코딩 산재 없음 | 유지. 모든 메트릭명이 scheduler.job.* 단일 네임스페이스로 정렬됨. |
| cardinality-discipline | ✅ pass | 메트릭 tag는 SchedulerLoggingAspect.java:78/88/100 job_name(어노테이션 정적값)·:89 outcome(success/error)·:101/:106 result(success/failed)뿐. userId/user_id 부재. job_name은 @SchedulerJob.value() 정적 설정값이라 카디널리티 한정. | 유지. 고카디널리티(userId 등) 식별자가 메트릭 태그에 없음. |
| trace-propagation | ⚪ N/A | platform-scheduler/src/main 내 RestTemplate/RestClient/WebClient/HttpClient grep 0건. 모듈에 HTTP 클라이언트 없음. | 대상 아님 — 모듈에 발신 HTTP 클라이언트 부재. |
| mdc-key-consistency | ✅ pass | SchedulerLoggingAspect.java:46 MDC.put(MdcKeys.TRACE_ID, ...), :65 MDC.remove(MdcKeys.TRACE_ID) — SSOT 상수 사용. MdcKeys.java:18 TRACE_ID 정의. 리터럴 "traceId" 산재 없음. | 유지. MDC 키가 platform-common-domain MdcKeys SSOT 상수 경유. |
| meter-optional | ✅ pass | PlatformSchedulerAutoConfiguration.java:25-26 ObjectProvider<MeterRegistry>.getIfAvailable() 주입. SchedulerLoggingAspect.java:44/:70/:84/:95 null-guard로 레지스트리 부재 시 메트릭 no-op, 로깅·TraceId는 정상 동작. | 유지. MeterRegistry 부재 시 zero-config 동작(ObjectProvider + null-guard). |

#### platform-outbox (🥇 Gold, 3/3)

| check | status | evidence | direction |
|---|---|---|---|
| metric-naming | ✅ pass | BatchOutboxRelayTemplate.java:28 (METRIC_RELAY="outbox.relay"); PerItemOutboxRelayTemplate.java:34 동일 상수; Counter.builder(METRIC_RELAY) BatchOutboxRelayTemplate.java:117, PerItemOutboxRelayTemplate.java:180 — 하드코딩 산재 없음 | 메트릭명을 SSOT 상수로 일관 dot-네임스페이스 유지 |
| cardinality-discipline | ✅ pass | BatchOutboxRelayTemplate.java:118-119 .tag("pipeline",...).tag("result",...); PerItemOutboxRelayTemplate.java:181-182 동일. result는 success/failure/deferred/permanent_failure 고정값(PerItemOutboxRelayTemplate.java:169-172). outboxId 등은 로그에만 사용(예: PerItemOutboxRelayTemplate.java:109,114) | 고카디널리티 식별자(outboxId/businessId/idempotencyKey)를 메트릭 tag에 넣지 않고 저카디널리티 pipeline·result만 태깅 — 현 상태 유지 |
| trace-propagation | ⚪ N/A | grep new RestTemplate\|RestClient\|WebClient\|HttpClient over src/main = 0 hits; PerItemOutboxRelayTemplate.java:114 adapter.notify(...) (SPI 추상화) | 모듈 내 HTTP 클라이언트 없음 — 실제 전송은 adapter.notify(callbackUrl,...) SPI로 소비측 위임 |
| mdc-key-consistency | ⚪ N/A | grep MDC.\|MdcKeys over src/main = 0 hits | 모듈 내 MDC 사용 없음 — MdcKeys SSOT 적용 대상 아님 |
| meter-optional | ✅ pass | BatchOutboxRelayTemplate.java:30-31 nullable 필드, :113 if (meterRegistry==null...) return; PerItemOutboxRelayTemplate.java:40-41,:176 동일; PlatformOutboxAutoConfiguration.java:25-26 ObjectProvider<MeterRegistry>.getIfAvailable(); 기록 실패도 try/catch 흡수 BatchOutboxRelayTemplate.java:122 | MeterRegistry nullable·null-guard·ObjectProvider 패턴 유지 — zero-config 동작 보장 |

#### resilient-client (🥇 Gold, 4/4)

| check | status | evidence | direction |
|---|---|---|---|
| metric-naming | ✅ pass | ResilientClientMetricsBinder.java:28 단일 PREFIX="resilient_client" 상수로 모든 메트릭명 파생(:48 _duration_seconds, :55 _total, :93 _errors_total, :63 _retry_total). dot이 아닌 snake_case(Prometheus 규약)이나 일관됨. CircuitBreakerMetricsBinder.java:30은 "resilient_client_circuit_breaker_state" 리터럴로 PREFIX 미재사용(경미한 산재)이나 네임스페이스 일관 유지. | CircuitBreakerMetricsBinder.java:30 메트릭명을 PREFIX 상수 공유로 SSOT 통일하면 완전 일관. |
| cardinality-discipline | ✅ pass | 메트릭 tag는 ResilientClientMetricsBinder.java:49-102의 name(clientName, 바운드)·outcome(success/error)·method(HttpMethod enum)·result(retry 결과 열거)·exception(:95 getSimpleName 클래스명, 바운드)뿐. userId 등 고카디널리티·request URL/path는 tag로 사용되지 않음. | exception tag는 사용자 정의 예외가 늘면 카디널리티 증가 가능하나 현재 SDK 예외 계층(ExternalCallException 하위)으로 바운드. |
| trace-propagation | ✅ pass | ResilientClientRestSupport.java:44-45에서 RestClient.builder().baseUrl().requestFactory()로 생성. new RestTemplate() 수동 생성 없음. RequestSender는 RestClient.exchange 사용(:69). | RestClient.builder()를 정적 호출하므로 Boot가 auto-config한 RestClient.Builder 빈(ObservationRegistry 적용본)을 경유하지 않음 — trace/observation 자동계측 미적용. 주입된 RestClient.Builder 빈을 받아 커스터마이즈하면 trace 전파가 자동 연동됨. |
| mdc-key-consistency | ⚪ N/A | 모듈 전체에 MDC / MdcKeys 사용 없음(grep 결과 0건). 컨텍스트 로깅 계측 비대상. | — |
| meter-optional | ✅ pass | ResilientClientAutoConfiguration.java:30 @ConditionalOnBean(MeterRegistry.class) + :31/:37 @ConditionalOnMissingBean로 MeterRegistry 부재 시 :39 MetricsRecorder.NOOP 폴백. 추가로 ResilientClientMetricsBinder.java:44/74/109 if (registry == null) return; null-guard. zero-config 동작 보장(AutoConfigurationTest noMeterRegistry_noopMetrics 케이스로도 확인). | — |

#### platform-web (🥇 Gold, 1/1)

| check | status | evidence | direction |
|---|---|---|---|
| metric-naming | ⚪ N/A | platform-web 모듈 전체에 MeterRegistry/Counter/Timer/메트릭 등록 코드 없음. grep 결과 main 소스에 메트릭 정의 부재. | 메트릭 미정의 모듈이므로 채점 대상 아님. |
| cardinality-discipline | ⚪ N/A | 메트릭 자체가 없으므로 고카디널리티 태그 위험 없음. userId(MdcKeys.USER_ID)는 MDC/트레이스 컨텍스트에만 사용됨: RequestContextFilter.java:41. | 메트릭 부재로 평가 불가. |
| trace-propagation | ⚪ N/A | 모듈 내 RestTemplate/RestClient/WebClient 사용처 없음(grep 0건). HTTP 클라이언트 미보유 모듈. | HTTP 아웃바운드 클라이언트 부재로 na. |
| mdc-key-consistency | ✅ pass | main 소스 MDC 접근 전부 MdcKeys SSOT 상수 사용: RequestContextFilter.java:38,41,42 / GlobalExceptionHandler.java:251-257 / ApiResponse.java:28. 리터럴 키는 GlobalExceptionHandlerTest.java:74-75 테스트 픽스처에만 존재(프로덕션 계측 아님). | 프로덕션 MDC 계측은 MdcKeys SSOT 준수. (참고: 테스트 어서션은 MdcKeys로 통일하면 더 좋음) |
| meter-optional | ⚪ N/A | MeterRegistry 의존 코드 자체가 없음(grep 0건). PlatformWebAutoConfiguration도 메트릭 빈 미정의. | MeterRegistry 의존 부재로 zero-config 위반 가능성 없음. |

#### platform-security (🥇 Gold, 1/1)

| check | status | evidence | direction |
|---|---|---|---|
| metric-naming | ⚪ N/A | platform-security/src/main/java 전체에 MeterRegistry/Counter/Timer/Gauge/Observation 신호 없음 (grep 0건). 메트릭을 발행하지 않는 인증 필터·핸들러 어댑터 모듈이라 메트릭명 채점 비대상. | 메트릭 미발행 모듈 — 해당 없음 |
| cardinality-discipline | ⚪ N/A | 메트릭 태그 자체가 없음. main 코드에서 increment/record/.tag( 신호 0건이며 userId 등 고카디널리티의 메트릭 tag 사용 없음. | 메트릭 태그 부재 — 해당 없음 |
| trace-propagation | ⚪ N/A | RestTemplate/RestClient/WebClient grep 0건. build.gradle 의존성도 spring-security-web·spring-web(servlet)뿐 HTTP 클라이언트 없음. 아웃바운드 HTTP 호출 없는 adapter-in 모듈. | HTTP 클라이언트 부재 — 해당 없음 |
| mdc-key-consistency | ✅ pass | platform-security/src/main/java/com/ryuqqq/platform/security/error/ServiceTokenProblemDetailWriter.java:62-68 에서 MDC.get(MdcKeys.TRACE_ID)/MDC.get(MdcKeys.SPAN_ID) 및 pd.setProperty(MdcKeys.TRACE_ID/SPAN_ID) 로 platform-common-domain 의 MdcKeys SSOT 상수만 사용. 'traceId' 등 리터럴 키 산재 없음. | MdcKeys SSOT 일관 사용 — 유지 |
| meter-optional | ⚪ N/A | MeterRegistry 의존(직접/ObjectProvider/Conditional) 자체가 없음 — main 코드 grep 0건. 메트릭 미발행 모듈이라 zero-config 채점 비대상. | MeterRegistry 미사용 — 해당 없음 |

#### platform-common-domain (🥇 Gold, 1/1)

| check | status | evidence | direction |
|---|---|---|---|
| metric-naming | ⚪ N/A | 순수 도메인 모듈. src/main 전체에 MeterRegistry/Counter/Timer/Gauge/metric name 신호 0건. 정의된 메트릭 자체가 없음. | 메트릭 계측은 다른 인프라/스타터 모듈 소유. 도메인 모듈에 측정 코드 유입 금지 유지. |
| cardinality-discipline | ⚪ N/A | 메트릭 tag 코드 부재(.tag(/Tags. no-match). 카디널리티 판정 대상(메트릭 태그) 자체가 없음. MdcKeys.USER_ID는 MDC/트레이스용 상수 정의일 뿐 메트릭 태그가 아님. | USER_ID는 MDC/트레이스 전용임을 MdcKeys.java:21 doc로 명시 중 — 향후 메트릭 모듈이 이 상수를 메트릭 tag로 오용하지 않도록 주의. |
| trace-propagation | ⚪ N/A | RestTemplate/RestClient/WebClient/HttpClient src/main 0건, build.gradle에 web/restclient/webflux 의존 0건. 모듈에 HTTP 클라이언트 없음. | HTTP 클라이언트는 도메인 모듈 비대상. 추가 시 auto-config builder 경유 원칙 적용. |
| mdc-key-consistency | ✅ pass | 이 모듈이 MdcKeys SSOT를 정의함. platform-common-domain/src/main/java/com/ryuqqq/platform/common/observability/MdcKeys.java:13-43 — TRACE_ID/USER_ID/TENANT_ID/SPAN_ID/REQUEST_TYPE/ERROR_CODE 및 X-*-Id 헤더 상수 집중. MDC.put/get 리터럴 산재 없음(src/main MDC ops 0건). MdcKeysTest.java:13-22가 계약 고정. | SSOT 정상. logback XML mirror 주의문(MdcKeys.java:11)도 명시되어 키 drift 위험 관리됨. |
| meter-optional | ⚪ N/A | MeterRegistry 의존/주입 0건(grep no-match), build.gradle micrometer 의존 0건. zero-config 가드 판정 대상 자체가 없음. | 도메인 모듈은 MeterRegistry 비대상. 유지. |
