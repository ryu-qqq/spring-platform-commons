# platform MDC trace context Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** MDC 키/헤더 SSOT(`MdcKeys`)를 정의하고, platform-web `RequestContextFilter`를 표준 3키(traceId·userId·tenantId)로 확장하며, 흩어진 MDC 리터럴을 `MdcKeys`로 통일한다.

**Architecture:** `MdcKeys` 상수를 platform-common-domain(최저 공유 모듈)에 둔다. servlet 필터가 게이트웨이 헤더에서 표준 키를 MDC에 채우고(traceId 없으면 생성), platform-web·scheduler·security의 리터럴이 `MdcKeys`를 참조하게 한다. spanId는 추적 계측 소유로 명문화(필터 비관여).

**Tech Stack:** Java 21, Spring Web servlet filter, SLF4J MDC, JUnit5 + AssertJ.

**Spec:** `docs/superpowers/specs/2026-06-08-platform-mdc-trace-design.md`

---

## File Structure

```text
platform-common-domain/src/main/java/com/ryuqqq/platform/common/observability/
  MdcKeys.java                                         # 키·헤더 SSOT (Task 1)
platform-web/src/main/java/com/ryuqqq/platform/web/filter/
  RequestContextFilter.java                            # 3키 확장 (Task 2)
platform-web/src/main/java/com/ryuqqq/platform/web/dto/ApiResponse.java          # 리터럴→MdcKeys (Task 3)
platform-web/src/main/java/com/ryuqqq/platform/web/error/GlobalExceptionHandler.java  # (Task 3)
platform-scheduler/.../aspect/SchedulerLoggingAspect.java                        # (Task 3)
platform-security/.../error/ServiceTokenProblemDetailWriter.java                 # 리터럴→MdcKeys (Task 4)
platform-security/build.gradle                         # common-domain 의존 추가 (Task 4)
platform-bootstrap/src/main/resources/logback-spring.xml  # SSOT 주석 (Task 5)
```

---

## Task 1: MdcKeys SSOT (common-domain)

**Files:**
- Create: `platform-common-domain/src/main/java/com/ryuqqq/platform/common/observability/MdcKeys.java`
- Test: `platform-common-domain/src/test/java/com/ryuqqq/platform/common/observability/MdcKeysTest.java`

- [ ] **Step 1: 실패 테스트 작성**

Create `platform-common-domain/src/test/java/com/ryuqqq/platform/common/observability/MdcKeysTest.java`:

```java
package com.ryuqqq.platform.common.observability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MdcKeysTest {

    @Test
    @DisplayName("표준 MDC 키·인바운드 헤더 이름이 계약대로 고정된다")
    void keysAndHeaders() {
        assertThat(MdcKeys.TRACE_ID).isEqualTo("traceId");
        assertThat(MdcKeys.USER_ID).isEqualTo("userId");
        assertThat(MdcKeys.TENANT_ID).isEqualTo("tenantId");
        assertThat(MdcKeys.SPAN_ID).isEqualTo("spanId");
        assertThat(MdcKeys.REQUEST_TYPE).isEqualTo("requestType");
        assertThat(MdcKeys.ERROR_CODE).isEqualTo("errorCode");

        assertThat(MdcKeys.TRACE_ID_HEADER).isEqualTo("X-Trace-Id");
        assertThat(MdcKeys.USER_ID_HEADER).isEqualTo("X-User-Id");
        assertThat(MdcKeys.TENANT_ID_HEADER).isEqualTo("X-Tenant-Id");
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :platform-common-domain:test --tests '*MdcKeysTest'`
Expected: FAIL — `MdcKeys` 없음 (컴파일 에러).

- [ ] **Step 3: 구현**

Create `platform-common-domain/src/main/java/com/ryuqqq/platform/common/observability/MdcKeys.java`:

