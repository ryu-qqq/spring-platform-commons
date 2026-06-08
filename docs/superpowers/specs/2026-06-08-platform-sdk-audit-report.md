---
title: platform SDK 공용성 감사 + P2 후보 공통성 조사 리포트
date: 2026-06-08
source: 병렬 워크플로 platform-sdk-audit (17 agents)
---

# spring-platform-commons 통합 의사결정 리포트

근거는 모두 입력 감사·조사 결과의 file:line evidence를 보존했다.

## 1. 신뢰 판정 요약

전 모듈 verdict가 CONCERN이며 REDESIGN/명백한 PASS는 없다. major 이슈 수가 많은 순으로 정렬했다.

| 모듈 | verdict | major | 한 줄 top 권고 |
|---|---|---|---|
| platform-outbox | CONCERN | 3 | QueueOutboxAdapter를 OutboxStore + OutboxPublisher 두 SPI로 분리하고 이름에서 Queue·enqueue 제거 (spi/QueueOutboxAdapter.java:35-57) |
| resilient-client | CONCERN | 3 | toResilience4j() public 노출을 package-private로 봉인, createRestClientBacked RestClient 고정 해제 (CircuitBreakerConfig.java:60, ResilientClientFactory.java:70) |
| platform-web | CONCERN | 2 | AccessDenied/OptimisticLock 핸들러를 @ConditionalOnClass로 분리, 한국어 메시지 MessageSource 외부화 (GlobalExceptionHandler.java:25,18,53) |
| platform-bootstrap | CONCERN | 2 | logback 'com.ryuqq' 하드코딩 springProperty 외부화, http.server.requests SLO 웹 조건부 분리 (logback-spring.xml:37/84, application-bootstrap.yml:13-39) |
| platform-redis | CONCERN | 3 | CachePort glob 계약을 opt-in 인터페이스로 분리, get() 미사용 type 파라미터 결정, Redisson Javadoc 제거 (CachePort.java:26, RedissonCacheAdapter.java:30-33) |
| platform-archrules | CONCERN | 2 | ArchitectureRules.java의 'com.ryuqqq.platform.template..' 17개 절대경로 제거, platform-archrules 실제 의존·참조 전환 (ArchitectureRules.java:33-68) |
| platform-security | CONCERN | 1 | 공개 타입명의 'ServiceToken'(구현 메커니즘) 역할 중심 이름·인터페이스 파라미터로 완화 (ServiceTokenSecurity.java:29-33) |
| platform-scheduler | CONCERN | 1 | 로깅+TraceId(불변)와 메트릭(가변)을 한 aspect에 묶어 MeterRegistry 없으면 로깅도 안 됨 — seam 분리 (PlatformSchedulerAutoConfiguration.java:21-25) |
| platform-persistence-jpa | CONCERN | 1 | PersistenceQueryMetaEntity의 @Entity 제거 (entity scan DDL 위험) (PersistenceQueryMetaEntity.java:15-16) |
| platform-common-application | CONCERN | 0 | CommonVoFactory @Component → AutoConfiguration + @ConditionalOnMissingBean (CommonVoFactory.java:18) |
| platform-common-domain | CONCERN | 0 | ErrorCode.getHttpStatus() 제거(HTTP를 adapter-in으로), Versioned.refreshVersion 분리/삭제 검토 (ErrorCode.java:12, Versioned.java:10-12) |

## 2. 공통 패턴 (critic/rubric 자동 검출 대상)

여러 모듈에 걸쳐 반복된 동형 문제다. 이것이 abstraction-critic이 단발성 지적이 아니라 패턴으로 잡아야 할 핵심이다.

### 패턴 A — 구현 기술 이름이 중립 SPI/계약에 박힘 (neutrality, 최다 발생)
- platform-outbox: SPI·메서드·DTO·빈명 전반에 'Queue/enqueue' 박힘 (QueueOutboxAdapter.java:17,48; OutboxEnqueueCommand.java:4)
- platform-redis/common-application: CachePort glob 계약 + 'Redisson 기반 추상화' Javadoc (CachePort.java:26, DistributedLockPort.java:7)
- platform-security: 'ServiceToken'이 거의 모든 공개 타입명에 박힘 (ServiceTokenProperties.java:22, ServiceTokenSecurity.java:19)
- resilient-client: toResilience4j() public, bindCircuitBreaker(Object)+instanceof 캐스팅 (CircuitBreakerConfig.java:60, ResilientClientMetricsBinder.java:109)
- platform-archrules: PLATFORM_ADAPTER_OUT_PACKAGES = persistence.jpa (ArchitectureRules.java:79-81)
- common-domain: CacheKey/LockKey Javadoc이 'Redis'를 유일 구현으로 못 박음 (CacheKey.java:4)
- persistence-jpa: package-info/AutoConfig Javadoc이 'persistence-mysql' 위키 반복 참조 (package-info.java:4)
공통 신호: 구현체 이름(Redisson/Queue/ServiceToken/Resilience4j/JPA/MySQL)이 포트·계약·타입명·Javadoc에 노출 → 구현 교체 시 공개 계약 파단.

