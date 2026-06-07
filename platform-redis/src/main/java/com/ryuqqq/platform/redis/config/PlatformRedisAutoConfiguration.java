package com.ryuqqq.platform.redis.config;

import com.ryuqqq.platform.common.port.CachePort;
import com.ryuqqq.platform.common.port.DistributedLockPort;
import com.ryuqqq.platform.redis.adapter.RedissonCacheAdapter;
import com.ryuqqq.platform.redis.adapter.RedissonDistributedLockAdapter;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Platform Redis 자동 설정 — {@link RedissonClient} 빈이 있으면 분산락·캐시 어댑터를 등록한다.
 *
 * <p>연결 설정({@code RedissonClient})은 소비측이 제공한다 (consumer provides — DataSource와 동일 원칙).
 * 소비측이 동일 타입 포트 빈을 정의하면 {@link ConditionalOnMissingBean}으로 양보한다.
 */
@AutoConfiguration
@ConditionalOnClass(RedissonClient.class)
public class PlatformRedisAutoConfiguration {

    @Bean
    @ConditionalOnBean(RedissonClient.class)
    @ConditionalOnMissingBean
    public DistributedLockPort distributedLockPort(RedissonClient redissonClient) {
        return new RedissonDistributedLockAdapter(redissonClient);
    }

    @Bean
    @ConditionalOnBean(RedissonClient.class)
    @ConditionalOnMissingBean
    public CachePort cachePort(RedissonClient redissonClient) {
        return new RedissonCacheAdapter(redissonClient);
    }
}
