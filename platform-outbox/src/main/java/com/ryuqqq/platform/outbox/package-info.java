/**
 * Platform Outbox — generic Queue Outbox relay 공통 모듈 (application 레이어).
 *
 * <p>{@link com.ryuqqq.platform.outbox.BatchOutboxRelayTemplate} 이 claim → enqueue → bulkMark
 * 흐름을 캡슐화하고, 도메인 의존부(타입·ID 추출·발행·마킹)는 {@link
 * com.ryuqqq.platform.outbox.spi.BatchOutboxAdapter} SPI 로 위임한다. 소비측이 SPI 를 구현하면 여러
 * 도메인의 릴레이 흐름이 한 곳으로 수렴한다.
 *
 * <p>Callback Outbox relay 와 {@code OutboxStatus}·{@code OutboxRetryPolicy} 는 2단계로 분리되어 있다.
 */
package com.ryuqqq.platform.outbox;
