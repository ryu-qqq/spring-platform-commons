# 0006. common-domain을 순수 도메인 커널로 — 횡단 인프라 어휘 분리(platform-observability) + Versioned 읽기전용화

- 상태: Accepted
- 날짜: 2026-06-17

## 맥락 (Context)

ADR-0005(common-domain의 `OutboxStatus` 제거)의 **후속**이다. 같은 렌즈(인프라 메커니즘이 도메인 커널에 무임승차했는가)로 `platform-common-domain`의 잔여 타입을 점검한 결과, 이 모듈이 **두 역할을 겸하고** 있었다:

1. **순수 도메인 커널** — 페이징/쿼리 VO · soft-delete · 키 마커(CacheKey · LockKey) · `ErrorCode` · `DomainException`.
2. **범용 횡단 인프라 SSOT** — `MdcKeys` · `Versioned`.

②는 "모두가 import하는 최저 모듈"이라는 이유로 도메인 커널에 얹혀 있다 — `OutboxStatus`와 **동일한 레이어-회피 패턴**이다.

**두 타입의 실측:**

- **`MdcKeys`** — MDC 키 + HTTP 헤더 이름 = 순수 로깅/웹 인프라 어휘. 소비처 실측은 platform-web · platform-security · platform-scheduler뿐이다(전부 바깥 레이어, 도메인/애플리케이션 소비 0). **도메인 개념이 아니다.**
- **`Versioned`** — 낙관적 락 version 계약. `version()` getter에 더해 `refreshVersion(long)` **세터**가 있는데, 이는 flush 후 영속성이 도메인 객체에 버전을 도로 밀어넣는 용도 = **persistence-reconciliation-shaped wart**이다. 다만 aggregate 레벨 낙관적 동시성은 도메인 패턴으로 인정되고, version 의미가 균일(commonality 통과)이라 `OutboxStatus`보다 방어 가능하다.

**vault 5계층 분류**(Independent → Common → Domain → Internal → Application) 기준으로 `MdcKeys`는 Domain이 아니라 **Independent 층** 자리다. 모듈명이 `...-domain`인데 인프라 어휘를 담는 **이름-내용 레이어 불일치**가 있다.

**변경 창:** pre-1.0 · 소비자 0 = 변경 비용이 가장 낮은 창(ADR-0004가 minor breaking 허용).

## 결정 동인 (Decision Drivers)

1. **공통 모듈의 정확성** — 이 SDK는 모두가 의존하는 핵심이라 레이어 오배치 비용이 크다.
2. **"common 모듈 = 자석" 회피** — 응집 원칙 없는 모듈은 잡동사니화한다.
3. **레이어 정합** — 도메인 커널에는 도메인 타입만 둔다.
4. **소비자 0 · pre-1.0** — 변경 창 비용이 최저.
5. **새 모듈 이름은 역할로** — core/common/util 금지(다음 자석을 미리 막는다).

## 검토한 옵션 (Considered Options)

### MdcKeys 위치

- **A) `platform-observability` 신설(채택)** — 의존성 0의 최저 모듈, 응집 원칙 명확(관측성/트레이스 어휘 SSOT, 미래 메트릭/태그 규약 수용). web · security · scheduler가 의존한다. 패키지 `com.ryuqqq.platform.observability`.
- B) `platform-commons-core` 같은 범용 foundational 모듈 — 유연하나 응집 원칙이 없어 **재자석화 위험**. 기각.
- C) common-domain 유지 + 문서화 — churn 0이나 레이어 어긋남이 잔존. 기각.

### Versioned 처리

- **A) 도메인 유지 + 읽기전용화(채택)** — `version()`만 남기고 `refreshVersion(long)`을 제거. 버전 반영은 영속성 매퍼가 재구성 시 주입한다. aggregate 낙관적 동시성은 도메인 패턴으로 인정하고, infra-향 세터(wart)만 제거.
- B) 커널에서 제거(영속성 전담) — `BaseVersionedEntity`가 이미 처리한다. 단 aggregate 레벨 동시성 노출을 포기. 기각(여지 보존).
- C) 현행 유지 — persistence-향 세터가 도메인 계약에 잔존. 기각.

## 결정 (Decision Outcome)

- **MdcKeys → 신규 `platform-observability` 모듈**(패키지 `com.ryuqqq.platform.observability`, 의존성 0). common-domain의 observability 패키지를 제거한다. web · security · scheduler가 새 모듈을 의존한다.
- **Versioned → 읽기전용화** — `refreshVersion(long)`을 제거하고 `version()`만 남긴다. common-domain `domain` 패키지에 유지한다.
- **common-domain 입주 기준 확립** — 순수 도메인 타입/계약만 둔다. 횡단 인프라 어휘(로깅 · 헤더 · 메트릭)는 도메인 커널에 금지하며, 향후 fitness 게이트 후보로 둔다.

## 결과 (Consequences)

**긍정:**

- common-domain이 **순수 도메인 커널로 수렴**한다.
- 인프라 어휘가 레이어에 맞는 집(`platform-observability`)을 가진다.
- 응집 원칙 있는 모듈명으로 **재자석화를 방지**한다.
- Versioned의 persistence-향 세터(wart)를 제거한다.

**비용/후속(구현 작업):**

1. `platform-observability` 모듈 신설(settings/build.gradle · README).
2. `MdcKeys` + 테스트 이동, 패키지 변경 `common.observability` → `observability`.
3. web · security · scheduler import 교체 + 의존 추가.
4. `Versioned`의 `refreshVersion` 제거 + `CommonVoTest` 갱신.
5. persistence-jpa `BaseVersionedEntity` javadoc 점검.
6. README 3종 · CHANGELOG 갱신.
7. ArchUnit에 observability framework-free 게이트 추가.

**비목표:**

- 페이징/키 마커(CacheKey · LockKey) 재배치 — adoption-gap 선례로 도메인 정당.
- 새 모듈의 메트릭/태그 규약 확장 — 별도 작업.

**후속 가드:**

- common-domain 입주 기준을 ArchUnit/리뷰 게이트로 명문화(별도 작업).

## 관련

- ADR-0005(`OutboxStatus` 제거 — 같은 렌즈의 선행) · ADR-0003(드리프트 표준 수렴) · ADR-0004(버저닝/breaking 정책)
- vault: `platform-team-taxonomy`(5계층 · 공통 모듈 지옥) · `adoption-gap`(common-domain 입주 기준)
- 코드: `MdcKeys.java` · `Versioned.java` · `RequestContextFilter` · `GlobalExceptionHandler` · `ServiceTokenProblemDetailWriter` · `SchedulerLoggingAspect`
