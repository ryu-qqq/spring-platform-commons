package com.ryuqqq.platform.web.dto;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ryuqqq.platform.common.vo.PageMeta;

import static org.assertj.core.api.Assertions.assertThat;

class PageApiResponseTest {

    @Test
    @DisplayName("PageMeta 기반 페이징 envelope")
    void fromPageMeta() {
        PageApiResponse<String> response =
                PageApiResponse.of(List.of("a", "b"), PageMeta.of(0, 10, 25));

        assertThat(response.content()).containsExactly("a", "b");
        assertThat(response.page()).isZero();
        assertThat(response.size()).isEqualTo(10);
        assertThat(response.totalElements()).isEqualTo(25);
        assertThat(response.totalPages()).isEqualTo(3);
        assertThat(response.first()).isTrue();
        assertThat(response.last()).isFalse();
    }

    @Test
    @DisplayName("마지막 페이지 first/last 계산")
    void lastPage() {
        PageApiResponse<String> response = PageApiResponse.of(List.of("z"), 2, 10, 25);

        assertThat(response.first()).isFalse();
        assertThat(response.last()).isTrue();
        assertThat(response.totalPages()).isEqualTo(3);
    }

    @Test
    @DisplayName("content defensive copy")
    void defensiveCopy() {
        List<String> mutable = new java.util.ArrayList<>(List.of("x"));
        PageApiResponse<String> response = PageApiResponse.of(mutable, 0, 10, 1);
        mutable.add("y");

        assertThat(response.content()).containsExactly("x");
    }
}