### 패턴 B — 변하는 축과 안 변하는 축의 역할 융합 (seam)
- platform-outbox: claim→send→mark 흐름(불변)에 저장소·상태전이 + 발행채널(가변 2축)이 단일 SPI로 융합 (QueueOutboxAdapter.java:35-57)
- platform-scheduler: 로깅+TraceId(불변)와 메트릭(@ConditionalOnBean MeterRegistry, 가변)이 한 aspect에 묶여 Micrometer 없으면 로깅도 안 됨 (PlatformSchedulerAutoConfiguration.java:21-25)
- platform-web: 핸들러 코어에 Security 도입 여부·ORM 선택(가변)이 박힘 (GlobalExceptionHandler.java:25,18)
- resilient-client: 트랜스포트 선택(RestClient)이 팩토리 공개 API·YAML 경로에 고정 (ResilientClientFactory.java:70, ResilientClientBeansConfiguration.java:47)
- platform-bootstrap: 웹 vs 비웹(가변)이 설정 기본값에 하드코딩 (application-bootstrap.yml:13-39)

### 패턴 C — 사용자 대면 문자열/언어 정책 하드코딩 (commonality)
- platform-web: 한국어 메시지 5개 바이너리 하드코딩 (GlobalExceptionHandler.java:53,153,166,220,229)
- platform-security: EntryPoint 영어 / AccessDeniedHandler 한국어 혼재, 재정의 수단 없음 (ServiceTokenAuthenticationEntryPoint.java:37, ServiceTokenAccessDeniedHandler.java:33)
공통 신호: 메시지만 교체 가능한 seam(MessageSource) 부재.

### 패턴 D — 앱 특화 식별자/패키지 누수 (commonality)
- platform-bootstrap: logback 'com.ryuqq' 베이스 패키지 하드코딩(오탈자 포함) (logback-spring.xml:37,84)
- platform-archrules: 'com.ryuqqq.platform.template..' 17개 절대경로 (ArchitectureRules.java:33-68), Javadoc 'com.ryuq.marketplace' (HexagonalArchRules.java:15)
- persistence-jpa: Javadoc 'Product-like roots', '(Outbox, inventory)' 도메인 예시 (BaseVersionedSoftDeleteEntity.java:10)
- outbox: Javadoc '다운로드/변환' 예시 (QueueOutboxAdapter.java:19)

### 패턴 E — 컴포넌트 스캔 의존 / AutoConfig 누락 (conventions)
- common-application: CommonVoFactory @Component 단독, AutoConfiguration 없음 → 스캔 범위 밖 소비자는 빈 못 얻음 (CommonVoFactory.java:18). redis/scheduler/web/jpa는 모두 @AutoConfiguration 준수와 대비.

### 패턴 F — 실수요 없는 god SPI 표면 (isp/yagni)
- outbox: 단일 SPI 10개 메서드 강제 (QueueOutboxAdapter.java:19-57)
- common-application/redis: evictByPattern·getTtl·isHeldByCurrentThread·isLocked 소비측 호출 없음, 어댑터 내부 자가소비뿐 (CachePort.java:27,33; DistributedLockPort.java:26-29)
- common-domain: Versioned 실구현체 = 테스트 스텁 1개뿐 (Versioned.java:10-12)
- redis: CachePort.get()의 type 파라미터 구현이 무시 (RedissonCacheAdapter.java:30-33)

