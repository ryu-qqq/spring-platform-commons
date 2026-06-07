package com.ryuqqq.platform.common.port;

import com.ryuqqq.platform.common.vo.CacheKey;
import java.time.Duration;
import java.util.Optional;

/**
 * 캐시 아웃바운드 포트. 키는 타입 안전한 {@link CacheKey}. 구현은 platform-redis(Redisson) 등 adapter-out.
 *
 * <p>메서드 단위 제네릭({@code <T>})으로 단일 빈을 어디서나 주입받을 수 있다.
 */
public interface CachePort {

    /** 기본 TTL(무기한)로 저장. */
    void set(CacheKey key, Object value);

    /** 지정 TTL로 저장. */
    void set(CacheKey key, Object value, Duration ttl);

    /** 지정 타입으로 조회. 없으면 empty. */
    <T> Optional<T> get(CacheKey key, Class<T> type);

    /** 단건 삭제. */
    void evict(CacheKey key);

    /** glob 패턴 매칭 일괄 삭제. */
    void evictByPattern(String pattern);

    /** 존재 여부. */
    boolean exists(CacheKey key);

    /** 남은 TTL. 키가 없거나 TTL이 없으면 empty. */
    Optional<Duration> getTtl(CacheKey key);
}
