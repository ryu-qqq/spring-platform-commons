export const meta = {
  name: 'platform-fix-fleet',
  description: '스코어카드 findings를 게이트키퍼로 분류하고 mechanical을 item별 worktree(main 분기)에서 병렬 자율 수정해 PR을 연다 (머지는 사람)',
  phases: [
    { title: 'Gatekeeper', detail: 'finding 분류 (mechanical/design-fork/skip)' },
    { title: 'Fan-out', detail: 'mechanical item 병렬 수정→re-audit→PR' },
  ],
}

// args = {findings:[{module,check,severity,evidence,direction}...], gatekeeperCriteria, auditorCriteria}
const a = typeof args === 'string' ? JSON.parse(args) : args
if (!a || !Array.isArray(a.findings) || !a.gatekeeperCriteria || !a.auditorCriteria) {
  throw new Error('args에 {findings:[], gatekeeperCriteria, auditorCriteria} 필요')
}
const REPO = '/Users/ryu-qqq/Documents/ryu-qqq/spring-platform-commons'

const GATE_SCHEMA = { type:'object', required:['class'], properties:{
  module:{type:'string'}, check:{type:'string'},
  class:{enum:['mechanical','design-fork','skip']}, confidence:{enum:['high','medium','low']},
  reason:{type:'string'}, apiImpact:{enum:['none','internal','public']} } }

const IMPL_SCHEMA = { type:'object', required:['status','buildGreen'], properties:{
  status:{enum:['DONE','BLOCKED','DESIGN_FORK']}, branch:{type:'string'},
  filesChanged:{type:'array',items:{type:'string'}}, commit:{type:'string'},
  testSummary:{type:'string'}, buildGreen:{type:'boolean'}, notes:{type:'string'} } }

const REAUDIT_SCHEMA = { type:'object', required:['check','status','closed'], properties:{
  module:{type:'string'}, check:{type:'string'}, status:{enum:['pass','warn','fail','na']},
  evidence:{type:'string'}, closed:{type:'boolean'} } }

const PR_SCHEMA = { type:'object', required:['pushed'], properties:{ prUrl:{type:'string'}, pushed:{type:'boolean'}, worktreeRemoved:{type:'boolean'} } }

// item 하나를 명시 worktree(main 분기)에서 수정→re-audit→PR
async function fixItem(fd) {
  const slug = `${fd.module}-${fd.check}`.replace(/[^a-zA-Z0-9-]/g, '-')
  const branch = `fix/${slug}`
  const wt = `${REPO}/.worktrees/${slug}`

  const impl = await agent(
    `너는 자율 수정 구현자다. 격리 worktree에서 TDD로 감사 finding을 수정하라.\n` +
    `## 셋업\n1. 메인 레포 ${REPO} 에서: \`git worktree add ${wt} -b ${branch} main\` (이미 있으면 제거 후 재생성).\n2. 이후 모든 작업은 ${wt} 에서 수행(cd ${wt}).\n` +
    `## finding\nmodule=${fd.module}\ncheck=${fd.check}\nevidence=${fd.evidence}\ndirection=${fd.direction}\n` +
    `## TDD\n3. 선례 참고(platform-security/outbox 의 ApplicationContextRunner 테스트 스타일) → 실패 테스트 → \`./gradlew :${fd.module}:test\` red → green → \`./gradlew :${fd.module}:build\` 그린.\n4. 커밋(한국어, Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>).\n` +
    `## 경계\n- 이 finding 범위만. 가짜green 금지(실제 시나리오 검증). 설계 갈림 드러나면 status=DESIGN_FORK.\n` +
    `JSON: {"status","branch":"${branch}","filesChanged":[],"commit","testSummary","buildGreen","notes"}`,
    { label:`impl:${fd.module}`, phase:'Fan-out', schema:IMPL_SCHEMA })
  if (impl.status !== 'DONE' || !impl.buildGreen) return { ...fd, stage:'implement', escalate:true, reason:`구현 미완 status=${impl.status} build=${impl.buildGreen}`, impl, branch, wt }

  const reaudit = await agent(
    `${a.auditorCriteria}\n\n작업 트리: ${wt} (cd 해서 그 상태를 감사). 대상 모듈 ${fd.module}. check id="${fd.check}" 의 현재 status를 판정하라. 읽기전용 — 구현자 주장 불신, 코드 직접 확인.\n` +
    `JSON: {"module":"${fd.module}","check":"${fd.check}","status":"pass|warn|fail|na","evidence","closed"}`,
    { label:`reaudit:${fd.module}`, phase:'Fan-out', schema:REAUDIT_SCHEMA })
  if (!reaudit.closed || reaudit.status !== 'pass') return { ...fd, stage:'re-audit', escalate:true, reason:`closure 실패 ${fd.check}=${reaudit.status}`, impl, reaudit, branch, wt }

  const pr = await agent(
    `작업 트리 ${wt}, 브랜치 ${branch}. PR을 열어라(머지 금지):\n` +
    `1. cd ${wt}; \`git push -u origin ${branch}\`.\n2. \`gh pr create\` 제목 "fix(${fd.module}): ${fd.check} 자율 수정 (Phase 2b-1)", 본문에 finding·한 일(${impl.testSummary})·re-audit closure(${reaudit.evidence})·"🤖 자율 fleet 산출, 머지는 사람" 명시.\n` +
    `3. 정리: cd ${REPO}; \`git worktree remove ${wt} --force\`.\n` +
    `JSON: {"prUrl","pushed","worktreeRemoved"}`,
    { label:`pr:${fd.module}`, phase:'Fan-out', schema:PR_SCHEMA })
  return { ...fd, stage:'PR', escalate:false, impl, reaudit, pr, branch, wt }
}

phase('Gatekeeper')
const classified = (await parallel(a.findings.map(fd => () =>
  agent(`${a.gatekeeperCriteria}\n\nfinding: ${JSON.stringify(fd)}\n분류 JSON만 반환.`,
        { label:`gate:${fd.module}:${fd.check}`, phase:'Gatekeeper', schema:GATE_SCHEMA })
    .then(c => ({ ...fd, ...c }))))).filter(Boolean)

const mechanical = classified.filter(c => c.class === 'mechanical')
const designForks = classified.filter(c => c.class === 'design-fork')
const skipped = classified.filter(c => c.class === 'skip')

phase('Fan-out')
const fixed = mechanical.length
  ? (await parallel(mechanical.map(fd => () => fixItem(fd)))).filter(Boolean)
  : []

return {
  gatekeeper: { mechanical: mechanical.length, designForks, skipped },
  results: fixed,
  prs: fixed.filter(r => !r.escalate && r.pr).map(r => ({ module:r.module, check:r.check, prUrl:r.pr.prUrl })),
  escalations: fixed.filter(r => r.escalate).map(r => ({ module:r.module, check:r.check, stage:r.stage, reason:r.reason })),
}