```java
package com.ryuqqq.platform.common.observability;

/**
 * MDC 키·인바운드 헤더 이름의 SSOT. 흩어진 문자열 리터럴을 한 곳으로 모은다.
 *
 * <p>{@code TRACE_ID}·{@code USER_ID}·{@code TENANT_ID}는 servlet 필터가 게이트웨이 전달 헤더에서
 * 채우는 trace context다. {@code SPAN_ID}는 <b>분산추적 계측(Micrometer Tracing/OTel) 소유</b> —
 * 이 platform 필터는 set 하지 않으며, 핸들러는 존재할 때만 출력한다(forward-compat).
 * {@code REQUEST_TYPE}·{@code ERROR_CODE}는 앱/핸들러가 set 한다(logback 참조).
 *
 * <p>logback 등 XML은 Java 상수를 import 할 수 없으므로 동일 문자열을 mirror 하되 이 클래스를 SSOT로 본다.
 */
public final class MdcKeys {

    private MdcKeys() {}

    /** 추적 상관 ID — 필터가 헤더에서 채우거나 없으면 생성. */
    public static final String TRACE_ID = "traceId";

    /** 인증 사용자 ID — 게이트웨이 전달 헤더에서 채움(있을 때). */
    public static final String USER_ID = "userId";

    /** 멀티테넌트 ID — 게이트웨이 전달 헤더에서 채움(있을 때). */
    public static final String TENANT_ID = "tenantId";

    /** 분산추적 span ID — 추적 계측 소유. platform 필터는 set 하지 않는다. */
    public static final String SPAN_ID = "spanId";

    /** 요청 분류 — 앱이 set(logback 참조). */
    public static final String REQUEST_TYPE = "requestType";

    /** 도메인 에러 코드 — GlobalExceptionHandler 등이 set(logback 참조). */
    public static final String ERROR_CODE = "errorCode";

    /** {@link #TRACE_ID} 인바운드 헤더. */
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    /** {@link #USER_ID} 인바운드 헤더. */
    public static final String USER_ID_HEADER = "X-User-Id";

    /** {@link #TENANT_ID} 인바운드 헤더. */
    public static final String TENANT_ID_HEADER = "X-Tenant-Id";
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :platform-common-domain:test --tests '*MdcKeysTest'`
Expected: PASS.

- [ ] **Step 5: 커밋**

```bash
git add platform-common-domain/src/main/java/com/ryuqqq/platform/common/observability/ platform-common-domain/src/test/java/com/ryuqqq/platform/common/observability/
git commit -m "feat(mdc): MdcKeys — MDC 키·헤더 SSOT (common-domain)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: RequestContextFilter 확장 (traceId·userId·tenantId)

**Files:**
- Modify: `platform-web/src/main/java/com/ryuqqq/platform/web/filter/RequestContextFilter.java`
- Test: `platform-web/src/test/java/com/ryuqqq/platform/web/filter/RequestContextFilterTest.java` (재작성)

> platform-web 은 이미 `api project(':platform-common-domain')` → `MdcKeys` 사용 가능.
> 동작 변경: traceId를 **헤더 없으면 생성**(기존엔 헤더 있을 때만 set) + userId·tenantId 추가.

- [ ] **Step 1: 테스트 재작성 (실패 상태로)**

Overwrite `platform-web/src/test/java/com/ryuqqq/platform/web/filter/RequestContextFilterTest.java`:

```java
package com.ryuqqq.platform.web.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.ryuqqq.platform.common.observability.MdcKeys;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestContextFilterTest {

    private final RequestContextFilter filter = new RequestContextFilter();

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    /** 체인 실행 중(=clear 전) MDC 스냅샷을 캡처한다. */
    private Map<String, String> runAndCaptureMdc(MockHttpServletRequest request, MockHttpServletResponse response)
            throws Exception {
        Map<String, String> captured = new HashMap<>();
        filter.doFilter(
                request,
                response,
                (req, res) -> {
                    captured.put(MdcKeys.TRACE_ID, MDC.get(MdcKeys.TRACE_ID));
                    captured.put(MdcKeys.USER_ID, MDC.get(MdcKeys.USER_ID));
                    captured.put(MdcKeys.TENANT_ID, MDC.get(MdcKeys.TENANT_ID));
                    captured.put(MdcKeys.SPAN_ID, MDC.get(MdcKeys.SPAN_ID));
                });
        return captured;
    }

    @Test
    @DisplayName("X-Trace-Id 있으면 그 값을 MDC traceId·응답 헤더로 전파")
    void traceIdFromHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(MdcKeys.TRACE_ID_HEADER, "trace-abc");
        MockHttpServletResponse response = new MockHttpServletResponse();

        Map<String, String> mdc = runAndCaptureMdc(request, response);

        assertThat(mdc.get(MdcKeys.TRACE_ID)).isEqualTo("trace-abc");
        assertThat(response.getHeader(MdcKeys.TRACE_ID_HEADER)).isEqualTo("trace-abc");
    }

    @Test
    @DisplayName("X-Trace-Id 없으면 traceId를 생성해 MDC·응답 헤더에 설정")
    void traceIdGeneratedWhenAbsent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        Map<String, String> mdc = runAndCaptureMdc(request, response);

        assertThat(mdc.get(MdcKeys.TRACE_ID)).isNotBlank();
        assertThat(response.getHeader(MdcKeys.TRACE_ID_HEADER)).isEqualTo(mdc.get(MdcKeys.TRACE_ID));
    }

    @Test
    @DisplayName("X-User-Id·X-Tenant-Id 있으면 MDC에 채우고, 없으면 미설정")
    void userAndTenantHeaders() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(MdcKeys.USER_ID_HEADER, "u-1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        Map<String, String> mdc = runAndCaptureMdc(request, response);

        assertThat(mdc.get(MdcKeys.USER_ID)).isEqualTo("u-1");
        assertThat(mdc.get(MdcKeys.TENANT_ID)).isNull();
    }

    @Test
    @DisplayName("필터는 spanId를 설정하지 않는다 (추적 계측 소유)")
    void doesNotSetSpanId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        Map<String, String> mdc = runAndCaptureMdc(request, response);

        assertThat(mdc.get(MdcKeys.SPAN_ID)).isNull();
    }

    @Test
    @DisplayName("체인 후 MDC를 비운다 (스레드 누수 없음)")
    void clearsMdcAfterChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(MdcKeys.TRACE_ID_HEADER, "trace-abc");
        request.addHeader(MdcKeys.USER_ID_HEADER, "u-1");

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {});

        assertThat(MDC.get(MdcKeys.TRACE_ID)).isNull();
        assertThat(MDC.get(MdcKeys.USER_ID)).isNull();
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :platform-web:test --tests '*RequestContextFilterTest'`
Expected: FAIL — `traceIdGeneratedWhenAbsent`·`userAndTenantHeaders` 등이 현재 필터(traceId만, 생성 없음)와 불일치.

- [ ] **Step 3: 필터 구현**

Overwrite `platform-web/src/main/java/com/ryuqqq/platform/web/filter/RequestContextFilter.java`:

```java
package com.ryuqqq.platform.web.filter;

