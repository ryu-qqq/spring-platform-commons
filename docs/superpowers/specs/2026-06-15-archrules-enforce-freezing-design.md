---
title: platform-archrules 고도화 Spec 1 — Enforce 토대 (FreezingArchRule ratchet + 한 줄 Apply)
date: 2026-06-15
스냅샷: 2026-06-15
project: spring-platform-commons
status: 설계 승인됨 (구현 대기)
---

# platform-archrules 고도화 Spec 1 — Enforce 토대

- **선례 근거**: vault `adoption-gap.md` — Netflix Nebula `Share → Apply → Enforce` 벤치마크
- **선행 결정**: `docs/adr/0002-repo-topology-monorepo-vs-multirepo.md` (토폴로지 A/B **defer**),
  `docs/adr/0003-drift-standard-convergence.md` (드리프트 표준의 ArchUnit 강제는 Enforce 단계 후속으로 명시)
- **현 모듈**: `platform-archrules` — 헥사고날 규칙 3종(`HexagonalArchRules`) + self-test

## 1. 배경·문제

`platform-archrules`는 Netflix Nebula의 `Share → Apply → Enforce`를 벤치마크해 설계됐으나, 현재는
**Share만 충족**한다. 규칙 3종을 이식 가능한 상대 패키지 매처로 잘 추출했지만:

- **Enforce가 binary다.** 규칙은 pass/fail뿐 — 레거시 위반이 있는 기존 서버(MarketPlace 등)가
  입양하려면 **빌드가 즉시 터진다**. 점진 입양 경로가 없다.
- **Apply에 마찰이 있다.** 소비측이 규칙마다 `@ArchTest static final ArchRule`을 수동 선언해야 한다.
- 그 결과 입양갭의 근본 원인(마찰)이 archrules에도 그대로 있다 — adoption-gap이 진단한 것과 동일 패턴.

**핵심 통찰:** baseline/freezing 없이 규칙(카탈로그)부터 늘리면 입양이 *더* 어려워진다(규칙↑ = 기존
위반↑ = 빌드가 더 크게 터짐). 따라서 **Enforce 토대를 먼저** 깔고, 규칙 정밀화·카탈로그 확장은 그
위에서 안전하게 한다(= Spec 2).

## 2. 목표 / 비목표

**목표 (Spec 1)**

`platform-archrules`를 "신규 레포만 통과하는 binary 게이트"에서 **"기존 레포도 레거시를 동결한 채 입양
가능 + 신규 위반만 차단하는 ratchet 게이트"**로 끌어올린다. 동시에 소비측 입양을 **한 줄**로 줄인다.

**비목표 (경계 — YAGNI)**

- severity/우선순위 다이얼(HIGH/MEDIUM/LOW) ❌ — freezing이 점진성을 담당하므로 중복.
- 발행 Gradle convention plugin ❌ — ADR-0002가 토폴로지 A/B를 defer. 빌드설정 공유는 그 결정에
  종속되므로 시기상조. 순수 ArchUnit 관용구(project/발행 양쪽 동작)로만 Apply를 개선한다.
- 신규 규칙 카탈로그·기존 매처 정밀화 ❌ — **Spec 2**.
- violation-store 자동 커밋·CI 배선 자동화 ❌ — 소비측 README 가이드까지만.

## 3. 결정 (승인됨)

1. **Enforce 모델 = Freezing 중심**, severity 미도입.
2. **strict / frozen 두 표면을 모두 제공.** raw 규칙(strict, 레거시 없는 그린필드용)은 유지하고,
   `FreezingArchRule`로 감싼 frozen 변종(브라운필드용)을 추가한다.
3. **violation-store는 소비측 레포 소유.** 라이브러리는 freezing 래퍼 + store 규약(README 템플릿)만
   제공하고, baseline 텍스트 파일 자체는 각 소비 레포에 생기고 git에 커밋된다.
4. **Apply는 ArchUnit `ArchRules.in()` 집약**으로 한 줄화한다(발행 플러그인 없음).

## 4. 핵심 메커니즘 — `FreezingArchRule` ratchet

- 기존 3종 규칙을 `FreezingArchRule.freeze(rule)`로 감싼다.
- 첫 실행: 현재 위반 전부를 violation-store에 baseline으로 기록.
- 이후: store에 **없는 신규 위반만** 실패시킨다(기존 위반은 동결).
- 위반이 고쳐지면 store에서 자동 prune → **되돌아갈 수 없는 ratchet**. 점진적으로 0을 향해 조여진다.

## 5. 컴포넌트

### 5.1 frozen 묶음 — `HexagonalArchRulesFrozen` (신규, src/main)

