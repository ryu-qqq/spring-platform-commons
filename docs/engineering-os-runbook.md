# Engineering OS + Harness — spring-platform-commons 실행 가이드

> Cursor에서 Task 단위로 작업하고, agent-crew 메타 Harness + 프로젝트 Harness로 실행한다.  
> OpsPilot 검증: [`opspilot-feedback-loop.md`](./opspilot-feedback-loop.md) · Notion Task 초안: [`notion-tasks-spc-h1-h4.md`](./notion-tasks-spc-h1-h4.md)

---

## Harness 구성 (이 레포)

| 구분 | 위치 | 버전 |
|------|------|------|
| **공통 (agent-crew v0.4.0)** | `.claude/agents/` · `skills/` · `references/java-spring/` · `references/notion/` | `agent-crew.lock` + git tag |
| **프로젝트 고유** | `resilience-reviewer`, `resilient-client-dev`, `references/resilient-client/` | **이 레포 git commit** |
| **설정** | `.claude/project.yaml` | 이 레포 |

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
| 테스트 계획만 | `test-plan` skill |
| ADR·의사결정 | `adr` skill |
| Notion Task 갱신 | `engineering-os` / `notion-manager` |

### 3. Task 완료

```
engineering-os eo-done — 산출물 요약, commit hash, wiki raw 경로
```

- Notion: `완료` · Wiki ADR · Commit

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

## agent-crew 재동기화

```bash
AC=~/Documents/ryu-qqq/agent-crew
SPC=~/Documents/ryu-qqq/spring-platform-commons/.claude

rsync -av --delete "$AC/agents/" "$SPC/agents/" \
  --exclude resilience-reviewer.md   # 프로젝트 고유 보존은 수동 merge 권장

rsync -av --delete "$AC/skills/" "$SPC/skills/" \
  --exclude resilient-client-dev/

rsync -av "$AC/references/" "$SPC/references/" \
  --exclude resilient-client/

# agent-crew.lock 의 version/commit/tag 갱신 후 커밋
```

공통 에이전트 sync 시 **프로젝트 고유 3종은 덮어쓰지 않는다.**

---

## Cursor 에이전트 프롬프트 예시

```
docs/engineering-os-runbook.md 와 Notion Task {URL} 을 읽고,
engineering-os eo-start 후 resilient-client-dev 로 작업해.
끝나면 ./gradlew test, eo-done, OpsPilot fixture ingest까지.
```
