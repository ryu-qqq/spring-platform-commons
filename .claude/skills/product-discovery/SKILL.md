---
name: product-discovery
description: 사용자가 사내 공통 SDK에 신규 기능/개선을 제안하거나 "이런 기능 만들고 싶은데", "X 추가하면 좋을 것 같다", "어떤 옵션이 있나", "사례 조사부터", "결정 도와줘", "ADR 써줘" 같이 *구현 전 단계*(요건·조사·옵션 비교·결정) 작업을 요청할 때 자동 발화. 4단계 라인업(PO → researcher → adr-writer → arbiter)을 순차로 돌려 결정 문서를 만든다. 코드 구현은 별도 skill(sdk-feature-dev) 영역.
allowed-tools: Task, Read, Grep, Glob, WebSearch, WebFetch
---

# product-discovery

신규 기능·개선 요청을 *결정 문서*(ADR with decision)까지 발전시키는 라이프사이클 묶음. 코드 구현 전 단계 전부 — 요건 정의, 사례 조사, 옵션 비교, 최종 결정. 한 단계 산출물이 다음 단계 입력.

## 발화 즉시 절차

사용자 요청을 1줄 문제 설명으로 정리한 뒤, 4단계 순차 위임. 각 단계는 *직전 단계 산출물 전체를 다음 단계 prompt에 그대로 전달* 한다.

### 1단계: sdk-product-owner (요건 정의)
Task tool 위임. prompt:
- "사용자 요청 원문: <원문>"
- "산출물: 페르소나 / 문제 정의(정량) / 사용자 시나리오 3개(Given-When-Then) / 수용 기준 / 비목표 / 성공 지표"

산출물을 `<PO output>` 변수로 보관.

### 2단계: tech-researcher (사례 조사)
Task tool 위임. prompt:
- "문제 정의:" + `<PO output>` 중 문제 정의·비목표 섹션
- "조사 범위: 공식 docs, OSS 동급 라이브러리 사례 3개 이상, 컨퍼런스/블로그 1차 출처"
- "산출물: 발견된 패턴 카탈로그(N개 접근별 핵심·트레이드오프·실 사용 데이터) + 출처 URL 모두 인용"
- "결정 X(다음 단계)"

산출물을 `<research output>` 으로 보관.

### 3단계: adr-writer (옵션 매트릭스)
Task tool 위임. prompt:
- "Context:" + `<PO output>`
- "조사 자료:" + `<research output>`
- "산출물: ADR 형식 — Context / Considered Options(최소 3개, do-nothing 포함) / Trade-off Matrix(평가축: 호환성·성능·운영비용·구현난이도·소비팀 학습곡선·테스트가능성·관측성) / Consequences per option / Open Questions / Decision Drivers(가중치 사유)"
- "결정 X(다음 단계)"

산출물을 `<adr output>` 으로 보관.

### 4단계: tech-arbiter (최종 결정)
Task tool 위임. prompt:
- "ADR 초안:" + `<adr output>`
- "산출물: Decision(한 줄 + 채택 옵션) / Rationale(3-5줄, 가중치 사유) / Rejected Options(각각 거절 사유) / Consequences accepted / Reconsideration Triggers(측정 가능) / Status"
- "Status는 Accepted 로 설정"

## 출력 형식

4단계 산출물을 그대로 이어 붙이되, 마지막에 1줄 요약을 덧붙인다:

```
# Product Discovery — <기능 한 줄>

## 1. PO 산출물
<sdk-product-owner output 전체>

## 2. 조사 산출물
<tech-researcher output 전체>

## 3. ADR 초안 (옵션 매트릭스)
<adr-writer output 전체>

## 4. 최종 결정
<tech-arbiter output 전체>

---
## ✅ 발의 → 결정 한 줄 요약
- 문제: <PO 한 줄>
- 채택: <arbiter 채택 옵션>
- 거절: <arbiter 거절 옵션 + 핵심 사유 1개>
- 다음 단계: sdk-feature-dev skill 로 구현 진행 권장
```

## 규칙

- **순차 위임**. 4단계 병렬 금지(다음 단계는 이전 산출물 필요).
- 직전 산출물을 *요약하지 않고* 그대로 다음 단계에 전달. 요약은 정보 손실.
- 단계 중간에 사용자가 끼어들면 그 단계만 재실행. 처음부터 다시 X.
- arbiter가 reconsideration trigger를 측정 불가능하게 작성하면 한 번만 재위임("측정 가능한 조건으로 다시 적어주세요").
- 코드 구현은 절대 하지 않는다. 결정 문서까지가 이 skill의 끝.
