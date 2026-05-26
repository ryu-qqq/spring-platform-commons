# OpsPilot 피드백 루프 — spring-platform-commons 실행 가이드

> **목적**: Cursor에서 한 작업 단위를 OpsPilot에 ingest → `work-evaluator` 평가 → 개선안(draft) → 사람 승인(HITL) → 이 레포 clone에 apply까지 **한 바퀴** 돌린다.  
> **North Star**: 에이전트·지침이 제대로·일관되게 작동하는지 판단을 빨리 돕는가.  
> **상위 설계**: ops-pilot wiki `raw/ops-pilot-feedback-loop-mvp-2026-05-25.md`, Pivot ADR `raw/ops-pilot-cursor-first-pivot-2026-05-25.md`

---

## 역할 분담 (Pivot)

| 도구 | 역할 |
|------|------|
| **Cursor IDE** | 일상 코딩·작업 (이 레포) |
| **Claude Code** | 백그라운드 평가 (`work-evaluator`) |
| **OpsPilot** | ingest · eval run · proposal · apply · MCP |

OpsPilot 웹 UI는 IDE 대체가 아니다. **피드백** 탭은 ingest 목록·승인·적용(HITL)용이다.

---

## 이 레포 OpsPilot 등록 정보

| 항목 | 값 |
|------|-----|
| **작업 경로 (clone)** | `/Users/ryu-qqq/Documents/ryu-qqq/spring-platform-commons` |
| **OpsPilot project ID** | `9f83dd39-85e2-4fb2-807c-b565c27d82b3` |
| **git URL** | `git@github.com:ryu-qqq/spring-platform-commons.git` |
| **API** | http://localhost:3001 |
| **Web UI** | http://localhost:5173 → **피드백** 탭 |

> Harness·작업 루프 전체: [`engineering-os-runbook.md`](./engineering-os-runbook.md) · Notion Task 초안: [`notion-tasks-spc-h1-h4.md`](./notion-tasks-spc-h1-h4.md)

---

## 사전 조건 체크리스트

에이전트/사람 모두 시작 전 확인:

- [ ] OpsPilot 서버 `:3001` 기동 (`curl -s http://localhost:3001/api/runs` → JSON 200)
- [ ] OpsPilot 웹 `:5173` 기동
- [ ] 이 레포에 `.claude/agents/work-evaluator.md` 존재
- [ ] `.claude/project.yaml` 존재
- [ ] `.claude/` 가 **git에 커밋**됨 (untracked면 eval run 불가 — 자산 버전 0개)
- [ ] OpsPilot **레지스트리**에서 `spring-platform-commons` 선택 → **스캔** 후 `work-evaluator` 자산 보임

### OpsPilot 기동 (별도 터미널 2개)

```bash
# 터미널 A — server (루트 pnpm dev 금지)
cd ~/Documents/ryu-qqq/ops-pilot/apps/server
OPS_PROJECTS_DIR=~/Documents/ryu-qqq OPS_WORKTREES_DIR=~/.opspilot/worktrees pnpm dev

# 터미널 B — web
cd ~/Documents/ryu-qqq/ops-pilot/apps/web && pnpm dev
```

---

## Step 1 — `.claude` 커밋 (최초 1회, 아직 안 했으면)

```bash
cd ~/Documents/ryu-qqq/spring-platform-commons
git status   # .claude/ untracked 이면 아래 실행

git add .claude/
git commit -m "chore: agent-crew assets + work-evaluator (OpsPilot feedback loop)"
```

커밋 후 OpsPilot UI **레지스트리 → spring-platform-commons → 스캔** (또는 API):

```bash
curl -s -X POST http://localhost:3001/api/projects/9f83dd39-85e2-4fb2-807c-b565c27d82b3/scan
```

`work-evaluator` 에 **버전(git 커밋) 1개 이상** 있어야 한다.

---

## Step 2 — 평가할 작업 단위 준비

ingest는 **`gitRef`(커밋 SHA)** 기준으로 diff를 만든다.

- **옵션 A**: 이미 끝난 작업 → 그 작업의 커밋 SHA 사용  
- **옵션 B**: 지금 Cursor에서 작업 → 커밋 → `git rev-parse HEAD`

```bash
git rev-parse HEAD   # ingest에 넣을 SHA
```

회고 한 줄을 정해 두면 eval 시나리오에 들어간다 (예: `"JitPack README 작업 회고"`).

---

## Step 3 — Ingest (트리거)

피드백 탭 UI에는 ingest **생성 폼이 없다**. API 또는 MCP로 트리거한다.

### 3a. 빠른 연습 — `fixture` (Claude 안 돌림, ~5초)

UI·HITL 흐름만 확인할 때:

```bash
SHA=$(git rev-parse HEAD)
curl -s -X POST http://localhost:3001/api/feedback/ingest \
  -H 'Content-Type: application/json' \
  -d "{
    \"projectId\": \"9f83dd39-85e2-4fb2-807c-b565c27d82b3\",
    \"gitRef\": \"$SHA\",
    \"evalSource\": \"fixture\",
    \"retro\": \"fixture smoke — UI HITL 확인\"
  }" | python3 -m json.tool
```

