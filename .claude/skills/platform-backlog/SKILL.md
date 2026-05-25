---
name: platform-backlog
description: spring-platform-commons 제품·플랫폼 백로그 수립. Epic/현재 레포 상태를 읽고 P0~P2 Task 후보·수용 기준·비목표를 정리하고 Notion Engineering OS Tasks 등록까지. "백로그", "뭐부터", "우선순위", "로드맵", "다음 Task", "P0 정리", "platform-backlog" 요청에 트리거. 구현·ADR 확정·ingest는 하지 않는다 — Task 고정까지만.
allowed-tools:
  - Read
  - Glob
  - Grep
  - Bash
---

# platform-backlog Skill — PO급 백로그 (프로젝트 Harness)

> spring-platform-commons 전용. 메타 작업 루프는 `engineering-os`, 구현은 `resilient-client-dev`, 결정은 `adr` skill.

## 작업 전 — 설정 로드 (필수)

1. `.claude/project.yaml` Read — `project.*`, `tracking.notion.*`, `knowledge.vault.*`
2. `tracking.notion.enabled` false면 멈추고 보고
3. (있으면) `docs/notion-tasks-*.md`, `docs/engineering-os-runbook.md` Read
4. Epic URL 또는 Notion Tasks DB 맥락 — 호출자가 주거나 Engineering OS에서 조회

## 역할

**무엇을 다음에 할지**를 정한다. 구현·채점·ingest는 하지 않는다.

| 한다 | 하지 않는다 |
|------|-------------|
| 현황 스캔 (레포·Epic·완료 Task) | 코드 작성 |
| 후보 Task 3~7개 + P0/P1/P2 | ADR 최종 결정 (`adr` skill) |
| 수용 기준 · 비목표 · Repo Impact | `eo-start` / `eo-done` |
| Notion Task 초안 → `notion-manager` 위임 | OpsPilot ingest |

## 흐름

### 1. 컨텍스트 수집

- `wiki-lookup` 위임(선택) — vault 선례. vault 없으면 레포 README·`references/`·docs만.
- Notion: Epic · Projects · 기존 Tasks · 완료/진행 중 상태
- Git: 최근 commit, 모듈 tree, CI 유무
- OpsPilot/ Harness: SPC-H*, open backlog (ops-pilot docs)

### 2. 갭 분석

Epic 목표 대비:

- **제품** — resilient-client 다음 버전, 새 SDK 모듈
- **플랫폼** — CI/CD, 배포, reference-service, Harness
- **운영** — Engineering OS 루프, OpsPilot E2E

각 후보에 **Repo Impact** · **노력(S/M/L)** · **선행 Task** 표시.

### 3. 우선순위 제안 (HITL)

사용자에게 **P0 1~3개** 제시. 각 Task:

```markdown
### {제목}
- **우선순위:** P0 | **Phase:** MVP | **Repo Impact:** 코드|문서만|없음
- **Epic:** {링크}
- **설명:** 1~2문장
- **수용 기준:**
  - [ ] …
  - [ ] …
- **비목표:** …
- **완료 후:** engineering-os eo-start → {skill} → commit → local-claude ingest
- **ingest retro 예시:** "…"
```

**P0 선정 원칙 (이 프로젝트):**

1. 이미 있는 SDK/CI 위에 **쌓을 수 있는** 것 우선
2. Harness-only smoke보다 **소비자 가치** (SDK·배포·레퍼런스)
3. ops-pilot **블로커**는 P1 (workflow_patch 등) — 명시
4. 한 Task = **1 ingest 단위** (1~3일 분량)

### 4. 사용자 확정 (HITL)

- P0 목록·순서·제외 항목은 **사용자 승인** 후에만 Notion 등록
- 애매하면 질문 1~2개만 (범위 폭발 금지)

### 5. Notion 등록

`notion-manager` / Notion MCP `notion-create-pages` 위임:

- parent: `tracking.notion.tasksDatabaseId`
- `프로젝트` → spring-platform-commons
- `Epic` → 해당 Epic relation
- `상태` → `시작 전`
- 필드: `작업`, `설명`, `우선순위`, `Phase`, `Repo Impact`, `유형`

### 6. 산출물 기록

- `docs/platform-backlog.md` 갱신 (이 레포 스냅샷 — Notion과 dual write)
- 사용자에게: Task URL 목록 + **다음 권장 첫 Task 1개** + 진입 skill (`resilient-client-dev` / `adr` / …)

## 다른 Harness와의 관계

| 자산 | 관계 |
|------|------|
| `engineering-os` | Task **시작·완료** (이 skill 이후) |
| `resilient-client-dev` | SDK Task **구현** |
| `adr` | 갈림길 Task **결정** (reference-service 등) |
| `test-plan` | 큰 기능 전 테스트 계획 |
| `work-evaluator` / OpsPilot | Task **commit 후** ingest — 이 skill 범위 밖 |

## North Star

백로그 논의 → **Notion Task(수용 기준)** → 구현 → **ingest가 채점할 대상**을 만든다.
