package com.ryuqqq.platform.resilient;

/**
 * 실제 HTTP 전송을 담당하는 함수형 인터페이스.
 *
 * <p>사용자가 구현하여 SDK에 주입한다.
 * RestClient, WebClient, Apache HC5 등 어떤 HTTP 클라이언트든 사용 가능.
 */
@FunctionalInterface
public interface RequestSender {

    /**
     * HTTP 요청을 전송하고 응답을 반환한다.
     *
     * @param request 요청 정보 (URL, method, headers, body)
     * @return 응답 (status, headers, body)
     * @throws Exception 전송 중 발생한 모든 예외 (SDK가 분류 처리)
     */
    RawResponse send(ExternalRequest request) throws Exception;
}