응답 `"status": "done"` + `proposals` 1건이면 성공.

### 3b. MVP 본番 — `local-claude` (실제 eval)

로컬 `claude` CLI·키체인 인증 필요. 수 분 걸릴 수 있음.

```bash
SHA=$(git rev-parse HEAD)
curl -s -X POST http://localhost:3001/api/feedback/ingest \
  -H 'Content-Type: application/json' \
  -d "{
    \"projectId\": \"9f83dd39-85e2-4fb2-807c-b565c27d82b3\",
    \"gitRef\": \"$SHA\",
    \"evalSource\": \"local-claude\",
    \"retro\": \"<이번 작업 회고 한 줄>\"
  }" | python3 -m json.tool
```

- `"status": "evaluating"` → **실행** 탭 또는 폴링으로 run 완료 대기  
- `"status": "done"` → 개선안 draft 생성됨  
- `"status": "failed"` → `contextJson.evalError` 확인 (아래 트러블슈팅)

상태 폴링:

```bash
INGEST_ID="<ingest 응답 id>"
curl -s "http://localhost:3001/api/feedback/ingest/$INGEST_ID" | python3 -m json.tool
```

### 3c. (선택) MCP — Cursor/Claude Code에서

OpsPilot MCP 등록 후:

```bash
claude mcp add --transport http opspilot http://localhost:3001/mcp
```

툴: `ingest_cursor_session` · `list_proposals` · `apply_proposal`

---

## Step 4 — OpsPilot UI에서 HITL

1. http://localhost:5173 → **피드백** 탭  
2. 프로젝트 **spring-platform-commons** 선택  
3. 좌측 ingest 클릭 (`done` / `evaluating` 배지)  
4. 개선안 카드 확인 (`targetKind`, `targetPath`, `content`)  
5. **승인** → **clone에 반영** → 확인  
6. 이 레포에서 적용 커밋 확인:

```bash
git -C ~/Documents/ryu-qqq/spring-platform-commons log -1 --oneline
git status
```

apply는 **이 clone 경로**에 파일을 쓰고 OpsPilot 구조화 커밋을 만든다.

---

## Step 5 — 완료 기록 (Engineering OS, 선택)

MVP Done 조건에 맞추려면 Notion TASK-5 등에 기록:

- ingest id  
- proposal id  
- apply commit SHA  
- wiki ADR 링크  

ingest 시 `notionTaskUrl` 필드를 넣을 수 있다:

```json
"notionTaskUrl": "https://www.notion.so/..."
```

---

## 트러블슈팅

| 증상 | 원인 | 조치 |
|------|------|------|
| `work-evaluator asset not found` | 스캔 안 됨 / 파일 없음 | `.claude/agents/work-evaluator.md` + **스캔** |
| eval failed, 버전 없음 | `.claude` 미커밋 | Step 1 커밋 후 재스캔 |
| `InvalidGitRef` | SHA가 이 clone에 없음 | `git rev-parse HEAD` 로 **이 레포** SHA 사용 |
| ingest failed (다른 프로젝트 SHA) | ops-pilot 등 다른 레포 커밋을 gitRef로 넣음 | 반드시 **spring-platform-commons** SHA |
| UI ingest 목록 비어 있음 | projectId 불일치 | 피드백 탭에서 **spring-platform-commons** 선택 |
| `:3001` 이상 JSON | 스테일 서버 / DB 불일치 | `lsof -ti:3001 \| xargs kill` 후 server 재기동 |

---

## 에이전트에게 시킬 때 (Cursor 프롬프트 예시)

```
docs/opspilot-feedback-loop.md 를 읽고 순서대로 실행해줘.
1) 사전 조건 체크
2) .claude 커밋·스캔 필요하면 처리
3) fixture ingest 1회로 UI까지 확인
4) 문제 없으면 local-claude ingest 제안
각 단계 결과(ingest id, status, error)를 짧게 보고해.
```

---

## Platform 로드맵과 ingest (점진 고도화)

Phase Task마다 **commit 1회 = ingest 1회**로 묶으면 `work-evaluator` 4축 채점·proposal(HITL)로 Harness(skill·rule·agent)를 단계적으로 올릴 수 있다.

- **로드맵 SSOT:** [`platform-backlog.md`](./platform-backlog.md) — Phase 0~5, 동시성·보상 전략, Task별 ingest retro 예시
- **메타:** ingest JSON에 `notionTaskUrl` + commit message에 Task ID (`HT2`, `platform-persistence-v1`)
- **블로커:** `workflow_patch` parser 완료 전(SPC-H4)에는 eval·수동 retro 중심, auto-apply proposal은 제한적

---

## 관련 파일 (이 레포)

| 경로 | 설명 |
|------|------|
| `docs/platform-backlog.md` | Platform Phase 로드맵 + ingest 연동 |
| `.claude/agents/work-evaluator.md` | 피드백 eval 에이전트 (agent-crew) |
| `.claude/project.yaml` | Engineering OS / vault 설정 |
| `docs/opspilot-feedback-loop.md` | 이 문서 |

---

*최종 갱신: 2026-05-25 — Platform roadmap ingest 연동 섹션 추가*