### 패턴 G — 모듈 간 암묵 계약 코드 미공유 (seam, 조용한 불일치 위험)
- web↔security: ProblemDetail 포맷·MDC traceId/spanId 키를 주석 수준으로만 동기화, 공유 타입 없음 (ServiceTokenProblemDetailWriter.java:17-19, GlobalExceptionHandler.java:239-257)
- web 내부: RequestContextFilter는 spanId를 안 넣는데 핸들러는 MDC.get("spanId") 참조 (RequestContextFilter.java:24-25, GlobalExceptionHandler.java:250-257)
- ApiResponse(DTO)가 MDC를 직접 읽음 (ApiResponse.java:26-29)

## 3. P2 후보 로드맵

우선순위(DO 중 추출성·발산 유리한 순) 정렬.

### [P2-1] TransactionEventRegistry — DO (최우선)
- 서버: MarketPlace + AuthHub (Gateway는 ArchTest만, FileFlow 없음)
- divergence: none / extractability: high
- evidence: MarketPlace/AuthHub 두 구현이 패키지명 외 공백·Javadoc·로그문자열까지 100% 동일 (MarketPlace .../common/component/TransactionEventRegistry.java, AuthHub 동일 경로). DomainEvent 인터페이스도 3곳 동일 복붙.
- 제안 범위(최소 seam): 2단계. ① DomainEvent → platform-common-domain (com.ryuqqq.platform.common.event.DomainEvent, 순수 Java 마커, 의존 추가 없음) ② TransactionEventRegistry → platform-common-application (spring-tx implementation + libs.versions.toml에 spring-tx 카탈로그 항목 추가 필요 — 현재 spring-context만 존재). registerObjectForPublish(Object) 오버로드는 유지. Gateway ArchTest는 후속으로 platform-archrules 흡수.
- 주의: 이미 동일 구현 2개 → outbox 교훈상 '수렴 대기' 아닌 '승격'. 단, 패턴 E와 동일하게 @Component 단독 등록 안티패턴을 반복하지 말 것 — AutoConfiguration + @ConditionalOnMissingBean 검토.

### [P2-2] SQS MDC Interceptor (platform-messaging 일부) — DO (조건부, outbox로 흡수)
- 서버: MarketPlace + FileFlow (AuthHub/Gateway SQS 없음)
- divergence: high / extractability: medium
- evidence: MarketPlace SqsMdcInterceptor(MessageInterceptor, 4키) (.../sqs/common/logging/SqsMdcInterceptor.java) vs FileFlow @Header traceId 인라인 MDC.put/remove (DownloadTaskSqsConsumer.java, TransformRequestSqsConsumer.java). FileFlow의 enqueueBatch는 Download/TransformQueueSqsPublisher 간 완전 복붙.
- 제안 범위(최소 seam): ① SqsMdcInterceptor를 @ConditionalOnClass(SqsMessageListenerContainerFactory) AutoConfiguration으로 승격, FileFlow 인라인 코드 제거. ② enqueueBatch 배치 발행은 신규 messaging 모듈을 만들지 말고 platform-outbox 내 sqs-support 서브모듈로 흡수해 QueueOutboxAdapter(→재설계 후 OutboxPublisher) SQS 기본 구현으로 등록. ③ 제외: SqsClientConfig(동기/비동기 분리), 큐 URL 바인딩.
- 주의: 이 후보는 P2-3(MDC 키 통일) 및 outbox 재설계(P2-1 of 실행순서)와 의존 — 키 표준 확정 전 진행 불가.

### [P2-3] MDC/Trace 전파 — DO (단계적, 키 계약부터)
- 서버: Gateway + MarketPlace + AuthHub + FileFlow (4서버)
- divergence: high / extractability: medium
- evidence: 키 집합 불일치 — Gateway Set{traceId,userId,tenantId} (LogContextKeys.java) vs MP List{+requestId} vs AuthHub SDK correlationId/X-Correlation-Id (SecurityHeaders.java). SQS 전략·REST 필터 유무도 발산.
- 제안 범위(최소 seam): 3레이어 분리. ① 공통 상수(MDC 키·헤더명) 단일 정의 — 이것부터 통일해야 나머지 의미. ② servlet MdcRequestContextFilter 승격(MP RequestContextFilter 기반). ③ reactive MdcContextLifter/ReactorMdcContextConfiguration은 Gateway 단독·Reactor 의존 → optional 모듈 격리 또는 1차 제외.
- 주의: spanId 미설정 이슈(패턴 G)와 직접 연결 — 키 계약 정의 시 spanId 출처(외부 instrumentation)도 명문화.

