package com.ryuqqq.platform.web.dto;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ryuqqq.platform.common.vo.SliceMeta;

import static org.assertj.core.api.Assertions.assertThat;

class SliceApiResponseTest {

    @Test
    @DisplayName("SliceMeta 기반 슬라이스 envelope")
    void fromSliceMeta() {
        SliceApiResponse<String, Long> response =
                SliceApiResponse.of(List.of("a", "b"), SliceMeta.of(20, true, 99L));

        assertThat(response.content()).containsExactly("a", "b");
        assertThat(response.size()).isEqualTo(20);
        assertThat(response.hasNext()).isTrue();
        assertThat(response.nextCursor()).isEqualTo(99L);
    }

    @Test
    @DisplayName("null content는 빈 리스트")
    void nullContent() {
        SliceApiResponse<String, String> response = SliceApiResponse.of(null, 10, false, null);

        assertThat(response.content()).isEmpty();
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();
    }
}
