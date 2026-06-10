# platform-bootstrap

**부트스트랩(조립) 레이어 SDK — 실행 가능한 Spring Boot 앱의 공통 인프라 기본값.**

logback 프로파일(local/prod JSON) · actuator 노출 기본값 · graceful shutdown · prometheus 메트릭을
**의존성 하나로** 제공한다. 소비 앱은 `implementation project(':platform-bootstrap')` 만 추가하면
별도 설정 없이 표준 부트스트랩 구성을 받는다.

## 역할

여러 실행 가능 앱(`bootstrap/*`)이 매번 반복하던 logback·actuator·shutdown 설정을 **한 곳에 모은
조립 레이어**다. 도메인/어댑터 모듈과 달리 비즈니스 빈을 제공하지 않는다 — 앱을 "실행 가능"하게
만드는 운영 인프라 기본값(observability·lifecycle)만 제공한다.

- **의존성 하나로 적용** — `EnvironmentPostProcessor` 가 `application-bootstrap.yml` 을
  `spring.config.import` 에 자동 병합하므로, 소비 앱은 import 선언조차 필요 없다.
- **logback SSOT 정렬** — logback MDC 키 문자열은 `com.ryuqqq.platform.common.observability.MdcKeys`
  를 mirror 한다(XML 은 import 불가). traceId/userId/tenantId/requestType/errorCode 를 일관 출력한다.
- **프로파일별 분기 내장** — local/test 는 사람이 읽는 콘솔 + DEBUG, prod/stage 는 JSON 구조화 로그 +
  축소 노출 actuator. 소비 앱은 프로파일만 활성화하면 된다.
- **override 우선** — 모든 기본값은 소비 앱 `application.yml` 에서 덮어쓸 수 있다(중립 기본값 제공).

## 자동 적용 흐름

```
앱 기동 → PlatformBootstrapEnvironmentPostProcessor (HIGHEST_PRECEDENCE+10)
        → spring.config.import 에 optional:classpath:application-bootstrap.yml 병합
        → application-bootstrap.yml 로드 (actuator·shutdown·metrics 기본값)
        → logback-spring.xml 로드 (프로파일별 콘솔/JSON appender)
        → PlatformBootstrapAutoConfiguration (마커 — 추가 빈 없음)
```

| 단계 | 동작 |
|------|------|
| `EnvironmentPostProcessor` | `application-bootstrap.yml` 을 `spring.config.import` 에 1회 병합(중복·이미 존재 시 no-op). |
| `application-bootstrap.yml` | graceful shutdown · actuator 노출 · prometheus 메트릭 · health 기본값. |
| `logback-spring.xml` | `local,test,default` → 콘솔 패턴, `prod,stage,staging` → Logstash JSON. |
| `PlatformBootstrapAutoConfiguration` | `@AutoConfiguration` 진입점(현재 마커 — 모든 동작은 위 config 가 수행). |

## 확장점

### `application-bootstrap.yml` — 인프라 기본값

자동 import 되는 공통 yml. 소비 앱이 같은 키를 자기 `application.yml` 에 정의하면 그 값이 우선한다
(yml 은 import 된 것보다 소비 앱 쪽이 늦게 로드되어 override).

| 프로파일 | actuator 노출 | 비고 |
|----------|----------------|------|
| (default) | `health,info,metrics,prometheus,loggers` | health show-details `when-authorized`, diskspace on. |
| `stage,staging` | `health,info,metrics,prometheus` | loggers 비노출. |
| `prod` | `health,metrics,prometheus` | shutdown phase timeout `30s`, info 비노출. |

메트릭 기본 태그: `application=${spring.application.name:platform-app}` ·
`environment=${spring.profiles.active:local}`. `http.server.requests` 에 percentile-histogram
(0.5/0.95/0.99) 과 SLO(100ms/500ms/1s/5s) 가 켜져 있다.

### `logback-spring.xml` — 로깅 프로파일

`logging.platform.base-package` 로 앱 레벨 로그 verbosity 대상 패키지를 정한다. 기본값은
`com.ryuqqq`(platform 네임스페이스). 다른 베이스 패키지를 쓰는 앱은 이 키로 override 한다.

```yaml
logging:
  platform:
    base-package: com.example.myapp   # 기본 com.ryuqqq 대신 앱 베이스 패키지
```

| 프로파일 | appender | base-package 레벨 |
|----------|----------|-------------------|
| `local,test,default` | 콘솔(사람용 패턴 + MDC traceId/userId/type) | `DEBUG` |
| `prod,stage,staging` | `LogstashEncoder` JSON(CloudWatch/ELK/Datadog) | `INFO` |

JSON 모드는 MDC 키(traceId·userId·tenantId·requestType·errorCode)와
`{"service":..,"environment":..}` custom field, 단축 stack-trace(maxDepth 30, rootCauseFirst)를
포함한다.

### `PlatformBootstrapAutoConfiguration` — 자동설정 진입점

`META-INF/spring/...AutoConfiguration.imports` 로 등록되는 `@AutoConfiguration` 마커. 현재는 추가
빈을 등록하지 않고, 실제 부트스트랩 동작은 `EnvironmentPostProcessor` + import 된 config 가 수행한다.
향후 코드 기반 부트스트랩 빈(예: 공통 lifecycle hook)이 필요하면 이 클래스에 추가한다.

## 의존성

```groovy
implementation project(':platform-bootstrap')
```

`spring-boot-starter` · `spring-boot-starter-actuator` · `spring-boot-autoconfigure` ·
`logstash-logback-encoder`(JSON 인코더) 에 의존한다. logback MDC 키의 SSOT 인 `MdcKeys` 는
`platform-common-domain` 에 있으나(XML mirror), 빌드 의존은 두지 않는다 — 문자열만 일치시킨다.

## 비목표

- 비즈니스/도메인 빈을 제공하지 않는다(조립 레이어). 보안은 `platform-security`, 웹 예외 포맷은
  `platform-web`, MDC 키 SSOT(`MdcKeys`)와 채움은 `platform-common-domain`/`platform-web` 가 담당한다.
- `SecurityFilterChain`·라우팅·datasource 등 앱 고유 구성은 소비 앱이 소유한다.
