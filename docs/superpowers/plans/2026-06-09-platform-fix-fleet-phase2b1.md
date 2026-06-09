# 자율 수정 fleet Phase 2b-1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development 또는 인라인. Task 1(에이전트 저작)=서브에이전트, Task 2(워크플로우 저작)=서브에이전트, Task 3(파일럿 실행·검증)=컨트롤러 인라인. Steps use checkbox syntax.

**Goal:** finding을 mechanical/design-fork/skip 분류하는 `fix-gatekeeper` 에이전트 + 분류된 mechanical을 item별 명시 worktree(main 분기)로 병렬 수정하는 `platform-fix-fleet.js`를 만들고, scheduler·jpa 2건으로 검증한다.

**Architecture:** 컨트롤러가 스코어카드에서 findings 추출 → fleet 워크플로우에 args로 주입 → gatekeeper 병렬 분류 → mechanical item별로 worktree 생성 후 implement(TDD)→독립 re-audit→PR→worktree 정리 → 집계. 머지는 사람.

**Tech Stack:** Workflow(JS), 서브에이전트, git worktree, gh CLI, Java/Spring.

**Spec:** `docs/superpowers/specs/2026-06-09-platform-fix-fleet-phase2b1-design.md`

---

### Task 1: fix-gatekeeper 에이전트

**Files:** Create `.claude/agents/fix-gatekeeper.md`

- [ ] **Step 1: 파일 작성**

```markdown
---
name: fix-gatekeeper
description: 감사 finding 1건을 받아 자율 수정 파이프라인에 태울지 분류한다 — mechanical(자동 수정 가능)·design-fork(사람 brainstorming 필요)·skip(불요). 애매하면 design-fork로 편향(보수). 읽기전용 분류 전담, 코드·머지 결정 안 함. platform-fix-fleet 워크플로우가 호출.
tools:
  - Read
  - Glob
  - Grep
---

# Fix Gatekeeper

## 역할
감사 finding이 "사람 없이 자동 수정해도 되는가"를 분류한다. 이 판정이 자율성과 안전의 분리선이다 — 설계 결정을 자동화로 태우지 않는 것이 핵심.

## 입력
finding 1건 `{module, check, severity, evidence, direction}`. 필요 시 해당 모듈 코드를 Read/Grep으로 확인.

## 분류
- **mechanical**: `direction`이 구체적·기계적이고 public API 불변, 유효 접근이 단일. 예: 테스트 추가(ApplicationContextRunner), README 작성, MDC 리터럴→상수 통일, 메트릭명 정리.
- **design-fork**: 새 공개 추상화(port/SPI/DTO)·public API 변경·유효 접근 다수·도메인 정책 결정 필요. 예: 새 VO 신설, 키 조립 규칙 설계.
- **skip**: 이미 pass/na 거나 ROI 낮음(근거 명시).

## 판정 규율
- **애매하면 design-fork**(escalate-when-uncertain). 자동으로 틀린 설계를 내는 것보다 사람을 부르는 비용이 싸다.
- public API에 닿으면 mechanical 금지(최소 design-fork).
- 근거 없는 분류 금지 — direction·evidence·코드 신호로 판정.

## 출력 (JSON)
\```json
{"module":"...","check":"...","class":"mechanical|design-fork|skip","confidence":"high|medium|low","reason":"<근거>","apiImpact":"none|internal|public"}
\```

## 경계
- 읽기전용. 코드 수정·머지·설계 안 함 — 분류 전담.
- 자가 분류로 머지 결정 X — 사람 게이트와 결합.
```

- [ ] **Step 2: 커밋** (opspilot 훅 자동커밋 시 메시지 amend)

```bash
git add .claude/agents/fix-gatekeeper.md
git commit -m "feat(fix-fleet): fix-gatekeeper 분류 에이전트 (Phase 2b-1)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

- [ ] **Step 3: 컨트롤러 검증 (Task 3에서)** — known 케이스 분류 진위는 Task 3 Step 1에서 일괄 확인.

---

### Task 2: platform-fix-fleet 워크플로우

**Files:** Create `.claude/workflows/platform-fix-fleet.js`

- [ ] **Step 1: 스크립트 작성**

```javascript
export const meta = {
  name: 'platform-fix-fleet',
  description: '스코어카드 findings를 게이트키퍼로 분류하고 mechanical을 item별 worktree(main 분기)에서 병렬 자율 수정해 PR을 연다 (머지는 사람)',
  phases: [
    { title: 'Gatekeeper', detail: 'finding 분류 (mechanical/design-fork/skip)' },
    { title: 'Fan-out', detail: 'mechanical item 병렬 수정→re-audit→PR' },
  ],
}

