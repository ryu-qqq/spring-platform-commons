---
name: code-review
description: 사용자가 PR/변경 코드를 보여주며 "코드 리뷰", "PR 리뷰해줘", "이 변경 봐줘", "머지 전 검토", "diff 리뷰", "이거 리뷰" 같은 표현을 쓰거나, 명백히 사내 SDK PR 본문(코드 블록·diff·변경 파일 목록)이 첨부되어 검토를 요청하는 상황에 자동 발화. 단일 관점 1명에게 위임하지 않고 보안·성능·아키텍처·호환성 4개 관점을 *병렬* 위임해 통합 리포트를 만든다.
allowed-tools: Task, Read, Grep, Glob, Bash(git diff:*), Bash(git log:*), Bash(git show:*)
---

# code-review

사내 공통 SDK의 변경(PR/diff/패치)을 4개 시니어 리뷰어 관점에서 동시에 검토하고 통합 리포트로 종합한다. 단일 관점 미스를 막는 게 핵심 — SDK는 전사가 의존하므로 한 관점 누락이 곧 사고다.

## 발화 즉시 절차

1. **변경 범위 확정 (당신 직접)**: `git diff` / `git log --stat` 로 어떤 파일·어떤 라인이 바뀌었는지 확인. 변경이 없으면 사용자에게 어떤 ref를 비교할지 물어보고 중단. 변경 파일 목록·핵심 변경 요약(3줄)을 머릿속에 정리.

2. **4 리뷰어 *병렬* 위임 (Task tool, 한 메시지에 4개 tool_use)**:
   - `server-platform-reviewer` — 아키텍처·추상화·운영·관측성 관점
   - `security-reviewer` — SAST·OWASP·CWE·시크릿·의존성 관점
   - `performance-reviewer` — JVM 알로케이션·동시성·N+1·블로킹·GC 관점
   - `api-contract-reviewer` — SemVer·호환성·deprecation·옵션 폭발 관점

   각 위임 prompt에 다음 4가지를 *동일하게* 전달:
   - "리뷰 대상: <변경 파일 목록>"
   - "변경 요약: <3줄 요약>"
   - "비교 base: <branch/commit>"
   - "출력 형식: Critical/Major/Minor + 파일:라인 + 근거"

3. **결과 통합 (4개 리포트가 모두 돌아온 후)**:
   - **Critical 통합** — 4개 관점 중 누구든 Critical이면 통합 Critical에 올림. 중복(같은 라인 같은 사유)은 머지하고 "지적자: [관점1, 관점2]" 표기.
   - **Major / Minor** 동일 패턴.
   - **상충 의견 별도 섹션**: 한 리뷰어가 OK 한 항목을 다른 리뷰어가 지적한 경우 *상충* 으로 가시화. 결정은 사용자에게 미루지 말고 *우선 순위 가중치*(보안 > 호환성 > 성능 > 아키텍처)로 결정 후 사유 명시.
   - **머지 가능 여부 한 줄**: PASS / CONDITIONAL(N건 수정 후) / BLOCK.

## 출력 형식

```
## 🔍 SDK 변경 통합 리뷰

**머지 판정**: BLOCK / CONDITIONAL / PASS
**범위**: <파일 N개, 라인 +X -Y>
**핵심 변경**: <3줄>

## 🔴 Critical
- [path/File.java:42] CWE-89 — <내용> (지적자: security, performance)
  - 근거: <인용>
  - 수정: ```code```

## 🟠 Major
## 🟡 Minor
## ⚠️ 상충 의견 (리뷰어 간 불일치)
- [path/File.java:88] performance=OK / security=Major
  - 채택: security 의견 (우선순위 가중치)

## 📊 관점별 요약
- server-platform-reviewer: <한 줄>
- security-reviewer: <한 줄>
- performance-reviewer: <한 줄>
- api-contract-reviewer: <한 줄>
```

## 규칙

- 4개 위임은 반드시 *한 메시지에 4개 tool_use* 로 병렬 발화. 순차 위임하면 안 됨(지연·맥락 분기 막기).
- 리뷰어 본인 의견을 *재검열하지 않는다*. 통합만 한다. 추가 의견 덧붙이지 않음.
- 모든 Critical/Major 는 파일:라인 인용이 있어야 한다. 인용 없는 지적은 통합 리포트에서 *제외* (해당 리뷰어가 추측한 것).
- 사용자 추가 질문에는 *해당 관점 리뷰어를 한 번 더 Task tool로 위임* 해서 답한다. 직접 추측 답변 금지.
