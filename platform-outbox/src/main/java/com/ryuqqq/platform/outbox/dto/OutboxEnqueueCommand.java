package com.ryuqqq.platform.outbox.dto;

/** Queue outbox 릴레이 시 발행에 필요한 business id + 멱등키 쌍. */
public record OutboxEnqueueCommand(String businessId, String idempotencyKey) {}
