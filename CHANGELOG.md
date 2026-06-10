# Changelog

이 프로젝트의 주요 변경을 기록한다. 형식은 [Keep a Changelog](https://keepachangelog.com/),
버전은 [SemVer](https://semver.org/)를 따른다. **pre-1.0(0.x)은 minor에서 breaking 변경을 허용한다**
(버저닝 정책: [ADR-0004](docs/adr/0004-versioning-release-policy.md)).

## [Unreleased]

### Added
- 자가 감사·개선 fleet — 감사 에이전트(autoconfig·observability·versioning auditor)와
  `platform-audit-sweep` 스코어카드, 자율 수정 파이프라인(`platform-fix-item`·`platform-fix-fleet`),
  자동머지 보수 게이트(merge-gate, scope: tests-docs/internal).
- `IdempotencyKeyValue` 단일값 VO (common-domain).
- 전 모듈 README(역할·확장점 문서화).
- 버저닝/릴리스 정책 (ADR-0004) · 루트 CHANGELOG.

### Changed
- resilient-client: RestClient를 auto-configured `RestClient.Builder` 기반으로 빌드 —
  W3C traceparent 자동 전파 복구(타임아웃 유지).

### Deprecated
- `ResilientClientFactory(ResilientClientProperties, MetricsRecorder)` 2-인자 생성자
  (`@Deprecated(since="0.2.0", forRemoval=true)`) — 트레이스 전파를 위해 `RestClient.Builder`를
  받는 3-인자 생성자를 사용하라.

## [0.1.0] - 2026-06
- 초기 공개 모듈: archrules · redis · scheduler · security · outbox · web · persistence-jpa ·
  common-domain · common-application · bootstrap · resilient-client.

[Unreleased]: https://github.com/ryu-qqq/spring-platform-commons/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/ryu-qqq/spring-platform-commons/releases/tag/v0.1.0
