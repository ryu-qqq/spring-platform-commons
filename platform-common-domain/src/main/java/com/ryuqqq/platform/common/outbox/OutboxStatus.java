package com.ryuqqq.platform.common.outbox;

/** 아웃박스 처리 상태. PENDING → PROCESSING → SENT | FAILED. */
public enum OutboxStatus {
    PENDING,
    PROCESSING,
    SENT,
    FAILED
}
