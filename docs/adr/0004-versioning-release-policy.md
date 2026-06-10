# 0004. 버저닝/릴리스 정책 — pre-1.0 형식 규율, 무거운 게이트는 1.0에

- 상태: Accepted
- 날짜: 2026-06-10

## 맥락 (Context)

spring-platform-commons는 JitPack으로 배포되는 멀티모듈 SDK다. 현재 `version = 0.1.0`(태그 `v0.1.0` 1개, README는 태그 안 된 `v0.2.0` 참조로 불일치). 자가 감사 fleet의 `versioning-auditor`가 resilient-client에서 3 finding을 포착했다:

- `@Deprecated`에 `since`/`forRemoval` 누락 (ResilientClientFactory #29) — Spring Boot deprecation 형식 미준수.
- 바이너리 호환 게이트(japicmp/revapi) 부재 — 배포 SDK인데 ABI 회귀 안전망 없음.
- CHANGELOG 부재 — JitPack 소비자용 변경 이력 없음.

세 finding은 "버저닝/릴리스 거버넌스 부재"라는 한 뿌리다. 단 **현재 0.x는 SemVer상 명시적으로 불안정 단계**라, 1.0 라이브러리에 적용할 엄격 게이트를 그대로 들이면 정당한 0.x 진화(minor에서 breaking 허용)를 막는다.

## 결정 동인 (Decision Drivers)

- build-out 진행 중 — API가 아직 수렴 중이라 pre-1.0 유연성이 필요.
- 소비자(JitPack) 신뢰 — @Deprecated 형식·CHANGELOG로 변경 의도를 투명하게.
- 과투자 회피(YAGNI) — 0.x에서 ABI 게이트·2-minor 제거 보장은 비용 대비 이득 낮음.
- 자가 감사 정합 — auditor finding이 정책으로 깔끔히 닫히거나 의도된 defer로 분류되게.

## 검토한 옵션 (Considered Options)

- **(1) pre-1.0 SemVer**: (a) 지금부터 엄격(minor breaking 금지) vs **(b) 표준 0.x 해석**(1.0 전 minor breaking 허용, 1.0 = build-out 완료 + 첫 서버 입양).
- **(2) Deprecation**: (a) Spring Boot 풀(2-minor 제거 윈도 보장) vs **(b) 형식만 강제**(`@Deprecated(since, forRemoval=true)` + Javadoc 필수, pre-1.0 제거 윈도 미보장) vs (c) 정책 없음.
- **(3) CHANGELOG**: **(a) 루트 `CHANGELOG.md`**(Keep a Changelog) vs (b) GitHub Releases만.
- **(4) binary-compat 게이트**: (a) 지금 도입 vs **(b) 1.0까지 DEFER**(0.x breaking 허용이라 게이트가 정당 진화 차단) vs (c) 안 함.

## 결정 (Decision Outcome — ①b ②b ③a ④b)

1. **pre-1.0 = 표준 0.x.** 1.0 전 minor에서 breaking 변경 허용(SemVer 명시 의미). **1.0 승격 기준 = build-out 완료 + 첫 서버 입양으로 API 안정 확인.** 그 전까지 `0.MINOR.PATCH` 운영(기능=minor, 수정=patch, breaking도 minor 가능). README의 버전 참조는 실제 태그와 일치시킨다.
2. **Deprecation = 형식 규율만.** 공개 API deprecate 시 `@Deprecated(since="<deprecate된 버전>", forRemoval=true)` + Javadoc `@deprecated since <ver> ... in favor of <대체>` 필수. **단 pre-1.0 제거 윈도(2-minor)는 보장하지 않는다** — any minor에서 제거 가능. 1.0에서 Spring Boot식 2-minor 윈도로 승격.
3. **CHANGELOG 채택.** 레포 루트 `CHANGELOG.md`(Keep a Changelog 형식), Unreleased 섹션 + 버전별 Added/Changed/Deprecated/Removed/Fixed.
4. **binary-compat 게이트 1.0까지 DEFER.** japicmp/revapi는 1.0 진입 시 CI에 도입. 그 전까지 `versioning-auditor`의 `binary-compat-gate` check는 pre-1.0에서 `info`(의도된 defer)로 다룬다.

## 결과 (Consequences)

긍정:
- auditor 3 finding이 정합되게 닫힌다 — @Deprecated 형식 수정(즉시), CHANGELOG 추가(즉시), binary-compat은 1.0-defer(의도).
- pre-1.0 유연성 유지하며 deprecation·변경 이력은 투명.

부담·후속:
- **즉시 적용**: ResilientClientFactory의 `@Deprecated` → `@Deprecated(since="0.2.0", forRemoval=true)` 보강. 루트 `CHANGELOG.md` 신설. `versioning-auditor` pre-1.0 binary-compat=info 조정.
- **1.0 진입 시 후속 ADR**: japicmp/revapi 도입 + 2-minor deprecation 윈도 승격 + 1.0 API 동결 기준.
- README 등 버전 참조를 실제 태그와 동기화(현재 v0.2.0 참조 vs v0.1.0 태그 불일치 해소).

## 관련

- ADR-0002(repo 토폴로지) · ADR-0003(드리프트 표준 수렴)
- 근거 리서치: vault `platform-team-taxonomy.md` 축5(버저닝 quality bar) · `platform_fleet_autopilot`
- 자가 감사: `versioning-auditor`(이 정책의 점검 주체)
