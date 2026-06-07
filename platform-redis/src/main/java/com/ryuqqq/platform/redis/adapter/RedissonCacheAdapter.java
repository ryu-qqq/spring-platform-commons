package com.ryuqqq.platform.redis.adapter;

import com.ryuqqq.platform.common.port.CachePort;
import com.ryuqqq.platform.common.vo.CacheKey;
import java.time.Duration;
import java.util.Optional;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;

/** Redisson 기반 캐시 구현 (RBucket). 직렬화는 RedissonClient의 codec을 따른다. */
public class RedissonCacheAdapter implements CachePort {

    private final RedissonClient redissonClient;

    public RedissonCacheAdapter(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    @Override
    public void set(CacheKey key, Object value) {
        redissonClient.getBucket(key.value()).set(value);
    }

    @Override
    public void set(CacheKey key, Object value, Duration ttl) {
        redissonClient.getBucket(key.value()).set(value, ttl);
    }

    @Override
    public <T> Optional<T> get(CacheKey key, Class<T> type) {
        RBucket<T> bucket = redissonClient.getBucket(key.value());
        return Optional.ofNullable(bucket.get());
    }

    @Override
    public void evict(CacheKey key) {
        redissonClient.getBucket(key.value()).delete();
    }

    @Override
    public void evictByPattern(String pattern) {
        redissonClient.getKeys().deleteByPattern(pattern);
    }

    @Override
    public boolean exists(CacheKey key) {
        return redissonClient.getBucket(key.value()).isExists();
    }

    @Override
    public Optional<Duration> getTtl(CacheKey key) {
        long remaining = redissonClient.getBucket(key.value()).remainTimeToLive();
        return remaining < 0 ? Optional.empty() : Optional.of(Duration.ofMillis(remaining));
    }
}
