package com.ryuqqq.platform.scheduler.aspect;

import com.ryuqqq.platform.common.observability.MdcKeys;
import com.ryuqqq.platform.common.scheduler.SchedulerBatchProcessingResult;
import com.ryuqqq.platform.scheduler.annotation.SchedulerJob;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.UUID;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * {@link SchedulerJob} 메서드에 TraceId/MDC·시작종료 로깅·Micrometer 메트릭·
 * {@link SchedulerBatchProcessingResult} 요약을 적용하는 AOP. platform-scheduler 자동설정이
 * {@code @Bean}으로 등록한다 (라이브러리 모범 — {@code @Component} 스캔에 의존하지 않음).
 *
 * <p><b>로깅·TraceId(불변 핵심)는 메트릭과 분리된다</b>: {@link MeterRegistry}가 null이면(소비측에
 * Micrometer 미존재) 메트릭만 no-op하고 로깅·TraceId·결과 요약은 그대로 동작한다.
 *
 * <p>메트릭(레지스트리 있을 때): {@code scheduler.job.duration}(Timer)·{@code scheduler.job.executions}
 * (성공/실패 카운터)·{@code scheduler.job.items}(배치 내 개별 아이템 성공/실패).
 */
@Aspect
public class SchedulerLoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(SchedulerLoggingAspect.class);

    /** nullable — null이면 메트릭 no-op, 로깅·TraceId는 정상 동작. */
    private final MeterRegistry meterRegistry;

    public SchedulerLoggingAspect(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Around("@annotation(schedulerJob)")
    public Object around(ProceedingJoinPoint joinPoint, SchedulerJob schedulerJob) throws Throwable {
        String jobName = schedulerJob.value();
        String traceId = generateTraceId();
        Timer.Sample sample = (meterRegistry != null) ? Timer.start(meterRegistry) : null;

        MDC.put(MdcKeys.TRACE_ID, traceId);
        try {
            log.info("[{}] 스케줄러 작업 시작", jobName);

            Object result = joinPoint.proceed();

            stopTimer(sample, jobName);
            recordExecution(jobName, "success");
            logResult(jobName, result);
            recordBatchMetrics(jobName, result);

            return result;
        } catch (Throwable t) {
            // proceed()는 Throwable을 던진다 — Error(OOM·NoClassDefFoundError 등)도 계측·로깅 후 재던진다(삼키지 않음).
            stopTimer(sample, jobName);
            recordExecution(jobName, "error");
            log.error("[{}] 스케줄러 작업 실패 - error: {}", jobName, t.getMessage(), t);
            throw t;
        } finally {
            MDC.remove(MdcKeys.TRACE_ID);
        }
    }

    private void stopTimer(Timer.Sample sample, String jobName) {
        if (meterRegistry == null || sample == null) {
            return;
        }
        sample.stop(timer(jobName));
    }

    private Timer timer(String jobName) {
        return Timer.builder("scheduler.job.duration")
                .tag("job_name", jobName)
                .publishPercentileHistogram()
                .register(meterRegistry);
    }

    private void recordExecution(String jobName, String outcome) {
        if (meterRegistry == null) {
            return;
        }
        Counter.builder("scheduler.job.executions")
                .tag("job_name", jobName)
                .tag("outcome", outcome)
                .register(meterRegistry)
                .increment();
    }

    private void recordBatchMetrics(String jobName, Object result) {
        if (meterRegistry == null) {
            return;
        }
        if (result instanceof SchedulerBatchProcessingResult batchResult && batchResult.total() > 0) {
            Counter.builder("scheduler.job.items")
                    .tag("job_name", jobName)
                    .tag("result", "success")
                    .register(meterRegistry)
                    .increment(batchResult.success());
            Counter.builder("scheduler.job.items")
                    .tag("job_name", jobName)
                    .tag("result", "failed")
                    .register(meterRegistry)
                    .increment(batchResult.failed());
        }
    }

    private String generateTraceId() {
        return "scheduler-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private void logResult(String jobName, Object result) {
        if (result instanceof SchedulerBatchProcessingResult batchResult) {
            logBatchResult(jobName, batchResult);
        } else {
            log.info("[{}] 스케줄러 작업 완료", jobName);
        }
    }

    private void logBatchResult(String jobName, SchedulerBatchProcessingResult result) {
        if (result.total() == 0) {
            log.info("[{}] 스케줄러 작업 완료 - 처리 대상 없음", jobName);
            return;
        }
        if (result.hasFailures()) {
            log.warn(
                    "[{}] 스케줄러 작업 완료 - total: {}, success: {}, failed: {}",
                    jobName,
                    result.total(),
                    result.success(),
                    result.failed());
        } else {
            log.info(
                    "[{}] 스케줄러 작업 완료 - total: {}, success: {}",
                    jobName,
                    result.total(),
                    result.success());
        }
    }
}
