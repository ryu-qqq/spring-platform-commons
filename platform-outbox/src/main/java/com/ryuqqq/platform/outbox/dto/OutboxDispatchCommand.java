package com.ryuqqq.platform.outbox.dto;

/** 배치 dispatch 시 발행에 필요한 business id + 멱등키 쌍. */
public record OutboxDispatchCommand(String businessId, String idempotencyKey) {}
