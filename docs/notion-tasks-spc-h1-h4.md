# Notion Engineering OS — Task 초안 (SPC-H1 ~ H4)

> Engineering OS 허브: https://www.notion.so/36be81355305810ab090d786d4384140  
> **Projects** DB에 `spring-platform-commons` 행 생성 후, 아래 Task를 **Tasks** DB에 등록.  
> **Epic** 제안: *Harness 소비 + OpsPilot 검증*

복사 시 속성: `프로젝트` → spring-platform-commons · `Phase` · `Repo Impact` · `우선순위` 아래 표 참고.

---

## Epic (Projects/Epics DB)

**제목:** spring-platform-commons — Harness 소비 + OpsPilot 검증  
**설명:** agent-crew v0.4.0 메타 Harness + 프로젝트 Harness(resilient-client) 적용. Engineering OS 루프 + OpsPilot feedback E2E.

---

## SPC-H1 — Harness bootstrap

| 필드 | 값 |
|------|-----|
| **작업** | Harness bootstrap — agent-crew sync + 프로젝트 Harness |
| **Phase** | MVP |
| **Repo Impact** | 문서만 |
| **우선순위** | P0 |

**설명:**  
- 구 SDK `.claude` 에셋 제거  
- agent-crew v0.4.0 agents/skills/references sync  
- 프로젝트 Harness: `resilience-reviewer`, `resilient-client-dev`, `references/resilient-client/`  
- `agent-crew.lock`, `docs/engineering-os-runbook.md`  
- git commit + OpsPilot scan → `work-evaluator` 버전 ≥1

**성공 기준:**  
- [ ] OpsPilot 레지스트리에 자산 12+ (공통 10 + 프로젝트 2)  
- [ ] `work-evaluator` git 버전 1개 이상  
- [ ] `docs/engineering-os-runbook.md` 존재

**검증:** OpsPilot POST `/api/projects/{id}/scan`

---

## SPC-H2 — 프로젝트 Harness smoke

| 필드 | 값 |
|------|-----|
| **작업** | resilient-client-dev + resilience-reviewer smoke |
| **Phase** | MVP |
| **Repo Impact** | 문서만 |
| **우선순위** | P1 |

**설명:**  
- Cursor에서 `resilient-client-dev`로 README/주석 수준 micro change 또는 dry-run  
- `resilience-reviewer` read-only 리뷰 출력 확인  
- (선택) OpsPilot fixture run on `resilience-reviewer` if scenario exists

**성공 기준:**  
- [ ] reviewer 체크리스트 형식 출력  
- [ ] references (`sdk-overview`, `resilience-patterns`) 인용

---

## SPC-H3 — Engineering OS eo-start / eo-done 1건

| 필드 | 값 |
|------|-----|
| **작업** | Engineering OS 루프 실전 1건 (이 Task 자체) |
| **Phase** | 운영 |
| **Repo Impact** | 문서만 |
| **우선순위** | P1 |

**설명:**  
- 이 Task URL로 `engineering-os eo-start`  
- 완료 시 `eo-done`: Wiki ADR(vault raw) + Commit URL  
- Notion 필드 `Wiki ADR`, `Commit`, `상태=완료`

**성공 기준:**  
- [ ] Notion 3필드 채움  
- [ ] vault `raw/spring-platform-commons-*` 시드 1건

---

## SPC-H4 — OpsPilot feedback loop E2E

| 필드 | 값 |
|------|-----|
| **작업** | OpsPilot feedback loop E2E |
| **Phase** | MVP |
| **Repo Impact** | 문서만 |
| **우선순위** | P0 |

**설명:**  
- `docs/opspilot-feedback-loop.md` 따라 fixture → (선택) local-claude  
- ingest에 `notionTaskUrl` = 이 Task  
- 피드백 탭: 승인 → apply (HITL)

**성공 기준:**  
- [ ] fixture ingest `done` + proposal 1  
- [ ] (MVP Done) local-claude ingest 1회  
- [ ] Task 메모: ingest id, proposal id, apply commit

**OpsPilot project ID:** `9f83dd39-85e2-4fb2-807c-b565c27d82b3`

---

## 이후 제품 Task (예시)

| 작업 | Phase | Repo Impact |
|------|-------|-------------|
| resilient-client v0.2.0 — {기능명} | 코드 | 코드 |

제품 Task도 동일 루프: eo-start → resilient-client-dev → test → eo-done → ingest.
