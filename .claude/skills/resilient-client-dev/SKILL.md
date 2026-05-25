---
name: resilient-client-dev
description: spring-platform-commons resilient-client SDK 기능·버그·리팩터 구현 파이프라인. "resilient-client", "CB 추가", "메트릭 수정", "starter 설정", "JitPack", "resilient SDK" 요청에 트리거. 설계→구현→테스트→resilience-reviewer 리뷰 순. Engineering OS Task와 함께 쓴다.
allowed-tools:
  - Read
  - Glob
  - Grep
  - Bash
  - Write
  - Edit
---

# resilient-client-dev Skill

> spring-platform-commons 프로젝트 고유 Harness. 메타 작업 루프는 `engineering-os` skill.

## 작업 전 — 설정·references (필수)

1. `.claude/project.yaml` Read
2. Glob `**/references/java-spring/*.md` + `**/references/resilient-client/*.md` → Read
3. Notion Task가 있으면 `engineering-os` eo-start 상태인지 확인 (없으면 사용자에게 Task URL 요청)

## 역할

resilient-client **코드 변경** 파이프라인 Controller. 범위는 resilient-client 모듈과 관련 Gradle·README·테스트.

## 흐름

1. **범위 확인** — 어느 모듈(core/metrics/starter). 성공 기준·검증(`./gradlew test`) 명시.
2. **선례** — `wiki-lookup` 또는 references/sdk-overview·기존 코드 Read.
3. **구현** — 최소 diff. core에 Spring 금지.
4. **테스트** — test-pyramid + resilience-patterns 테스트 절 준수.
5. **리뷰** — `resilience-reviewer`에 diff 요약 전달. blocking 있으면 수정 후 재리뷰.
6. **완료** — `./gradlew test` 통과 보고. Task 있으면 `engineering-os` eo-done 위임 제안.

## HITL

- 모듈 경계·공개 API breaking 변경은 사용자 확인.
- Task 완료·Notion 갱신은 `engineering-os` + 사용자 승인.

## OpsPilot 연계 (선택)

작업 커밋 후:

```bash
# docs/opspilot-feedback-loop.md 참고 — ingest + work-evaluator
```

## 다른 자산

| 자산 | 관계 |
|------|------|
| `engineering-os` | Task 시작·완료 |
| `test-plan` / `test-strategist` | 큰 기능 시 테스트 계획 |
| `resilience-reviewer` | 구현 후 read-only 리뷰 |
| `work-evaluator` | OpsPilot ingest eval (백그라운드) |
