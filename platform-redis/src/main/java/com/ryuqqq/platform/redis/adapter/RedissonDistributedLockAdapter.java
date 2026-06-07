package com.ryuqqq.platform.redis.adapter;

import com.ryuqqq.platform.common.port.DistributedLockPort;
import com.ryuqqq.platform.common.vo.LockKey;
import java.util.concurrent.TimeUnit;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

/** Redisson 기반 분산락 구현 (블로킹/servlet). */
public class RedissonDistributedLockAdapter implements DistributedLockPort {

    private final RedissonClient redissonClient;

    public RedissonDistributedLockAdapter(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    @Override
    public boolean tryLock(LockKey key, long waitTime, long leaseTime, TimeUnit unit) {
        try {
            return redissonClient.getLock(key.value()).tryLock(waitTime, leaseTime, unit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Override
    public void unlock(LockKey key) {
        RLock lock = redissonClient.getLock(key.value());
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }

    @Override
    public boolean isHeldByCurrentThread(LockKey key) {
        return redissonClient.getLock(key.value()).isHeldByCurrentThread();
    }

    @Override
    public boolean isLocked(LockKey key) {
        return redissonClient.getLock(key.value()).isLocked();
    }
}
