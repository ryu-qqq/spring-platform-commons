# 플랫폼 감사 fleet — Phase 1 설계 (자가 감사 루프)

- 일자: 2026-06-08
- 상위 프로젝트: 자가 감사·자가 개선 플랫폼 fleet ([[platform-fleet-autopilot]])
- 근거 리서치: `wiki/projects/spring-platform-commons/platform-team-taxonomy.md` (deep-research + 직접 fetch 사례)
- 범위: **Phase 1 = 감사 루프** (findings 산출까지). Phase 2(자율수정 파이프라인)는 별도 spec.

## 1. 목적

spring-platform-commons 11개 배포 모듈이 "플랫폼 품질 바"를 만족하는지 **분야별 감사 에이전트**로 점검하고, 결과를 **Soundcheck식 모듈 스코어카드**로 산출한다. 스코어카드는 Phase 2 자율수정 파이프라인의 입력(백로그)이 된다. 강제(하드 차단)가 아니라 **가시성 기반 컴플라이언스**(토스증권·Spotify Soundcheck 패턴)다.

## 2. 아키텍처

```
.claude/agents/autoconfig-auditor.md      (읽기전용 감사 에이전트)
.claude/agents/observability-auditor.md   (읽기전용 감사 에이전트)
.claude/workflows/platform-audit-sweep.js (fan-out 오케스트레이션)
docs/superpowers/audits/YYYY-MM-DD-platform-audit.md (산출 스코어카드)
```

- 감사 에이전트는 **프로젝트 로컬**(`.claude/agents/`)에만. agent-crew 중앙 레포에 만들지 않음(플랫폼 특화).
- 읽기전용(Read/Glob/Grep). 코드 수정 안 함 — 감사 전담. → worktree 불필요.
- check 기준은 taxonomy에서 박제(공식문서 출처 포함)해 "환각 감사" 방지.

## 3. 감사 에이전트

### 3.1 autoconfig-auditor (track: Paved Road)
대상 모듈: `platform-redis`, `platform-scheduler`, `platform-security`, `platform-outbox`, `platform-web`, `platform-persistence-jpa`.

| check id | 기준 | 근거(출처) |
|---|---|---|
| `imports-registered` | 자동설정 클래스가 `@AutoConfiguration` 표시 + `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`에 한 줄당 한 클래스 등록 | spring-boot/developing-auto-configuration |
| `conditional-override` | 기본 빈이 `@ConditionalOnMissingBean`으로 사용자 오버라이드 허용 | 동 |
| `context-runner-test` | `ApplicationContextRunner` 기반 테스트 존재 — backs-off(`withUserConfiguration`)·프로퍼티(`withPropertyValues`)·의존성부재(`FilteredClassLoader`+`@ConditionalOnClass`) 중 해당 검증 | 동 |
| `conditional-on-class` | optional 의존성(예: MeterRegistry)에 `@ConditionalOnClass`/`ObjectProvider` 가드 | 동 |

### 3.2 observability-auditor (track: Observability)
대상 모듈: `platform-scheduler`, `platform-outbox`, `resilient-client`, `platform-web`, `platform-security`, `platform-common-domain`.

| check id | 기준 | 근거(출처) |
|---|---|---|
| `metric-naming` | 메트릭 이름이 일관된 dot-구분 네임스페이스(예: `scheduler.job.*`) | micrometer 관례 |
| `cardinality-discipline` | userId 등 고카디널리티 값이 **메트릭 태그로 노출되지 않음**(트레이스 한정) | actuator/observability (low/highCardinalityKeyValue) |
| `trace-propagation` | SDK 제공 HTTP 클라이언트가 auto-configured builder(`RestClient.Builder` 등) 경유 — 수동 생성 시 전파 깨짐 | actuator/tracing |
| `mdc-key-consistency` | MDC 키가 `MdcKeys` SSOT 상수 사용(리터럴 산재 X) | 내부 MdcKeys(P2-3) |
| `meter-optional` | MeterRegistry 부재 시 zero-config 동작(옵셔널 주입) | actuator/observability |

