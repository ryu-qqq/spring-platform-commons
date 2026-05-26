# OpsPilot local-claude ingest (본番)

실제 작업 커밋 + 회고로 **work-evaluator → proposal-reviewer** 파이프라인을 돌린다.

## 이 레포 설정 (한 번만 수정)

- **projectId:** `9f83dd39-85e2-4fb2-807c-b565c27d82b3`
- **workspaceMode:** `linked`
- **workPath:** `/Users/ryu-qqq/Documents/ryu-qqq/spring-platform-commons`
- **opsPilotApi:** `http://localhost:3001`
- **notionTaskUrl:** (선택) Engineering OS Task URL

## 사전조건

- `/opspilot-preflight` 통과
- `.claude/` 커밋 + OpsPilot **스캔** 완료
- **커밋 subject**가 `SPC-<n> ` 형식 (`git log -1 --format=%s`) — `requireOnIngest: true`
- `local-claude` = 로컬 `claude` CLI·인증 사용 가능
- **gitRef는 workPath의 SHA** (다른 레포 SHA 넣지 말 것)

## 워크플로

1. 사용자에게 **회고 한 줄** 요청 (없으면 커밋 메시지 기반으로 제안)
2. `workPath`에서 HEAD SHA·subject 확인 (`git log -1 --format=%s` — `SPC-` convention)
3. ingest:
   ```bash
   curl -s -X POST http://localhost:3001/api/feedback/ingest \
     -H 'Content-Type: application/json' \
     -d '{
       "projectId": "9f83dd39-85e2-4fb2-807c-b565c27d82b3",
       "gitRef": "<HEAD_SHA>",
       "evalSource": "local-claude",
       "retro": "<회고>",
       "notionTaskUrl": "<optional>"
     }'
   ```
4. `evaluating` → `reviewing` → `reviewed`/`done`/`failed`까지 폴링 (수 분 소요 가능)
5. **실행 / 트레이스** 탭에서 eval run id 안내
6. 완료 시 proposal·review 상태 요약

## MCP 대안

OpsPilot MCP(`http://localhost:3001/mcp`) 등록 시 `ingest_cursor_session` 동일 역할.

## 출력

| 필드 | 값 |
|---|---|
| ingest id | |
| eval run id | |
| status | |
| proposals | draft n건 |
| review | approved/rejected 요약 |

## 다음

피드백 탭 HITL → apply → `/opspilot-post-apply`
