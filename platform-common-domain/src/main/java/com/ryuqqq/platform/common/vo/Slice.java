package com.ryuqqq.platform.common.vo;

import java.util.List;
import java.util.function.Function;

/**
 * 커서 페이징 결과 — 콘텐츠 + 커서 응답 메타 묶음. {@code content}는 불변이다.
 *
 * @param content 슬라이스 콘텐츠
 * @param meta 커서 응답 메타
 * @param <T> 콘텐츠 타입
 * @param <C> 커서 타입
 */
public record Slice<T, C>(List<T> content, SliceMeta<C> meta) {

    public Slice {
        if (content == null) {
            throw new IllegalArgumentException("content must not be null");
        }
        if (meta == null) {
            throw new IllegalArgumentException("meta must not be null");
        }
        content = List.copyOf(content);
    }

    public static <T, C> Slice<T, C> of(List<T> content, SliceMeta<C> meta) {
        return new Slice<>(content, meta);
    }

    /** 콘텐츠를 변환하고 커서 메타는 보존한다. */
    public <R> Slice<R, C> map(Function<? super T, ? extends R> mapper) {
        return new Slice<>(content.stream().<R>map(mapper).toList(), meta);
    }
}
