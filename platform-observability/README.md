# platform-observability

**횡단 관측성 어휘의 SSOT — 의존성 0인 최저 모듈.**

MDC 키·인바운드 트레이스 헤더 이름처럼 여러 레이어가 공유하는 **관측성/트레이스 어휘**를 한곳에 모은다.
로깅·헤더는 인프라 관심사이므로 도메인 커널(`platform-common-domain`)이 아니라 이 모듈에 둔다([ADR-0006](../docs/adr/0006-common-domain-kernel-vs-observability-module.md)).

## 역할

`platform-web`(필터·에러 핸들러)·`platform-security`·`platform-scheduler` 같은 바깥 레이어가 로깅 컨텍스트와
트레이스 상관에 쓰는 문자열 리터럴을 단일 출처로 수렴시킨다. 응집 원칙은 **"관측성 어휘"** — 잡다한 공용
상수의 자석이 되지 않도록 이 경계를 지킨다.

## 내용 — `MdcKeys`

MDC 키·인바운드 헤더 이름의 SSOT.

| 상수 | 값 | 소유 |
|------|----|------|
| `TRACE_ID` / `USER_ID` / `TENANT_ID` | `traceId` / `userId` / `tenantId` | servlet 필터가 게이트웨이 전달 헤더에서 채움 |
| `SPAN_ID` | `spanId` | **분산추적 계측(Micrometer Tracing/OTel) 소유** — platform 필터는 set 안 함 |
| `REQUEST_TYPE` / `ERROR_CODE` | `requestType` / `errorCode` | 앱·핸들러가 set |
| `TRACE_ID_HEADER` / `USER_ID_HEADER` / `TENANT_ID_HEADER` | `X-Trace-Id` / `X-User-Id` / `X-Tenant-Id` | 인바운드 헤더 이름 |

logback 등 XML은 Java 상수를 import 할 수 없어 동일 문자열을 mirror 하되, **이 클래스를 SSOT로 본다.**

## 의존성

런타임 의존 없음. JUnit/AssertJ 등 테스트 의존만 갖는다.

```groovy
testImplementation platform(libs.junit.bom)
testImplementation libs.bundles.testing
```

## 비목표

- 메트릭 이름·태그 카디널리티 규약 — 향후 이 모듈에 수용할 수 있으나 현재 범위 밖.
- 분산추적 span 생성 — Micrometer Tracing/OTel 계측 소유.