import com.ryuqqq.platform.common.observability.MdcKeys;
import java.io.IOException;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 게이트웨이 전달 헤더에서 표준 trace context(traceId·userId·tenantId)를 MDC에 채운다.
 *
 * <p>traceId는 {@code X-Trace-Id}가 없으면 생성하고 응답 헤더로 echo 한다. 키·헤더 이름은 {@link MdcKeys}
 * SSOT를 따른다. spanId는 추적 계측 소유라 여기서 set 하지 않는다.
 *
 * <p>{@link com.ryuqqq.platform.web.config.PlatformWebAutoConfiguration}가 {@code @Bean}으로 등록한다.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String traceId = headerOrNull(request, MdcKeys.TRACE_ID_HEADER);
            if (traceId == null) {
                traceId = UUID.randomUUID().toString();
            }
            MDC.put(MdcKeys.TRACE_ID, traceId);
            response.setHeader(MdcKeys.TRACE_ID_HEADER, traceId);

            putIfPresent(request, MdcKeys.USER_ID_HEADER, MdcKeys.USER_ID);
            putIfPresent(request, MdcKeys.TENANT_ID_HEADER, MdcKeys.TENANT_ID);

            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }

    private static String headerOrNull(HttpServletRequest request, String header) {
        String value = request.getHeader(header);
        return (value != null && !value.isBlank()) ? value : null;
    }

    private static void putIfPresent(HttpServletRequest request, String header, String mdcKey) {
        String value = headerOrNull(request, header);
        if (value != null) {
            MDC.put(mdcKey, value);
        }
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :platform-web:test --tests '*RequestContextFilterTest'`
Expected: PASS (5 tests).

- [ ] **Step 5: 커밋**

```bash
git add platform-web/src/main/java/com/ryuqqq/platform/web/filter/RequestContextFilter.java platform-web/src/test/java/com/ryuqqq/platform/web/filter/RequestContextFilterTest.java
git commit -m "feat(web): RequestContextFilter — traceId(생성)·userId·tenantId 표준화 (MdcKeys)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: 리터럴 통일 — platform-web 리더 + scheduler aspect

**Files:**
- Modify: `platform-web/src/main/java/com/ryuqqq/platform/web/dto/ApiResponse.java`
- Modify: `platform-web/src/main/java/com/ryuqqq/platform/web/error/GlobalExceptionHandler.java`
- Modify: `platform-scheduler/src/main/java/com/ryuqqq/platform/scheduler/aspect/SchedulerLoggingAspect.java`

> 동작 변경 없음 — `"traceId"`/`"spanId"` 리터럴을 `MdcKeys`로 치환만. 기존 테스트가 회귀 검증.

- [ ] **Step 1: ApiResponse 치환**

`ApiResponse.java`에 import 추가 후 `MDC.get("traceId")` → `MDC.get(MdcKeys.TRACE_ID)`:

```java
import com.ryuqqq.platform.common.observability.MdcKeys;
```
그리고 해당 라인:
```java
        String traceId = MDC.get(MdcKeys.TRACE_ID);
```

- [ ] **Step 2: GlobalExceptionHandler 치환**

`GlobalExceptionHandler.java`에 import `com.ryuqqq.platform.common.observability.MdcKeys;` 추가 후, MDC 읽기·setProperty 키를 `MdcKeys`로:

```java
        String traceId = MDC.get(MdcKeys.TRACE_ID);
        String spanId = MDC.get(MdcKeys.SPAN_ID);
        if (traceId != null) {
            problemDetail.setProperty(MdcKeys.TRACE_ID, traceId);
        }
        if (spanId != null) {
            problemDetail.setProperty(MdcKeys.SPAN_ID, spanId);
        }
```

- [ ] **Step 3: SchedulerLoggingAspect 치환**

`SchedulerLoggingAspect.java`에서 로컬 상수 `private static final String TRACE_ID_KEY = "traceId";`를 제거하고, import `com.ryuqqq.platform.common.observability.MdcKeys;` 추가 후 사용처를 교체:

```java
        MDC.put(MdcKeys.TRACE_ID, traceId);
```
```java
            MDC.remove(MdcKeys.TRACE_ID);
```

- [ ] **Step 4: 컴파일·기존 테스트 통과 확인**

Run: `./gradlew :platform-web:test :platform-scheduler:test`
Expected: PASS (동작 동일, 기존 테스트 green).

- [ ] **Step 5: 커밋**

```bash
git add platform-web/src/main/java/com/ryuqqq/platform/web/dto/ApiResponse.java platform-web/src/main/java/com/ryuqqq/platform/web/error/GlobalExceptionHandler.java platform-scheduler/src/main/java/com/ryuqqq/platform/scheduler/aspect/SchedulerLoggingAspect.java
git commit -m "refactor(mdc): web·scheduler MDC 리터럴을 MdcKeys로 통일

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: 리터럴 통일 — security writer + common-domain 의존

**Files:**
- Modify: `platform-security/build.gradle`
- Modify: `platform-security/src/main/java/com/ryuqqq/platform/security/error/ServiceTokenProblemDetailWriter.java`

- [ ] **Step 1: security build.gradle에 common-domain 의존 추가**

`platform-security/build.gradle`의 `dependencies { ... }`에서 첫 `implementation platform(libs.spring.boot.dependencies)` 줄 위에 추가:

```groovy
    implementation project(':platform-common-domain')
```

- [ ] **Step 2: ServiceTokenProblemDetailWriter 치환**

import `com.ryuqqq.platform.common.observability.MdcKeys;` 추가 후, MDC 읽기·setProperty 키를 `MdcKeys`로:

```java
        String traceId = MDC.get(MdcKeys.TRACE_ID);
        String spanId = MDC.get(MdcKeys.SPAN_ID);
        if (traceId != null) {
            pd.setProperty(MdcKeys.TRACE_ID, traceId);
        }
        if (spanId != null) {
            pd.setProperty(MdcKeys.SPAN_ID, spanId);
        }
```

- [ ] **Step 3: 모듈 테스트 통과 확인**

Run: `./gradlew :platform-security:test`
Expected: PASS (동작 동일 — ProblemDetail 포맷 그대로; 기존 18+ 테스트 green).

- [ ] **Step 4: 커밋**

```bash
git add platform-security/build.gradle platform-security/src/main/java/com/ryuqqq/platform/security/error/ServiceTokenProblemDetailWriter.java
git commit -m "refactor(mdc): security ProblemDetail MDC 리터럴을 MdcKeys로 통일 (+common-domain 의존)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 5: logback SSOT 주석 + 전체 빌드 + 백로그

**Files:**
- Modify: `platform-bootstrap/src/main/resources/logback-spring.xml`
- Modify: `/Users/ryu-qqq/Documents/ryu-qqq-wiki/wiki/projects/spring-platform-commons/build-out-backlog.md`

- [ ] **Step 1: logback에 SSOT 주석**

`logback-spring.xml`의 MDC Keys 설명 블록(상단 주석)에 한 줄 추가 — 키 문자열의 SSOT가 `MdcKeys`임을 명시. 기존 주석 블록 안 `errorCode: domain error code (GlobalExceptionHandler)` 줄 다음에 추가:

```xml
        NOTE: 위 키 문자열의 SSOT는 com.ryuqqq.platform.common.observability.MdcKeys (XML은 import 불가라 문자열 mirror).
```

- [ ] **Step 2: 전체 빌드**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`. archrules·기존 모듈 포함.

> 실패 시 systematic-debugging 으로 분석 후 수정 — 가정 금지.

- [ ] **Step 3: 백로그 P2-3 완료 반영**

`build-out-backlog.md` P2 줄에서 MDC 항목을 갱신:

기존:
```markdown
- **P2** idempotency VO(→common-domain)·platform-messaging(SQS)·rate-limit(servlet+reactive)·reactive MDC
```
변경:
```markdown
- **P2** idempotency VO(→common-domain)·platform-messaging(SQS)·rate-limit(servlet+reactive)
- **P2** MDC/Trace — ✅ **servlet 단계 완료** (`feat/platform-mdc-trace`). MdcKeys SSOT(common-domain) + RequestContextFilter 표준 3키(traceId 생성·userId·tenantId) + web/scheduler/security 리터럴 통일 + spanId 출처(추적 계측 소유) 명문화. **reactive(Gateway MdcContextLifter)는 defer.**
```

변경 이력에 한 줄 추가:
```markdown
- 2026-06-08: P2-3 MDC/Trace servlet 단계 완료. 복붙 수렴이 아니라 platform이 키 표준(MdcKeys)을 정의 — 흩어진 리터럴·spanId 갭(패턴 G) 해소. reactive는 별도.
```

- [ ] **Step 4: 백로그 커밋 (vault repo)**

```bash
cd /Users/ryu-qqq/Documents/ryu-qqq-wiki && git add wiki/projects/spring-platform-commons/build-out-backlog.md && git commit -m "docs(platform): P2-3 MDC/Trace servlet 단계 완료 반영"
```

- [ ] **Step 5: 완료 보고**

work-evaluator 4축 self-check 후 완료 보고. reactive defer·spanId는 계측 소유임을 명시.

---

## Self-Review (작성자 점검 결과)

- **Spec coverage:** §3.1 MdcKeys→Task1, §3.2 필터 확장→Task2, §3.3 리터럴 통일→Task3(web·scheduler)+Task4(security+dep), §4 spanId 명문화→Task1(Javadoc)+Task2(필터 비관여), §5 테스트→Task1·2, §6 logback 주석→Task5. 전 항목 매핑.
- **Placeholder scan:** TBD/TODO 없음. 코드 스텝 완전, 펜스 언어 태그 부여.
- **Type consistency:** `MdcKeys` 상수명(TRACE_ID·USER_ID·TENANT_ID·SPAN_ID·REQUEST_TYPE·ERROR_CODE + *_HEADER)이 필터·리더·테스트에서 일치. 필터는 traceId 생성+echo+userId/tenantId putIfPresent, spanId 미설정 — 테스트와 일치. security는 common-domain 의존 추가 후 MdcKeys 도달.
- **동작 변경 주의:** Task2만 동작 변경(traceId 생성·2키 추가) — 기존 RequestContextFilterTest 재작성으로 반영. Task3·4는 리터럴 치환(무동작변경)이라 기존 테스트가 회귀 가드.
