# Changelog

이 프로젝트의 주요 변경을 기록한다. 형식은 [Keep a Changelog](https://keepachangelog.com/),
버전은 [SemVer](https://semver.org/)를 따른다. **pre-1.0(0.x)은 minor에서 breaking 변경을 허용한다**
(버저닝 정책: [ADR-0004](docs/adr/0004-versioning-release-policy.md)).

## [Unreleased]

### Added
- platform-archrules: **도메인 작성 컨벤션 룰 + 건강 리포트**(하이브리드). `DomainConventionRules`
  11종(시간 주입·setter·DomainException 상속·aggregate 정의·일급 컬렉션·타입 형태·패키지 슬라이스,
  상대 매처 `..domain..`)과 `DomainHealthReporter`(빌드 안 죽이고 score+findings 산출) + `Severity`·
  `HealthReport`. CRITICAL(프레임워크·Lombok)은 게이트, 나머지는 리포트. 작성 룰은 자작(marketplace 귀납).
- platform-archrules: `DOMAIN_FRAMEWORK_FREE`에 `lombok..` 금지 추가(도메인 Lombok 차단).
- **(breaking)** common-domain 정렬/결과 모델 보강 — `Sort<T>`·`SortOrder<T>`(복합 정렬
  `ORDER BY a DESC, b ASC`), `Page<T>`·`Slice<T,C>`(콘텐츠+메타 결과 래퍼, `map()`). `QueryContext`·
  `CursorQueryContext`의 `(sortKey, sortDirection)` 필드가 `Sort<T>`로 교체(단일 정렬 편의 팩토리 유지).
- **platform-observability** 모듈 신설 — 횡단 관측성 어휘 SSOT(의존성 0, 패키지
  `com.ryuqqq.platform.observability`). `MdcKeys`가 이 모듈로 이동. 근거:
  [ADR-0006](docs/adr/0006-common-domain-kernel-vs-observability-module.md).

### Changed
- **(breaking)** `MdcKeys` 이동 — `com.ryuqqq.platform.common.observability.MdcKeys` →
  `com.ryuqqq.platform.observability.MdcKeys`. 로깅 키·HTTP 헤더는 인프라 어휘이므로 도메인 커널이
  아니라 `platform-observability` 소유. import 경로 변경 필요(소비측 web·security·scheduler 반영 완료).
- **(breaking)** `Versioned` 읽기전용화 — `void refreshVersion(long)` 제거, `long version()`만 남김.
  version 반영은 영속성 매퍼 책임. `platform-common-domain`이 순수 도메인 커널로 수렴(ADR-0006).
- **(breaking)** `platform-outbox` SPI `PerItemOutboxAdapter`의 `OutboxStatus outboxStatus(O)` →
  `boolean isTerminalFailure(O)`. 릴레이가 status에서 실제 필요로 하는 "종착 실패 여부" 하나만 노출(ISP).
  소비측 어댑터는 자기 status를 이 불리언으로 매핑한다.

### Removed
- **(breaking)** `platform-common-domain`의 `com.ryuqqq.platform.common.outbox.OutboxStatus` enum 제거.
  outbox 처리 상태는 인프라 수명주기이므로 도메인 커널이 아니라 소비측 도메인이 `<Domain>OutboxStatus`로
  소유한다. 근거·대안 검토: [ADR-0005](docs/adr/0005-outbox-status-shared-enum-vs-behavioral-spi.md).
- 헥사고날 템플릿 스켈레톤(`domain`·`application`·`adapter-in`·`adapter-out`·`bootstrap` 모듈,
  `example-client` 포함) 제거 — 이 레포는 **SDK 전용**으로 수렴. 템플릿은 발행 아티팩트가 아니라
  소비측 영향 없음. `architecture-tests`는 유지(플랫폼 SDK 레이어 게이트 `PlatformSdkLayerArchTest`).

## [0.2.0] - 2026-06-15

> 전 플랫폼 모듈의 **최초 JitPack 배포**. `v0.1.0`은 resilient-client만 포함했고, 이번 릴리스로
> platform-* 모듈(common-domain·application·web·bootstrap·persistence-jpa·archrules·redis·
> scheduler·security·outbox)이 처음으로 버전 좌표로 소비 가능해진다.

### Added
- platform-* 모듈 전체를 JitPack 좌표(`com.github.ryu-qqq.spring-platform-commons:<module>:v0.2.0`)로
  최초 배포.
- platform-archrules: **한 줄 Apply**(`ArchTests.in(HexagonalArchRules.class)`) strict 표면과
  **frozen ratchet 번들**(`HexagonalArchRulesFrozen`) — 레거시 위반 동결 + 신규 위반만 차단으로
  기존 레포의 점진 입양 지원. violation-store는 소비측 소유.
- 자가 감사·개선 fleet — 감사 에이전트(autoconfig·observability·versioning auditor)와
  `platform-audit-sweep` 스코어카드, 자율 수정 파이프라인(`platform-fix-item`·`platform-fix-fleet`),
  자동머지 보수 게이트(merge-gate, scope: tests-docs/internal).
- `IdempotencyKeyValue` 단일값 VO (common-domain).
- 전 모듈 README(역할·확장점 문서화).
- 버저닝/릴리스 정책 (ADR-0004) · 루트 CHANGELOG.

### Changed
- resilient-client: RestClient를 auto-configured `RestClient.Builder` 기반으로 빌드 —
  W3C traceparent 자동 전파 복구(타임아웃 유지).
- platform-archrules: 기존 규칙 상수 3종에 `@ArchTest` 부여(하위호환) — `ArchTests.in(...)` 집약 입양 지원.

### Deprecated
- `ResilientClientFactory(ResilientClientProperties, MetricsRecorder)` 2-인자 생성자
  (`@Deprecated(since="0.2.0", forRemoval=true)`) — 트레이스 전파를 위해 `RestClient.Builder`를
  받는 3-인자 생성자를 사용하라.

## [0.1.0] - 2026-03-31
- 초기 공개: resilient-client(core·metrics·spring-boot-starter)만 배포.

[Unreleased]: https://github.com/ryu-qqq/spring-platform-commons/compare/v0.2.0...HEAD
[0.2.0]: https://github.com/ryu-qqq/spring-platform-commons/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/ryu-qqq/spring-platform-commons/releases/tag/v0.1.0
