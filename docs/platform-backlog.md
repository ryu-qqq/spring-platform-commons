# Platform Backlog — spring-platform-commons

> **스냅샷:** 2026-05-25 · skill: `platform-backlog`  
> **Notion Epic:** [Harness 소비 + OpsPilot 검증](https://www.notion.so/36be81355305819dafa1febb33cbf3ca)  
> **Engineering OS Tasks DB:** Notion Engineering OS → Tasks

---

## 현황 (2026-05-25)

| 영역 | 상태 |
|------|------|
| resilient-client v0.1 | ✅ core · metrics · starter |
| GitHub Actions CI | ✅ TASK-8 완료 |
| Harness (agent-crew v0.4 + 프로젝트) | ✅ SPC-H1 |
| OpsPilot ingest | ⏸ eval ✅ / `workflow_patch` parser 갭 (ops-pilot backlog) |
| reference-service / hexagonal template | ❌ |
| 다음 SDK (exception, logging) | ❌ |

---

## P0 — 다음에 할 것 (승인됨)

| ID | Notion Task |
|----|-------------|
| P0-1 | https://www.notion.so/36be813553058195a061c873cbcdc71c |
| P0-2 | https://www.notion.so/36be8135530581dda209c6e720c3b57e |
| P0-3 | https://www.notion.so/36be8135530581e9a305f856016fc954 |

### P0-1 · resilient-client v0.2.0 — Timeout configuration

- **Repo Impact:** 코드 · **Phase:** MVP · **노력:** M
- **설명:** Per-client connect/read timeout을 Properties·Builder로 노출. 외부 HTTP SLA 차등 대응.
- **수용 기준:**
  - [ ] `ResilientClientBuilder` + starter YAML에 timeout 설정
  - [ ] 단위 테스트 (timeout → `NetworkException` 등)
  - [ ] `./gradlew test` · CI green
  - [ ] README/sdk-overview 갱신
- **비목표:** WebClient adapter, retry 정책 전면 개편
- **진입:** `engineering-os eo-start` → `resilient-client-dev` → ingest

### P0-2 · GitHub Actions — release tag → JitPack publish

- **Repo Impact:** 코드 · **Phase:** MVP · **노력:** S
- **설명:** tag push 시 publish job (또는 JitPack 연동 문서+workflow). v0.1.0 배포 경로 검증.
- **수용 기준:**
  - [ ] `.github/workflows/`에 release/publish workflow
  - [ ] README 배포 섹션과 일치
  - [ ] (dry-run 또는 실제 tag) publish 경로 검증
- **비목표:** 사내 Nexus (별 Task)
- **진입:** `engineering-os` → 구현 → ingest

### P0-3 · ADR-001 — reference-service 레포 전략

- **Repo Impact:** 문서만 · **Phase:** MVP · **노력:** S
- **설명:** 헥사고날 레퍼런스를 별도 레포 vs monorepo `examples/` — ADR Accepted.
- **수용 기준:**
  - [ ] `docs/adr/0001-reference-service-placement.md` Accepted
  - [ ] Notion Task Commit/Wiki ADR 기록
- **비목표:** reference-service 구현 (ADR 다음 Task)
- **진입:** `adr` skill → eo-done

---

## P1 — 바로 다음

| Task | 비고 |
|------|------|
| OpsPilot feedback E2E (SPC-H4) | ops-pilot `workflow_patch` 후 |
| reference-service bootstrap | ADR-001 이후 |
| platform-exception SDK | resilient-client 패턴 복제 |
| SPC-H3 eo 루프 정리 Task | Harness 운영 |

---

## P2 — 이후

- 사내 Nexus / Maven Central 배포
- Cursor rules ← `.claude` skill 포팅
- agent-crew upstream에 generic `platform-backlog` 기여

---

## Task 완료 루프 (반복)

```
platform-backlog (주기적) → Notion Task
  → engineering-os eo-start
  → resilient-client-dev | adr | …
  → commit → local-claude ingest → eo-done
```
