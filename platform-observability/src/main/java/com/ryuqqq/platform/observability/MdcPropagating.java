package com.ryuqqq.platform.observability;

import java.util.Map;
import org.slf4j.MDC;

/**
 * 비동기 경계에서 MDC(traceId 등)를 워커 스레드로 전파한다. 제출 스레드의 MDC 스냅샷을 캡처해
 * 워커에서 복원하고, 작업 후 워커의 기존 컨텍스트를 원복한다(스레드풀 재사용 누수 방지).
 *
 * <pre>{@code
 * executor.submit(MdcPropagating.wrap(() -> doWork()));
 * }</pre>
 */
public final class MdcPropagating {

    private MdcPropagating() {}

    /** 제출 시점 MDC 스냅샷을 워커 스레드에 복원해 실행하는 Runnable로 감싼다. */
    public static Runnable wrap(Runnable task) {
        Map<String, String> captured = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> backup = MDC.getCopyOfContextMap();
            setOrClear(captured);
            try {
                task.run();
            } finally {
                setOrClear(backup);
            }
        };
    }

    private static void setOrClear(Map<String, String> context) {
        if (context != null) {
            MDC.setContextMap(context);
        } else {
            MDC.clear();
        }
    }
}
