# 자율 수정 파이프라인 — Phase 2a 설계 (단일 항목, PR까지)

- 일자: 2026-06-09
- 상위 프로젝트: 자가 감사·자가 개선 플랫폼 fleet ([[platform-fleet-autopilot]])
- 의존: Phase 1 감사 fleet (`feat/platform-audit-fleet` — autoconfig-auditor·observability-auditor·platform-audit-sweep)
- 범위: **Phase 2a = 단일 mechanical 항목을 자율로 PR까지.** 병렬 fan-out·자동 머지·게이트키퍼 분류기는 Phase 2b.

## 1. 목적

Phase 1 감사가 산출한 스코어카드의 **mechanical finding** 하나를 받아, 사람 개입 없이 worktree에서 plan→implement(TDD)→verify→**re-audit closure**→PR까지 자율 수행하는 *재사용 가능한 단위 파이프라인*을 만든다. 이 단위 파이프라인이 Phase 2b의 병렬 stage가 된다.

**자율성 경계(확정):** PR 오픈까지 자율, **머지는 사람**. 자동 머지는 게이트키퍼 판정이 known-good으로 검증된 뒤 Phase 2b에서.

## 2. 라우팅 원칙 (Phase 2 전체 관통)

- **Mechanical → 자동**: `direction`이 명확하고 설계 갈림 없음(테스트·문서·일관성 보수, public API 불변). → brainstorming 생략, 파이프라인이 처리.
- **Design-fork → 사람**: 새 공개 추상화·public API 변경·유효 접근 다수. → 파이프라인이 멈추고 사람에게 brainstorming 위임. **자동 설계 금지**(value-only catch류 보존).
- Phase 2a는 사람이 known-mechanical 항목을 직접 선택하므로 분류기 불필요(2b로 DEFER). 단 파이프라인은 **implement 중 설계 갈림이 드러나면 BLOCKED로 에스컬레이트**한다(안전판).

## 3. 단위 파이프라인 stage

입력: 감사 finding 1건 `{module, check, severity, evidence, direction}` (스코어카드에서).

| stage | 동작 | 산출 | 실패 시 |
|---|---|---|---|
| **intake** | finding 검증(mechanical인지 사람이 사전 확인), worktree 생성 | 격리 worktree | — |
| **plan** | `direction`을 입력으로 TDD 계획 수립(writing-plans 수준). brainstorming 생략 | 단계별 계획 | — |
| **implement** | 서브에이전트 TDD — 테스트 red→구현 green→commit | 커밋(들) | BLOCKED 에스컬레이트 |
| **verify** | `./gradlew :<module>:build` 그린 | 빌드 그린 | 재시도 1회 후 BLOCKED |
| **re-audit** ★ | 해당 auditor 재실행 → 그 check가 `fail/warn → pass` 전환 확인 | closure 판정 | **닫히지 않으면 BLOCKED**(수정이 finding을 못 닫음) |
| **review** | 변경 성격별 조건부(§4) | 리뷰 verdict(들) | major 발견 시 escalation 플래그 |
| **PR** | 요약 + 스코어카드 delta + escalation 플래그로 PR 오픈 | PR URL | — |

**re-audit가 루프의 심장:** 주관적 "고친 것 같다"가 아니라, 감사 에이전트가 자기가 잡은 finding의 객체적 pass 전환을 재검증. 이 지점에서 자가 감사·자가 개선 루프가 닫힌다.

## 4. review stage — 조건부 리뷰어 선택
변경 성격으로 분기(과투자 회피):
- **테스트·문서만 변경** → build 그린 + re-audit pass 로 충분(추가 리뷰어 불필요).
- **공개 추상화(port/SPI/DTO/autoconfig) 변경** → abstraction-critic.
- **resilient-client 변경** → resilience-reviewer.
- 어느 리뷰어든 major → PR에 escalation 플래그(2a는 사람이 머지하므로 차단 아닌 가시화).

## 5. escalation (PR에 플래그할 "중요")
- re-audit가 닫히지 않음(BLOCKED) / 리뷰어 major / 직전 스코어카드 대비 회귀 / implement가 설계 갈림 보고.
- Phase 2a: PR 본문 상단 + (옵션) Notion 승격. 사람이 머지 결정.

## 6. 오케스트레이션
- **Workflow `scriptPath` 직접 실행**(세션 등록 우회 — Phase 1 학습). `.claude/workflows/platform-fix-item.js`.
- worktree 격리 = Workflow `isolation:'worktree'` (implement가 파일 변경하므로 필요).
- 단위 파이프라인이라 1 worktree·순차 stage. 2b가 이를 `pipeline()`/병렬로 fan-out.
- re-audit는 auditor 재호출 — Phase 1 제약과 동일하게 criteria 인라인 또는 등록된 세션에서 agentType.

## 7. Phase 2a 파일럿 항목
스코어카드 escalation 중 가장 깨끗한 mechanical: **platform-redis `context-runner-test` (major fail)** — `ApplicationContextRunner` 슬라이스 테스트 부재. direction 명확(RedissonClient 빈 부재 backs-off / 존재 등록 / FilteredClassLoader 가드). 설계 갈림 없음, TDD 검증 가능, re-audit로 Silver→Gold 객체 확인 가능.

## 8. 비목표 (Phase 2a)
- 병렬 fan-out (2b) / 자동 머지 (2b) / 게이트키퍼 분류기 (2b).
- 설계-fork 항목 자동 처리 (영구히 사람).
- 항목 자동 선별(스코어카드→큐) (2b).

## 9. 검증 (이 파이프라인이 제대로 동작하는가)
- redis 파일럿 end-to-end 실행 → PR 생성까지. 
- **closure 진위 확인**: re-audit가 실제로 pass 전환을 보고하는가 + 사람이 PR 디프를 봐서 테스트가 진짜 슬라이스 시나리오(backs-off/FilteredClassLoader)를 검증하는가(가짜 green 방지).
- BLOCKED 경로 검증: 일부러 닫히지 않을 finding을 줘서 re-audit가 BLOCKED 내는지(후속, 선택).

## 10. 설계 근거
- 2a를 단위 파이프라인으로 = 2b 병렬 stage 재사용. build-out 규율(한 건 제대로 후 확장).
- re-audit closure = 감사·수정 루프의 객체적 닫힘. 자가 개선의 정의.
- 게이트키퍼 DEFER = 사람이 항목 고르는 2a엔 불필요(YAGNI), 분류 신뢰는 2b에서 known-good 검증.
- PR까지만 자율 = 자동 머지 위험을 게이트키퍼 검증 뒤로 미룸.
