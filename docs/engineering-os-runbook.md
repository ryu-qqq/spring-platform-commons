# Engineering OS + Harness — spring-platform-commons 실행 가이드

> Cursor에서 Task 단위로 작업하고, agent-crew 메타 Harness + 프로젝트 Harness로 실행한다.  
> OpsPilot 검증: [`opspilot-feedback-loop.md`](./opspilot-feedback-loop.md) · Notion Task 초안: [`notion-tasks-spc-h1-h4.md`](./notion-tasks-spc-h1-h4.md)

---

## Harness 구성 (이 레포)

| 구분 | 위치 | 버전 |
|------|------|------|
| **공통 (agent-crew v0.6.0)** | `.claude/agents/` · `skills/` · `references/` (conventions·java-spring·notion·infra-aws) | `agent-crew.lock` + git tag |
| **프로젝트 고유** | `resilience-reviewer`, `resilient-client-dev`, `platform-backlog`, `references/resilient-client/` | **이 레포 git commit** |
| **설정** | `.claude/project.yaml` | 이 레포 (`git.commit` → SPC 티켓) |
| **Cursor** | `.cursor/rules/commit-format-spc.mdc` | SPC subject 규칙 |

구 SDK 에셋(`feature-designer`, `sdk-feature-dev` 등)은 **제거됨** — 무시.

---

## 일상 작업 루프

### 1. Task 시작

```
engineering-os eo-start — Notion Task URL (또는 TASK-n)
```

- `notion-manager`가 상태 → `진행 중`
- wiki 선례 있으면 요약

### 2. 작업 실행

| 작업 유형 | 진입 |
|-----------|------|
| SDK 코드·테스트 | `resilient-client-dev` skill |
| **백로그·우선순위·Task 발행** | **`platform-backlog` skill** |
| 테스트 계획만 | `test-plan` skill |
| ADR·의사결정 | `adr` skill |
| **헥사고날 컨벤션 조회** | Obsidian wiki `wiki/conventions/java-springboot-hexagonal/_overview.md` · `wiki-lookup` · `.cursor/rules/java-spring-hexagonal-conventions.mdc` |
| Notion Task 갱신 | `engineering-os` / `notion-manager` |

### 3. Task 완료

```
engineering-os eo-done — 산출물 요약, commit hash, wiki raw 경로
```

- Notion: `완료` · Wiki ADR · Commit
- **커밋 subject**는 `.claude/references/conventions/commit-format.md` — `docs(platform): SPC-123 …` 형식

### 4. OpsPilot 검증 (코드/SDK 작업 후)

[`opspilot-feedback-loop.md`](./opspilot-feedback-loop.md) — ingest → 피드백 탭 HITL

---

## OpsPilot 등록

| 항목 | 값 |
|------|-----|
| project ID | `9f83dd39-85e2-4fb2-807c-b565c27d82b3` |
| clone | `/Users/ryu-qqq/Documents/ryu-qqq/spring-platform-commons` |

Harness 변경 후 **레지스트리 → 스캔** 필수.

---

## agent-crew 재동기화 (권장: OpsPilot API)

```bash
curl -s -X POST http://localhost:3001/api/projects/9f83dd39-85e2-4fb2-807c-b565c27d82b3/sync-agent-crew \
  -H 'content-type: application/json' \
  -d '{"tag":"v0.6.0","scan":true}'
```

MCP: `sync_agent_crew({ projectId: "9f83dd39-...", tag: "v0.6.0", scan: true })`

`cpSync` 방식이라 **프로젝트 고유** `resilience-reviewer` · `resilient-client-dev` · `platform-backlog` · `references/resilient-client/` 는 그대로 유지된다.  
수동 rsync 시에만 `--exclude` 로 보호 (아래 레거시).

<details>
<summary>레거시 rsync (오프라인)</summary>

```bash
AC=~/Documents/ryu-qqq/agent-crew
SPC=~/Documents/ryu-qqq/spring-platform-commons/.claude

rsync -av --delete "$AC/agents/" "$SPC/agents/" \
  --exclude resilience-reviewer.md

rsync -av --delete "$AC/skills/" "$SPC/skills/" \
  --exclude resilient-client-dev/ --exclude platform-backlog/

rsync -av "$AC/references/" "$SPC/references/" \
  --exclude resilient-client/
```

</details>

---

## Cursor 에이전트 프롬프트 예시

```
docs/engineering-os-runbook.md 와 Notion Task {URL} 을 읽고,
engineering-os eo-start 후 resilient-client-dev 로 작업해.
끝나면 ./gradlew test, eo-done, OpsPilot fixture ingest까지.
```
