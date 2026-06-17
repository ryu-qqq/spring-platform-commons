/**
 * Platform cross-cutting observability vocabulary.
 *
 * <p>{@code MdcKeys} — MDC 키·인바운드 트레이스 헤더 이름의 SSOT. 의존성 0인 최저(Independent) 모듈로,
 * 로깅 컨텍스트·트레이스 상관 어휘가 레이어를 가로질러 공유된다. 도메인 커널(platform-common-domain)이
 * 아니라 여기 둔다 — 로깅·헤더는 인프라 관심사이기 때문(ADR-0006).
 */
package com.ryuqqq.platform.observability;
