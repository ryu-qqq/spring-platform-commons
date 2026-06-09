# 자율 수정 파이프라인 Phase 2a Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development 또는 인라인 실행. 이 계획은 워크플로우 저작(Task 1) + 컨트롤러 인라인 실행·검증(Task 2~3)의 하이브리드다. Steps use checkbox (`- [ ]`) syntax.

**Goal:** 감사 finding 1건을 받아 worktree/branch에서 plan→implement(TDD)→verify→re-audit closure→PR까지 자율 수행하는 재사용 단위 파이프라인(`platform-fix-item.js`)을 만들고, redis 슬라이스 테스트 파일럿으로 end-to-end 검증한다.

**Architecture:** Workflow 스크립트가 `args`로 finding을 받아 implement 에이전트(메인 트리 feature 브랜치에서 TDD)→독립 re-audit 에이전트(closure 확인)→PR 오픈 순으로 오케스트레이션. 머지는 사람(2a).

**Tech Stack:** Workflow(JS), 서브에이전트(TDD), gh CLI, Java/Spring(ApplicationContextRunner 테스트).

**Spec:** `docs/superpowers/specs/2026-06-09-platform-fix-pipeline-phase2a-design.md`

**정제(계획 단계):** 2a는 단일·순차라 per-agent `isolation:'worktree'`를 쓰지 않는다(stage 간 파일 가시성 보존). 메인 트리의 feature 브랜치로 작업. worktree 격리는 병렬이 필요한 2b에서 도입.

---

### Task 1: platform-fix-item 워크플로우 저작

**Files:**
- Create: `.claude/workflows/platform-fix-item.js`

- [ ] **Step 1: 워크플로우 스크립트 작성**

`.claude/workflows/platform-fix-item.js`:

```javascript
export const meta = {
  name: 'platform-fix-item',
  description: '감사 finding 1건을 worktree/branch에서 자율 수정(TDD)하고 re-audit closure 후 PR을 연다 (머지는 사람)',
  phases: [
    { title: 'Implement', detail: 'feature 브랜치에서 TDD 구현' },
    { title: 'Re-audit', detail: '독립 auditor로 finding closure 확인' },
    { title: 'PR', detail: 'PR 오픈 (머지는 사람)' },
  ],
}

// args = finding: {module, check, severity, evidence, direction, branch, auditorCriteria}
const f = args
if (!f || !f.module || !f.check || !f.direction) {
  throw new Error('args에 finding {module, check, direction, branch, auditorCriteria} 필요')
}
const REPO = '/Users/ryu-qqq/Documents/ryu-qqq/spring-platform-commons'

phase('Implement')
const impl = await agent(
  `너는 자율 수정 구현자다. 작업 디렉토리: ${REPO}. TDD로 감사 finding을 수정하라.\n\n` +
  `## finding\nmodule=${f.module}\ncheck=${f.check}\nevidence=${f.evidence}\ndirection=${f.direction}\n\n` +
  `## 절차 (반드시 순서대로)\n` +
  `1. \`git checkout -b ${f.branch}\` (이미 있으면 checkout).\n` +
  `2. 참고: 잘 된 선례 테스트를 먼저 읽어라 — platform-security/src/test 의 ApplicationContextRunner 테스트(PlatformSecurityAutoConfigurationTest), platform-outbox 의 동일 패턴. 그 스타일(WebApplicationContextRunner/ApplicationContextRunner + AutoConfigurations.of + withUserConfiguration backs-off + FilteredClassLoader)을 모사.\n` +
  `3. TDD: direction이 요구하는 시나리오의 실패 테스트부터 작성 → \`./gradlew :${f.module}:test\` 로 red 확인(컴파일/실패) → 최소 구현/테스트 보강으로 green.\n` +
  `4. \`./gradlew :${f.module}:build\` 그린 확인(전체 모듈 빌드).\n` +
  `5. 변경을 커밋(한국어 메시지, Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>).\n` +
  `## 경계\n- 오직 이 finding 범위만. 다른 리팩터 금지.\n- 설계 갈림(유효 접근 다수·public API 변경)이 드러나면 즉시 멈추고 status=DESIGN_FORK 로 보고(자동 설계 금지).\n- 가짜 green 금지 — 테스트가 실제 시나리오(backs-off/FilteredClassLoader)를 검증해야 한다.\n\n` +
  `JSON 반환: {"status":"DONE|BLOCKED|DESIGN_FORK","branch":"${f.branch}","filesChanged":[...],"commit":"<sha>","testSummary":"<무엇을 어떻게 검증했나>","buildGreen":true|false,"notes":"..."}`,
  { label: `implement:${f.module}`, phase: 'Implement', schema: {
      type:'object', required:['status','buildGreen'],
      properties:{ status:{enum:['DONE','BLOCKED','DESIGN_FORK']}, branch:{type:'string'},
        filesChanged:{type:'array',items:{type:'string'}}, commit:{type:'string'},
        testSummary:{type:'string'}, buildGreen:{type:'boolean'}, notes:{type:'string'} } } })

