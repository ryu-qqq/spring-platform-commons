export const meta = {
  name: 'platform-audit-sweep',
  description: 'spring-platform-commons 모듈을 분야별 auditor로 감사해 Soundcheck식 스코어카드를 산출한다',
  phases: [
    { title: 'Audit', detail: '모듈×차원 fan-out 감사' },
    { title: 'Scorecard', detail: '집계 + 스코어카드 리포트 작성' },
  ],
}

// 감사 기준은 .claude/agents/{autoconfig,observability}-auditor.md 와 동기 (SSOT: spec §3).
// 세션 내 신규 에이전트 미등록 제약으로 agentType 대신 criteria 를 인라인한다.
// 에이전트가 레지스트리에 로드된 세션에서는 agentType 으로 전환해 중복 제거 가능.
const CRITERIA = {
  'autoconfig-auditor': `너는 읽기전용 autoconfig-auditor 다. 파일을 수정하지 마라.
track="Paved Road". Spring Boot 자동설정 paved-road 위생을 채점한다.
- imports-registered: 자동설정 클래스가 @AutoConfiguration 표시 + META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports 에 등록(한 줄 한 클래스). FAIL=imports 없음/클래스 누락/spring.factories 잔존.
- conditional-override: 공개 기본 빈에 @ConditionalOnMissingBean. FAIL=무조건 빈 등록.
- context-runner-test: ApplicationContextRunner 기반 테스트 존재(backs-off/property/FilteredClassLoader). FAIL=자동설정 테스트 부재.
- conditional-on-class: optional 의존성(MeterRegistry 등)에 @ConditionalOnClass/@ConditionalOnBean/ObjectProvider 가드. FAIL=무가드 직접 주입.
절차: Glob <module>/**/*.java + <module>/src/main/resources/META-INF/spring/*.imports + <module>/build.gradle* 로 표면 식별. 모듈이 자동설정 비대상이면 전 check na. evidence는 실제 file:line 만, 추측 금지.`,
  'observability-auditor': `너는 읽기전용 observability-auditor 다. 파일을 수정하지 마라.
track="Observability". 메트릭·트레이스·MDC 계측 위생을 채점한다.
- metric-naming: 메트릭명 일관 dot-네임스페이스(예: scheduler.job.*). FAIL=비일관/하드코딩 산재.
- cardinality-discipline: userId 등 고카디널리티가 메트릭 태그에 없음(트레이스 한정). FAIL=고카디널리티를 메트릭 tag로.
- trace-propagation: 제공 HTTP 클라이언트가 auto-config builder(RestClient.Builder 등) 경유. 모듈에 HTTP 클라이언트 없으면 na. FAIL=수동 new RestTemplate().
- mdc-key-consistency: MDC 키가 MdcKeys SSOT 상수 사용. FAIL=리터럴 산재.
- meter-optional: MeterRegistry 부재 시 zero-config 동작(ObjectProvider/Conditional/null-guard). FAIL=무조건 의존.
절차: Grep <module> 으로 MeterRegistry/Observation/MDC/RestTemplate|RestClient|WebClient/MdcKeys/메트릭명 신호 수집. 관측성 비대상 모듈은 na. evidence는 실제 file:line 만, 추측 금지.`,
  'versioning-auditor': `너는 읽기전용 versioning-auditor 다. 파일을 수정하지 마라.
track="Versioning". JitPack 배포 SDK의 하위호환 위생을 채점한다.
- deprecated-discipline: 모든 @Deprecated 에 since="X" + forRemoval=true 동반 + 매칭 Javadoc @deprecated(since·removal·대체). FAIL=since/forRemoval 누락. @Deprecated 전무면 na.
- binary-compat-gate: 배포 모듈 빌드(build.gradle*)·레포 CI(.github/workflows)에 바이너리 호환 게이트(japicmp/revapi) 존재. 단 루트 build.gradle version이 pre-1.0(0.x)이면 info(ADR-0004로 1.0까지 의도적 defer). 1.0 이상인데 부재면 fail.
- changelog-present: 레포 루트에 CHANGELOG.md 또는 releases/ 존재. FAIL=부재.
절차: Grep <module>/**/src/main 에서 @Deprecated 수집→since·forRemoval 확인. Glob <module>/**/build.gradle* + .github/workflows 에서 japicmp/revapi grep. 레포 루트 CHANGELOG/releases 확인. 공개 표면 없는 모듈은 deprecated-discipline na. evidence는 실제 file:line, 추측 금지.`,
}