// args = {findings:[{module,check,severity,evidence,direction}...], gatekeeperCriteria, auditorCriteria}
const a = typeof args === 'string' ? JSON.parse(args) : args
if (!a || !Array.isArray(a.findings) || !a.gatekeeperCriteria || !a.auditorCriteria) {
  throw new Error('args에 {findings:[], gatekeeperCriteria, auditorCriteria} 필요')
}
const REPO = '/Users/ryu-qqq/Documents/ryu-qqq/spring-platform-commons'

const GATE_SCHEMA = { type:'object', required:['class'], properties:{
  module:{type:'string'}, check:{type:'string'},
  class:{enum:['mechanical','design-fork','skip']}, confidence:{enum:['high','medium','low']},
  reason:{type:'string'}, apiImpact:{enum:['none','internal','public']} } }

const IMPL_SCHEMA = { type:'object', required:['status','buildGreen'], properties:{
  status:{enum:['DONE','BLOCKED','DESIGN_FORK']}, branch:{type:'string'},
  filesChanged:{type:'array',items:{type:'string'}}, commit:{type:'string'},
  testSummary:{type:'string'}, buildGreen:{type:'boolean'}, notes:{type:'string'} } }

const REAUDIT_SCHEMA = { type:'object', required:['check','status','closed'], properties:{
  module:{type:'string'}, check:{type:'string'}, status:{enum:['pass','warn','fail','na']},
  evidence:{type:'string'}, closed:{type:'boolean'} } }

const PR_SCHEMA = { type:'object', required:['pushed'], properties:{ prUrl:{type:'string'}, pushed:{type:'boolean'}, worktreeRemoved:{type:'boolean'} } }

// item 하나를 명시 worktree(main 분기)에서 수정→re-audit→PR
async function fixItem(fd) {
  const slug = `${fd.module}-${fd.check}`.replace(/[^a-zA-Z0-9-]/g, '-')
  const branch = `fix/${slug}`
  const wt = `${REPO}/.worktrees/${slug}`

  const impl = await agent(
    `너는 자율 수정 구현자다. 격리 worktree에서 TDD로 감사 finding을 수정하라.\n` +
    `## 셋업\n1. 메인 레포 ${REPO} 에서: \`git worktree add ${wt} -b ${branch} main\` (이미 있으면 제거 후 재생성).\n2. 이후 모든 작업은 ${wt} 에서 수행(cd ${wt}).\n` +
    `## finding\nmodule=${fd.module}\ncheck=${fd.check}\nevidence=${fd.evidence}\ndirection=${fd.direction}\n` +
    `## TDD\n3. 선례 참고(platform-security/outbox 의 ApplicationContextRunner 테스트 스타일) → 실패 테스트 → \`./gradlew :${fd.module}:test\` red → green → \`./gradlew :${fd.module}:build\` 그린.\n4. 커밋(한국어, Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>).\n` +
    `## 경계\n- 이 finding 범위만. 가짜green 금지(실제 시나리오 검증). 설계 갈림 드러나면 status=DESIGN_FORK.\n` +
    `JSON: {"status","branch":"${branch}","filesChanged":[],"commit","testSummary","buildGreen","notes"}`,
    { label:`impl:${fd.module}`, phase:'Fan-out', schema:IMPL_SCHEMA })
  if (impl.status !== 'DONE' || !impl.buildGreen) return { ...fd, stage:'implement', escalate:true, reason:`구현 미완 status=${impl.status} build=${impl.buildGreen}`, impl, branch, wt }

  const reaudit = await agent(
    `${a.auditorCriteria}\n\n작업 트리: ${wt} (cd 해서 그 상태를 감사). 대상 모듈 ${fd.module}. check id="${fd.check}" 의 현재 status를 판정하라. 읽기전용 — 구현자 주장 불신, 코드 직접 확인.\n` +
    `JSON: {"module":"${fd.module}","check":"${fd.check}","status":"pass|warn|fail|na","evidence","closed"}`,
    { label:`reaudit:${fd.module}`, phase:'Fan-out', schema:REAUDIT_SCHEMA })
  if (!reaudit.closed || reaudit.status !== 'pass') return { ...fd, stage:'re-audit', escalate:true, reason:`closure 실패 ${fd.check}=${reaudit.status}`, impl, reaudit, branch, wt }

  const pr = await agent(
    `작업 트리 ${wt}, 브랜치 ${branch}. PR을 열어라(머지 금지):\n` +
    `1. cd ${wt}; \`git push -u origin ${branch}\`.\n2. \`gh pr create\` 제목 "fix(${fd.module}): ${fd.check} 자율 수정 (Phase 2b-1)", 본문에 finding·한 일(${impl.testSummary})·re-audit closure(${reaudit.evidence})·"🤖 자율 fleet 산출, 머지는 사람" 명시.\n` +
    `3. 정리: cd ${REPO}; \`git worktree remove ${wt} --force\`.\n` +
    `JSON: {"prUrl","pushed","worktreeRemoved"}`,
    { label:`pr:${fd.module}`, phase:'Fan-out', schema:PR_SCHEMA })
  return { ...fd, stage:'PR', escalate:false, impl, reaudit, pr, branch, wt }
}

