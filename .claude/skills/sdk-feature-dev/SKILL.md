---
name: sdk-feature-dev
description: product-discovery 의 결정 문서 또는 *명확한 설계 의도*가 있는 상태에서 사용자가 "구현해줘", "이 결정대로 코드 작성", "구현 들어가자", "이 기능 만들어줘", "SDK에 X 추가 구현" 같이 *실제 코드 작성*을 요청할 때 자동 발화. 5단계 라인업(designer → coder → test-writer → performance-tester → migration-writer)을 순차로 돌려 코드·테스트·측정·마이그레이션 가이드까지 산출. migration-writer는 BREAKING 변경일 때만 합류.
allowed-tools: Task, Read, Grep, Glob, Bash, Write, Edit
---

# sdk-feature-dev

결정된 기능을 사내 SDK 코드·테스트·성능 측정·마이그레이션 가이드까지 한 흐름으로 완성한다. 코드 *결정*은 이미 product-discovery 또는 사용자 지시로 끝났다고 전제 — 이 skill 안에서 설계 옵션을 다시 비교하지 않는다.

## 발화 즉시 절차

사용자 요청에서 1줄 기능 설명·전제 조건(결정된 옵션·제약)을 추출 후, 5단계 순차 위임. 직전 산출물을 다음 단계 prompt에 그대로 전달.

### 1단계: feature-designer (인터페이스 설계)
Task tool 위임. prompt:
- "기능 요청:" + 사용자 1줄 + 결정 컨텍스트(있으면 product-discovery 산출물 전체)
- "산출물: 사용자 시나리오 3개(행복/엣지/실패) / 공개 API 시그니처 / 내부 구조(ASCII 다이어그램) / 대안 1-2개 + 거절 사유 / 미해결 질문"
- "코드 구현 X, 시그니처/스케치까지"

산출물을 `<design output>` 으로 보관.

### 2단계: impl-coder (Kotlin/Java 구현)
Task tool 위임. prompt:
- "설계 산출물:" + `<design output>`
- "산출물: 인터페이스·구현 클래스 / Builder/Factory / 설정 모델 / deps.gradle 변경 / KDoc·JavaDoc / 미해결 질문"
- "테스트 X(다음 단계)"

산출물을 `<impl output>` 으로 보관.

### 3단계: test-writer (단위·통합 테스트)
Task tool 위임. prompt:
- "구현 산출물:" + `<impl output>`
- "산출물: 단위 테스트 파일 / 통합 테스트 파일 / Testcontainers 설정 / 픽스처 / 커버리지 사각지대 노트"
- "경계 조건(0/1/empty/max/null/timeout/concurrent) 우선"

산출물을 `<test output>` 으로 보관.

### 4단계: performance-tester (측정)
Task tool 위임. prompt:
- "구현 산출물:" + `<impl output>` (성능 임팩트 있는 부분만 발췌해도 됨)
- "산출물: JMH 마이크로 벤치마크 또는 k6/Gatling 시나리오 + before/after 비교 리포트 + raw JSON 아카이브 경로"
- "측정 환경 6요소(JVM·GC·heap·CPU·OS·warmup) 명시 강제"

산출물을 `<perf output>` 으로 보관.

### 5단계 (조건부): migration-writer
api-contract-reviewer의 BREAKING 분류가 있거나, `<design output>` 또는 `<impl output>` 에 *기존 public API 변경* 이 명시되면 합류. 그 외에는 *생략* 하고 마무리로 점프.

Task tool 위임. prompt:
- "변경 컨텍스트:" + `<design output>` + `<impl output>` 의 변경 요약
- "산출물: deprecation 노트 / BEFORE/AFTER 시나리오 3개 / 자동 마이그레이션 가능성 판정 / 체크리스트 / 호환성 매트릭스"
- "실 사용 호출 패턴은 Grep으로 사내 호출 샘플링"

## 출력 형식

```
# SDK Feature Dev — <기능 한 줄>

## 1. 설계
<feature-designer output>

## 2. 구현
<impl-coder output>

## 3. 테스트
<test-writer output>

## 4. 측정
<performance-tester output>

## 5. 마이그레이션 (BREAKING 시)
<migration-writer output 또는 "비활성: 호환성 영향 없음">

---
## ✅ 완료 체크리스트
- [ ] 설계 산출물 5요소 모두 있음
- [ ] 구현 + KDoc/JavaDoc
- [ ] 단위·통합 테스트
- [ ] JMH/부하 측정 raw JSON 아카이브
- [ ] (BREAKING 시) 마이그레이션 가이드
- [ ] api-contract-reviewer 재검토 권장 (code-review skill 호출)
```

## 규칙

- **순차 위임**. 다음 단계는 이전 산출물 필요.
- 한 단계 산출물에 *문제* 가 보이면 그 단계만 재위임(개선 요청). 전체 다시 X.
- 사용자가 5단계 중 일부만 원하면 명시적으로 받고 그것만 실행("측정은 빼고 구현+테스트만").
- 모든 코드 산출물은 *컴파일 가능* 수준이어야 한다. 의사 코드 금지.
- 끝낸 뒤 `code-review` skill 재호출을 권장 라인으로 추가한다(자동 호출은 X — 사용자 결정).