> 주의: 모든 Spring 기준은 actuator+micrometer 의존성 전제. "노출 안 됨"이 항상 결함은 아님(도메인 모듈은 메트릭 비대상) — auditor는 **모듈 성격을 보고 적용 여부 판단**(N/A 허용).

### 3.3 출력 스키마 (두 에이전트 공통)
```json
{
  "module": "platform-scheduler",
  "track": "Observability",
  "checks": [
    {"id": "metric-naming", "status": "pass|warn|fail|na", "severity": "info|minor|major", "evidence": "...", "direction": "..."}
  ]
}
```

## 4. 감사 sweep 워크플로우 (`platform-audit-sweep.js`)

```
meta.phases = [{title:'Audit'}, {title:'Scorecard'}]

Phase 'Audit' (fan-out 병렬):
  TARGETS = [(module, dimension) ...]  // §3 매핑, cross-product 아님
  pipeline(TARGETS,
    t => agent(<dimension auditor 호출, module 경로+check 기준 주입>,
               {agentType: t.auditor, phase:'Audit', schema: FINDINGS_SCHEMA}))

Phase 'Scorecard' (synthesize):
  결정론적 집계: findings → 모듈별·track별 그룹, pass/total 계산
  level: Gold(100%) / Silver(>=70%) / Bronze(그 외)
  synthesis agent → docs/superpowers/audits/<date>-platform-audit.md 작성
    - 모듈 × track 스코어카드 매트릭스(레벨)
    - check별 상세(status·evidence·direction)
    - "중요"(escalation) 섹션 상단 배치
```

- **Structure(위생) track**: README 존재·단일 인프라·계층경계는 결정론적 체크(Glob `*/README.md`, archrules 결과 참조)로 Scorecard 단계에서 직접 산출 — 별도 에이전트 불필요.
- 동시성: 읽기전용이라 worktree 없음. fan-out은 워크플로우 기본 동시성 캡(min(16, cores-2)).

## 5. "중요"(escalation) 정의
다음 중 하나면 escalation 후보로 스코어카드 상단 + Notion 승격 후보:
- `severity=major`인 `fail`
- 직전 감사(`docs/superpowers/audits/` 최신 파일) 대비 회귀(pass→fail)
- auditor 간 신호 충돌

Phase 1에선 리포트 표시까지. Phase 2에서 PR단 보고 트리거가 됨.

## 6. findings 이음새 (Phase 1 → Phase 2)
- **1차 source of truth**: in-repo `docs/superpowers/audits/<date>-platform-audit.md` (기계가 읽음).
- **중요분만 Notion Engineering OS Task 승격**(사람 가시성). platform-backlog/engineering-os 스킬 재사용.

## 7. 비목표 (Phase 1)
- 자동 수정·PR 생성 — Phase 2.
- governance-auditor(archrules CI 강제 점검) — archrules와 중복, 첫 sweep 후 증거 기반 재검토.
- 버저닝/하위호환(japicmp/revapi)·보안/SBOM auditor — 리서치 미검증 갭, 별도 리서치 후 DEFER 해제.
- 판단 에이전트(사람 게이트 치환) — Phase 2 설계 사안.

## 8. 검증 (이 자산이 제대로 동작하는가)
- **known-good 파일럿**: 이미 잘 된 것으로 아는 모듈에 sweep 실행 → auditor가 거짓 fail을 내지 않는지(예: scheduler 메트릭 seam은 P2에서 정리됨 → pass 나와야). 
- **known-bad 주입**: 일부러 `@ConditionalOnMissingBean` 빠진 케이스를 보고 fail 잡는지.
- auditor `.md`는 harness-creator/agent-evaluator 기준(트리거·구조)으로 점검. 워크플로우는 작은 대상(2모듈)으로 먼저 dry-run.

## 9. 설계 근거 요약
- 4 검증 축(Spring autoconfig·Observation·fitness governance·PRR) 중 grounded·고신호 2축(autoconfig·observability)만 1차. (YAGNI)
- consolidator = Soundcheck Checks→Tracks→Levels 스코어카드(사례 검증).
- 소프트 게이트(가시성 기반) = 토스증권·Soundcheck exception 패턴과 정합.
- 모듈 위생 = 우아한 5계층(우리 구조와 동일)의 README·단일인프라 → check 항목.
