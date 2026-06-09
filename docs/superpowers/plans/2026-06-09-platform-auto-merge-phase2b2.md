# 자율 수정 fleet Phase 2b-2 Implementation Plan

> **For agentic workers:** Task 1·2(에이전트·워크플로우 저작)=서브에이전트, Task 3(dry-run→실가동 검증)=컨트롤러 인라인. Steps use checkbox syntax.

**Goal:** `merge-gate` 적대적 리뷰어 + `platform-fix-fleet.js`의 자동머지 단계(보수 게이트)를 만들고, README 배치를 dry-run→실가동으로 검증한다.

**Architecture:** fleet의 Fan-out(PR 산출) 뒤에 Auto-merge 단계 추가. 각 PR에 게이트(① gatekeeper mechanical+high+apiImpact none ② re-audit closed ③ scope=test/docs ④ merge-gate approve)를 적용, 전부 통과+비-dryRun이면 `gh pr merge --auto`(CI green 후 GitHub 머지), 아니면 escalate. arg `autoMerge`(기본 false)로 2b-1 동작 보존.

**Tech Stack:** Workflow(JS), 서브에이전트, gh CLI.

**Spec:** `docs/superpowers/specs/2026-06-09-platform-auto-merge-phase2b2-design.md`

---

### Task 1: merge-gate 리뷰어 에이전트

**Files:** Create `.claude/agents/merge-gate.md`

- [ ] **Step 1: 파일 작성**

```markdown
---
name: merge-gate
description: 자율 수정 PR이 자동머지 가능한지 디프를 적대적으로 검토한다 — 가짜green·범위이탈(scope creep)·회귀·보수 scope(test/docs만) 위반을 잡아 approve/block 판정. green+closure만으로 놓치는 "통과하지만 틀린" 변경을 막는 안전겹. 읽기전용, 머지·수정 안 함. platform-fix-fleet 자동머지 단계가 호출.
tools:
  - Read
  - Glob
  - Grep
  - Bash
---

# Merge Gate Reviewer

## 역할
자동머지 직전, PR 디프가 정말 안전한지 적대적으로 검토한다. CI green·re-audit closed를 통과해도 "통과하지만 틀린" 변경(예: 빈 단언으로 green, finding 오해, 범위 이탈)이 빠져나갈 수 있다 — 그 사각을 막는다. **읽기전용**: `git diff`·`git diff --name-only`·코드 Read 만. 머지·수정·답글 안 함.

## 입력
PR 브랜치명(base main)과 finding 컨텍스트. `git diff --name-only main..<branch>` 와 `git diff main..<branch>` 를 읽어 판정.

## 적대적 검토 축
- **scope(보수)**: 변경 파일이 전부 `src/test/`·`docs/`·`*.md`·`README` 인가. `src/main` 등 프로덕션 코드 변경이 있으면 `scopeOk=false`.
- **가짜green**: 추가/수정된 테스트가 실제 시나리오를 검증하나 — 빈 단언·trivially-true·assert 없는 테스트는 `fakeGreenRisk=true`.
- **범위 이탈**: finding 범위 밖 변경이 섞였나(`scopeCreep=true`).
- **회귀/정확성**: 변경이 기존 동작을 깨거나 finding을 잘못 해석했나.

## 판정 규율
- 하나라도 의심되면 `block`(자동머지 막고 사람으로). "의심되면 멈춤".
- 근거 없는 approve 금지 — 디프를 실제로 읽고 판정.

## 출력 (JSON)
\```json
{"verdict":"approve|block","scopeOk":true,"fakeGreenRisk":false,"scopeCreep":false,"reasons":["..."]}
\```

## 경계
- 읽기전용(git diff/read 만). 머지·push·수정 안 함 — 게이트 판정 전담.
- block이면 사람에게 넘긴다. 자가 판정으로 직접 머지하지 않는다.
```

- [ ] **Step 2: 커밋**(opspilot 훅 시 amend)
```bash
git add .claude/agents/merge-gate.md
git commit -m "feat(auto-merge): merge-gate 적대적 리뷰어 에이전트 (Phase 2b-2)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: platform-fix-fleet 자동머지 단계 추가

**Files:** Modify `.claude/workflows/platform-fix-fleet.js`

- [ ] **Step 1: 상수 추가 (PR_SCHEMA 아래에 삽입)**

```javascript
const MERGE_GATE_CRITERIA = `너는 merge-gate 적대적 리뷰어다. 읽기전용(git diff/read만). ` +
  `보수 scope = src/test/·docs/·*.md·README 만 허용(src/main 변경 있으면 scopeOk=false). ` +
  `검토: 가짜green(빈/trivially-true 단언)·범위이탈(finding 밖 변경)·회귀. 하나라도 의심되면 verdict=block. 디프를 실제로 읽어라.`

