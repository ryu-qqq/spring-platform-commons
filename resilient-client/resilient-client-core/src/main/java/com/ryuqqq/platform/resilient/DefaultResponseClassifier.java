package com.ryuqqq.platform.resilient;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import com.ryuqqq.platform.resilient.exception.BadRequestException;
import com.ryuqqq.platform.resilient.exception.ClientException;
import com.ryuqqq.platform.resilient.exception.ExternalCallException;
import com.ryuqqq.platform.resilient.exception.NetworkException;
import com.ryuqqq.platform.resilient.exception.ServerException;

/**
 * 기본 응답 분류기.
 *
 * <ul>
 *   <li>2xx → 정상</li>
 *   <li>400 → BadRequestException</li>
 *   <li>4xx → ClientException</li>
 *   <li>5xx → ServerException</li>
 *   <li>SocketTimeoutException, ConnectException → NetworkException</li>
 * </ul>
 */
public class DefaultResponseClassifier implements ResponseClassifier {

    @Override
    public Optional<ExternalCallException> classify(RawResponse response) {
        if (response.is2xx()) {
            return Optional.empty();
        }

        String bodySnippet = extractBodySnippet(response);

        if (response.statusCode() == 400) {
            return Optional.of(new BadRequestException(
                "Bad Request (400): " + bodySnippet));
        }

        if (response.is4xx()) {
            return Optional.of(new ClientException(
                response.statusCode(),
                "Client Error (" + response.statusCode() + "): " + bodySnippet));
        }

        if (response.is5xx()) {
            return Optional.of(new ServerException(
                response.statusCode(),
                "Server Error (" + response.statusCode() + "): " + bodySnippet));
        }

        return Optional.of(new ServerException(
            response.statusCode(),
            "Unexpected status (" + response.statusCode() + "): " + bodySnippet));
    }

    @Override
    public ExternalCallException classifyException(Exception cause) {
        if (cause instanceof ExternalCallException ece) {
            return ece;
        }

        Throwable root = findRoot(cause);

        if (root instanceof SocketTimeoutException) {
            return new NetworkException("Request timed out", cause);
        }
        if (root instanceof ConnectException) {
            return new NetworkException("Connection failed", cause);
        }
        if (root instanceof java.net.UnknownHostException) {
            return new NetworkException("Unknown host", cause);
        }
        if (root instanceof java.io.IOException) {
            return new NetworkException("I/O error: " + root.getMessage(), cause);
        }

        return new NetworkException("Unexpected error: " + cause.getMessage(), cause);
    }

    private String extractBodySnippet(RawResponse response) {
        if (response.body() == null || response.body().length == 0) {
            return "(empty body)";
        }
        String body = new String(response.body(), StandardCharsets.UTF_8);
        return body.length() > 200 ? body.substring(0, 200) + "..." : body;
    }

    private Throwable findRoot(Throwable t) {
        Throwable current = t;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }
}
