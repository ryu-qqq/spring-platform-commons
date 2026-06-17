# connectly 핸드오버 진행 상황 (다음 세션 이어서 작업용)

> **스냅샷:** 2026-06-17

> 개인 인큐베이터(`com.ryuqqq.platform`)에서 다듬은 모듈을 회사(`com.connectly.platform`, GitLab)로
> **단계적 독립 핸드오프**. 이 문서만 보면 다음 세션에서 바로 이어갈 수 있다.

## 좌표·위치

| 항목 | 값 |
|------|----|
| 인큐베이터(소스) | `~/Documents/ryu-qqq/spring-platform-commons` (`com.ryuqqq.platform`, GitHub ryu-qqq) |
| connectly 레포(타깃) | `~/Documents/ryu-qqq/platform-commons-connectly` (`com.connectly.platform`) |
| GitLab 원격 | `git@gitlab.com:connectly-us/platform-commons.git` (private) |
| 배포 | GitLab Package Registry, **그룹 ID 8506575**, **태그 push 시 CI가 자동 publish** |
| 소비 런북 | `~/Documents/ryu-qqq/connectly-services/docs/platform-adoption-runbook.md` |
| 리네임 스크립트 | connectly 레포의 `tools/promote-module.sh` (com.ryuqqq→com.connectly, .imports/.factories 포함) |

## ✅ 완료 — v0.1.0 (5개 모듈, 배포·소비 검증됨)
- `platform-common-domain` · `platform-common-application` · `platform-observability`
  · `platform-archrules` · `platform-outbox`
- crawling-services 에서 `platform-common-domain`·`platform-archrules` 소비 정상 확인.

## ✅ 완료 — v0.2.0 (push 완료, CI 배포 트리거됨)
- `platform-persistence-jpa` 승격 — 빌드·테스트·publishToMavenLocal 검증 OK.
- DST 커밋(`e9ae736`)·태그(`v0.2.0`) **push 완료** (main + 태그). GitLab CI publish 잡 트리거됨.
- 배포 검증: 아래 "검증 한 줄"로 `platform-persistence-jpa/0.2.0` POM 확인(토큰 필요).
- 추후 추상화 후보(아래 "추상화 백로그") 검토 중.

## ⏭️ 남은 모듈 (전부 의존성 충족 — 아무 순서나 가능)

| 모듈 | project 의존(전부 connectly에 이미 있음) |
|------|------|
| `platform-web` | common-domain·observability |
| `platform-redis` | common-application |
| `platform-scheduler` | common-application·observability |
| `platform-security` | common-domain·observability |
| `platform-bootstrap` | (build.gradle엔 없음 — 승격 시 재확인) |
| `resilient-client` | core → metrics(core) → starter(core+metrics). platform-* 무관, 3개 한 묶음 |

> `architecture-tests`는 test 전용(발행 안 함) → 승격 대상 아님.

## 다음 세션 작업 절차 (모듈당)

```bash
SRC=~/Documents/ryu-qqq/spring-platform-commons
DST=~/Documents/ryu-qqq/platform-commons-connectly
M=platform-web            # ← 승격할 모듈

# 1) 소스 복사(빌드 산출물 제외) → 2) 모듈별 리네임(README/스크립트 안 건드리게 모듈 디렉토리만!)
rsync -a --exclude build/ --exclude .gradle/ "$SRC/$M/" "$DST/$M/"
"$DST/tools/promote-module.sh" "$DST/$M" com.ryuqqq com.connectly

# 3) settings.gradle 에 include 추가 (resilient-client는 서브모듈 3개 + projectDir)
#    예: echo "include '$M'" 를 적절히 추가

# 4) 빌드 검증 + 좌표 확인
"$DST/gradlew" -p "$DST" build
"$DST/gradlew" -p "$DST" publishToMavenLocal -q && ls ~/.m2/repository/com/connectly/platform/

# 5) 버전 올리고(0.1.0 → 0.2.0) 커밋·태그·push
#    DST/build.gradle 의 version = '0.1.0' → '0.2.0'
git -C "$DST" add -A && git -C "$DST" commit -m "feat: <M> 승격 (v0.2.0)"
git -C "$DST" push origin main          # 보호 브랜치 — force 금지
git -C "$DST" tag -a v0.2.0 -m "v0.2.0 — <M> 추가" && git -C "$DST" push origin v0.2.0
```