const MERGE_GATE_SCHEMA = { type:'object', required:['verdict','scopeOk'], properties:{
  verdict:{enum:['approve','block']}, scopeOk:{type:'boolean'},
  fakeGreenRisk:{type:'boolean'}, scopeCreep:{type:'boolean'}, reasons:{type:'array',items:{type:'string'}} } }
```

- [ ] **Step 2: args 가드·주석에 autoMerge 옵션 반영**

`// args = {...}` 주석 줄을 다음으로 교체:
```javascript
// args = {findings:[...], gatekeeperCriteria, auditorCriteria, repo?,
//         autoMerge?(기본 false), dryRun?(기본 false), mergeCapN?(기본 3)}
```

- [ ] **Step 3: 최종 return 직전에 Auto-merge 단계 삽입**

`phase('Fan-out')` 의 `fixed` 계산 이후, `return {...}` **직전**에 삽입:

```javascript
phase('Auto-merge')
const prRef = r => ({ module:r.module, check:r.check, prUrl:r.pr && r.pr.prUrl })
const autoMerge = []
if (a.autoMerge) {
  const CAP = a.mergeCapN || 3
  let merged = 0
  for (const r of fixed.filter(x => !x.escalate && x.pr && x.pr.prUrl)) {
    // 게이트 ①②: gatekeeper(mechanical+high+apiImpact none) + re-audit closed
    if (!(r.class === 'mechanical' && r.confidence === 'high' && r.apiImpact === 'none' && r.reaudit && r.reaudit.closed)) {
      autoMerge.push({ ...prRef(r), action:'escalate', reason:'게이트①② 미충족(mechanical/high/apiImpact-none/closed)' }); continue
    }
    if (merged >= CAP) { autoMerge.push({ ...prRef(r), action:'escalate', reason:`per-run 캡(${CAP}) 도달` }); continue }
    // 게이트 ③④: merge-gate 리뷰어(scope+적대 검토)
    const review = await agent(
      `${MERGE_GATE_CRITERIA}\n\n작업 디렉토리 ${REPO}. PR 브랜치 ${r.branch}(base main).\n` +
      `\`git diff --name-only main..${r.branch}\` 와 \`git diff main..${r.branch}\` 를 읽어 판정.\n` +
      `finding: ${JSON.stringify({ module:r.module, check:r.check, direction:r.direction })}\n` +
      `JSON: {"verdict":"approve|block","scopeOk":bool,"fakeGreenRisk":bool,"scopeCreep":bool,"reasons":[]}`,
      { label:`merge-gate:${r.module}`, phase:'Auto-merge', schema:MERGE_GATE_SCHEMA })
    if (review.verdict !== 'approve' || !review.scopeOk) {
      autoMerge.push({ ...prRef(r), action:'escalate', reason:`merge-gate=${review.verdict} scopeOk=${review.scopeOk}: ${(review.reasons || []).join('; ')}` }); continue
    }
    // 게이트 ⑤: CI는 gh --auto 위임. dry-run이면 머지 안 함.
    if (a.dryRun) { autoMerge.push({ ...prRef(r), action:'would-merge', reason:'dry-run(머지 안 함)' }); merged++; continue }
    const m = await agent(
      `작업 디렉토리 ${REPO}. \`gh pr merge ${r.pr.prUrl} --auto --squash --delete-branch\` 실행(CI green 후 GitHub가 머지). JSON: {"enabled":bool,"detail":""}`,
      { label:`merge:${r.module}`, phase:'Auto-merge', schema:{ type:'object', required:['enabled'], properties:{ enabled:{type:'boolean'}, detail:{type:'string'} } } }).catch(() => ({ enabled:false, detail:'agent error' }))
    autoMerge.push({ ...prRef(r), action: m.enabled ? 'auto-merge-enabled' : 'escalate', reason: m.enabled ? 'gh --auto 설정' : `머지 실패: ${m.detail}` })
    if (m.enabled) merged++
  }
}
```

- [ ] **Step 4: return 객체에 autoMerge 추가**

`return { gatekeeper:..., results:..., prs:..., escalations:... }` 에 `autoMerge,` 필드 추가.

- [ ] **Step 5: node --check 후 커밋**
```bash
node --check .claude/workflows/platform-fix-fleet.js
git add .claude/workflows/platform-fix-fleet.js
git commit -m "feat(auto-merge): platform-fix-fleet 자동머지 단계 (Phase 2b-2, 보수 게이트)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```
(opspilot 훅 시 amend)

---

### Task 3: README 배치 dry-run → 실가동 (컨트롤러 인라인)