const REPO = '/Users/ryu-qqq/Documents/ryu-qqq/spring-platform-commons'

// §3 매핑 — cross-product 아님
const TARGETS = [
  ...['platform-redis', 'platform-scheduler', 'platform-security', 'platform-outbox', 'platform-web', 'platform-persistence-jpa']
    .map(m => ({ module: m, auditor: 'autoconfig-auditor', track: 'Paved Road' })),
  ...['platform-scheduler', 'platform-outbox', 'resilient-client', 'platform-web', 'platform-security', 'platform-common-domain']
    .map(m => ({ module: m, auditor: 'observability-auditor', track: 'Observability' })),
  // Versioning: 공개 표면·@Deprecated 가능성 있는 배포 모듈
  ...['resilient-client', 'platform-common-domain', 'platform-common-application', 'platform-redis', 'platform-outbox']
    .map(m => ({ module: m, auditor: 'versioning-auditor', track: 'Versioning' })),
]

const FINDINGS_SCHEMA = {
  type: 'object',
  required: ['module', 'track', 'checks'],
  properties: {
    module: { type: 'string' },
    track: { type: 'string' },
    checks: {
      type: 'array',
      items: {
        type: 'object',
        required: ['id', 'status'],
        properties: {
          id: { type: 'string' },
          status: { enum: ['pass', 'warn', 'fail', 'na'] },
          severity: { enum: ['info', 'minor', 'major'] },
          evidence: { type: 'string' },
          direction: { type: 'string' },
        },
      },
    },
  },
}

phase('Audit')
const findings = (await parallel(TARGETS.map(t => () =>
  agent(
    `${CRITERIA[t.auditor]}\n\n대상 모듈: ${t.module} (경로: ${REPO}/${t.module}).\n` +
    `위 기준으로 채점해 다음 JSON 만 반환하라: {"module":"${t.module}","track":"${t.track}","checks":[{"id","status","severity","evidence","direction"}...]}`,
    { label: `${t.auditor}:${t.module}`, phase: 'Audit', schema: FINDINGS_SCHEMA }
  )
))).filter(Boolean)

phase('Scorecard')
// 결정론적 집계: 모듈×track pass/total + level
const LEVEL = (pass, total) => total === 0 ? 'N/A' : (pass === total ? 'Gold' : (pass / total >= 0.7 ? 'Silver' : 'Bronze'))
const scorecard = findings.map(f => {
  const scored = f.checks.filter(c => c.status !== 'na')
  const pass = scored.filter(c => c.status === 'pass').length
  return { module: f.module, track: f.track, pass, total: scored.length, level: LEVEL(pass, scored.length), checks: f.checks }
})
// escalation: severity=major 인 fail
const escalations = findings.flatMap(f =>
  f.checks.filter(c => c.status === 'fail' && c.severity === 'major')
    .map(c => ({ module: f.module, track: f.track, ...c })))

const report = await agent(
  `다음 감사 결과로 한국어 Soundcheck식 모듈 스코어카드 마크다운을 작성하라. 구조:\n` +
  `1) "## 🚨 중요(escalation)" — escalations 를 모듈·check·evidence·direction 표로. 비어있으면 "없음".\n` +
  `2) "## 스코어카드 (모듈 × track)" — 행=모듈, 열=track(Paved Road/Observability), 셀=level(Gold/Silver/Bronze/N-A) + (pass/total).\n` +
  `3) "## check 상세" — track별로 모듈마다 각 check 의 status·evidence·direction.\n` +
  `오직 마크다운 본문만 반환(코드펜스로 감싸지 말 것).\n\n` +
  `scorecard=${JSON.stringify(scorecard)}\n\nescalations=${JSON.stringify(escalations)}`,
  { label: 'scorecard-writer', phase: 'Scorecard' })

return { scorecard, escalations, report }
