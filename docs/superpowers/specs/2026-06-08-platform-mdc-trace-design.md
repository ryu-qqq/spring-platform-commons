---
title: platform MDC trace context — 키 계약 SSOT + servlet 표준화 (P2-3)
date: 2026-06-08
스냅샷: 2026-06-08
project: spring-platform-commons
status: 설계 승인됨 (구현 대기)
---

# platform MDC trace context — 키 계약 SSOT + servlet 표준화

- **백로그**: vault `build-out-backlog.md` — P2-3 MDC/Trace 전파
- **선행 근거**: `2026-06-08-platform-sdk-audit-report.md` §3 P2-3 (4서버 키 발산, "키 계약부터" 권고)

## 1. 배경·문제

이건 "복붙 수렴"이 아니라 **platform이 MDC 키 표준을 정의**하는 작업이다. 현황:

- platform-web에 이미 servlet MDC 필터 `RequestContextFilter`가 있으나 **traceId 한 키만** 다룬다(X-Trace-Id→MDC+echo).
- MDC 키가 **문자열 리터럴로 흩어져 SSOT 없음**: platform-web 필터·ApiResponse·GlobalExceptionHandler(traceId·spanId), platform-security ProblemDetail(traceId·spanId), platform-scheduler aspect(traceId), platform-bootstrap logback(traceId·userId·tenantId·requestType·errorCode).
- **spanId는 읽기만 하고 아무도 set 안 함** (감사 패턴 G — 조용한 갭).
- 4서버 키 발산: Gateway{traceId,userId,tenantId}·MP{+requestId}·AuthHub{correlationId}.

## 2. 결정 (승인됨)

1. **범위 = ① 키 계약 SSOT + ② servlet 표준화.** reactive(Gateway MdcContextLifter)는 defer.
2. **표준 키 집합 = traceId + userId + tenantId.** Gateway가 쓰는 가장 넓은 공통. requestId는 traceId와 중복이라 제외, correlationId는 traceId 동의반복이라 흡수.

## 3. 컴포넌트

### 3.1 상수 SSOT — `MdcKeys` (platform-common-domain)

- 위치: `com.ryuqqq.platform.common.observability.MdcKeys`. 순수 상수(프레임워크 무의존). 모두가 닿는 최저 공유 모듈 — CacheKey·OutboxStatus와 같은 "공유 어휘" 자리.
- 내용(MDC 키 + 인바운드 헤더):
  - `TRACE_ID = "traceId"` / `TRACE_ID_HEADER = "X-Trace-Id"`
  - `USER_ID = "userId"` / `USER_ID_HEADER = "X-User-Id"`
  - `TENANT_ID = "tenantId"` / `TENANT_ID_HEADER = "X-Tenant-Id"`
  - `SPAN_ID = "spanId"` — **분산추적 계측(Micrometer Tracing/OTel) 소유. 이 platform 필터는 set 하지 않는다**(Javadoc 명문화).
  - `REQUEST_TYPE = "requestType"`·`ERROR_CODE = "errorCode"` — 앱/핸들러가 set(logback 참조). 리터럴 통일용 상수.
- `private` 생성자(인스턴스화 금지).

### 3.2 servlet 필터 — platform-web `RequestContextFilter` 확장

- 게이트웨이 전달 헤더에서 표준 3키를 MDC에 채운다(`MdcKeys` 사용):
  - `traceId`: `X-Trace-Id` 헤더, **없으면 UUID 생성**(신규 — 추적 시작점 보장).
  - `userId`: `X-User-Id` 헤더(있을 때만).
  - `tenantId`: `X-Tenant-Id` 헤더(있을 때만).
- `traceId`를 응답 헤더로 echo(기존 유지). `finally`에서 `MDC.clear()`(기존 유지).
- **spanId는 건드리지 않는다**(추적 계측 몫).
- `@Order(HIGHEST_PRECEDENCE)` 유지.

### 3.3 리터럴 → 상수 통일

- platform-web: `RequestContextFilter`·`ApiResponse`·`GlobalExceptionHandler`의 `"traceId"`/`"spanId"` 리터럴 → `MdcKeys`.
- platform-scheduler: aspect `"traceId"` → `MdcKeys` (scheduler→common-application→common-domain 도달 가능).
- platform-security: `ServiceTokenProblemDetailWriter`의 `"traceId"`/`"spanId"` → `MdcKeys`. **common-domain 의존 추가**(방향상 정합 — 보안도 도메인 공유 어휘 사용).
- platform-bootstrap: logback XML은 import 불가 → 키 문자열 일치 유지 + 주석으로 `MdcKeys` SSOT 가리킴.

## 4. spanId 갭 해소

핸들러들의 `spanId` 읽기는 조건부(`if (spanId != null)`)라 **추적 계측 붙으면 자동 동작하는 forward-compat**다. 제거하지 않고 `MdcKeys.SPAN_ID` Javadoc에 "계측 소유, 필터 비관여"로 출처를 명문화해 갭을 닫는다.

## 5. 테스트

- **MdcKeys** 상수 값 회귀(키·헤더 이름 고정).
- **RequestContextFilter 확장** (`MockHttpServletRequest`/`MockFilterChain`):
  - X-Trace-Id 있으면 MDC traceId=그 값 + 응답 echo / 없으면 UUID 생성
  - X-User-Id·X-Tenant-Id → MDC userId·tenantId / 없으면 미설정
  - 체인 후 `MDC.clear()` 확인(누수 없음)
  - spanId는 필터가 set 안 함 확인
- **자동설정 슬라이스**: 기존 platform-web 빈 등록 유지(RequestContextFilter 빈).

## 6. 검증·완료 기준

- [ ] `./gradlew build` 전체 green (archrules 포함)
- [ ] MDC 키 문자열 리터럴이 producer(필터)·platform-web/scheduler/security에서 `MdcKeys`로 통일
- [ ] 필터가 traceId(생성 포함)·userId·tenantId를 표준 헤더에서 채움
- [ ] spanId 출처가 `MdcKeys`에 명문화됨
- [ ] abstraction-critic 재게이트(선택) — 키 계약 SSOT로 패턴 G(주석 동기화) 완화 확인

## 7. 비목표

- reactive(Gateway MdcContextLifter, Reactor 의존) — 별도/defer.
- requestType·errorCode **생성** 로직(현행 앱/핸들러가 set; 상수만 제공).
- 분산추적 계측 도입(Micrometer Tracing/OTel) — spanId는 그 계측의 몫.
- 서버 입양 마이그레이션.

## 8. 변경 이력

| 날짜 | 변경 내용 | 작성자 |
|------|----------|--------|
| 2026-06-08 | 초안: MDC 키 SSOT(MdcKeys) + servlet 필터 표준화(traceId/userId/tenantId) + spanId 출처 명문화 | ryu-qqq |

*최종 갱신: 2026-06-08*
