package com.ryuqqq.platform.resilient;

import java.util.Optional;

import com.ryuqqq.platform.resilient.exception.ExternalCallException;

/**
 * 응답을 예외로 변환하는 분류기.
 * 기본 구현체가 제공되며, 사용자가 커스텀 가능.
 */
public interface ResponseClassifier {

    /**
     * HTTP 응답을 분석하여 적절한 예외를 반환한다.
     * 정상 응답이면 empty 반환.
     */
    Optional<ExternalCallException> classify(RawResponse response);

    /**
     * 전송 중 발생한 예외를 SDK 예외로 변환한다.
     */
    ExternalCallException classifyException(Exception cause);
}
