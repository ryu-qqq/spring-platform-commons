package com.ryuqqq.platform.common.vo;

/**
 * 분산락 키 marker. 각 도메인(bounded context)이 {@code record}로 구현한다.
 *
 * <p>키 형식 권장: {@code lock:{domain}:{entity}:{id}}. 순수 인터페이스(프레임워크 비의존) —
 * 소비측 application 레이어의 {@code DistributedLockPort}가 이 마커를 받아 처리한다.
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
