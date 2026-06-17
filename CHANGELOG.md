# Changelog

이 프로젝트의 주요 변경을 기록한다. 형식은 [Keep a Changelog](https://keepachangelog.com/),
버전은 [SemVer](https://semver.org/)를 따른다. **pre-1.0(0.x)은 minor에서 breaking 변경을 허용한다**
(버저닝 정책: [ADR-0004](docs/adr/0004-versioning-release-policy.md)).

## [Unreleased]

### Added
- **Spotless 포맷 게이트** (google-java-format AOSP·4-space) — `ratchetFrom 'origin/main'`으로
  **main 대비 바뀐/새 `.java`만** 검사(레거시 grandfather, 전체 재포맷 없음). `spotlessCheck`가
  `check`→`build`에 연결돼 CI에서 자동 강제. CI checkout `fetch-depth: 0`(ratchet 비교용).
  새 코드 포맷은 `./gradlew spotlessApply`.

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