if (impl.status !== 'DONE' || !impl.buildGreen) {
  return { stage: 'Implement', escalate: true, reason: `구현 미완: status=${impl.status}, buildGreen=${impl.buildGreen}`, impl }
}

phase('Re-audit')
// 독립 auditor 재실행 — 같은 트리(브랜치 체크아웃 상태)에서 finding이 닫혔는지 객체 확인
const reaudit = await agent(
  `${f.auditorCriteria}\n\n대상 모듈: ${f.module} (경로: ${REPO}/${f.module}). 현재 git 브랜치 상태 그대로 감사하라.\n` +
  `특히 check id="${f.check}" 의 현재 status를 판정하라. 너는 읽기전용이다 — 파일 수정 금지, 구현자 주장을 믿지 말고 코드를 직접 확인.\n` +
  `JSON 반환: {"module":"${f.module}","check":"${f.check}","status":"pass|warn|fail|na","evidence":"<file:line>","closed":true|false}`,
  { label: `re-audit:${f.module}`, phase: 'Re-audit', schema: {
      type:'object', required:['check','status','closed'],
      properties:{ module:{type:'string'}, check:{type:'string'},
        status:{enum:['pass','warn','fail','na']}, evidence:{type:'string'}, closed:{type:'boolean'} } } })

if (!reaudit.closed || reaudit.status !== 'pass') {
  return { stage: 'Re-audit', escalate: true, reason: `closure 실패: ${f.check} 가 ${reaudit.status} (pass 아님)`, impl, reaudit }
}

phase('PR')
const pr = await agent(
  `작업 디렉토리 ${REPO}, 현재 브랜치 ${impl.branch}. 다음으로 PR을 열어라(머지는 하지 마라):\n` +
  `1. \`git push -u origin ${impl.branch}\`.\n` +
  `2. \`gh pr create\` — 제목 "fix(${f.module}): ${f.check} 자율 수정 (Phase 2a)", 본문에:\n` +
  `   - 감사 finding(evidence) 요약\n   - 한 일(${impl.testSummary})\n   - re-audit closure: ${f.check} → ${reaudit.status} (${reaudit.evidence})\n` +
  `   - 스코어카드 delta(해당 track Silver→Gold 기대)\n   - "🤖 Phase 2a 자율 파이프라인 산출 — 머지는 사람 검토 후" 명시.\n` +
  `JSON 반환: {"prUrl":"<url>","pushed":true|false}`,
  { label: `pr:${f.module}`, phase: 'PR', schema: {
      type:'object', required:['pushed'], properties:{ prUrl:{type:'string'}, pushed:{type:'boolean'} } } })

return { stage: 'PR', escalate: false, impl, reaudit, pr }
```

- [ ] **Step 2: 스크립트 문법·구조 점검**

Read로 다시 읽어 `meta` 순수 리터럴·`args` 가드·각 stage schema·early-return(escalate) 경로 확인. (실행은 Task 2)

- [ ] **Step 3: Commit**

```bash
git add .claude/workflows/platform-fix-item.js
git commit -m "feat(fix-pipeline): platform-fix-item 단위 자율 수정 워크플로우 (Phase 2a)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```
(OpsPilot 훅이 먼저 커밋하면 메시지 amend.)

---

### Task 2: redis 파일럿 실행 (컨트롤러 인라인)

**Files:** (워크플로우 산출 — redis 모듈에 테스트 파일 생성, feature 브랜치)

- [ ] **Step 1: 파일럿 finding으로 워크플로우 실행**