`@ArchTest` 필드를 가진 묶음 클래스. `ArchRules.in()`이 집을 수 있도록 노출한다.

```java
public final class HexagonalArchRulesFrozen {
    @ArchTest static final ArchRule layers      = FreezingArchRule.freeze(HexagonalArchRules.HEXAGONAL_LAYERS);
    @ArchTest static final ArchRule domainPure  = FreezingArchRule.freeze(HexagonalArchRules.DOMAIN_FRAMEWORK_FREE);
    @ArchTest static final ArchRule appIsolated = FreezingArchRule.freeze(HexagonalArchRules.APPLICATION_NO_WEB_OR_PERSISTENCE);
}
```

### 5.2 strict 묶음 표면 정리 — `HexagonalArchRules`

현재 `HexagonalArchRules`는 `public static final ArchRule` 상수만 있고 `@ArchTest`가 없어
`ArchRules.in()`이 못 집는다. 해결:

- raw 상수 3종은 **직접 참조용으로 그대로 유지**(하위호환).
- strict도 한 줄 Apply가 되도록 `@ArchTest` 필드를 갖춘 묶음 표면을 제공한다.
  (raw 상수에 `@ArchTest`를 직접 부여하거나, strict 묶음 클래스를 별도로 두는 것 중 구현 시 택1 —
  하위호환을 깨지 않는 쪽으로. 결정은 구현 단계 ADR/PR에서 못박는다.)

### 5.3 소비측 표면 (한 줄 Apply)

```java
// 브라운필드 (레거시 있는 기존 서버) — 동결 입양
@AnalyzeClasses(packages = "com.ryuqq.marketplace")
class HexagonalArchitectureTest {
    @ArchTest static final ArchRules platform = ArchRules.in(HexagonalArchRulesFrozen.class);
}

// 그린필드 (레거시 없음) — strict, 처음부터 0 위반
@AnalyzeClasses(packages = "com.ryuqq.newservice")
class HexagonalArchitectureTest {
    @ArchTest static final ArchRules platform = ArchRules.in(HexagonalArchRules.class); // 또는 strict 묶음
}
```

### 5.4 소비측 보일러플레이트 — `archunit.properties` (README 템플릿)

소비 레포 `src/test/resources/archunit.properties`:

```properties
freeze.store.default.allowStoreCreation=true   # 로컬 최초 1회 baseline 생성용
freeze.refreeze=false                            # 신규 위반을 store에 자동 흡수 금지(=실패시킴)
```

- 최초 1회 로컬 실행으로 baseline 생성 → git 커밋.
- CI에서 우발적 store 생성을 막으려면 CI 환경에서 `allowStoreCreation=false`로 두는 운용을 README에
  안내(자동화는 비목표).

## 6. self-test (회귀 방어)

기존 self-test(규칙이 실제로 잡는지)는 유지하고, **freezing 동작** 검증을 추가한다. store는 ArchUnit
`ViolationStore` API로 **temp-dir 주입**해 소비측 파일을 건드리지 않는다.

- 위반 픽스처를 freeze → baseline 기록 → 같은 코드 재평가 시 **통과**(동결됨) 확인.
- baseline 기록 후 **새 위반 추가** → 신규 위반은 **실패**하는지 확인(ratchet positive control).
- 위반 제거 → store 자동 prune → **통과** 확인.

## 7. 영향 범위

- **신규**: `HexagonalArchRulesFrozen`(src/main), freezing self-test(src/test), README 갱신
  (frozen/strict 표면 + `archunit.properties` 템플릿 + ratchet 설명).
- **변경**: `HexagonalArchRules` 표면 정리(5.2 — 하위호환 유지).
- **무변경**: 기존 3종 규칙의 매처 로직(정밀화는 Spec 2), `architecture-tests` 모듈.
- **의존성**: `archunit-junit5`에 `FreezingArchRule`·`ArchRules` 포함 — 신규 의존 없음.

## 8. 열린 질문 (구현 단계에서 확정)

1. 5.2 strict 한 줄 표면을 raw 상수 `@ArchTest` 부여로 할지 별도 묶음 클래스로 할지(하위호환 기준).
2. `archunit.properties`의 CI/로컬 분기 안내를 README 텍스트로만 둘지, 샘플 파일까지 제공할지.

## 9. 다음 (Spec 2 예고)

- 기존 3종 매처 정밀화(정확도/표현력).
- 카탈로그 확장: ADR-0003 드리프트 표준 강제 규칙(Instant·success없음·deletedAt만) + 네이밍·순환의존·
  포트/어댑터 규칙. **Spec 1의 freezing 토대 위에서 신규 규칙을 frozen으로 안전 도입.**
