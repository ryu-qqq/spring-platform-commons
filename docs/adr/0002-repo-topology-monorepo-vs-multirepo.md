# 0002. 백엔드 서비스군 repo 토폴로지 — monorepo(활성 셋) vs 멀티레포 유지 + 공통화 발행 정비

- 상태: Accepted
- 날짜: 2026-06-07

## 맥락 (Context)

5개 헥사고날 Spring 서비스가 운영 중이다 — MarketPlace(코어·트래픽 허브), Gateway(reactive 엣지),
AuthHub(인증인가·소비자 2개: Gateway·MarketPlace), FileFlow(파일/이미지·무거운 버스트 워커),
CrawlingHub(동결). 서비스 triage는 완료됐고, **분리 자체는 정당하다**는 결론이 났다. 따라서 이 ADR은
서비스를 하나의 monolith로 합치는 결정이 아니다 — 어느 옵션을 택하든 **서비스별 독립 deployable은 유지**된다.
이 ADR이 다루는 것은 오직 **소스 repo 토폴로지와 공통 코드 공유 메커니즘**이다.

핵심 통증은 **입양갭(adoption gap)** 이다. `spring-platform-commons`가 platform-web / persistence-jpa /
common-domain / bootstrap을 잘 추출했으나 사실상 아무도 안 쓴다(Share만 되고 Apply가 비어있음).
`resilient-client` starter만 5개 중 3개가 소비한다. 미입양 원인은 두 갈래다:

- **포장 마찰** — web/jpa/domain은 `-spring-boot-starter` 포장이 없어 수동 배선(`@Import`, `@ComponentScan`)이
  필요하다. 반면 zero-config starter인 `resilient-client`만 입양됐다. (활성화 방식도 비일관:
  web `@ComponentScan` vs jpa `@AutoConfiguration`)
- **드리프트** — 추출 후 입양이 지연되는 사이 복붙본이 갈라졌다. AuthHub의 `success` 필드·timestamp 포맷,
  CrawlingHub의 `LocalDateTime`(platform은 `Instant` — 타입 불일치=마이그레이션 비용), soft-delete 모델 불일치
  (서버 다수는 `deletedAt`만), Gateway reactive와 servlet 기반 platform-web의 **구조적 비호환**.

근거: `ryu-qqq-wiki/wiki/projects/spring-platform-commons/adoption-gap.md`

### 조사로 검증된 코드/CI 사실

- **빌드 표준화 인프라 전무** — build-logic / buildSrc / convention plugin이 없다. 빌드 설정이
  `subprojects {}` 인라인 복붙으로 흩어져 있다.
- **발행 메커니즘** — JitPack 발행 + 레포별 `libs.versions.toml` 버전 고정. catalog 드리프트를 수동으로 동기화한다.
- **CI 비일관** — MarketPlace·Gateway·FileFlow는 중앙 `Infrastructure/reusable-ci.yml`을 쓰지만,
  AuthHub·platform-commons는 인라인 CI다.
- **monorepo CI 패턴은 이미 검증됨** — MarketPlace `deploy.yml`이 이미 `dorny/paths-filter`로
  5개 bootstrap을 affected-only 배포한다. 즉 monorepo의 affected-only CI 패턴 자체는 사내에서 검증된 상태다.
- **모듈 경로 충돌 위험** — 5개 레포가 동일 모듈 경로(`:domain` 등)를 쓴다. 단일 settings로 통합하면 충돌 →
  네임스페이싱 또는 `includeBuild`가 필요하다.
- **좌표 분산** — group 좌표가 갈려 있다(`com.ryuqqq.marketplace` vs `com.ryuqqq.platform`).

## 결정 동인 (Decision Drivers)

- **입양갭 해소** — web/jpa/domain이 실제로 소비되게 만드는 것이 1차 목표.
- **드리프트 차단** — 갈라짐을 구조적으로 막을지(단일 소스) enforce(ArchUnit·SemVer)로 막을지.
- **리팩터 원자성** — cross-cutting 변경을 1 PR로 끝낼 수 있는가 vs N PR.
- **blast radius** — 루트/공통 변경이 몇 개 서비스에 파급되는가.
- **되돌리기 가역성** — 결정을 무를 수 있는가(비가역 vs 점진·가역).
- **CI 작업량** — 1회 재설계 비용 vs 상시 발행 사이클 비용.
- **1인 운영 적합성** — 단독 운영자가 견딜 수 있는 운영 부하 형태.
- **Gateway(reactive) 경계** — servlet 기반 platform-web과의 비호환은 어느 옵션에서도 도메인 모듈 공유로만 풀린다.

## 검토한 옵션 (Considered Options)

### 옵션 A — monorepo (활성 셋)

**정의:** MarketPlace·FileFlow·AuthHub·Gateway·platform-commons·platform-archrules를 한 레포에 둔다.
서비스별 독립 배포는 유지하고, 공통 코드는 `includeBuild`/project 의존으로 당겨쓴다. CrawlingHub는
동결 상태로 별도 레포에 잔류한다.

