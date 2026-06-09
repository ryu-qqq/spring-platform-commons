package com.ryuqqq.platform.redis.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.ryuqqq.platform.common.port.CachePort;
import com.ryuqqq.platform.common.port.DistributedLockPort;
import com.ryuqqq.platform.redis.adapter.RedissonCacheAdapter;
import com.ryuqqq.platform.redis.adapter.RedissonDistributedLockAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class PlatformRedisAutoConfigurationTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(PlatformRedisAutoConfiguration.class));

    @Test
    @DisplayName("RedissonClient 빈이 없으면 어댑터가 등록되지 않는다 (backs-off)")
    void backsOffWhenNoRedissonClient() {
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(DistributedLockPort.class);
            assertThat(context).doesNotHaveBean(CachePort.class);
        });
    }

    @Test
    @DisplayName("RedissonClient 빈이 있으면 분산락·캐시 어댑터가 등록된다")
    void registersAdaptersWhenRedissonClientPresent() {
        runner.withBean(RedissonClient.class, () -> mock(RedissonClient.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(DistributedLockPort.class);
                    assertThat(context).hasSingleBean(CachePort.class);
                    assertThat(context.getBean(DistributedLockPort.class))
                            .isInstanceOf(RedissonDistributedLockAdapter.class);
                    assertThat(context.getBean(CachePort.class))
                            .isInstanceOf(RedissonCacheAdapter.class);
                });
    }

    @Test
    @DisplayName("소비측이 동일 타입 포트 빈을 정의하면 ConditionalOnMissingBean 으로 양보한다")
    void backsOffWhenUserDefinesOwnPorts() {
        runner.withBean(RedissonClient.class, () -> mock(RedissonClient.class))
                .withBean(
                        "customLock",
                        DistributedLockPort.class,
                        () -> mock(DistributedLockPort.class))
                .withBean("customCache", CachePort.class, () -> mock(CachePort.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(DistributedLockPort.class);
                    assertThat(context).hasSingleBean(CachePort.class);
                    assertThat(context.getBeanNamesForType(DistributedLockPort.class))
                            .containsExactly("customLock");
                    assertThat(context.getBeanNamesForType(CachePort.class))
                            .containsExactly("customCache");
                });
    }

    @Test
    @DisplayName("클래스패스에 Redisson 이 없으면 @ConditionalOnClass 로 자동설정이 비활성화된다")
    void backsOffWhenRedissonNotOnClasspath() {
        runner.withClassLoader(new FilteredClassLoader(RedissonClient.class))
                .run(context -> {
                    assertThat(context).doesNotHaveBean(PlatformRedisAutoConfiguration.class);
                    assertThat(context).doesNotHaveBean(DistributedLockPort.class);
                    assertThat(context).doesNotHaveBean(CachePort.class);
                });
    }
}
