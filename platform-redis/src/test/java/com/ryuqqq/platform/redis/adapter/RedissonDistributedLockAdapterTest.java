package com.ryuqqq.platform.redis.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ryuqqq.platform.common.vo.LockKey;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

class RedissonDistributedLockAdapterTest {

    private final RedissonClient redissonClient = mock(RedissonClient.class);
    private final RedissonDistributedLockAdapter adapter =
            new RedissonDistributedLockAdapter(redissonClient);

    private final LockKey key = () -> "lock:test:1";

    @Test
    @DisplayName("tryLock은 RLock.tryLock에 위임한다")
    void tryLockDelegates() throws InterruptedException {
        RLock lock = mock(RLock.class);
        when(redissonClient.getLock("lock:test:1")).thenReturn(lock);
        when(lock.tryLock(10, 30, TimeUnit.SECONDS)).thenReturn(true);

        boolean acquired = adapter.tryLock(key, 10, 30, TimeUnit.SECONDS);

        assertThat(acquired).isTrue();
        verify(lock).tryLock(10, 30, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("unlock은 현재 스레드가 보유한 경우에만 RLock.unlock을 호출한다")
    void unlockOnlyWhenHeld() {
        RLock lock = mock(RLock.class);
        when(redissonClient.getLock("lock:test:1")).thenReturn(lock);
        when(lock.isHeldByCurrentThread()).thenReturn(false);

        adapter.unlock(key);

        verify(lock, never()).unlock();
    }
}
