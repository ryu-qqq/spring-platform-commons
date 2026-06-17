package com.ryuqqq.platform.observability;

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