### [P2-4] Idempotency Key VO — DEFER (최소 seam만 조건부 DO)
- 서버: MarketPlace(14개) + FileFlow(릴레이 4 + 클라이언트 2). AuthHub raw String, Gateway 없음.
- divergence: medium / extractability: medium
- evidence: MarketPlace 14개 record 골격 복붙(ShipmentOutboxIdempotencyKey.java 등)이나, FileFlow는 forNew(outboxId) 단순형과 SHA-256 derive형으로 키 조립 규칙이 갈라짐.
- 제안 범위: generate/forNew/derive 팩토리는 도메인 특화 → SDK 제외. 추출 가능 최소 seam만: ① IdempotencyKeyValue record (value + blank/null 가드 + toString, 팩토리 없음) ② PREFIX:value 네임스페이스 규약 Javadoc. 기존 VO가 이를 위임하도록 리팩터.
- 주의: 도메인 비즈니스 필드를 SDK로 끌어오면 범위 초과 — DEFER 유지.

### [P2-5] Rate Limiting — DEFER
- 서버: MarketPlace + Gateway (두 서버 보유하나 목적 상이)
- divergence: high / extractability: low
- evidence: MP Token Bucket/Redisson/blocking/egress (DistributedRateLimiterPort.java) vs Gateway Sliding Window/Lua/reactive/ingress + LimitType 7종·IP차단 (LimitType.java). AuthHub/FileFlow 0건.
- 제안 범위: 없음(공용 seam 추출 = 사실상 신규 라이브러리 설계). 우선순위 상향 조건: 3번째 서버가 동일 패턴 사용, 또는 AuthHub가 MP DistributedRateLimiterPort를 그대로 복사하는 신호.

## 4. 권장 실행 순서

병렬 가능/불가를 명시한다.

**1단계 (병렬 가능, 독립적·저위험 정리):**
- platform-persistence-jpa: PersistenceQueryMetaEntity @Entity 제거 (런타임 DDL 위험, 단독 변경)
- platform-common-application: CommonVoFactory AutoConfiguration화 (패턴 E)
- platform-bootstrap: logback 'com.ryuqq' springProperty 외부화 (패턴 D, 단독)
- Javadoc 중립화(persistence-mysql/Product/다운로드·변환/Redisson 문구 일괄) — neutrality minor 묶음

**2단계 (outbox 2단계 재설계 — 다른 작업의 선행, 병렬 불가):**
- (a) QueueOutboxAdapter → OutboxStore<O>(claim/bulkMark/bulkRelease + ID 추출 3종) + OutboxPublisher(publish→result) 분리, 이름에서 Queue/enqueue 제거 (QueueOutboxAdapter.java:35-57). seam·isp·neutrality 동시 해소.
- (b) OutboxRelayTemplate로 rename, 두 SPI 파라미터 주입.
- 이 재설계가 끝나야 P2-2(SQS publisher를 OutboxPublisher 구현으로 흡수)가 가능 → **P2-2는 2단계 완료 전 착수 불가.**

**3단계 (P2-3 키 계약 먼저, 그 다음 P2-2):**
- P2-3 레이어① MDC 키·헤더 상수 통일 (선행). 4서버 키 발산을 표준으로 고정.
- → 표준 확정 후 P2-2 SqsMdcInterceptor 승격 + P2-3 레이어② servlet 필터 승격 (병렬 가능).
- spanId 출처 명문화 동반(패턴 G).

**4단계 (P2-1, 독립 — 1~3단계와 병렬 가능):**
- TransactionEventRegistry 2단계 추출. spring-tx 카탈로그 추가가 유일한 선행. 단 @Component 안티패턴 반복 금지(패턴 E와 정합).

**상시(설계 의사결정 필요, 코드 변경은 합의 후):**
- platform-web/security 메시지 MessageSource 외부화 + ProblemDetail 공유 타입화(패턴 C·G) — web과 security 동시 변경이라 조율 필요, 단독 병렬 부적합.
- ErrorCode.getHttpStatus() 제거는 web HTTP 매핑 책임 이동과 한 묶음(common-domain↔web 동시) → 별도 ADR 권장.

**DEFER 유지:** P2-4(최소 seam만 별도 소화), P2-5(조건 충족 시 재평가).

## 5. rubric 정제 제안 (abstraction-critic 운영 체크포인트)

이번 감사에서 반복 검출된 패턴을 6축에 운영가능한 자동 체크로 추가한다.