> **버전 정책**: connectly 레포는 전 모듈 단일 버전. 모듈 추가/변경 시 minor 올림(0.1.0 → 0.2.0 → …).
> README 의 "현재 포함 모듈" 목록도 같이 갱신.

## ⚠️ 교훈 (재현 방지)
1. **리네임은 모듈 디렉토리만** 대상으로 — 레포 전체에 돌리면 `README`·`tools/promote-module.sh` 의
   `com.ryuqqq` 예시까지 망가진다(과거 1회 주의).
2. **`.imports`/`.factories` 포함** — Spring AutoConfiguration FQN이 거기 있어 안 고치면 자동설정 깨짐.
   (스크립트엔 이미 반영됨.)
3. **GitLab `main`은 보호 브랜치** — force-push 거부. 최초 push에서 placeholder 충돌나면
   `git merge -s ours origin/main --allow-unrelated-histories` 후 일반 push.
4. **SSH 키**: 이 머신 `~/.ssh/id_ed25519` 가 GitLab `@RyuSangwon` 에 등록됨(인증 OK).
5. push는 회사 계정 권한이라 사람이 실행(에이전트는 준비까지). 단 이 머신에선 키 등록돼 있어 push 가능.

## 검증 한 줄 (배포 후)
```bash
curl -s --header "PRIVATE-TOKEN: <read_api 토큰>" \
  "https://gitlab.com/api/v4/groups/8506575/-/packages/maven/com/connectly/platform/<module>/<ver>/<module>-<ver>.pom" | head
```

## 다음 추천
- **resilient-client(3개, 독립 묶음)** 부터 — 의존 단순해 빠름.
- 그다음 web·redis·scheduler·security, **bootstrap은 마지막**(조립 레이어, 의존 재확인).

## 📋 추상화 백로그 (persistence-jpa — abstraction-critic 6축 비평, 2026-06-17)
> 원칙: **컨벤션 강제 ≠ 코드 재사용**. 강제는 archrules 룰로, 재사용은 persistence-jpa 유틸로 분리.

| 후보 | 최초 6축 | 실측 후 | 비고 |
|------|------|------|------|
| QueryDSL 페이징 헬퍼 | 🟢 GO | 🔴 **보류** | Pageable→QueryDSL 변환 사용처 **0개**. 페이징 모델 3종(offset/cursor/limit) 분기 → 단일 타입 불가 |
| QueryDSL 동적조건 결합 유틸 | 🟡 조건부 | 🔴 **보류** | 결합은 QueryDSL 네이티브(where varargs null-skip·BooleanBuilder)로 이미 충족. 231개 빌더 시그니처 제각각 = 관용구 공유 |
| 마커/@FunctionalInterface Repository | 🔴 NO-GO | 🔴 NO-GO | Spring Data 프록시 시맨틱 충돌·우회 가능. 룰로 강제 |

- **decision-researcher 실측(2026-06-17, 8개 서비스·231개 ConditionBuilder)**: 두 코드 유틸 모두 commonality·타입공유 미충족 → **보류**. 반복되는 건 코드가 아니라 *관용구* → archrules 컨벤션이 정답.
- 사람 결정 필요: ① 페이징 모델(offset/cursor)을 fleet 표준화할 의향? ② 목표가 코드 재사용 vs 일관성 강제? (데이터는 후자=archrules 방향)
- **archrules 신규 룰** `AdapterOutPersistenceRules`(가칭, 의존성 0):
  `REPOSITORY_COMMAND_ONLY`(save/saveAll만, HIGH)·`NO_QUERYDSL_OUTSIDE_ADAPTER_OUT`(CRITICAL 게이트)·
  `CONDITION_LOGIC_IN_BUILDER`(MEDIUM)·`JPA_ENTITY_EXTENDS_BASE`(LOW). 도메인 룰과 동일 하이브리드 전달.

### archrules 의존성 분리 — 오해 해소 (확인 완료)
archrules production 의존성은 `archunit-junit5` **하나뿐**. 룰은 전부 문자열 패키지 매처(`..adapter.out..`)라
도메인 타입 import 0. `common-domain`은 fixture용 `testImplementation`이라 소비측에 안 따라옴.
→ persistence 룰은 archrules에 추가해도 의존성 누수 없음.

---

*최종 갱신: 2026-06-17 — persistence-jpa v0.2.0 승격·push 완료, archrules persistence 룰 백로그·의존성 분리 해소 반영*
