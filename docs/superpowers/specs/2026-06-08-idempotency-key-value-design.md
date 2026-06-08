# IdempotencyKeyValue VO 설계

- 일자: 2026-06-08
- 모듈: `platform-common-domain`
- 백로그: P2-4 Idempotency Key VO (감사 보고서 `2026-06-08-platform-sdk-audit-report.md` line 100-104)
- 설계 게이트: abstraction-critic 6축 통과(CONCERN → value-only 재설계로 해소)

## 1. 목적

활성 서버 중 MarketPlace(14개 record)·FileFlow(릴레이 4 + 클라이언트 2)가 멱등 키 VO를
보유하고, AuthHub는 raw `String`을 쓴다. 키 조립 규칙(`forNew`/SHA-256 derive)은 서버마다
발산하지만, **"비어 있지 않은 불투명 키 값을 타입으로 감싸 검증한다"** 는 책임은 ≥2 서버 공통이다.
이 최소 공통 책임만 SDK로 추출한다.

## 2. 범위

### 만든다
- `platform-common-domain` 의 `vo/IdempotencyKeyValue.java` — 단일 필드 value record.
  - blank/null 가드 (`IllegalArgumentException`).
  - `toString()` 은 raw value 반환 (로깅·저장 키 용도).
  - 프레임워크-프리(순수 도메인, Spring Assert 미사용).
- 단위 테스트.

### 만들지 않는다 (비목표 — Javadoc에 명문화)
- `generate`/`forNew`/`derive`/SHA-256 등 **키 조립·파생 팩토리** — 도메인 특화, SDK 제외.
- `namespace` 를 **코어 record 필드로 보유** — `PREFIX:value` 는 Javadoc **규약**으로만.
  - 근거: namespace 결합 규칙이 서버 간 미수렴(MP 복붙 vs FF 분기). 코어에 박으면 과대 추상화.
    형제 `CacheKey`/`LockKey` 도 키 포맷은 도메인이 강제하므로 결합은 소비측 귀속이 자연스럽다.
- `of()` 등 정적 팩토리 — 감사 권고("팩토리 없음")대로 canonical 생성자만 노출.
- charset/length 제약, trim 정규화 — 외부 불투명 입력을 SDK가 임의 변형하지 않음. 동등성은 원문 기준.
- 영속화 매핑.

### 입양(deferred)
- MP 14개·FF 6개 기존 VO가 이 타입에 위임하도록 리팩터 — build-out 완료 후 서버 마이그레이션 단계.

## 3. 형태

```java
package com.ryuqqq.platform.common.vo;

/**
 * 멱등 키의 검증된 불투명 값.
 *
 * <p>외부(클라이언트 헤더 등)에서 받은 불투명 키 문자열을 타입으로 감싸 blank/null 만 막는다.
 * SDK는 값을 정규화(trim/대소문자/charset)하지 않으며, 동등성은 원문 기준이다.
 *
 * <p><b>네임스페이스 규약:</b> 같은 키가 컨텍스트 간 충돌하지 않도록 소비측이
 * {@code PREFIX:value}(예: {@code "payment:abc-123"}) 형태로 결합해 보관할 것을 권장한다.
 * 결합 규칙은 도메인 정책이므로 이 타입이 강제하지 않는다.
 *
 * <p><b>비목표:</b> 키 파생/생성 팩토리(forNew·SHA-256 derive 등), namespace 코어 필드,
 * charset/length 제약, trim 정규화, 영속화 매핑 — 모두 호출자 책임.
 *
 * <pre>{@code
 * var key = new IdempotencyKeyValue(request.getHeader("Idempotency-Key"));
 * String stored = "payment:" + key; // 규약에 따른 namespacing은 소비측
 * }</pre>
 */
public record IdempotencyKeyValue(String value) {

    public IdempotencyKeyValue {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("idempotency key value must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
```

## 4. 테스트 (단위)

| 시나리오 | 기대 |
|---|---|
| `null` value | `IllegalArgumentException` |
| 빈 문자열 `""` | `IllegalArgumentException` |
| 공백 only `"   "` | `IllegalArgumentException` |
| 정상 값 | 생성 성공, `value()` 원문 반환 |
| 같은 value 두 인스턴스 | `equals`/`hashCode` 동일 (구조 동등) |
| 다른 value | 비동일 |
| 앞뒤 공백 포함 `" abc "` | 그대로 보존(trim 안 함), `"abc"` 와 비동일 |
| `toString()` | record 기본형이 아닌 raw value(`value`) 반환 |

## 5. 설계 결정 근거

- **value-only**: 감사 보고서 line 104 확정 최소 seam. abstraction-critic CONCERN(namespace 코어 융합 = seam/yagni 위반) 해소.
- **팩토리 없음**: 감사 권고. `of()` 정적 팩토리는 derive/generate 의미와 혼동될 수 있어 canonical 생성자만 노출. (DateRange·PageRequest 의 `of()` 관례와는 의도적으로 어긋남 — 이 타입은 "검증만" 하는 래퍼라 생성 변형이 불필요.)
- **toString 오버라이드**: 감사가 seam 구성요소로 명시. 로깅·저장 키로 직접 쓰일 때 record 기본형(`IdempotencyKeyValue[value=...]`)보다 raw value가 유용. 멱등 키는 비밀값이 아니라 로그 노출 우려 없음.
- **프레임워크-프리**: 형제 VO·common-domain 규약 일치(`IllegalArgumentException`, Spring 미의존).
