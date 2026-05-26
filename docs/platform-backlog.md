# Platform Roadmap — spring-platform-commons

> **스냅샷:** 2026-05-26  
> **컨벤션 SSOT:** Obsidian wiki `wiki/conventions/java-springboot-hexagonal/_overview.md`  
> **Notion Epic (템플릿):** [Hexagonal template v1 — reference skeleton](https://www.notion.so/36be81355305818bb905cd7d2b32077b)  
> **Notion Epic (Harness):** [Harness 소비 + OpsPilot 검증](https://www.notion.so/36be81355305819dafa1febb33cbf3ca)

---

## North Star

**샘플 비즈니스 도메인 없이** 헥사고날 5레이어 **빈 골격 + 레이어별 Platform SDK**를 제공한다.  
각 서비스는 wiki 패턴대로 자기 `{Domain}` 코드만 채운다.

```
domain → application → adapter-in / adapter-out → bootstrap
         ↑ platform-common-*     ↑ platform-persistence, resilient-client, platform-outbox-*, …
```

**완료 루프 (모든 Phase Task 공통):**

```
Notion Task (수용 기준) → eo-start → 구현 skill → ./gradlew test → commit
  → OpsPilot ingest → work-evaluator 4축 채점 → proposal (HITL) → Harness 점진 고도화 → eo-done
```

상세: [`opspilot-feedback-loop.md`](./opspilot-feedback-loop.md) · [`engineering-os-runbook.md`](./engineering-os-runbook.md)

---

## 현황 (2026-05-26)

| 영역 | 상태 |
|------|------|
| **Phase 0 (HT1–HT8)** | ✅ 완료 — `./gradlew build` green |
| `platform-common-domain` (HT2) | ✅ Query/paging VOs + `Versioned` |
| `platform-common-application` (HT7) | ✅ `CommonVoFactory` |
| `platform-web` (HT3) | ✅ ApiResponse, GEH, RequestContextFilter |
| `platform-bootstrap` (HT4) | ✅ logback JSON, actuator, bootstrap yml |
| `architecture-tests` (HT5) | ✅ ArchUnit smoke |
| template `example-client` (HT6) | ✅ YAML-only resilient-client wiring |
| resilient-client v0.2 | ✅ timeout, YAML auto-register, Registry, RestSupport |
| HT8 docs | ✅ README, backlog, `docs/sdk/resilient-client`, wiki |
| GitHub Actions CI | ✅ |
| Harness (agent-crew v0.4 + `.claude/`) | ✅ SPC-H1 |
| OpsPilot ingest | ⏸ eval ✅ · `workflow_patch` parser 갭 → proposal apply는 P1 블로커 (ops-pilot backlog) |
| JitPack publish (P0-2) | ⏳ 태그·release 후 |

---

## Platform SDK 지도

| 모듈 | 레이어 | 역할 | Phase |
|------|--------|------|-------|
| `platform-common-domain` | domain | `DateRange`, `QueryContext`, `PageRequest`, `SearchField`, `SortKey`, `Versioned` 계약 | 1 |
| `platform-common-application` | application | `CommonVoFactory`, assembly helpers | 1 |
| `platform-web` | adapter-in | `ApiResponse`, `GlobalExceptionHandler`, 409 낙관적 락 매핑 | 1 |
| `platform-bootstrap` | bootstrap | JSON logging, actuator, bootstrap yml | 1 ✅ |
| `platform-persistence` | adapter-out | `BaseEntity`(`@Version`), QueryDSL condition helpers, Auditing | 2 |
| `resilient-client` | adapter-out | HTTP CB/retry/timeout, YAML beans (v0.2) | 0 ✅ / JitPack P0-2 |
| `platform-outbox-domain` | domain | Outbox machinery 공통 VO/enum (본체 Aggregate는 per-domain) | 3 |
| `platform-outbox-persistence` | adapter-out | JPA 공통 필드, `version`, 멱등키 매핑 | 3 |
| `platform-outbox-application` | application | poller(SKIP LOCKED), processor, `deferRetry`, timeout recover | 3 |
| `platform-messaging` | adapter-out | SQS publish/consume, visibility timeout, redelivery metrics | 4 |
| `platform-persistence-redis` | adapter-out | `CachePort`, `DistributedLockPort`, `StockCounterPort`, Lua 보상 템플릿 | 4 |
| `platform-observability` | cross | traceId, metric naming | 4 |
| `platform-saga` (선택) | application | `CompensationStep` 실행기, Outbox/MQ 연동 — **보상 비즈니스 로직 X** | 4+ |
| `platform-scheduler` (선택) | bootstrap | ShedLock — 스케줄러 전용 lease (Redisson과 역할 분리) | 4+ |

**의도적으로 SDK화하지 않는 것**

- `{Domain}SearchCriteria` + endpoint별 enum 2개 (wiki per-endpoint 패턴)
- `{Domain}Outbox` 5-class set (wiki `outbox-family.md` — per-domain)
- Saga/보상 **내용** (결제 취소, 재고 복구 등)

---

## 동시성·일관성 플랫폼 (락·보상 전략)

wiki + FileFlow 실전 사례 기준. **플랫폼은 “도구 + 결정 트리”**를 제공하고, **어디에 쓸지는 서비스**가 wiki대로 선택한다.

### 결정 트리 (우선순위)

```
1. 같은 Aggregate / Outbox 행 경쟁?
   → @Version 낙관적 락 (platform-persistence + platform-web 409)
   → Outbox는 wiki 필수: version + refreshVersion()

2. Outbox poller 다중 인스턴스?
   → SELECT … FOR UPDATE SKIP LOCKED (platform-outbox-application)
   → ShedLock/Redisson 불필요 (FileFlow F9 본해결)

3. Redis 단일 키 원자 연산?
   → DECRBY/INCRBY (platform-persistence-redis) — 분산락 불필요

4. Redis 다중 키 원자 + 실패 롤백?
   → Lua + compensation 역연산 (platform-persistence-redis)

5. DB row 없이 cross-resource 배타?
   → Redisson 분산락 (platform-persistence-redis, optional dep)

6. 로컬 커밋 후 외부 실패 보정?
   → Outbox + 멱등 소비 (platform-outbox + platform-messaging)
   → (선택) platform-saga 실행기 — step 구현은 서비스

7. 스케줄러 1회만 실행?
   → ShedLock (platform-scheduler) 또는 DB lease
```

### 레이어별 SDK 배치

| 관심사 | SDK | 서비스 책임 |
|--------|-----|-------------|
| **낙관적 락** | `Versioned`, `BaseEntity.@Version`, 409 envelope | Aggregate/Entity conform |
| **분산락** | `DistributedLockPort`, Redisson Adapter, `LockExecutor` | `LockKey` VO, leaseTime·범위 |
| **보상 (Redis)** | Lua rollback 템플릿 | 키 설계·역연산 의미 |
| **보상 (분산/Saga)** | Outbox enqueue, (선택) saga recorder | CompensationHandler 구현 |
| **Outbox 재시도** | `fail` / `deferRetry` / `recoverFromTimeout` | payload·타입 enum |

**교훈 (FileFlow):** 트래픽 낮은 만료 처리에서 Redisson 분산락 → `@Version`으로 대체. 분산락은 **optional dependency**로 유지.

---

## Phase 로드맵

### Phase 0 — 골격 ✅ (2026-05-26)

| ID | Task | 수용 기준 (요약) | 상태 |
|----|------|------------------|------|
| **HT1** | Gradle hexagonal skeleton | `settings.gradle` 5모듈, wiki deps, `./gradlew build` green | ✅ |
| HT2 | `platform-common-domain` v0.1 | Query/paging VOs + `Versioned` | ✅ |
| HT3 | `platform-web` + bootstrap wiring | ApiResponse, exception handler, bootstrap deps | ✅ |
| HT4 | `platform-bootstrap` | logging JSON, actuator, bootstrap yml | ✅ |
| HT5 | wiki-aligned ArchUnit smoke | 레이어 의존 방향 테스트 | ✅ |
| HT6 | `resilient-client` in template | adapter-out example-client, YAML-only wiring | ✅ |
| HT7 | `platform-common-application` v0.1 | `CommonVoFactory` | ✅ |
| HT8 | README · runbook · backlog · SDK docs | monorepo README, wiki resilient-client | ✅ |

### Phase 1 — Core + Web + Bootstrap (대부분 Phase 0에서 완료)

- `platform-common-domain`, `platform-common-application`, `platform-web`, `platform-bootstrap` — ✅
- 템플릿 `rest-api` → `application` → `domain` 의존 검증
- **비목표:** SampleOrder 등 데모 도메인

### Phase 2 — Persistence + 낙관적 락

- `platform-persistence`: `BaseEntity`, Auditing, QueryDSL helpers
- `OptimisticLockException` → `platform-web` 409
- (선택) Manager 1회 재시도 정책 가이드 (wiki application 트랜잭션 정책 정합)
- resilient-client v0.2 ✅ — JitPack publish만 P0-2 잔여

### Phase 3 — Outbox machinery

- wiki `outbox-family.md` machinery만 SDK화 (5-class set은 per-domain)
- poller: **SKIP LOCKED** (분산락 대체)
- `deferRetry`, timeout recover, 멱등키 persistence
- SQS redelivery: `idempotencyKey` + idempotent handler (wiki)

### Phase 4 — Messaging · Redis · Observability

- `platform-messaging` (SQS)
- `platform-persistence-redis`: Cache, **DistributedLock**(Redisson optional), StockCounter, Lua compensation
- `platform-observability`
- (선택) `platform-saga`, `platform-scheduler` (ShedLock)

### Phase 5 — 소비자 서비스

- 각 레포가 template + SDK를 copy/depend
- per-service `{Domain}` 구현 (platform은 machinery만)

---

## Legacy / 병행 Task (resilient-client · Harness)

Phase 2·Harness Epic과 병행. Hexagonal Phase 0 완료 후 우선순위 재조정 가능.

| ID | Task | Phase | 비고 |
|----|------|-------|------|
| ~~P0-1~~ | ~~resilient-client v0.2~~ | — | ✅ HT6 + v0.2 commit |
| P0-2 | release tag → JitPack | 2 | [Notion](https://www.notion.so/36be8135530581dda209c6e720c3b57e) |
| ~~P0-3~~ | ~~ADR-001 reference-service 별도 레포~~ | — | **대체:** template은 본 monorepo 골격 (ADR 재작성 후보) |
| SPC-H4 | OpsPilot feedback E2E | Harness | `workflow_patch` parser 후 |
| SPC-H3 | eo 루프 정리 | Harness | |

---

## OpsPilot ingest — 로드맵과 함께 점차 고도화

**가능하다.** 로드맵 Task를 **1~3일 = ingest 1회** 단위로 쪼개면, 구현할수록 Harness 품질을 수치·제안으로 끌어올릴 수 있다.

### 루프

| 단계 | 도구 | 산출 |
|------|------|------|
| Task 시작 | `engineering-os eo-start` | Notion `진행 중` |
| 구현 | `resilient-client-dev` / (추후) platform skill | 코드 + test |
| 커밋 | `git rev-parse HEAD` | **ingest gitRef** |
| 평가 | OpsPilot → `work-evaluator` | 4축: 가정금지 · 최소 · 범위 · 검증 |
| 개선 | proposal draft → **HITL 승인** → apply | `.cursor/rules`, skill, agent patch |
| 기록 | vault `raw/spring-platform-commons-*-evaluation.md` | 점수 추이·retro |
| Task 완료 | `eo-done` | Notion `완료` + ingest id 메모 |

### ingest 시 메타데이터

```json
{
  "gitRef": "<spring-platform-commons SHA>",
  "notionTaskUrl": "https://www.notion.so/...",
  "sessionType": "local-claude"
}
```

Phase·Task ID를 commit message 또는 ingest 메모에 넣으면 (`HT2`, `platform-persistence-v1`) **로드맵 진행과 채점 이력을 1:1 매칭**할 수 있다.

### 점수 고도화가 의미 있으려면

1. **`.claude/`가 git에 커밋**되어 있어야 eval run이 Harness 버전을 본다.
2. Task마다 **수용 기준 체크리스트**를 Notion에 두면 원칙 4「검증」축 채점 근거가 된다.
3. proposal apply(HITL)로 skill·rule·agent를 패치 → **다음 Task ingest 점수 상승**을 기대.
4. 현재 블로커: ops-pilot **`workflow_patch` parser** — eval은 되나 auto-apply proposal은 SPC-H4 완료 후 본격화.

### Phase별 ingest retro 예시

| Task | ingest retro (한 줄) |
|------|----------------------|
| HT1 | "골격만 — bootRun disabled 명시, sample domain 0건" |
| HT2 | "Versioned + PageRequest만 — SearchCriteria per-endpoint는 비목표 유지" |
| Phase 2 persistence | "BaseEntity @Version + 409 — Manager retry는 문서만, 코드 X" |
| Phase 3 outbox | "SKIP LOCKED poller — ShedLock/Redisson 도입 안 함" |
| Phase 4 redis lock | "Redisson optional starter — FileFlow 교훈 wiki 링크" |

---

## Notion Tasks (Hexagonal Epic)

Epic: [Hexagonal template v1](https://www.notion.so/36be81355305818bb905cd7d2b32077b)

| ID | Task | URL |
|----|------|-----|
| HT1 | Gradle hexagonal skeleton | https://www.notion.so/36be8135530581339c6bd7fae9c81f5d |
| HT2 | platform-common-domain v0.1 | https://www.notion.so/36be8135530581998f4ed6b70906f0ac |
| HT3 | platform-web + bootstrap wiring | https://www.notion.so/36be8135530581c5912ff1f1bbc0f58a |
| HT4 | platform-bootstrap v0.1 | https://www.notion.so/36be8135530581018e2ecb33636f0367 |
| HT5 | wiki-aligned ArchUnit smoke | https://www.notion.so/36be8135530581e1b625f8d395f5e924 |
| HT6 | resilient-client v2 template wiring | https://www.notion.so/36be813553058127b0acd8746fd9bf03 |
| HT7 | platform-common-application v0.1 | https://www.notion.so/36be8135530581c3a92fe4968dba86f3 |
| HT8 | README · runbook · platform-backlog | https://www.notion.so/36be8135530581659a91dc96f9f9e945 |
| P0-1 | resilient-client v0.2 timeout | https://www.notion.so/36be813553058195a061c873cbcdc71c |
| P0-2 | JitPack publish | https://www.notion.so/36be8135530581dda209c6e720c3b57e |
| P2-1 | platform-persistence v0.1 | https://www.notion.so/36be8135530581a8a658c2ee60e768cf |
| P3-1 | platform-outbox-domain + persistence | https://www.notion.so/36be81355305812b84f9e79ac1ffc182 |
| P3-2 | platform-outbox-application | https://www.notion.so/36be8135530581728d0ce54542ca44d8 |
| P4-1 | platform-messaging | https://www.notion.so/36be81355305814db0a7cfe684752b1d |
| P4-2 | platform-persistence-redis | https://www.notion.so/36be8135530581a3b0d2c9114c76bca3 |
| P4-3 | platform-observability | https://www.notion.so/36be813553058121b5bae7d9f3cf1fac |
| P4-4 | platform-saga (optional) | https://www.notion.so/36be81355305819494a6c1ba97c6d96c |
| P4-5 | platform-scheduler (optional) | https://www.notion.so/36be813553058103a320d34384bca771 |
| P5-1 | 소비자 서비스 adoption 가이드 | https://www.notion.so/36be813553058182acffe111dfefead0 |
| ADR-002 | hexagonal template monorepo | https://www.notion.so/36be813553058138b029d84990175ca5 |

---

## Notion Task 등록 가이드 (다음 작업)

1. Epic **Hexagonal template v1** 아래 Phase 0 Task (HT2~) 등록
2. 필드: `수용 기준` 체크리스트 · `비목표` · `ingest retro 예시` (위 표 복사)
3. `platform-backlog` skill → `notion-manager` 위임
4. 완료 시 Task 본문에 **ingest id · 4축 점수 · proposal id** 메모

---

## 변경 이력

| 날짜 | 내용 |
|------|------|
| 2026-05-25 | 초版 — resilient-client P0 중심 |
| 2026-05-25 | **전면 개편** — hexagonal platform SDK Phase 0~5, 동시성·보상 전략, OpsPilot ingest 연동 |
| 2026-05-26 | Phase 0 (HT1–HT8) 완료, resilient-client v0.2, README·wiki SDK docs |
