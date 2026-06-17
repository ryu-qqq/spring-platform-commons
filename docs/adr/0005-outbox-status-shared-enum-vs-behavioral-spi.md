# 0005. Outbox 상태 — 공유 enum(common-domain) vs 행위 SPI(불리언 술어)

- 상태: Accepted
- 날짜: 2026-06-17

## 맥락 (Context)

platform-outbox 릴레이 SDK가 outbox **처리상태**를 소비측과 어떻게 계약하느냐의 결정이다.

**현행:**

- `platform-common-domain`에 **행위 없는 4값 enum** `OutboxStatus{PENDING, PROCESSING, SENT, FAILED}`가 있다.
- SPI `PerItemOutboxAdapter<O, T, P>`가 소비측에 `OutboxStatus outboxStatus(O)`를 요구한다(`PerItemOutboxAdapter.java:25`).
- 그러나 SDK가 이 enum을 **실제로 묻는 유일 지점**은 `PerItemOutboxRelayTemplate.java:154`의 `== OutboxStatus.FAILED` 하나뿐이다 — deferRetry 후 *종착 실패*를 감지하는 용도. 즉 SDK가 status에서 끌어내는 정보는 사실상 **불리언 1개**다.

이 SPI는 본래 **구조적 타이핑**을 쓴다 — 소비측이 엔티티 `O`를 소유하고, 함수로 필요한 필드를 추출해 SDK에 넘긴다. 그런데 status만 `platform-common-domain`의 공유 enum 타입을 강요하는 **예외점**이 되어 있다.

**변경 창:** pre-1.0(ADR-0004가 minor breaking 허용)이고, 레포 내 어댑터 구현 0개 · 외부 adopter 0이다 — 변경 비용이 **가장 낮은 창**이다. 이 레포 platform-archrules엔 outbox 네이밍 규칙이 아직 없다.

**식별된 4개 결함:**

- **D1** 인프라성 status 타입이 *도메인* 레이어(`platform-common-domain`)에 배치됨 — 레이어 정합 위반.
- **D2** SDK의 빈약(behavior-less) enum vs 컨벤션상 도메인별 `<Domain>OutboxStatus` — **두 어휘가 경쟁**한다.
- **D3** ISP · raw 비교 위반 — 컨벤션은 status에 전이 메서드만 두고 raw `==` 비교를 금지하는데, RelayTemplate:154가 raw `==`를 쓴다.
- **D4** 값 이름 드리프트 — SDK는 `SENT`, 컨벤션은 `COMPLETED`.

## 결정 동인 (Decision Drivers)

1. **두 어휘 경쟁 해소** — SDK enum vs 도메인별 `<Domain>OutboxStatus`의 충돌을 정리.
2. **SDK가 실제 필요로 하는 최소 계약(ISP)** — SDK는 불리언 1개만 묻는데 4값 enum 전체를 강요하지 않는다.
3. **레이어 정합** — "메커니즘만 공유, 비즈니스는 각자" — 인프라 메커니즘과 도메인 어휘의 분리.
4. **churn / lock-in 비용 vs 변경 창** — 소비자 0인 지금 vs 첫 dogfood·외부 adopter 후의 breaking 비용.
5. **컨벤션 ArchUnit 게이트와의 충돌** — 컨벤션은 도메인-prefix를 요구하는 simple-name 게이트가 있어, SDK의 단일 `OutboxStatus`가 구조적으로 이를 통과 불가.

## 검토한 옵션 (Considered Options)

### 옵션 A — 행위 SPI(불리언 술어), 공유 enum 제거

- **메커니즘:** `platform-common-domain`의 `OutboxStatus`를 삭제. SPI를 `OutboxStatus outboxStatus(O)` → `boolean isTerminalFailure(O)`로 교체. RelayTemplate:154가 `adapter.isTerminalFailure(outbox)`로 묻는다. status 어휘 자체는 **도메인별 소유**(SDK는 enum을 모름).
- **장점:** SDK가 실제 필요로 하는 최소 계약만 노출(ISP). 구조적 타이핑 일관성 회복(status도 함수 추출). D1~D4 **모두 해소**.
- **비용:** SPI 시그니처 breaking(단 소비자 0 → 실질 ≈ 0). adoption-gap의 "common-domain 입주분류" · ADR-001의 churn 경계와 *표면상* 충돌하나, 전제 차이(여기선 enum이 **빈약**해 입주 정당성이 약함)로 반박 가능.
- **선례 정합:** ADR-001(shared-kernel 추출 기각)과 방향 일치 — 공유를 줄이고 각 도메인 소유로.

### 옵션 B — 공유 enum 유지 + 컨벤션 정합화

- **메커니즘:** `OutboxStatus`에 전이 메서드(`isTerminal()` 등) 추가, `SENT` → `COMPLETED` rename, RelayTemplate의 raw `==` 제거. enum은 `platform-common-domain`에 그대로 둔다.
- **장점:** D3 **부분 해소** · D4 **해소**. adoption-gap · ADR-001과 충돌 없음.
- **비용:** **D1 잔존(오히려 짙어짐)** — 행위까지 얹으면서 인프라 타입의 도메인-레이어 고착이 강화. **D2 잔존(구조적)** — 단일 simple-name `OutboxStatus`는 컨벤션의 도메인-prefix ArchUnit 게이트를 구조적으로 통과 불가.
- **선례 정합:** churn 측면에선 ADR-001과 정합. 단 레이어·어휘 결함은 못 닫음.