- [ ] **Step 1: README findings 구성 + dry-run 실행**

컨트롤러가 README 없는 모듈 2건으로 finding을 만들고 `autoMerge:true, dryRun:true` 로 fleet 실행:
```
Workflow({scriptPath:".../platform-fix-fleet.js", args:{
  findings:[
    {module:"platform-redis", check:"readme-missing", severity:"minor",
     evidence:"platform-redis에 README.md 없음", direction:"모듈 역할·확장점(CachePort/DistributedLockPort 어댑터·자동설정)을 문서화한 README.md 추가"},
    {module:"platform-outbox", check:"readme-missing", severity:"minor",
     evidence:"platform-outbox에 README.md 없음", direction:"모듈 역할·확장점(BatchOutboxRelayTemplate·OutboxStore SPI)을 문서화한 README.md 추가"}
  ],
  gatekeeperCriteria:"<fix-gatekeeper 기준 인라인>",
  auditorCriteria:"<README 존재를 점검하는 간이 기준: README.md 있으면 pass, 없으면 fail>",
  autoMerge:true, dryRun:true, mergeCapN:3
}})
```
주의: README는 autoconfig-auditor의 정식 check가 아니므로 auditorCriteria는 "README.md 존재 시 pass" 간이 기준을 인라인. (또는 re-audit를 README 존재 확인으로 한정.)
Expected: `autoMerge` 배열에 각 PR `action:"would-merge"`(게이트 통과 시) 또는 `escalate`(사유). 실제 머지 0.

- [ ] **Step 2: dry-run 판정 검증 (사람 게이트)**

`autoMerge` 결과·merge-gate 판정을 컨트롤러가 확인:
- scope가 README/docs로만 잡혀 scopeOk=true 인가.
- merge-gate가 가짜green/scope-creep 없다고 approve 했는가(생성된 README가 실제 내용인가 디프 확인).
- 게이트①②(mechanical/high/apiImpact none/closed) 충족했는가.
- **일부러 깨보기**: 한 finding에 src/main 변경을 유도하는 direction을 주거나, 빈 README를 만들게 해서 merge-gate가 block/scopeOk=false 내는지(선택, merge-gate 민감도 확인).

- [ ] **Step 3: 실가동 (dryRun=false)**

dry-run 판정이 옳으면 동일 args에 `dryRun:false` 로 재실행 → 게이트 통과 PR이 `gh pr merge --auto` 설정됨. CI green 후 GitHub 자동머지.
- 결과 `autoMerge[].action="auto-merge-enabled"` 확인.
- `gh pr list`·`gh run list`로 머지·CI 상태 추적.

- [ ] **Step 4: audit log 기록 + 회고**

자동머지된 PR·게이트 판정을 `docs/superpowers/audits/2026-06-09-auto-merge-log.md`에 기록. merge-gate 실전 정확도·escalate 사유를 메모리에 남겨 단계적 확대(중도=src/main apiImpact none) 판단 입력으로.

---

## Self-Review

**Spec coverage:**
- §3 merge-gate 리뷰어 → Task 1 ✅ / §2 게이트 ①~⑤ → Task 2 Step3(①② 결정론, ③④ 리뷰어, ⑤ gh --auto) ✅
- §2 ③ 보수 scope(test/docs) → merge-gate scopeOk + Task 2 ✅
- §4 arg-gated(autoMerge 기본 false=2b-1 보존) → Task 2 Step2·3 ✅
- §5 안전장치(dry-run·캡·escalate 우선·kill switch) → Task 2(dryRun/CAP/escalate), autoMerge=false 기본 ✅. audit log → Task 3 Step4 ✅
- §7 검증(dry-run 먼저·merge-gate 정확도·실가동) → Task 3 ✅
- §6 비목표(src/main 자동머지) → merge-gate scopeOk=false로 차단 ✅

**Placeholder scan:** 에이전트 본문·워크플로우 코드·실행 args 구체. `<...인라인>`은 Task 3 Step1에서 주입 명시.

**Type consistency:** autoMerge 옵션·MERGE_GATE_SCHEMA(verdict/scopeOk)·게이트 필드(class/confidence/apiImpact/reaudit.closed)·action enum(would-merge/auto-merge-enabled/escalate)이 spec·에이전트·워크플로우 일치.

**주의(실행자):** README는 정식 auditor check가 아니라 re-audit를 "README.md 존재" 간이 기준으로 인라인. `gh pr merge --auto`는 레포에 auto-merge 활성화 필요 — 미지원이면 escalate로 떨어지거나 `--auto` 제거하고 CI 수동 확인 후 머지로 폴백(Task 3 Step3에서 확인).
