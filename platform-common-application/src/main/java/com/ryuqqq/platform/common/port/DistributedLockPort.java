package com.ryuqqq.platform.common.port;

import com.ryuqqq.platform.common.vo.LockKey;
import java.util.concurrent.TimeUnit;

/**
 * 분산락 아웃바운드 포트. 구현은 platform-redis(Redisson) 등 adapter-out.
 *
 * <p>사용 패턴: {@code tryLock} 성공 시 {@code try ... finally}로 반드시 {@code unlock} 한다.
 */
public interface DistributedLockPort {

    /**
     * 락 획득 시도.
     *
     * @param waitTime 최대 대기 시간
     * @param leaseTime 락 유지 시간(자동 해제)
     * @return 획득 성공 여부
     */
    boolean tryLock(LockKey key, long waitTime, long leaseTime, TimeUnit unit);

    /** 현재 스레드가 보유한 경우 해제. */
    void unlock(LockKey key);

    /** 현재 스레드가 보유 중인지. */
    boolean isHeldByCurrentThread(LockKey key);

    /** 어떤 스레드든 보유 중인지. */
    boolean isLocked(LockKey key);
}
