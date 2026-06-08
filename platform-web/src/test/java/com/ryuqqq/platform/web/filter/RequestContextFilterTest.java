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
