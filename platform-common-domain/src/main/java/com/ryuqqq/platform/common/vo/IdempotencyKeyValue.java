package com.ryuqqq.platform.common.vo;

/**
 * 멱등 키의 검증된 불투명 값.
 *
 * <p>외부(클라이언트 헤더 등)에서 받은 불투명 키 문자열을 타입으로 감싸 blank/null 만 막는다.
 * SDK는 값을 정규화(trim/대소문자/charset)하지 않으며, 동등성은 원문 기준이다.
 *
 * <p><b>네임스페이스 규약:</b> 같은 키가 컨텍스트 간 충돌하지 않도록 소비측이
 * {@code PREFIX:value}(예: {@code "payment:abc-123"}) 형태로 결합해 보관할 것을 권장한다.
 * 결합 규칙은 도메인 정책이므로 이 타입이 강제하지 않는다.
 *
 * <p><b>비목표:</b> 키 파생/생성 팩토리(forNew·SHA-256 derive 등), namespace 코어 필드,
 * charset/length 제약, trim 정규화, 영속화 매핑 — 모두 호출자 책임.
 *
 * <pre>{@code
 * var key = new IdempotencyKeyValue(request.getHeader("Idempotency-Key"));
 * String stored = "payment:" + key; // 규약에 따른 namespacing은 소비측
 * }</pre>
 */
public record IdempotencyKeyValue(String value) {

    public IdempotencyKeyValue {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("idempotency key value must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
