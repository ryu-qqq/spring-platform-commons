package com.ryuqqq.platform.common.vo;

/**
 * 분산락 키 marker. 각 도메인(bounded context)이 {@code record}로 구현한다.
 *
 * <p>키 형식 권장: {@code lock:{domain}:{entity}:{id}}. 순수 인터페이스(프레임워크 비의존) —
 * {@link com.ryuqqq.platform.common.port.DistributedLockPort}가 소비한다.
 *
 * <pre>{@code
 * public record OrderLockKey(long orderId) implements LockKey {
 *     public String value() { return "lock:order:" + orderId; }
 * }
 * }</pre>
 */
public interface LockKey {

    String value();
}