**장점**
- 입양갭이 즉시 증발한다 — 발행·버전·JitPack 마찰이 0(project 의존).
- 드리프트를 구조적으로 차단한다 — 단일 소스라 갈라질 여지가 없다.
- 리팩터 원자성 — cross-cutting 변경을 1 PR로 처리.
- MarketPlace의 `paths-filter` affected-only 배포 패턴을 재사용할 수 있다(이미 검증됨).
- 1인 운영에 적합 — 발행 사이클 운영 부하가 사라진다.
- ArchUnit 규칙을 project 의존으로 공유.

**단점**
- settings 통합 시 모듈 경로 충돌(동일 `:domain` 등) → 대규모 재배치 필요.
- CI 전면 재설계가 필요하다.
- blast radius 증가 — 루트 변경이 4개 서비스에 파급될 수 있다.
- 되돌리기가 거의 비가역에 가깝다.
- 빌드 그래프·IDE 부하 증가.
- Gateway reactive 비호환은 풀리지 않는다(도메인 모듈만 공유 가능).

### 옵션 B — 멀티레포 유지 + 공통화 발행 정비

**정의:** 5개 레포 분리를 유지한다. platform-commons의 web/jpa/domain을 `-spring-boot-starter`로 포장하고,
platform-archrules를 발행하며, version catalog 규율을 세워 입양갭을 해결한다.

**장점**
- 현행 토폴로지·CI를 보존한다(AuthHub만 reusable CI로 전환).
- blast radius가 레포 경계로 격리된다 — SemVer 게이트로 파급 통제.
- 되돌리기가 가역·점진적이다.
- Netflix Nebula 모델의 정공법(Share → Apply → Enforce).
- Gateway는 도메인 모듈만 발행 소비하면 된다.

**단점**
- 발행 마찰이 상존한다 — 태그 → JitPack → 4개 catalog 갱신 → N PR. 1인 운영에 비용.
- 드리프트 재발 위험 — 소비측이 버전을 안 올리면 갈라진다("발행했으나 입양 0"이 이미 발생함).
- catalog 수동 동기화 부담.
- ArchUnit 발행 재구성이 필요하다.
- starter화 작업량 — 활성화 비일관(web `@ComponentScan` vs jpa `@AutoConfiguration`) 정리 포함.

### 트레이드오프 요약

| 축 | A monorepo | B 멀티레포 + 발행 |
|---|---|---|
| 입양 마찰 | 0 (project 의존) | 높음 (발행 사이클) |
| 드리프트 방어 | 구조적 차단 (단일 소스) | enforce 의존 (SemVer·ArchUnit) |
| 리팩터 원자성 | 1 PR | N PR |
| CI 작업량 | 큼 (전면 재설계) | 작음 (현행 보존) |
| blast radius | 큼 (루트=4서비스) | 작음 (레포=경계) |
| 되돌리기 | 높음 (비가역) | 낮음 (가역) |
| 1인 운영 | 적합 | 발행 마찰 비용 |
| Gateway reactive | 안 풀림 (도메인만 공유) | 안 풀림 (도메인만 발행 소비) |

## 결정 (Decision Outcome)

**A(monorepo) vs B(멀티레포)를 지금 확정하지 않는다.** 대신 A·B 어느 쪽으로 가도
손해 없는 **무후회(no-regret) 단계 경로**를 채택하고, 비가역 토폴로지 결정(서비스 흡수)은
**실측 데이터 뒤로 미룬다(defer).**

> 이 ADR이 확정하는 것은 **"단계적 무후회 경로 + A/B 결정 defer"** 다.
> **A/B 토폴로지 자체는 아래 3단계에서 별도 후속 ADR로 확정**된다 (이 ADR의 후속).

채택하는 단계는 다음과 같다.

- **0단계 (무후회 · A·B 공통)** — 입양갭의 실제 원인을 토폴로지와 무관하게 즉시 공략한다.
  1. **드리프트 표준 수렴 결정** — `Instant` vs `LocalDateTime` 통일, `ApiResponse`의 `success`
     필드 유무, soft-delete 모델 통일. *(표준값 자체는 후속 ADR로 분리 — 아래 후속 섹션 참조.)*
  2. **starter 포장** — platform-commons web/jpa/domain을 `-spring-boot-starter`로 포장하고
     활성화 방식을 일관화한다(web `@ComponentScan` → `@AutoConfiguration`).
- **1단계 (부분 monorepo · 가역)** — platform-commons + platform-archrules만 한 레포로 통합한다.
  (발행 단위이자 소비자 다수, 비즈니스 로직 없음 → 통합 리스크 최소.)
- **2단계 (검증 게이트)** — 입양 리스크 0인 platform-common-domain 소비처 1개(예: **AuthHub**)를
  `includeBuild` composite로 platform과 연결해, project 의존의 **"마찰 0" 효용을 실측**한다.
- **3단계 (분기 — 이때 A/B 확정)** — 2단계 실측으로 분기한다.
  - 효용이 크면 → 나머지 서비스를 흡수해 **A 완성**.
  - blast radius·CI 부하가 크면 → 1단계에서 멈추고 **B(발행 정비)로 확정**.
  - **비가역 결정은 반드시 2단계 실측 뒤로 미룬다.**

