/**
 * Platform Outbox — 트랜스포트 중립 Outbox relay 공통 모듈 (application 레이어).
 *
 * <p>수명주기(claim → mark → release)는 트랜스포트 무관 base SPI {@link
 * com.ryuqqq.platform.outbox.spi.OutboxStore} 로 공유하고, 발행 채널별로 두 릴레이 템플릿을 둔다:
 * 배치 발행 {@link com.ryuqqq.platform.outbox.BatchOutboxRelayTemplate}(+{@link
 * com.ryuqqq.platform.outbox.spi.BatchOutboxAdapter})와 건별 발송 {@link
 * com.ryuqqq.platform.outbox.PerItemOutboxRelayTemplate}(+{@code PerItemOutboxAdapter}). 소비측이
 * 어댑터를 구현하면 여러 도메인의 릴레이 흐름이 한 곳으로 수렴한다.
 */
package com.ryuqqq.platform.outbox;
