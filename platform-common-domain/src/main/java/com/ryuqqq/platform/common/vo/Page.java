package com.ryuqqq.platform.common.vo;

import java.util.List;
import java.util.function.Function;

/**
 * offset 페이징 결과 — 콘텐츠 + 응답 메타 묶음. {@code content}는 불변이다.
 *
 * @param content 페이지 콘텐츠
 * @param meta offset 응답 메타
 */
public record Page<T>(List<T> content, PageMeta meta) {

    public Page {
        if (content == null) {
            throw new IllegalArgumentException("content must not be null");
        }
        if (meta == null) {
            throw new IllegalArgumentException("meta must not be null");
        }
        content = List.copyOf(content);
    }

    public static <T> Page<T> of(List<T> content, PageMeta meta) {
        return new Page<>(content, meta);
    }

    /** 콘텐츠를 변환하고 메타는 보존한다. */
    public <R> Page<R> map(Function<? super T, ? extends R> mapper) {
        return new Page<>(content.stream().<R>map(mapper).toList(), meta);
    }
}
