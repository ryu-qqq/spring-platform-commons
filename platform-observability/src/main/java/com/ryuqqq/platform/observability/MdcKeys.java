package com.ryuqqq.platform.observability;

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
