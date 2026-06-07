package com.ryuqqq.platform.scheduler.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 스케줄러 작업을 표시하는 어노테이션. 이 어노테이션이 붙은 메서드는
 * {@code SchedulerLoggingAspect}에 의해 TraceId/MDC 설정·시작종료 로깅·Micrometer 메트릭·
 * {@code SchedulerBatchProcessingResult} 요약 로깅이 적용된다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SchedulerJob {

    /** 스케줄러 작업명. 로깅·메트릭 태그에 사용된다. */
    String value();
}