컨트롤러가 `Workflow({scriptPath, args})` 로 실행. args:
```json
{
  "module": "platform-redis",
  "check": "context-runner-test",
  "severity": "major",
  "evidence": "platform-redis/src/test 에 Mockito 어댑터 테스트만; ApplicationContextRunner 미사용",
  "direction": "ApplicationContextRunner 기반 테스트 추가: RedissonClient 빈 부재 시 backs-off, 존재 시 등록, FilteredClassLoader(Redisson 제외) 가드 검증",
  "branch": "fix/redis-autoconfig-slice-test",
  "auditorCriteria": "<autoconfig-auditor criteria 인라인 — platform-audit-sweep.js 의 CRITERIA['autoconfig-auditor'] 와 동일>"
}
```
Expected: `{stage:'PR', escalate:false, impl:{status:DONE,buildGreen:true}, reaudit:{closed:true,status:'pass'}, pr:{...}}` 또는 escalate=true 와 사유.

- [ ] **Step 2: 결과 분기 처리**

- `escalate:false, PR` 도달 → Step 3 진행.
- `escalate:true` (BLOCKED/DESIGN_FORK/closure 실패) → 사유를 사람에게 보고하고 원인 분석(파이프라인 결함인지 finding 성격인지). 파일럿이므로 여기서 멈추고 사람 판단.

- [ ] **Step 3: closure 진위·가짜green 검증 (사람 게이트)**

컨트롤러가 직접:
- `git diff main..fix/redis-autoconfig-slice-test -- platform-redis/src/test` 로 추가된 테스트를 읽어 **실제 슬라이스 시나리오**(ApplicationContextRunner + backs-off + FilteredClassLoader)를 검증하는지 확인(가짜 green/빈 테스트 아님).
- `./gradlew :platform-redis:build` 재실행으로 그린 독립 확인.
- re-audit가 보고한 pass 가 디프와 일치하는지 대조.

---

### Task 3: PR 확정 + 사람 머지 결정

- [ ] **Step 1: PR 내용 점검**

워크플로우가 연 PR(또는 push된 브랜치)을 `gh pr view` 로 확인 — finding·한 일·closure·delta·"머지는 사람" 문구 포함 여부.

- [ ] **Step 2: 사람에게 머지 결정 위임 (2a 경계)**

컨트롤러는 머지하지 않는다. PR URL + closure 요약 + 스코어카드 delta(redis Paved Road Silver→Gold)를 사람에게 제시하고 머지 여부를 받는다.

- [ ] **Step 3: 회고 기록**

파일럿 결과를 메모리/journal에 기록 — 파이프라인이 (a) closure를 객체적으로 닫았는가 (b) 가짜green 없었는가 (c) escalate 경로가 정상인가. 2b(병렬·게이트키퍼·자동머지) 설계 입력.

---

## Self-Review

**Spec coverage:**
- §3 stage(intake→implement→verify→re-audit→review→PR) → Task 1 워크플로우 implement(TDD+build)·re-audit·PR ✅. review(조건부)는 테스트-only 변경이라 build+re-audit로 충분(§4) — 별도 리뷰어 미호출이 의도 ✅
- §3 re-audit closure(독립·객체) → Task 1 Re-audit phase(독립 agent, 읽기전용, closed 판정) ✅
- §2 design-fork 안전판 → implement 프롬프트 status=DESIGN_FORK 경로 ✅
- §5 escalation → early-return escalate=true 경로(구현 미완/closure 실패) ✅
- 자율 PR까지·머지 사람 → Task 1 PR phase(머지 안 함) + Task 3 사람 위임 ✅
- §9 검증(closure 진위·가짜green) → Task 2 Step3 사람 게이트 ✅
- §8 비목표(병렬·자동머지·게이트키퍼) → 미포함 ✅

**Placeholder scan:** 없음 — 워크플로우 전문·args 예시·검증 명령 구체. (단 args.auditorCriteria 는 실행 시 platform-audit-sweep.js 의 CRITERIA 문자열을 그대로 주입 — Task 2 Step1에 명시)

**Type consistency:** finding 필드(module·check·direction·branch·auditorCriteria)·status enum(DONE/BLOCKED/DESIGN_FORK)·reaudit(closed·status)·escalate 분기가 spec·워크플로우·실행 args 전반 일치.

**주의(실행자):** Task 2~3은 컨트롤러 인라인(Workflow 실행·gh·사람 게이트). per-agent worktree 미사용(2a 단일·순차). re-audit 독립성 = implement와 별도 agent 호출로 보장.
