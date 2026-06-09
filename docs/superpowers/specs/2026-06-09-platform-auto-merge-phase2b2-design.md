# 자율 수정 fleet — Phase 2b-2 설계 (자동머지, 보수 게이트)

- 일자: 2026-06-09
- 상위: 자가 감사·자가 개선 플랫폼 fleet ([[platform-fleet-autopilot]])
- 의존: Phase 2b-1(게이트키퍼+병렬 fan-out, main 머지됨). 게이트키퍼 design-fork 포착 검증 완료(10 finding, 오분류 0).
- 범위: **2b-2 = 게이트 통과 시 자동머지.** 첫 가동은 **테스트·문서만** 자동머지(보수).

## 1. 목적

2b-1의 단위 파이프라인 끝(PR 오픈) 뒤에 **자동머지 게이트**를 둔다. 게이트를 전부 통과한 PR은 사람 없이 main 머지, 하나라도 실패하면 PR을 열어두고 사람에게 보고(escalate). "PR단에서 중요한 것만 사람에게"라는 게이트 정책의 최종 구현.

## 2. 자동머지 게이트 (전부 AND)

```
auto-merge IF:
  ① gatekeeper.class = mechanical AND confidence = high AND apiImpact = none
  ② re-audit.closed = pass
  ③ scope = 테스트·문서·README 만 (보수 — src/main 변경 0)
  ④ merge-gate 리뷰어 = approve         (새 안전겹)
  ⑤ CI green (archrules·build·test)     (GitHub가 강제)
ELSE → escalate: PR 열어둠 + 사람 보고
```

- **③ 보수 범위(확정 결정):** 첫 자동머지는 `src/test`·`docs`·`*.md`·`README` 변경만. `src/main`(프로덕션 코드) 변경은 게이트 통과해도 **사람 머지**. merge-gate 리뷰어 실전 정확도를 main 리스크 없이 축적 후 단계적 확대(중도=apiImpact none 내부구현 → 공격).
- **④ 왜 필요한가:** green+closure만으론 부족(봇리뷰 #1 교훈 — jpa mock EMF는 "통과하지만 회귀"). 독립 리뷰어가 디프를 적대적으로 검토해야 자동머지.
- **⑤:** 외부 봇(gemini/coderabbit)은 rate-limit·비동기라 게이트 부적합. CI(GitHub Actions)만 머지 차단 조건으로. `gh pr merge --auto` 로 GitHub가 CI green 후 머지하게 위임.

## 3. 새 자산: `merge-gate` 리뷰어 에이전트 (프로젝트 로컬, 읽기전용)

입력: PR diff(또는 브랜치)와 finding 컨텍스트.
적대적 검토 축:
- **가짜green**: 테스트가 실제 시나리오를 검증하나, 빈 단언·trivially-true 아닌가(뮤테이션 관점).
- **범위 이탈**: finding 범위 밖 변경이 섞였나(scope creep).
- **회귀/정확성**: 변경이 기존 동작을 깨거나, finding을 잘못 해석했나.
- **scope 확인**: 실제 변경 파일이 ③ 보수 범위(test/docs)인가.

출력 JSON: `{verdict:"approve|block", reasons:[...], scopeOk:bool, fakeGreenRisk:bool, scopeCreep:bool}`.
경계: 읽기전용. 머지·수정 안 함 — 게이트 판정만. block이면 사람에게 넘김.

## 4. 오케스트레이션 (`platform-fix-fleet.js` 확장, arg-gated)

- 기존 fleet에 `args.autoMerge`(기본 false = 2b-1 동작 보존) + `args.autoMergeScope`(기본 `"tests-docs"`) 추가.
- 각 item이 PR(escalate=false)에 도달하면 **auto-merge 단계**:
  1. 결정론적 scope 체크: `git diff --name-only main..<branch>` 가 전부 `src/test/`·`docs/`·`*.md`·`README`인지. 아니면 escalate(보수).
  2. ① gatekeeper 필드(이미 보유)·② re-audit(이미 보유) 확인.
  3. `merge-gate` 리뷰어 호출 → approve/block.
  4. 전부 통과면 `gh pr merge <pr> --auto --squash --delete-branch`(GitHub가 CI green 후 머지). block/scope-fail이면 escalate.
- 집계에 `merged`(자동머지됨)·`escalated`(사람 대기) 분리 보고.

## 5. 안전장치
- **dry-run 모드**: `args.dryRun=true` 면 머지 명령 대신 "머지했을 것" 로깅만(merge-gate 정확도 관찰용 첫 운영).
- **per-run 캡**: 한 런 자동머지 최대 N건(기본 3) — 폭주 방지.
- **audit trail**: 자동머지된 PR·게이트 판정을 `docs/superpowers/audits/<date>-auto-merge-log.md`에 기록.
- **kill switch**: `args.autoMerge=false`(기본)면 즉시 2b-1 동작(사람 머지)으로 회귀.
- **escalate 우선**: 게이트의 어느 단계든 불확실/실패면 머지 안 하고 사람. "의심되면 멈춤".

## 6. 비목표 (2b-2)
- `src/main`(프로덕션 코드) 자동머지 — 단계적 확대 대상(중도/공격), 첫 가동 제외.
- design-fork 자동 처리(영구 사람). 외부 봇 리뷰를 게이트로 사용.
- 자동 롤백·핫픽스(머지 후 회귀 발견 시) — 별도.

## 7. 검증
- **dry-run 먼저**: README ×11 배치를 dryRun=true로 돌려 merge-gate 판정·scope 체크가 옳은지 사람이 확인(머지 0).
- **merge-gate 정확도**: 일부러 가짜green/scope-creep 디프를 줘서 block 내는지.
- 실가동: dry-run 정확도 확인 후 README 배치를 실제 자동머지 → main 결과 점검.
- 첫 실가동 N=2~3으로 좁힘.

## 8. 설계 근거
- 보수 범위(test/docs) = merge-gate 신뢰를 main 리스크 없이 축적. 단계적 신뢰 확대.
- merge-gate 리뷰어 = green+closure의 사각(통과하지만 틀림)을 메우는 적대적 겹. 봇리뷰 #1 교훈의 제도화.
- CI를 GitHub `--auto`에 위임 = 워크플로우가 CI-wait를 떠안지 않음(단순·견고).
- dry-run·캡·audit·kill switch = 자율 머지의 되돌리기 비용(main 오염)에 대한 다층 방어.
