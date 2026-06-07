package com.ryuqqq.platform.redis.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ryuqqq.platform.common.vo.CacheKey;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;

class RedissonCacheAdapterTest {

    private final RedissonClient redissonClient = mock(RedissonClient.class);
    private final RedissonCacheAdapter adapter = new RedissonCacheAdapter(redissonClient);

    private final CacheKey key = () -> "cache:test:1";

    @Test
    @DisplayName("get은 RBucket.get을 Optional로 감싼다")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void getWrapsBucket() {
        RBucket bucket = mock(RBucket.class);
        when(redissonClient.getBucket("cache:test:1")).thenReturn(bucket);
        when(bucket.get()).thenReturn("value");

        Optional<String> result = adapter.get(key, String.class);

        assertThat(result).contains("value");
    }

    @Test
    @DisplayName("set은 RBucket.set에 위임한다")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void setDelegates() {
        RBucket bucket = mock(RBucket.class);
        when(redissonClient.getBucket("cache:test:1")).thenReturn(bucket);

        adapter.set(key, "value");

        verify(bucket).set("value");
    }
}
