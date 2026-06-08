package com.ryuqqq.platform.scheduler.aspect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ryuqqq.platform.common.scheduler.SchedulerBatchProcessingResult;
import com.ryuqqq.platform.scheduler.annotation.SchedulerJob;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SchedulerLoggingAspectTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final SchedulerLoggingAspect aspect = new SchedulerLoggingAspect(registry);

    private SchedulerJob job(String name) {
        SchedulerJob job = mock(SchedulerJob.class);
        when(job.value()).thenReturn(name);
        return job;
    }

    @Test
    @DisplayName("정상 실행 시 결과를 그대로 반환하고 success·item 메트릭을 기록한다")
    void recordsSuccessMetrics() throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        SchedulerBatchProcessingResult result = SchedulerBatchProcessingResult.of(3, 2, 1);
        when(pjp.proceed()).thenReturn(result);

        Object returned = aspect.around(pjp, job("ok-job"));

        assertThat(returned).isSameAs(result);
        assertThat(
                        registry.find("scheduler.job.executions")
                                .tag("outcome", "success")
                                .counter()
                                .count())
                .isEqualTo(1.0);
        assertThat(
                        registry.find("scheduler.job.items")
                                .tag("result", "success")
                                .counter()
                                .count())
                .isEqualTo(2.0);
        assertThat(
                        registry.find("scheduler.job.items")
                                .tag("result", "failed")
                                .counter()
                                .count())
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("예외 발생 시 error 메트릭을 기록하고 예외를 전파한다")
    void recordsErrorMetrics() throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.proceed()).thenThrow(new IllegalStateException("boom"));

        assertThatThrownBy(() -> aspect.around(pjp, job("err-job")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(
                        registry.find("scheduler.job.executions")
                                .tag("outcome", "error")
                                .counter()
                                .count())
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("MeterRegistry 가 null 이어도(메트릭 미존재) NPE 없이 로깅·결과 반환은 정상 동작한다")
    void worksWithoutMeterRegistry() throws Throwable {
        SchedulerLoggingAspect noMetricAspect = new SchedulerLoggingAspect(null);
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        SchedulerBatchProcessingResult result = SchedulerBatchProcessingResult.of(2, 2, 0);
        when(pjp.proceed()).thenReturn(result);

        Object returned = noMetricAspect.around(pjp, job("no-metric-job"));

        assertThat(returned).isSameAs(result);
    }

    @Test
    @DisplayName("MeterRegistry null 일 때 예외도 정상 전파한다(메트릭 no-op)")
    void propagatesExceptionWithoutMeterRegistry() throws Throwable {
        SchedulerLoggingAspect noMetricAspect = new SchedulerLoggingAspect(null);
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.proceed()).thenThrow(new IllegalStateException("boom"));

        assertThatThrownBy(() -> noMetricAspect.around(pjp, job("no-metric-err")))
                .isInstanceOf(IllegalStateException.class);
    }
}
