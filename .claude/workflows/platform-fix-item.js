export const meta = {
  name: 'platform-fix-item',
  description: '감사 finding 1건을 worktree/branch에서 자율 수정(TDD)하고 re-audit closure 후 PR을 연다 (머지는 사람)',
  phases: [
    { title: 'Implement', detail: 'feature 브랜치에서 TDD 구현' },
    { title: 'Re-audit', detail: '독립 auditor로 finding closure 확인' },
    { title: 'PR', detail: 'PR 오픈 (머지는 사람)' },
  ],
}

// args = finding: {module, check, severity, evidence, direction, branch, auditorCriteria}
// args 는 객체 또는 JSON 문자열로 들어올 수 있어 강제 정규화한다.
const f = typeof args === 'string' ? JSON.parse(args) : args
if (!f || !f.module || !f.check || !f.direction) {
  throw new Error('args에 finding {module, check, direction, branch, auditorCriteria} 필요')
}
const REPO = '/Users/ryu-qqq/Documents/ryu-qqq/spring-platform-commons'

phase('Implement')
const impl = await agent(
  `너는 자율 수정 구현자다. 작업 디렉토리: ${REPO}. TDD로 감사 finding을 수정하라.\n\n` +
  `## finding\nmodule=${f.module}\ncheck=${f.check}\nevidence=${f.evidence}\ndirection=${f.direction}\n\n` +
  `## 절차 (반드시 순서대로)\n` +
  `1. \`git checkout -b ${f.branch}\` (이미 있으면 checkout).\n` +
  `2. 참고: 잘 된 선례 테스트를 먼저 읽어라 — platform-security/src/test 의 ApplicationContextRunner 테스트(PlatformSecurityAutoConfigurationTest), platform-outbox 의 동일 패턴. 그 스타일(WebApplicationContextRunner/ApplicationContextRunner + AutoConfigurations.of + withUserConfiguration backs-off + FilteredClassLoader)을 모사.\n` +
  `3. TDD: direction이 요구하는 시나리오의 실패 테스트부터 작성 → \`./gradlew :${f.module}:test\` 로 red 확인(컴파일/실패) → 최소 구현/테스트 보강으로 green.\n` +
  `4. \`./gradlew :${f.module}:build\` 그린 확인(전체 모듈 빌드).\n` +
  `5. 변경을 커밋(한국어 메시지, Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>).\n` +
  `## 경계\n- 오직 이 finding 범위만. 다른 리팩터 금지.\n- 설계 갈림(유효 접근 다수·public API 변경)이 드러나면 즉시 멈추고 status=DESIGN_FORK 로 보고(자동 설계 금지).\n- 가짜 green 금지 — 테스트가 실제 시나리오(backs-off/FilteredClassLoader)를 검증해야 한다.\n\n` +
  `JSON 반환: {"status":"DONE|BLOCKED|DESIGN_FORK","branch":"${f.branch}","filesChanged":[...],"commit":"<sha>","testSummary":"<무엇을 어떻게 검증했나>","buildGreen":true|false,"notes":"..."}`,
  { label: `implement:${f.module}`, phase: 'Implement', schema: {
      type:'object', required:['status','buildGreen'],
      properties:{ status:{enum:['DONE','BLOCKED','DESIGN_FORK']}, branch:{type:'string'},
        filesChanged:{type:'array',items:{type:'string'}}, commit:{type:'string'},
        testSummary:{type:'string'}, buildGreen:{type:'boolean'}, notes:{type:'string'} } } })

if (impl.status !== 'DONE' || !impl.buildGreen) {
  return { stage: 'Implement', escalate: true, reason: `구현 미완: status=${impl.status}, buildGreen=${impl.buildGreen}`, impl }
}

phase('Re-audit')
// 독립 auditor 재실행 — 같은 트리(브랜치 체크아웃 상태)에서 finding이 닫혔는지 객체 확인
const reaudit = await agent(
  `${f.auditorCriteria}\n\n대상 모듈: ${f.module} (경로: ${REPO}/${f.module}). 현재 git 브랜치 상태 그대로 감사하라.\n` +
  `특히 check id="${f.check}" 의 현재 status를 판정하라. 너는 읽기전용이다 — 파일 수정 금지, 구현자 주장을 믿지 말고 코드를 직접 확인.\n` +
  `JSON 반환: {"module":"${f.module}","check":"${f.check}","status":"pass|warn|fail|na","evidence":"<file:line>","closed":true|false}`,
  { label: `re-audit:${f.module}`, phase: 'Re-audit', schema: {
      type:'object', required:['check','status','closed'],
      properties:{ module:{type:'string'}, check:{type:'string'},
        status:{enum:['pass','warn','fail','na']}, evidence:{type:'string'}, closed:{type:'boolean'} } } })

if (!reaudit.closed || reaudit.status !== 'pass') {
  return { stage: 'Re-audit', escalate: true, reason: `closure 실패: ${f.check} 가 ${reaudit.status} (pass 아님)`, impl, reaudit }
}

phase('PR')
const pr = await agent(
  `작업 디렉토리 ${REPO}, 현재 브랜치 ${impl.branch}. 다음으로 PR을 열어라(머지는 하지 마라):\n` +
  `1. \`git push -u origin ${impl.branch}\`.\n` +
  `2. \`gh pr create\` — 제목 "fix(${f.module}): ${f.check} 자율 수정 (Phase 2a)", 본문에:\n` +
  `   - 감사 finding(evidence) 요약\n   - 한 일(${impl.testSummary})\n   - re-audit closure: ${f.check} → ${reaudit.status} (${reaudit.evidence})\n` +
  `   - 스코어카드 delta(해당 track Silver→Gold 기대)\n   - "🤖 Phase 2a 자율 파이프라인 산출 — 머지는 사람 검토 후" 명시.\n` +
  `JSON 반환: {"prUrl":"<url>","pushed":true|false}`,
  { label: `pr:${f.module}`, phase: 'PR', schema: {
      type:'object', required:['pushed'], properties:{ prUrl:{type:'string'}, pushed:{type:'boolean'} } } })

return { stage: 'PR', escalate: false, impl, reaudit, pr }