phase('Gatekeeper')
const classified = (await parallel(a.findings.map(fd => () =>
  agent(`${a.gatekeeperCriteria}\n\nfinding: ${JSON.stringify(fd)}\n분류 JSON만 반환.`,
        { label:`gate:${fd.module}:${fd.check}`, phase:'Gatekeeper', schema:GATE_SCHEMA })
    .then(c => ({ ...fd, ...c }))))).filter(Boolean)

const mechanical = classified.filter(c => c.class === 'mechanical')
const designForks = classified.filter(c => c.class === 'design-fork')
const skipped = classified.filter(c => c.class === 'skip')

phase('Fan-out')
const fixed = mechanical.length
  ? (await parallel(mechanical.map(fd => () => fixItem(fd)))).filter(Boolean)
  : []

return {
  gatekeeper: { mechanical: mechanical.length, designForks, skipped },
  results: fixed,
  prs: fixed.filter(r => !r.escalate && r.pr).map(r => ({ module:r.module, check:r.check, prUrl:r.pr.prUrl })),
  escalations: fixed.filter(r => r.escalate).map(r => ({ module:r.module, check:r.check, stage:r.stage, reason:r.reason })),
}
```

- [ ] **Step 2: node --check 로 문법 확인** (`node --check .claude/workflows/platform-fix-fleet.js`)

- [ ] **Step 3: 커밋** (opspilot 훅 시 amend)

```bash
git add .claude/workflows/platform-fix-fleet.js
git commit -m "feat(fix-fleet): platform-fix-fleet 병렬 fan-out 워크플로우 (Phase 2b-1)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

- [ ] **Step 4: .gitignore에 worktree 디렉토리 추가**

