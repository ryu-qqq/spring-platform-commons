# 자율 수정 fleet — Phase 2b-1 설계 (병렬 fan-out + 게이트키퍼, 사람 머지)

- 일자: 2026-06-09
- 상위 프로젝트: 자가 감사·자가 개선 플랫폼 fleet ([[platform-fleet-autopilot]])
- 의존: Phase 1 감사 fleet, Phase 2a 단위 파이프라인(`platform-fix-item`) — 모두 main 머지됨(`fe7266e`)
- 범위: **2b-1 = 병렬 fan-out + 게이트키퍼 분류, 머지는 사람.** 자동머지는 2b-2.

## 1. 목적

Phase 2a에서 검증된 단위 파이프라인(`platform-fix-item`)을 스코어카드의 여러 finding에 **병렬로 fan-out**한다. 입구에 **게이트키퍼 분류기**를 두어 mechanical만 자동 처리하고 design-fork는 사람에게 위임한다. 머지는 여전히 사람(2b-1). 자동머지는 게이트키퍼 분류 정확도가 known-good으로 검증된 뒤 2b-2.

## 2. 아키텍처

```
스코어카드 findings (docs/superpowers/audits/<최신>.md)
  ↓
[gatekeeper] finding마다 분류 → mechanical | design-fork | skip
  ↓ mechanical 만
[fan-out 병렬] item마다:
    git worktree add <wt> main   (main에서 분기 — 작은 독립 PR)
    implement(TDD) → re-audit closure → PR    (3 에이전트 모두 <wt>에서)
    git worktree remove <wt>
  ↓
[집계] {PR목록, closure, escalation} 요약
  → 사람이 검토·머지 (2b-1)
  → design-fork·escalation 은 사람에게 별도 보고 (자동처리 X)
```

## 3. 새 자산: `fix-gatekeeper` 에이전트 (프로젝트 로컬, 읽기전용)

입력: finding 1건 `{module, check, severity, evidence, direction}`.
분류:
- **mechanical** — `direction`이 구체적·기계적(테스트 추가·문서·리터럴 통일 등), public API 불변, 유효 접근이 단일. → 파이프라인 처리.
- **design-fork** — 새 공개 추상화(port/SPI/DTO)·public API 변경·유효 접근 다수·도메인 정책 결정. → 사람 brainstorming 위임.
- **skip** — 이미 pass/na, 또는 ROI 낮음(판단 근거 명시).
- **편향: 애매하면 design-fork**(escalate-when-uncertain). known-good 검증 전까지 보수.

출력 JSON:
```json
{"module":"...","check":"...","class":"mechanical|design-fork|skip","confidence":"high|medium|low","reason":"<왜 이 분류인가>","apiImpact":"none|internal|public"}
```

경계: 읽기전용(Read/Glob/Grep), 코드 수정 X, 분류만. 자가 분류로 머지 결정 X.

## 4. 병렬 fan-out 오케스트레이션 (`platform-fix-fleet.js`)

- 입력: 최신 audit 리포트 경로(또는 finding 배열을 args로).
- **gatekeeper 단계**: 각 finding을 `fix-gatekeeper`로 분류(병렬).
- **fan-out 단계**: `class==mechanical` 인 item을 병렬 처리. 각 item:
  - **worktree 명시 관리(핵심 결정)**: `git worktree add <repo>/.worktrees/<branch> main` 으로 main에서 분기한 격리 트리 생성. (Workflow `isolation:'worktree'` 미사용 — 그건 에이전트마다 다른 트리를 줘 stage 간 파일 가시성·re-audit 독립성을 동시에 못 살림.)
  - 그 worktree 경로를 implement·re-audit·PR 3 에이전트에 주입 → 모두 그 경로에서 작업.
    - implement(TDD)·re-audit(독립 에이전트, 같은 worktree에서 구현 결과 객체 검증)·PR(push+gh).
  - 완료 후 `git worktree remove`.
  - 동시성 = Workflow 기본 캡(min(16, cores-2)). 항목 간 worktree 격리로 충돌 없음.
- **집계 단계**: 결과 수집 → `{mechanical: [{module,check,closed,prUrl,escalate}], designForks: [...], skipped: [...]}`.

> Phase 2a의 단위 파이프라인 로직(implement→re-audit→PR)을 재사용하되, 단일·메인트리 가정 대신 **item별 명시 worktree(main 분기)** 로 감싼다. 이것이 2a 학습(작은 독립 PR)의 구현.

## 5. 산출·보고
- 각 mechanical item = **독립 PR**(main 분기, 작은 디프) — 사람이 개별/일괄 머지.
- 집계 요약 = in-repo `docs/superpowers/audits/<date>-fix-fleet-run.md` + (중요분) Notion 승격.
- design-fork·escalation(BLOCKED/closure 실패/리뷰어 major) = 사람에게 명시 보고, 자동 처리 안 함.

## 6. 비목표 (2b-1)
- 자동머지 (2b-2) — 머지는 전부 사람.
- design-fork 자동 처리 (영구히 사람).
- 항목 자동 재감사 트리거·스케줄(별도).
- 게이트키퍼 학습/튜닝 루프(2b-2에서 정확도 측정 후).

## 7. 파일럿 배치 (현재 스코어카드 기준)
- **scheduler** `context-runner-test` (major) → mechanical 예상.
- **jpa** `context-runner-test` (minor warn) → mechanical 예상(positive만 → backs-off/FilteredClassLoader 보강).
- **README ×11** → 게이트키퍼 판단. 문서라 mechanical이나 내용 생성 부담 → 별도 배치 권고 또는 skip(2b-1 첫 런은 테스트 2건으로 좁힘 권장).

## 8. 검증
- **게이트키퍼 정확도(핵심)**: 알려진 케이스로 분류 진위 확인 — scheduler/jpa 테스트=mechanical, IdempotencyKeyValue류=design-fork, 이미 Gold인 것=skip. 오분류(특히 design-fork를 mechanical로) = 위험 신호.
- **병렬 격리**: 2 item 동시 실행이 worktree 충돌·교차오염 없는지.
- **closure 진위**: 각 PR 디프를 사람이 가짜green 점검(2a와 동일).
- 첫 런은 **2 item(scheduler·jpa)** 으로 좁혀 fan-out·게이트키퍼·worktree 관리를 검증한 뒤 확장.

## 9. 설계 근거
- 게이트키퍼 = "사람 게이트 치환"의 첫 구현이나, **분류만** 하고 머지·설계는 안 함 → 위험 최소. 정확도를 사람 머지 게이트 뒤에서 측정.
- 명시 worktree(main 분기) = 독립성(re-audit 별도 에이전트)+일관성(stage 공유 트리)+작은 PR(2a 학습) 동시 충족.
- 자동머지 분리(2b-2) = 게이트키퍼 오분류 위험이 main에 직접 들어가지 않게.
- 첫 런 2 item = build-out 규율(좁게 검증 후 확장).