**neutrality 축 추가 체크:**
- (N1) 공개 타입명·메서드명·빈명·DTO명에 *특정 구현 기술* 토큰이 있는가 — 금지어 사전 검사: `Redisson|Resilience4j|JPA|MySQL|ServiceToken|Kafka|SQS|<벤더명>` 가 port/spi/계약 패키지 식별자에 등장하면 flag (patternA 다수 적중). 단 `Queue|enqueue|Cache|Lock|Store` 등 범용 자료구조/패턴어는 추상화 본질이면 중립 — 트랜스포트 선택이 비(非)해당 추상화에 샐 때만 flag(예: outbox relay에 큐 고정).
- (N2) 포트 인터페이스가 toXxx() 형태로 구현 라이브러리 타입을 반환하거나 파라미터/반환에 구현 타입을 노출하는가 (toResilience4j 사례). public 메서드 시그니처에 구현 패키지 타입 직접 노출 = major.
- (N3) Javadoc이 특정 구현/위키/도메인을 '유일 구현' 또는 예시로 박았는가 (Redis/Product/다운로드).

**seam 축 추가 체크:**
- (S1) 한 SPI/aspect/핸들러가 "불변 흐름 + ≥2 가변 축"을 융합하는가 — claim/send/mark처럼 흐름과 채널·저장소가 한 인터페이스면 flag.
- (S2) @ConditionalOnBean/@ConditionalOnClass가 '핵심 불변 기능'을 '선택적 기능' 조건에 묶었는가 (스케줄러 로깅이 MeterRegistry에 종속).
- (S3) 모듈 간 공유 계약(포맷·MDC 키·헤더)이 코드 타입이 아니라 주석으로만 동기화되는가 (web↔security ProblemDetail).

**commonality 축 추가 체크:**
- (C1) 소스/리소스에 앱 베이스 패키지·프로젝트 골격명 절대경로가 박혔는가 (`com.ryuqq*`, `*.template..`, `marketplace`).
- (C2) 사용자 대면 자연어 문자열이 하드코딩되고 MessageSource seam이 없는가; 한 모듈 내 언어 혼재(영/한)도 flag.
- (C3) "현재 ≥2 서버 실측 공유" 증거가 있는가 — 1서버면 '승격'으로 분류하고 도메인 특화 팩토리 동반 이동 여부를 별도 평가(outbox/idempotency 교훈).

**isp 축 추가 체크:**
- (I1) SPI 메서드 중 소비측(application/타 모듈) 실호출 0건이고 어댑터 내부에서만 자가소비되는 메서드가 있는가 (evictByPattern/isLocked).
- (I2) 단일 인터페이스 메서드 수가 임계치(예: 8개) 초과 + 역할군이 2개 이상 혼재하는가 (outbox 10개).

**yagni 축 추가 체크:**
- (Y1) 계약은 선언됐으나 실구현체가 테스트 스텁뿐인가 (Versioned).
- (Y2) 계약이 선언한 동작(타입 기반 역직렬화)을 구현이 이행하지 않는가 — 계약-구현 불일치 = yagni/neutrality 동시.

**conventions 축 추가 체크:**
- (V1) SDK 라이브러리 빈이 @Component 단독 등록(스캔 의존)인가 vs @AutoConfiguration + @ConditionalOnMissingBean (CommonVoFactory).
- (V2) @Entity 등 스캔 부수효과를 가진 타입이 SDK 패키지에 있는가 — package-private 가시성은 JPA 바이트코드 스캔을 막지 못함(PersistenceQueryMetaEntity).
- (V3) 발행한 공유 규칙(archrules)을 소비측이 재구현/미참조하는가 — '공유 자산이 실제로 공유되는지' 역검증.

---

## 변경 이력

| 날짜 | 변경 내용 | 작성자 |
|------|----------|--------|
| 2026-06-08 | 초안: platform SDK 11개 모듈 6축 감사 + P2-1~P2-5 공통성 조사 리포트 (병렬 워크플로 17 agents) | ryu-qqq |
| 2026-06-08 | 코드리뷰 반영 — platform-scheduler 행 추가, §5 N1 범용어(Queue 등) 예외 명시 | ryu-qqq |

*최종 갱신: 2026-06-08*

(파일 산출물 없음. 본 리포트가 호출 스크립트로 반환되는 최종 결과물이다.)