## 결과 (Consequences)

**긍정**

- **가역성 보존** — A·B 어느 쪽으로도 갈 수 있는 옵션을 닫지 않는다.
- **즉시 공략** — 입양갭의 실제 원인(starter 포장 마찰·드리프트)을 토폴로지 결정과
  **무관하게 즉시** 친다.
- **1인 운영에 맞는 점진** — 비가역 결정을 데이터 뒤로 미룬다("Exception을 Question으로").
- **검증된 디딤돌 존재** — MarketPlace `deploy.yml`의 `paths-filter` affected-only 패턴이
  3단계 A 전환의 검증된 디딤돌로 이미 존재한다.

**비용 / 위험**

- **최종 결정 지연** — A/B 확정이 미뤄져 불확실성이 잔존한다. *언제까지 1단계에 머무를지의
  기준이 필요하다* (예: 2단계 실측 완료 시점).
- **0단계는 공짜가 아니다** — starter화·드리프트 수렴은 실제 작업이 필요하다.
- **드리프트 표준 수렴은 별도 결정거리** — `ApiResponse`의 `success` / 시간 타입 / soft-delete
  모델은 그 자체로 결정을 요한다 → **후속 ADR 또는 결정 분리**로 다룬다.
- **Gateway reactive 비호환은 별도 해결** — 토폴로지와 무관하게 도메인 모듈만 공유하는 방식으로
  푼다(servlet 기반 platform-web 비의존).

**비목표(non-goals · 유지)**

- 서비스를 monolith로 합치지 않는다 — 어느 옵션이든 서비스별 독립 deployable을 유지한다.
- CrawlingHub를 부활시키지 않는다 — 동결 상태를 유지한다.

## 후속 (Follow-up)

- **드리프트 표준 수렴 ADR (별도)** — `Instant` 통일 / `ApiResponse`의 `success` 필드 유무 /
  soft-delete 모델. *0단계의 선행 결정거리로, 표준값 자체는 이 ADR에서 확정하지 않는다.*
- **A/B 확정 ADR (이 ADR의 후속)** — 3단계 도달 시(2단계 실측 완료 후) A 완성 / B 확정을 가른다.

## 이행 경로 (Migration Path)

토폴로지 결정 자체가 비가역(A)일 수 있으므로, **무후회 단계를 먼저 끝내고 실측 게이트 뒤로 비가역 결정을 미룬다.**

- **0단계 (무후회 · A·B 공통)** — 드리프트 표준을 결정한다(`Instant`·`ApiResponse`·soft-delete).
  이어 web/jpa/domain을 starter화한다. *이 단계는 어느 토폴로지를 택해도 필요하므로 결정 전 선행 권장.*
- **1단계 (부분 monorepo)** — platform-commons + platform-archrules만 한 레포로 통합한다.
  (발행 단위이자 소비자 다수, 비즈니스 로직 없음 → 통합 리스크 최소.)
- **2단계 (검증 게이트)** — 1개 서비스(예: AuthHub)를 `includeBuild` composite로 시범 연결해
  project 의존의 "마찰 0" 효용을 실측한다.
- **3단계 (분기)** — 2단계 실측 결과로 분기한다.
  효용이 크면 나머지를 흡수해 A를 완성. blast radius·CI 부하가 크면 1단계에서 멈추고 B를 확정.
  **비가역 결정은 반드시 2단계 실측 뒤로 미룬다.**

## 열린 질문 (Open Questions)

1. CI 재작성 1회 비용(A) vs 발행 사이클 상시 비용(B) — 1인 운영에서 어느 쪽이 견딜 만한가?
2. cross-cutting 변경 빈도 — 잦다면 A의 원자성 가치가 올라간다.
3. 되돌리기 가역성을 얼마나 중시하는가 — monorepo는 거의 비가역.
4. Gateway(reactive)·CrawlingHub 경계 — monorepo에 넣되 platform-web 비의존으로 둘지 vs 멀티레포 잔류.
5. A의 통합 메커니즘 — `includeBuild`(가역) vs 단일 settings 흡수(깊은 통합) 중 어느 쪽인가?
6. ~~드리프트 표준 수렴(0단계)을 토폴로지 결정 *전*에 끝낼지(선행 권장).~~
   → **0단계로 채택됨** — 토폴로지 결정과 무관하게 선행한다(표준값 자체는 후속 ADR로 분리).
7. **언제까지 1단계에 머무를지의 기준** — A/B 확정 시점(3단계 진입)을 무엇으로 트리거할지.
   (현재 잠정: 2단계 `includeBuild` 실측 완료.)

## 관련 선례 (Links)

- `wiki/projects/spring-platform-commons/adoption-gap.md` — 입양갭 분석·서비스 triage
- `wiki/projects/spring-platform-commons/resilient-client.md` — JitPack 발행 유일 성공 선례
- `wiki/conventions/java-springboot-hexagonal/adr/ADR-002` ~ `ADR-005` — 모듈 분리·빈 충돌 패턴
- `raw/marketplace-htca6-outbox-archunit` — ArchUnit 빌드 게이트 선례
- `raw/우아한형제들 도메인 모듈 분리 선례`
