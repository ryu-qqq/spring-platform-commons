package com.ryuqqq.platform.scheduler.aspect;

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
 * <p>메트릭: {@code scheduler.job.duration}(Timer)·{@code scheduler.job.executions}(성공/실패 카운터)·
 * {@code scheduler.job.items}(배치 내 개별 아이템 성공/실패).
 */
@Aspect
public class SchedulerLoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(SchedulerLoggingAspect.class);
    private static final String TRACE_ID_KEY = "traceId";

    private final MeterRegistry meterRegistry;

    public SchedulerLoggingAspect(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Around("@annotation(schedulerJob)")
    public Object around(ProceedingJoinPoint joinPoint, SchedulerJob schedulerJob) throws Throwable {
        String jobName = schedulerJob.value();
        String traceId = generateTraceId();
        Timer.Sample sample = Timer.start(meterRegistry);

        MDC.put(TRACE_ID_KEY, traceId);
        try {
            log.info("[{}] 스케줄러 작업 시작", jobName);

            Object result = joinPoint.proceed();

            sample.stop(timer(jobName));
            recordExecution(jobName, "success");
            logResult(jobName, result);
            recordBatchMetrics(jobName, result);

            return result;
        } catch (Exception e) {
            sample.stop(timer(jobName));
            recordExecution(jobName, "error");
            log.error("[{}] 스케줄러 작업 실패 - error: {}", jobName, e.getMessage(), e);
            throw e;
        } finally {
            MDC.remove(TRACE_ID_KEY);
        }
    }

    private Timer timer(String jobName) {
        return Timer.builder("scheduler.job.duration")
                .tag("job_name", jobName)
                .publishPercentileHistogram()
                .register(meterRegistry);
    }

    private void recordExecution(String jobName, String outcome) {
        Counter.builder("scheduler.job.executions")
                .tag("job_name", jobName)
                .tag("outcome", outcome)
                .register(meterRegistry)
                .increment();
    }

    private void recordBatchMetrics(String jobName, Object result) {
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
