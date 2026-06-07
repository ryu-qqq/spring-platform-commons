package com.ryuqqq.platform.common.scheduler;

/**
 * 배치 처리 결과 (스케줄러 등 배치 작업). 순수 record — application 레이어가 반환하고
 * {@code platform-scheduler}의 aspect가 요약 로깅·메트릭에 사용한다.
 *
 * @param total 전체 처리 대상 수
 * @param success 성공 수
 * @param failed 실패 수
 */
public record SchedulerBatchProcessingResult(int total, int success, int failed) {

    public static SchedulerBatchProcessingResult of(int total, int success, int failed) {
        return new SchedulerBatchProcessingResult(total, success, failed);
    }

    public static SchedulerBatchProcessingResult empty() {
        return new SchedulerBatchProcessingResult(0, 0, 0);
    }

    public boolean hasFailures() {
        return failed > 0;
    }

    public SchedulerBatchProcessingResult merge(SchedulerBatchProcessingResult other) {
        return new SchedulerBatchProcessingResult(
                this.total + other.total, this.success + other.success, this.failed + other.failed);
    }
}