### 옵션 C — 현행 유지 + 문서화만

- **메커니즘:** 코드 0변경, 의미 규약(SDK가 status에 기대하는 것)만 문서로 명시.
- **장점:** churn 0(ADR-001 최대 정합).
- **비용:** D1~D4 **전부 잔존** + 변경 창 낭비 — 첫 dogfood · 외부 adopter 시점에 breaking 비용이 급등한다.
- **선례 정합:** churn 회피로만 정합, 결함은 하나도 못 닫음.

### 결함 해소 매트릭스

| 결함 | A | B | C |
| --- | --- | --- | --- |
| D1 인프라 타입 도메인 배치 | 해소 | 잔존(짙어짐) | 잔존 |
| D2 두 어휘 경쟁 | 해소 | 잔존(구조적) | 잔존 |
| D3 ISP · raw 비교 | 해소 | 부분 | 잔존 |
| D4 값 이름 드리프트 | 해소 | 해소 | 잔존 |

## 결정 (Decision Outcome)

**옵션 A — 공유 enum 제거 + 행위 SPI(불리언 술어).**

**구체:**

- `platform-common-domain`의 `OutboxStatus` enum을 **삭제**한다.
- SPI `PerItemOutboxAdapter`의 `OutboxStatus outboxStatus(O)`를 `boolean isTerminalFailure(O)`로 교체한다.
- `PerItemOutboxRelayTemplate.java:154`의 `adapter.outboxStatus(outbox) == OutboxStatus.FAILED`를 `adapter.isTerminalFailure(outbox)`로 교체한다.
- outbox status 어휘(`<Domain>OutboxStatus`, 값 집합, 전이 의미)는 **소비측 도메인이 소유**한다 — SDK는 status를 모른다.

**선택 근거(왜 A):**

1. 소비자 0 · pre-1.0 = breaking 비용이 최저인 **유일한 창**.
2. SDK가 status에서 실제 묻는 건 "종착 실패?" 불리언 **1개뿐**(ISP).
3. 팀 컨벤션(outbox-family)이 이미 outbox status를 도메인별 `<Domain>OutboxStatus`로 소유하도록 규정 → A가 그 컨벤션과 정합.

→ D1~D4 4개 결함 **모두 해소**.

**선례·기준과의 표면 충돌 해소(전제 차이로 명시):**

- 본 enum은 게이트 없음 · 소비자 0 · 전이 메서드조차 없는 **빈약 타입**이라 lock-in이 형성되지 않는다 → ADR-001(표준화 · 게이트화 · 소비 중 안정 계약)의 churn 경계가 **적용되지 않는다**.
- adoption-gap의 "순수 타입 = domain 입주" 기준은 "관심사가 **인프라/메커니즘이면 재분류**"로 갱신한다.

## 결과 (Consequences)

**긍정:**

- 구조적 타이핑 일관성 회복 — status도 함수 추출로 통일.
- SDK 계약이 실제 필요와 일치(ISP).
- 소비측이 자기 `<Domain>OutboxStatus` 하나만 소유 — 두 어휘 경쟁 · `SENT`/`COMPLETED` 드리프트 소멸.
- 인프라 관심사가 도메인 레이어에서 제거.

**비용/후속(구현 작업):**

1. `PerItemOutboxAdapter` 시그니처 변경(`outboxStatus` → `isTerminalFailure`).
2. `PerItemOutboxRelayTemplate.java:154` 호출부 교체.
3. `OutboxStatus.java` + 그 `package-info` + `OutboxStatusTest` 삭제.
4. outbox 관련 테스트(`PerItemOutboxRelayTemplateTest` 등)에서 `OutboxStatus` 사용 제거.
5. README/CHANGELOG 갱신 — common-domain README의 `OutboxStatus` 항목 · CHANGELOG breaking 기재.
6. 권장 4상태 모델(`PENDING → PROCESSING → COMPLETED | FAILED`)은 **문서로 안내**.

**비목표:**

- 소비측 도메인의 status 모델을 SDK가 강제하지 않음(컨벤션 outbox-family가 소유).
- dead-letter 별도 상태 도입은 범위 밖(YAGNI).

**후속 가드:**

- common-domain 입주 기준("인프라 메커니즘 관심사는 도메인 커널에 두지 않는다")을 향후 fitness/리뷰 게이트로 검토(별도).

## 관련

- ADR-0003(드리프트 표준 수렴) · ADR-0004(버저닝/breaking 정책 — pre-1.0 minor breaking 허용)
- vault: `outbox-family.md`(도메인별 status 컨벤션) · `adoption-gap.md`(common-domain 입주 기준) · ADR-001(shared-kernel 추출 기각 선례)
- 코드: `platform-common-domain` `OutboxStatus.java` · `platform-outbox` `PerItemOutboxAdapter.java:25` · `PerItemOutboxRelayTemplate.java:154`
