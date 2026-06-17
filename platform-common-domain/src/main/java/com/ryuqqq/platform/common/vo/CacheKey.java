package com.ryuqqq.platform.common.vo;

/**
 * 캐시 키 marker. 각 도메인(bounded context)이 {@code record}로 구현하여 키 포맷을 강제한다.
 *
 * <p>키 형식 권장: {@code cache:{domain}:{entity}:{id}}. 순수 인터페이스(프레임워크 비의존) —
 * 소비측 application 레이어의 {@code CachePort}가 이 마커를 받아 처리한다.
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
