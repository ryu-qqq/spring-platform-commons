package com.ryuqqq.platform.common.vo;

/**
 * 캐시 키 marker. 각 도메인(bounded context)이 {@code record}로 구현하여 키 포맷을 강제한다.
 *
 * <p>키 형식 권장: {@code cache:{domain}:{entity}:{id}}. 순수 인터페이스(프레임워크 비의존) —
 * {@link com.ryuqqq.platform.common.port.CachePort}가 소비한다.
 *
 * <pre>{@code
 * public record ProductCacheKey(long productId) implements CacheKey {
 *     public String value() { return "cache:product:" + productId; }
 * }
 * }</pre>
 */
public interface CacheKey {

    String value();
}
