# 플랫폼 감사 fleet Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** spring-platform-commons를 분야별로 감사하는 읽기전용 에이전트 2종 + Soundcheck식 스코어카드를 산출하는 sweep 워크플로우를 구축하고, 첫 감사 리포트를 만든다.

**Architecture:** 프로젝트 로컬 `.claude/agents/`의 auditor 2종(autoconfig·observability)을 `.claude/workflows/platform-audit-sweep.js`가 모듈×차원 fan-out으로 호출 → 결정론적 집계 + synthesis 에이전트가 `docs/superpowers/audits/`에 스코어카드 작성.

**Tech Stack:** Claude Code 서브에이전트(.md), Workflow 스크립트(JS), 대상은 Java/Spring 모듈.

**Spec:** `docs/superpowers/specs/2026-06-08-platform-audit-fleet-phase1-design.md`

---

### Task 1: autoconfig-auditor 에이전트

**Files:**
- Create: `.claude/agents/autoconfig-auditor.md`

- [ ] **Step 1: 에이전트 파일 작성**

`.claude/agents/autoconfig-auditor.md`:

```markdown
---
name: autoconfig-auditor
description: spring-platform-commons 자동설정 모듈이 Spring Boot paved-road 기준(@AutoConfiguration+.imports 등록·@ConditionalOnMissingBean 오버라이드·ApplicationContextRunner fitness test)을 만족하는지 읽기전용으로 감사한다. 모듈 경로를 받아 checks(JSON) 반환. 코드 수정 안 함 — 감사 전담. platform-audit-sweep 워크플로우가 호출.
tools:
  - Read
  - Glob
  - Grep
---

# Autoconfig Auditor

## 역할
공통 SDK starter가 "paved road로 제대로 패키징·기본값 제공되는가"를 채점한다. 설계 seam이 아니라 **자동설정 패키징 위생**을 본다. 코드를 수정하지 않는다 — 채점·근거·방향 출력만.

## 입력
대상 모듈 경로 1개(예: `platform-redis`). `src/main`의 autoconfig 클래스·`META-INF/spring/*.imports`·build 파일·`src/test`의 ApplicationContextRunner 테스트를 Read/Glob/Grep.

## check 기준 (grounded — Spring Boot 공식문서)
| id | pass 조건 | fail 신호 |
|---|---|---|
| `imports-registered` | 자동설정 클래스가 `@AutoConfiguration` 표시 + `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`에 등록 | imports 파일 없음 / 클래스 누락 / `spring.factories` 잔존 |
| `conditional-override` | 공개 기본 빈에 `@ConditionalOnMissingBean` | 가드 없는 무조건 빈 등록 |
| `context-runner-test` | `ApplicationContextRunner` 테스트 존재(backs-off/property/FilteredClassLoader 중 해당) | 자동설정 테스트 부재 |
| `conditional-on-class` | optional 의존성(MeterRegistry 등)에 `@ConditionalOnClass`/`ObjectProvider` 가드 | optional 의존성 무가드 직접 주입 |

## 절차
1. Glob `<module>/**/*.java` + `<module>/src/main/resources/META-INF/spring/*.imports` + `<module>/build.gradle*` 로 표면 식별.
2. 각 check를 신호 적중으로 채점. **모듈 성격상 자동설정 비대상이면 `na`** (예: common-domain은 순수 도메인 → 전 check na).
3. 근거는 file:line 또는 타입/메서드. 신호 없으면 추측 금지.

## 출력 (JSON)
\```json
{
  "module": "<모듈경로>",
  "track": "Paved Road",
  "checks": [
    {"id": "imports-registered", "status": "pass|warn|fail|na", "severity": "info|minor|major", "evidence": "<file:line>", "direction": "<설계수준 방향>"}
  ]
}
\```

## 경계
- 수정 금지. read-only.
- 버그·스타일·도메인 로직은 범위 외 — 자동설정 패키징 위생만.
- 근거 없는 finding 금지.
- 자가 채점으로 머지 결정 X — 사람 판단 결합.
```

- [ ] **Step 2: 알려진 모듈로 수동 검증 (false positive 점검)**

Agent 도구로 `autoconfig-auditor`를 `platform-redis`에 실행:
```
대상 모듈: platform-redis. checks(JSON)를 반환하라.
```
Expected: 유효한 JSON, `platform-redis`의 실제 autoconfig 상태 반영, 환각 없음(존재하지 않는 파일 인용 X). redis가 자동설정을 제대로 갖췄다면 대부분 pass/na.

- [ ] **Step 3: Commit**

```bash
git add .claude/agents/autoconfig-auditor.md
git commit -m "feat(audit-fleet): autoconfig-auditor 감사 에이전트 (Phase 1)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: observability-auditor 에이전트

**Files:**
- Create: `.claude/agents/observability-auditor.md`

- [ ] **Step 1: 에이전트 파일 작성**

`.claude/agents/observability-auditor.md`:

```markdown
---
name: observability-auditor
description: spring-platform-commons 모듈의 관측성(메트릭·트레이스·MDC)이 플랫폼 기준(Observation 단일점·카디널리티 규율·트레이스 전파·MdcKeys SSOT·MeterRegistry 옵셔널)을 만족하는지 읽기전용으로 감사한다. 모듈 경로를 받아 checks(JSON) 반환. 코드 수정 안 함 — 감사 전담. platform-audit-sweep 워크플로우가 호출.
tools:
  - Read
  - Glob
  - Grep
---

# Observability Auditor

## 역할
공통 SDK 모듈이 "관측성을 제대로 노출하는가"를 채점한다. 메트릭·트레이스·MDC 계측 위생을 본다. 코드를 수정하지 않는다 — 채점·근거·방향 출력만.

## 입력
대상 모듈 경로 1개. `src/main`의 메트릭(Micrometer/MeterRegistry)·트레이싱·MDC·HTTP 클라이언트 코드를 Read/Glob/Grep.

## check 기준 (grounded — Spring Boot Actuator 공식문서 + 내부 MdcKeys)
| id | pass 조건 | fail 신호 |
|---|---|---|
| `metric-naming` | 메트릭명 일관 dot-네임스페이스(예: `scheduler.job.*`) | 비일관·하드코딩 산재 |
| `cardinality-discipline` | userId 등 고카디널리티가 메트릭 태그에 없음(트레이스 한정) | 고카디널리티 값을 메트릭 tag로 |
| `trace-propagation` | 제공 HTTP 클라이언트가 auto-config builder(`RestClient.Builder` 등) 경유 | 수동 `new RestTemplate()` 등 전파 깨짐 |
| `mdc-key-consistency` | MDC 키가 `MdcKeys` SSOT 상수 사용 | MDC 키 리터럴 산재 |
| `meter-optional` | MeterRegistry 부재 시 zero-config 동작(ObjectProvider/Conditional) | MeterRegistry 무조건 의존 |

## 절차
1. Grep `<module>` 으로 `MeterRegistry`·`Observation`·`MDC`·`RestTemplate|RestClient|WebClient`·`MdcKeys` 신호 수집.
2. 각 check 채점. **모듈이 관측성 비대상이면 `na`**(예: 순수 VO 모듈). 단 MDC 키 정의/소비 모듈은 `mdc-key-consistency` 적용.
3. 근거는 file:line. 신호 없으면 추측 금지.

## 출력 (JSON)
\```json
{
  "module": "<모듈경로>",
  "track": "Observability",
  "checks": [
    {"id": "metric-naming", "status": "pass|warn|fail|na", "severity": "info|minor|major", "evidence": "<file:line>", "direction": "<방향>"}
  ]
}
\```

## 경계
- 수정 금지. read-only.
- "노출 안 됨"이 항상 결함 아님 — 모듈 성격 보고 na 판단.
- 근거 없는 finding 금지. 자가 채점으로 머지 결정 X.
```

- [ ] **Step 2: 알려진 모듈로 수동 검증**

Agent 도구로 `observability-auditor`를 `platform-scheduler`에 실행 (scheduler는 P2에서 `scheduler.job.*` 메트릭·MDC seam 정리됨 → 대부분 pass 나와야 false-positive 없음 확인):
```
대상 모듈: platform-scheduler. checks(JSON)를 반환하라.
```
Expected: 유효 JSON, `metric-naming` pass(scheduler.job.*), `mdc-key-consistency` 관련 MdcKeys 사용 반영.

- [ ] **Step 3: Commit**

```bash
git add .claude/agents/observability-auditor.md
git commit -m "feat(audit-fleet): observability-auditor 감사 에이전트 (Phase 1)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: platform-audit-sweep 워크플로우

**Files:**
- Create: `.claude/workflows/platform-audit-sweep.js`

- [ ] **Step 1: 워크플로우 스크립트 작성**

`.claude/workflows/platform-audit-sweep.js`:

```javascript
export const meta = {
  name: 'platform-audit-sweep',
  description: 'spring-platform-commons 모듈을 분야별 auditor로 감사해 Soundcheck식 스코어카드를 산출한다',
  phases: [
    { title: 'Audit', detail: '모듈×차원 fan-out 감사' },
    { title: 'Scorecard', detail: '집계 + 스코어카드 리포트 작성' },
  ],
}

// §3 매핑 — cross-product 아님
const TARGETS = [
  // Paved Road
  ...['platform-redis','platform-scheduler','platform-security','platform-outbox','platform-web','platform-persistence-jpa']
      .map(m => ({ module: m, auditor: 'autoconfig-auditor', track: 'Paved Road' })),
  // Observability
  ...['platform-scheduler','platform-outbox','resilient-client','platform-web','platform-security','platform-common-domain']
      .map(m => ({ module: m, auditor: 'observability-auditor', track: 'Observability' })),
]

const FINDINGS_SCHEMA = {
  type: 'object',
  required: ['module','track','checks'],
  properties: {
    module: { type: 'string' },
    track: { type: 'string' },
    checks: {
      type: 'array',
      items: {
        type: 'object',
        required: ['id','status'],
        properties: {
          id: { type: 'string' },
          status: { enum: ['pass','warn','fail','na'] },
          severity: { enum: ['info','minor','major'] },
          evidence: { type: 'string' },
          direction: { type: 'string' },
        },
      },
    },
  },
}

phase('Audit')
const findings = (await parallel(TARGETS.map(t => () =>
  agent(`대상 모듈: ${t.module}. checks(JSON)를 반환하라.`,
        { label: `${t.auditor}:${t.module}`, phase: 'Audit', agentType: t.auditor, schema: FINDINGS_SCHEMA })
))).filter(Boolean)

phase('Scorecard')
// 결정론적 집계: 모듈×track pass/total + level
const LEVEL = (pass, total) => total === 0 ? 'N/A' : pass === total ? 'Gold' : (pass / total >= 0.7 ? 'Silver' : 'Bronze')
const scorecard = findings.map(f => {
  const scored = f.checks.filter(c => c.status !== 'na')
  const pass = scored.filter(c => c.status === 'pass').length
  return { module: f.module, track: f.track, pass, total: scored.length, level: LEVEL(pass, scored.length), checks: f.checks }
})
// escalation: severity=major fail
const escalations = findings.flatMap(f =>
  f.checks.filter(c => c.status === 'fail' && c.severity === 'major')
          .map(c => ({ module: f.module, track: f.track, ...c })))

const report = await agent(
  `다음 감사 결과로 Soundcheck식 모듈 스코어카드 마크다운을 작성하라. ` +
  `상단에 escalation(중요) 섹션, 그 아래 모듈×track 레벨 매트릭스, 그 아래 check별 상세(status·evidence·direction). ` +
  `추가로 Structure(위생) track: 각 모듈 디렉토리에 README 존재 여부를 Glob으로 직접 확인해 행으로 포함하라.\n\n` +
  `scorecard=${JSON.stringify(scorecard)}\n\nescalations=${JSON.stringify(escalations)}`,
  { label: 'scorecard-writer', phase: 'Scorecard', agentType: 'general-purpose' })

return { scorecard, escalations, report }
```

- [ ] **Step 2: 2모듈 dry-run (워크플로우 골격 검증)**

먼저 TARGETS를 2개로 임시 축소(예: platform-redis autoconfig + platform-scheduler observability)하거나, 전체로 실행하되 결과를 sanity-check. 실행:
```
Workflow({ name: "platform-audit-sweep" })
```
Expected: Audit phase에서 auditor들이 병렬 실행, Scorecard phase에서 레벨 매트릭스 포함 리포트 반환. JSON 스키마 위반 없음.

- [ ] **Step 3: Commit**

```bash
git add .claude/workflows/platform-audit-sweep.js
git commit -m "feat(audit-fleet): platform-audit-sweep 워크플로우 (Phase 1)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: 첫 전체 감사 sweep + 리포트

**Files:**
- Create: `docs/superpowers/audits/2026-06-08-platform-audit.md` (워크플로우 산출물을 저장)

- [ ] **Step 1: 전체 sweep 실행**

```
Workflow({ name: "platform-audit-sweep" })
```
Expected: 12개 (module×dimension) 감사 + 스코어카드.

- [ ] **Step 2: 산출 리포트를 파일로 저장**

워크플로우가 반환한 `report` 마크다운을 `docs/superpowers/audits/2026-06-08-platform-audit.md`로 저장. 상단에 frontmatter(`title`, `date: 2026-06-08`, `source: platform-audit-sweep`) 추가.

- [ ] **Step 3: sanity-check (known-good/known-bad)**

리포트를 사람이 읽고 검증:
- known-good: `platform-scheduler` 메트릭 naming = pass 인가 (P2에서 정리됨). 거짓 fail이면 auditor 기준 조정.
- known-bad/gap: 자동설정 없는 모듈이 fail이 아니라 `na`로 처리됐는가.
- escalation 섹션의 major fail이 실제로 타당한가(코드 대조).

- [ ] **Step 4: Commit**

```bash
git add docs/superpowers/audits/2026-06-08-platform-audit.md
git commit -m "audit(platform): 첫 전체 감사 스코어카드 (Phase 1 sweep)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Self-Review

**Spec coverage:**
- §3.1 autoconfig-auditor → Task 1 ✅ / §3.2 observability-auditor → Task 2 ✅
- §4 sweep 워크플로우(Audit fan-out + Scorecard 집계/synthesis) → Task 3 ✅
- §4 Structure track(README 등 결정론적) → Task 3 Step 1 scorecard-writer 프롬프트에 포함 ✅
- §5 escalation(major fail) → Task 3 스크립트 escalations ✅
- §6 in-repo 산출 → Task 4 ✅ / Notion 승격은 Phase 1 비목표 경계라 수동(후속)
- §8 검증(known-good/known-bad) → Task 1 Step2·Task 2 Step2·Task 4 Step3 ✅

**Placeholder scan:** 없음 — 에이전트 본문·워크플로우 스크립트·실행 명령 모두 실제 내용.

**Type consistency:** `checks[].status`(pass/warn/fail/na)·`track`(Paved Road/Observability)·`agentType`(auditor name)·LEVEL 스킴(Gold/Silver/Bronze)이 spec·에이전트·워크플로우 전반 일치. FINDINGS_SCHEMA가 두 auditor 출력 스키마와 일치.

**주의(실행자):** Task 3 워크플로우는 Task 1·2의 에이전트가 `.claude/agents/`에 존재해야 `agentType`으로 해석된다 — 순서 준수. Notion 승격(§6)은 Phase 1에서 수동, 자동화는 Phase 2.