`.gitignore`에 `.worktrees/` 추가(워크플로우가 생성·정리하는 임시 트리). 커밋:
```bash
echo ".worktrees/" >> .gitignore
git add .gitignore && git commit -m "chore: .worktrees/ gitignore (fix-fleet 임시 트리)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: scheduler·jpa 파일럿 배치 (컨트롤러 인라인)

- [ ] **Step 1: findings 추출 + fleet 실행**

컨트롤러가 최신 스코어카드(`docs/superpowers/audits/2026-06-08-platform-audit.md`)에서 2 finding을 구성하고, gatekeeper·autoconfig-auditor criteria를 인라인해 args로 실행:
```
Workflow({scriptPath:".../.claude/workflows/platform-fix-fleet.js", args: {
  findings: [
    {module:"platform-scheduler", check:"context-runner-test", severity:"major",
     evidence:"SchedulerLoggingAspectTest(aspect 단위)만; ApplicationContextRunner 부재",
     direction:"ApplicationContextRunner로 빈 등록·@ConditionalOnMissingBean backs-off·@ConditionalOnClass(ProceedingJoinPoint) FilteredClassLoader·MeterRegistry 부재 시나리오 검증"},
    {module:"platform-persistence-jpa", check:"context-runner-test", severity:"minor",
     evidence:"ApplicationContextRunner 존재하나 positive(hasSingleBean) 1케이스뿐; backs-off/FilteredClassLoader 부재",
     direction:"backs-off(@ConditionalOnMissingBean 사용자 빈 우선)/FilteredClassLoader(@ConditionalOnClass) 시나리오 테스트 추가"}
  ],
  gatekeeperCriteria: "<fix-gatekeeper.md 분류 기준 인라인>",
  auditorCriteria: "<autoconfig-auditor 기준 인라인 — platform-audit-sweep.js CRITERIA와 동일>"
}})
```
Expected: `gatekeeper.mechanical=2`(둘 다 테스트 추가=mechanical), `prs` 2건, `escalations` 0.

- [ ] **Step 2: 게이트키퍼 분류 진위 확인 (핵심)**

반환된 `gatekeeper` 검증 — scheduler·jpa가 mechanical로(테스트 추가, API 불변) 분류됐는지. **design-fork를 mechanical로 오분류하면 위험 신호** → 기준 조정. (대조용: IdempotencyKeyValue류라면 design-fork여야 함을 별도 확인 가능.)

- [ ] **Step 3: closure·가짜green 검증 (사람 게이트, 2a와 동일)**

각 PR(scheduler·jpa)에 대해 컨트롤러가:
- `git diff main..fix/<slug> -- <module>/src/test` 로 추가 테스트가 실제 슬라이스 시나리오인지(가짜green 아님) 확인.
- `./gradlew :<module>:build` 독립 재실행 그린.
- re-audit `closed:true, status:pass` 가 디프와 일치.
- 병렬 격리 확인: 두 worktree가 충돌·교차오염 없이 각자 브랜치에 커밋됐는지.

- [ ] **Step 4: 집계 보고 + 사람 머지 결정**

`{prs, escalations, designForks, skipped}` 요약 + 스코어카드 delta(scheduler Silver→Gold, jpa Silver→Gold 기대)를 사람에게 제시. 머지는 사람(2b-1). worktree 정리됐는지(`git worktree list`) 확인.

- [ ] **Step 5: 회고 기록**

게이트키퍼 분류 정확도·병렬 격리·closure 진위를 메모리에 기록 → 2b-2(자동머지) 입력.

---

## Self-Review

**Spec coverage:**
- §3 fix-gatekeeper → Task 1 ✅ / §4 fan-out 워크플로우(gatekeeper 병렬+worktree 명시관리+집계) → Task 2 ✅
- §4 worktree 명시(main 분기, 3 에이전트 공유, 정리) → Task 2 fixItem() ✅ / §2a 학습 작은 PR(main 분기) → branch from main ✅
- §5 산출(독립 PR·집계·design-fork 별도) → Task 2 return + Task 3 Step4 ✅
- §8 검증(게이트키퍼 정확도·병렬격리·closure) → Task 3 Step2·3 ✅
- §6 비목표(자동머지·design-fork 자동처리) → 미포함(머지는 Task 3 Step4 사람) ✅
- §7 파일럿 2 item(scheduler·jpa), README skip → Task 3 Step1 ✅

**Placeholder scan:** 에이전트 criteria·워크플로우 전문·실행 args 구체. `<...인라인>` 2곳은 Task 3 Step1에서 fix-gatekeeper.md/CRITERIA 문자열을 주입하라고 명시(실행 시 채움) — 미완 아님.

**Type consistency:** finding 필드·class enum(mechanical/design-fork/skip)·status(DONE/BLOCKED/DESIGN_FORK)·reaudit(closed/status)·escalate가 spec·게이트키퍼·워크플로우·args 전반 일치. GATE/IMPL/REAUDIT/PR_SCHEMA가 각 에이전트 출력과 일치.

**주의(실행자):** Task 2 fixItem의 worktree는 `${REPO}/.worktrees/<slug>`; PR 단계가 `git worktree remove`로 정리. 동시 `git worktree add`가 2건이면 .git 락 경합 가능 — 첫 런 2 item은 안전, 확장 시 worktree 생성 직렬화 검토. Task 3는 컨트롤러 인라인(args 객체로 전달, JSON.parse 가드 있음).
