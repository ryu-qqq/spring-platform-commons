---
title: 플랫폼 감사 스코어카드 (재감사 — 자율 수정 후)
date: 2026-06-10
source: platform-audit-sweep (재실행)
---

# 재감사 스코어카드 — 2026-06-10

> 자율 fleet 수정 후 재감사. 2026-06-08 대비 delta 확인.

## 주요 delta (06-08 → 06-10)
- platform-redis Paved Road: 🥈 Silver → 🥇 **Gold** (context-runner-test fail→pass, 자율 #11)
- platform-scheduler Paved Road: 🥈 Silver → 🥇 **Gold** (자율 #12)
- platform-persistence-jpa Paved Road: 🥈 Silver → 🥇 **Gold** (자율 #13)
- Structure(README): 11/11 모듈 README 존재 (자율 #16~#27) — 06-08 전무에서 완전 해소
- resilient-client Observability: 🥇 Gold → 🥈 Silver (README만 변경된 모듈 — auditor 비결정성 의심, escalations 0)

## 🚨 중요(escalation)

없음

## 스코어카드 (모듈 × track)

| 모듈 | Paved Road | Observability |
|---|---|---|
| platform-redis | Gold (4/4) | — |
| platform-scheduler | Gold (4/4) | Gold (4/4) |
| platform-security | Gold (4/4) | Gold (1/1) |
| platform-outbox | Gold (4/4) | Gold (3/3) |
| platform-web | Gold (3/3) | Gold (2/2) |
| platform-persistence-jpa | Gold (4/4) | — |
| resilient-client | — | Silver (3/4) |
| platform-common-domain | — | Gold (1/1) |

## check 상세

### Paved Road

#### platform-redis (Gold, 4/4)

| check | status | evidence | direction |
|---|---|---|---|
| imports-registered | pass | PlatformRedisAutoConfiguration.java:20 (@AutoConfiguration); src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports:1 (한 줄 한 클래스 등록); src/ 하위 spring.factories 부재 | @AutoConfiguration 표시 + imports 등록 + spring.factories 없음 |
| conditional-override | pass | PlatformRedisAutoConfiguration.java:26 (distributedLockPort), :33 (cachePort) 모두 @ConditionalOnMissingBean | 공개 기본 빈에 @ConditionalOnMissingBean |
| context-runner-test | pass | PlatformRedisAutoConfigurationTest.java:19-22 (ApplicationContextRunner), :26 (backs-off), :49 (user override), :69 (FilteredClassLoader) | ApplicationContextRunner 기반 backs-off/override/FilteredClassLoader 테스트 존재 |
| conditional-on-class | pass | PlatformRedisAutoConfiguration.java:21 (@ConditionalOnClass(RedissonClient.class)), :25/:32 (@ConditionalOnBean(RedissonClient.class)) | optional Redisson 의존성에 @ConditionalOnClass/@ConditionalOnBean 가드 |

#### platform-scheduler (Gold, 4/4)

| check | status | evidence | direction |
|---|---|---|---|
| imports-registered | pass | PlatformSchedulerAutoConfiguration.java:18 @AutoConfiguration; META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports:1 등록(한 줄 한 클래스), spring.factories 없음 | 자동설정 클래스가 @AutoConfiguration 표시 + .imports 에 정확히 등록됨 |
| conditional-override | pass | PlatformSchedulerAutoConfiguration.java:23 schedulerLoggingAspect 빈에 @ConditionalOnMissingBean | 공개 기본 빈에 @ConditionalOnMissingBean 적용 — 소비측 정의 시 양보 |
| context-runner-test | pass | PlatformSchedulerAutoConfigurationTest.java:24-27 ApplicationContextRunner; :51-61 backs-off(@ConditionalOnMissingBean) 검증; :65-72 FilteredClassLoader(ProceedingJoinPoint) backs-off 검증; :42-46 property/존재 시 검증 | ApplicationContextRunner 기반 backs-off/FilteredClassLoader 테스트 존재 |
| conditional-on-class | pass | PlatformSchedulerAutoConfiguration.java:19 @ConditionalOnClass(ProceedingJoinPoint.class) 로 aspectj 가드; :24-26 optional MeterRegistry 를 ObjectProvider.getIfAvailable() 로 가드 주입 | optional 의존성(aspectj, MeterRegistry) 모두 @ConditionalOnClass/ObjectProvider 가드됨 |

#### platform-security (Gold, 4/4)

| check | status | evidence | direction |
|---|---|---|---|
| imports-registered | pass | PlatformSecurityAutoConfiguration.java:21 @AutoConfiguration; META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports:1 registers com.ryuqqq.platform.security.config.PlatformSecurityAutoConfiguration (single line); no spring.factories present | 자동설정 클래스가 @AutoConfiguration 표시 + imports 한 줄 등록, spring.factories 잔존 없음 — 기준 충족 |
| conditional-override | pass | PlatformSecurityAutoConfiguration.java:27,34,41 — serviceTokenAuthenticationFilter/serviceTokenAuthenticationEntryPoint/serviceTokenAccessDeniedHandler 세 공개 빈 모두 @ConditionalOnMissingBean | 모든 공개 기본 빈에 @ConditionalOnMissingBean — 소비측 override 가능 |
| context-runner-test | pass | PlatformSecurityAutoConfigurationTest.java:16 ApplicationContextRunner with AutoConfigurations.of(...); :26 registersBeans property-driven; :34 backsOffWhenUserDefinesOwnFilter 로 backs-off 검증 | ApplicationContextRunner 기반 backs-off + property 테스트 존재 (FilteredClassLoader는 없으나 기준 OR 충족) |
| conditional-on-class | pass | PlatformSecurityAutoConfiguration.java:22 @ConditionalOnClass(OncePerRequestFilter.class) 로 spring-web 부재 시 back-off; 주입 의존성 ServiceTokenProperties(:29)와 ObjectMapper(:36,43)는 각각 @EnableConfigurationProperties 등록분과 Jackson 핵심 타입 | optional 의존성(spring-web)에 @ConditionalOnClass 가드 적용 — 무가드 직접 주입 아님 |

#### platform-outbox (Gold, 4/4)

| check | status | evidence | direction |
|---|---|---|---|
| imports-registered | pass | /Users/ryu-qqq/Documents/ryu-qqq/spring-platform-commons/platform-outbox/src/main/java/com/ryuqqq/platform/outbox/config/PlatformOutboxAutoConfiguration.java:18 @AutoConfiguration; /Users/ryu-qqq/Documents/ryu-qqq/spring-platform-commons/platform-outbox/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports:1 registers PlatformOutboxAutoConfiguration (one class per line); no spring.factories present | 자동설정 클래스가 @AutoConfiguration 표시 + imports 단일 등록, spring.factories 잔존 없음 |
| conditional-override | pass | /Users/ryu-qqq/Documents/ryu-qqq/spring-platform-commons/platform-outbox/src/main/java/com/ryuqqq/platform/outbox/config/PlatformOutboxAutoConfiguration.java:23 @ConditionalOnMissingBean on public batchOutboxRelayTemplate bean (line 24) | 공개 기본 빈 BatchOutboxRelayTemplate 에 @ConditionalOnMissingBean 적용 |
| context-runner-test | pass | /Users/ryu-qqq/Documents/ryu-qqq/spring-platform-commons/platform-outbox/src/test/java/com/ryuqqq/platform/outbox/config/PlatformOutboxAutoConfigurationTest.java:15-18 ApplicationContextRunner with AutoConfigurations; backs-off test line 37, with/without MeterRegistry lines 22/29 | ApplicationContextRunner 기반 backs-off·property 가드 테스트 존재 |
| conditional-on-class | pass | /Users/ryu-qqq/Documents/ryu-qqq/spring-platform-commons/platform-outbox/src/main/java/com/ryuqqq/platform/outbox/config/PlatformOutboxAutoConfiguration.java:25 ObjectProvider<MeterRegistry> + getIfAvailable() (line 26); @ConditionalOnClass(SchedulerBatchProcessingResult.class) line 19 | optional MeterRegistry 를 ObjectProvider 로 가드, optional 의존성 @ConditionalOnClass 가드 |

#### platform-web (Gold, 3/3)

| check | status | evidence | direction |
|---|---|---|---|
| imports-registered | pass | PlatformWebAutoConfiguration.java:26 @AutoConfiguration; META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports:1 registers com.ryuqqq.platform.web.config.PlatformWebAutoConfiguration (single line, single class); no spring.factories present | 자동설정 클래스가 @AutoConfiguration으로 표시되고 imports에 한 줄로 정확히 등록됨. spring.factories 잔존 없음. |
| conditional-override | pass | PlatformWebAutoConfiguration.java:31,37,43 — errorMapperRegistry/globalExceptionHandler/requestContextFilter 모든 공개 @Bean에 @ConditionalOnMissingBean | 공개 기본 빈 3개 전부 @ConditionalOnMissingBean으로 소비측 override 양보. |
| context-runner-test | pass | PlatformWebAutoConfigurationTest.java:15-17 WebApplicationContextRunner.withConfiguration(AutoConfigurations.of(...)); :30-37 backsOffWhenUserDefinesOwnBean 테스트로 ConditionalOnMissingBean 검증 | ApplicationContextRunner(Web) 기반 자동설정 테스트 존재 + backs-off 케이스 커버. |
| conditional-on-class | na | build.gradle 의존성에 micrometer/MeterRegistry 등 optional 의존 없음; main 소스에 MeterRegistry/ObjectProvider/ConditionalOnClass 사용·필요 지점 없음(grep 무결과). 빈 주입은 List<ErrorMapper>/ErrorMapperRegistry 등 모듈 내부 타입뿐 | 가드가 필요한 optional 의존성이 없어 해당 없음. |

#### platform-persistence-jpa (Gold, 4/4)

| check | status | evidence | direction |
|---|---|---|---|
| imports-registered | pass | PlatformJpaAutoConfiguration.java:21 @AutoConfiguration; src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports:1 단일 클래스 한 줄 등록, spring.factories 잔존 없음 | 자동설정 등록 위생 충족 |
| conditional-override | pass | PlatformJpaAutoConfiguration.java:28 jpaQueryFactory 빈에 @ConditionalOnMissingBean 부착 | 공개 기본 빈 소비측 양보 보장 |
| context-runner-test | pass | PlatformJpaAutoConfigurationTest.java:18-84 ApplicationContextRunner 기반 — 등록(:31), ConditionalOnMissingBean backs-off(:41), FilteredClassLoader backs-off(:59,:74) | 자동설정 테스트 완비 |
| conditional-on-class | pass | PlatformJpaAutoConfiguration.java:23 @ConditionalOnClass({EntityManagerFactory.class, JPAQueryFactory.class}); :29 @ConditionalOnBean(EntityManagerFactory.class) | optional 의존성 가드 충족 |

### Observability

#### platform-scheduler (Gold, 4/4)

| check | status | evidence | direction |
|---|---|---|---|
| metric-naming | pass | SchedulerLoggingAspect.java:77 'scheduler.job.duration', :87 'scheduler.job.executions', :99/:104 'scheduler.job.items' — 일관된 dot-네임스페이스 scheduler.job.*, 하드코딩 산재 없음 | 유지 |
| cardinality-discipline | pass | 메트릭 tag는 모두 저카디널리티: job_name(:78,:88,:100,:105 어노테이션 정적값)·outcome(:89 success/error)·result(:101,:106 success/failed). 고카디널리티 traceId는 MDC에만(:46) 들어가고 meter tag 미사용 | 유지 |
| trace-propagation | na | src 전체에 RestTemplate/RestClient/WebClient/HttpClient 신호 0건 (grep). 모듈에 HTTP 클라이언트 없음 — AOP/스케줄러 계측 모듈 | 해당없음 |
| mdc-key-consistency | pass | SchedulerLoggingAspect.java:3 import MdcKeys SSOT, :46 MDC.put(MdcKeys.TRACE_ID,...), :65 MDC.remove(MdcKeys.TRACE_ID). MDC 호출에 리터럴 "traceId" 산재 없음 | 유지 |
| meter-optional | pass | PlatformSchedulerAutoConfiguration.java:25-26 ObjectProvider<MeterRegistry>.getIfAvailable() 주입(nullable). SchedulerLoggingAspect.java:44 sample null-guard, :70/:84/:95 'if (meterRegistry==null) return' — 레지스트리 부재 시 메트릭 no-op, 로깅·TraceId 정상 동작 | 유지 |

#### platform-outbox (Gold, 3/3)

| check | status | evidence | direction |
|---|---|---|---|
| metric-naming | pass | BatchOutboxRelayTemplate.java:28 와 PerItemOutboxRelayTemplate.java:34 모두 동일한 dot-네임스페이스 SSOT 상수 METRIC_RELAY="outbox.relay" 사용. 두 트랜스포트 간 메트릭명 일관, 하드코딩 산재 없음. | none |
| cardinality-discipline | pass | 메트릭 태그는 pipeline(OutboxStore.java:20-21 '메트릭 태그용 파이프라인 식별자 — 저카디널리티' 명시)과 result(고정 enum: success/failure/deferred/permanent_failure)뿐. BatchOutboxRelayTemplate.java:118 / PerItemOutboxRelayTemplate.java:181. userId·outboxId·businessId·idempotencyKey 등 고카디널리티 값은 태그에 없음. | none |
| trace-propagation | na | 모듈에 HTTP 클라이언트 없음 — RestTemplate/RestClient/WebClient grep 결과 0건. 실제 발송은 소비측 어댑터(PerItemOutboxAdapter.notify, PerItemOutboxRelayTemplate.java:114)에 위임. 관측성 비대상. | none |
| mdc-key-consistency | na | src/main 전체에 MDC/MdcKeys 사용 없음 — grep 결과 0건. 로깅은 SLF4J 파라미터 바인딩만 사용. 비대상. | none |
| meter-optional | pass | MeterRegistry nullable + null-guard(BatchOutboxRelayTemplate.java:113, PerItemOutboxRelayTemplate.java:176 'if (meterRegistry == null) return'). 자동설정은 ObjectProvider<MeterRegistry>.getIfAvailable()(PlatformOutboxAutoConfiguration.java:25-26). 기록부 try/catch 로 메트릭 실패 격리. MeterRegistry 부재 시 zero-config 동작. | none |

#### resilient-client (Silver, 3/4)

| check | status | evidence | direction |
|---|---|---|---|
| metric-naming | pass | ResilientClientMetricsBinder.java:28 PREFIX="resilient_client" SSOT 상수로 모든 메트릭명 파생(:48 _duration_seconds, :55 _total, :93 _errors_total, :63 _retry_total). 일관된 snake_case 네임스페이스. 단 CircuitBreakerMetricsBinder.java:30은 PREFIX 미사용 리터럴 "resilient_client_circuit_breaker_state" — 경미한 비일관. | CircuitBreakerMetricsBinder의 메트릭명도 공유 PREFIX 상수에서 파생하도록 통일하면 네이밍 SSOT가 완전해진다. |
| cardinality-discipline | pass | ResilientClientMetricsBinder.java:49-51,57,65,80-82,87-89,94-96,101-102 태그는 name(clientName)/outcome/method/result/exception 만 — 모두 bounded 저카디널리티. userId 등 고카디널리티 태그 없음. CircuitBreakerMetricsBinder.java:32 tag("name") 동일. | 현 상태 양호. clientName이 동적 무한 생성되지 않도록 운영 측 가이드만 유지. |
| trace-propagation | warn | ResilientClientRestSupport.java:44-45 RestClient.builder()...requestFactory(factory)로 직접 빌드(:35 HttpClient.newBuilder()). Boot auto-config의 RestClient.Builder 빈(observation/trace 계측 포함)을 주입받지 않음. new RestTemplate() 안티패턴은 아님(FAIL 아님). | auto-config된 RestClient.Builder를 주입받아 baseUrl/requestFactory만 커스터마이즈하면 Micrometer Observation 기반 trace context 전파가 자동 연동된다. |
| mdc-key-consistency | na | resilient-client/src/main 전체 grep 결과 MDC/MdcKeys/traceId 사용처 없음. 모듈이 MDC를 직접 쓰지 않으므로 SSOT 적용 대상 아님. | 해당 없음. 향후 MDC 키를 쓰게 되면 commons MdcKeys 상수를 사용할 것. |
| meter-optional | pass | ResilientClientAutoConfiguration.java:30 @ConditionalOnBean(MeterRegistry.class)로 binder 게이팅, :37-39 NOOP fallback recorder 제공. ResilientClientMetricsBinder.java:44,74,109 if(registry==null) return null-guard. MeterRegistry 부재 시 zero-config 동작 보장. | 현 상태 모범적. 유지. |

#### platform-web (Gold, 2/2)

| check | status | evidence | direction |
|---|---|---|---|
| metric-naming | na | src/main 전체에 MeterRegistry/Timer/Counter/micrometer 신호 0건. 모듈이 메트릭을 발행하지 않음(grep -rE 'MeterRegistry|Timer|Counter|micrometer' src/main → 무결과). build.gradle 의존성에도 micrometer 없음. | 메트릭 발행 비대상 모듈. 향후 요청 지표를 추가한다면 dot-네임스페이스(예: web.request.*) 규약 권장. |
| cardinality-discipline | pass | userId/tenantId는 RequestContextFilter.java:41-42 에서 MDC(트레이스 컨텍스트)에만 주입되고 메트릭 tag로 쓰이는 곳 없음(메트릭 자체 부재). 고카디널리티 값이 meter tag로 새지 않음. | 현 상태 양호. 메트릭 도입 시에도 userId/tenantId는 tag 금지·MDC/trace 한정 유지. |
| trace-propagation | na | RestTemplate/RestClient/WebClient 신호 0건(grep -rE 'RestTemplate|RestClient|WebClient' src/main → 무결과, 'new RestTemplate' 무결과). 모듈은 adapter-in(ApiResponse 엔벨로프·GlobalExceptionHandler·RequestContextFilter)이라 아웃바운드 HTTP 클라이언트 없음. | HTTP 클라이언트 비대상. 인바운드 traceId 전파는 RequestContextFilter.java:33-39 에서 X-Trace-Id echo로 처리됨. |
| mdc-key-consistency | pass | 모든 MDC 키가 MdcKeys SSOT 상수 사용: RequestContextFilter.java:38,41-42(TRACE_ID/USER_ID/TENANT_ID), ApiResponse.java:28(TRACE_ID), GlobalExceptionHandler.java:251-257(TRACE_ID/SPAN_ID). 리터럴 키 산재 없음. | SSOT 준수 양호. import com.ryuqqq.platform.common.observability.MdcKeys 일관 사용. |
| meter-optional | na | MeterRegistry 의존 자체가 없음(src/main grep 무결과, build.gradle micrometer 미선언). zero-config 가드 대상 부재로 평가 불가. | 메트릭 부재로 무조건 의존 위험 없음. 향후 계측 추가 시 ObjectProvider<MeterRegistry> 또는 @ConditionalOnClass 가드 적용 권장. |

#### platform-security (Gold, 1/1)

| check | status | evidence | direction |
|---|---|---|---|
| metric-naming | na | src/main 전체 grep 결과 MeterRegistry/Counter/Timer/Gauge/Observation 신호 0건. 정의된 메트릭명 자체가 없어 네이밍 일관성 채점 비대상. | none |
| cardinality-discipline | na | 메트릭 태그를 생성하는 코드 부재(MeterRegistry/Tags 0건). 고카디널리티 태그 위험 채점 비대상. | none |
| trace-propagation | na | src/main 전체 grep 결과 RestTemplate/RestClient/WebClient/HttpClient 신호 0건. 모듈에 HTTP 아웃바운드 클라이언트 없음 → 트레이스 전파 채점 비대상. | none |
| mdc-key-consistency | pass | ServiceTokenProblemDetailWriter.java:4 import com.ryuqqq.platform.common.observability.MdcKeys; / :62-68 MDC.get(MdcKeys.TRACE_ID), MDC.get(MdcKeys.SPAN_ID), pd.setProperty(MdcKeys.TRACE_ID,...) — 리터럴 "traceId"/"spanId" 산재 없이 MdcKeys SSOT 상수만 사용. | none |
| meter-optional | na | MeterRegistry 의존(필드/생성자 주입/ObjectProvider) 0건. 메트릭 계측 자체가 없어 zero-config 채점 비대상(무조건 의존 실패 케이스 없음). | none |

#### platform-common-domain (Gold, 1/1)

| check | status | evidence | direction |
|---|---|---|---|
| metric-naming | na | Grep MeterRegistry\|Counter\|Timer\|Gauge over platform-common-domain/src/main: no matches. Pure domain module emits no metrics. | none |
| cardinality-discipline | na | No metric Tag emission in module (Grep Tag\b\|MeterRegistry over src/main: 0 hits). No metric tags exist to carry high-cardinality values. | none |
| trace-propagation | na | Grep RestTemplate\|RestClient\|WebClient over src/main: no matches. Module ships no HTTP client. | none |
| mdc-key-consistency | pass | This module IS the MdcKeys SSOT: /Users/ryu-qqq/Documents/ryu-qqq/spring-platform-commons/platform-common-domain/src/main/java/com/ryuqqq/platform/common/observability/MdcKeys.java:13-43 defines TRACE_ID/USER_ID/TENANT_ID/SPAN_ID/REQUEST_TYPE/ERROR_CODE plus *_HEADER constants. No MDC.put/literal scatter in module (no MDC.* call sites in src/main). Canonical constant source, not a literal-scattering consumer. | none |
| meter-optional | na | No MeterRegistry reference anywhere in src/main (Grep MeterRegistry: 0 hits). Module has no Micrometer dependency, so zero-config by construction. | none |