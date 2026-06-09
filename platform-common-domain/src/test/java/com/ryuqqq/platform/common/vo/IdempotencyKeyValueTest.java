package com.ryuqqq.platform.common.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IdempotencyKeyValueTest {

    @Test
    @DisplayName("정상 값이면 value() 가 원문을 그대로 반환한다")
    void acceptsNonBlankValue() {
        IdempotencyKeyValue key = new IdempotencyKeyValue("abc-123");
        assertThat(key.value()).isEqualTo("abc-123");
    }

    @Test
    @DisplayName("null value 는 거부한다")
    void rejectsNull() {
        assertThatThrownBy(() -> new IdempotencyKeyValue(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be blank");
    }

    @Test
    @DisplayName("빈 문자열 value 는 거부한다")
    void rejectsEmpty() {
        assertThatThrownBy(() -> new IdempotencyKeyValue(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("공백 only value 는 거부한다")
    void rejectsBlank() {
        assertThatThrownBy(() -> new IdempotencyKeyValue("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("같은 value 두 인스턴스는 구조적으로 동등하다")
    void structuralEquality() {
        assertThat(new IdempotencyKeyValue("k"))
                .isEqualTo(new IdempotencyKeyValue("k"))
                .hasSameHashCodeAs(new IdempotencyKeyValue("k"));
        assertThat(new IdempotencyKeyValue("k"))
                .isNotEqualTo(new IdempotencyKeyValue("other"));
    }

    @Test
    @DisplayName("앞뒤 공백은 trim 하지 않고 보존한다 — 정규화 없음")
    void doesNotNormalize() {
        assertThat(new IdempotencyKeyValue(" abc ").value()).isEqualTo(" abc ");
        assertThat(new IdempotencyKeyValue(" abc "))
                .isNotEqualTo(new IdempotencyKeyValue("abc"));
    }

    @Test
    @DisplayName("toString 은 record 기본형이 아닌 raw value 를 반환한다")
    void toStringReturnsRawValue() {
        assertThat(new IdempotencyKeyValue("abc-123")).hasToString("abc-123");
    }
}
