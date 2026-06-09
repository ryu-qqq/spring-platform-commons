export const meta = {
  name: 'platform-fix-fleet',
  description: '스코어카드 findings를 게이트키퍼로 분류하고 mechanical을 item별 worktree(main 분기)에서 병렬 자율 수정해 PR을 연다 (머지는 사람)',
  phases: [
    { title: 'Gatekeeper', detail: 'finding 분류 (mechanical/design-fork/skip)' },
    { title: 'Fan-out', detail: 'mechanical item 병렬 수정→re-audit→PR' },
    { title: 'Auto-merge', detail: '보수 게이트 통과 시 자동머지 (arg-gated)' },
  ],
}

// args = {findings:[{module,check,severity,evidence,direction}...], gatekeeperCriteria, auditorCriteria, repo?,
//         autoMerge?(기본 false=사람 머지), dryRun?(기본 false), mergeCapN?(기본 3)}
const a = typeof args === 'string' ? JSON.parse(args) : args
if (!a || !Array.isArray(a.findings) || !a.gatekeeperCriteria || !a.auditorCriteria) {
  throw new Error('args에 {findings:[], gatekeeperCriteria, auditorCriteria} 필요')
}
// repo 경로는 args.repo 로 주입(이식성). 워크플로우 샌드박스엔 process.cwd() 미보장이라 args 우선.
const REPO = a.repo || '/Users/ryu-qqq/Documents/ryu-qqq/spring-platform-commons'

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

const MERGE_GATE_CRITERIA = `너는 merge-gate 적대적 리뷰어다. 읽기전용(git diff/read만). ` +
  `보수 scope = src/test/·docs/·*.md·README 만 허용(src/main 변경 있으면 scopeOk=false). ` +
  `검토: 가짜green(빈/trivially-true 단언)·범위이탈(finding 밖 변경)·회귀. 하나라도 의심되면 verdict=block. 디프를 실제로 읽어라.`

const MERGE_GATE_SCHEMA = { type:'object', required:['verdict','scopeOk'], properties:{
  verdict:{enum:['approve','block']}, scopeOk:{type:'boolean'},
  fakeGreenRisk:{type:'boolean'}, scopeCreep:{type:'boolean'}, reasons:{type:'array',items:{type:'string'}} } }

// item 하나를 명시 worktree(main 분기)에서 수정→re-audit→PR.
// worktree 정리는 finally에서 단일 지점으로 — 성공·실패·escalate 어느 경로든 누수 없음.
async function fixItem(fd) {
  const slug = `${fd.module}-${fd.check}`.replace(/[^a-zA-Z0-9-]/g, '-')
  const branch = `fix/${slug}`
  const wt = `${REPO}/.worktrees/${slug}`
  try {
    const impl = await agent(
      `너는 자율 수정 구현자다. 격리 worktree에서 TDD로 감사 finding을 수정하라.\n` +
      `## 셋업\n1. 메인 레포 ${REPO} 에서: \`git worktree add ${wt} -b ${branch} main\` (이미 있으면 제거 후 재생성).\n2. 이후 모든 작업은 ${wt} 에서 수행(cd ${wt}).\n` +
      `## finding\nmodule=${fd.module}\ncheck=${fd.check}\nevidence=${fd.evidence}\ndirection=${fd.direction}\n` +
      `## 수정 (finding 성격에 맞춰라)\n` +
      `- 테스트 추가형: 선례(platform-security/outbox 의 ApplicationContextRunner 스타일) → 실패 테스트 → \`./gradlew :${fd.module}:test\` red → green → \`./gradlew :${fd.module}:build\` 그린. buildGreen=true.\n` +
      `- 문서/README형: 모듈 역할·확장점을 담은 실제 내용의 .md(예: ${fd.module}/README.md) 작성. 코드 변경 없으면 gradle 불필요 → buildGreen=true로 보고(빌드 영향 없음).\n` +
      `3. 커밋(한국어, Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>).\n` +
      `## 경계\n- 이 finding 범위만. 가짜 충족 금지(테스트면 실제 시나리오 검증, 문서면 실제 내용). 설계 갈림 드러나면 status=DESIGN_FORK.\n` +
      `JSON: {"status","branch":"${branch}","filesChanged":[],"commit","testSummary","buildGreen","notes"}`,
      { label:`impl:${fd.module}`, phase:'Fan-out', schema:IMPL_SCHEMA })
    if (impl.status !== 'DONE' || !impl.buildGreen) return { ...fd, stage:'implement', escalate:true, reason:`구현 미완 status=${impl.status} build=${impl.buildGreen}`, impl, branch, wt }

    const reaudit = await agent(
      `${a.auditorCriteria}\n\n작업 트리: ${wt} (cd 해서 그 상태를 감사). 대상 모듈 ${fd.module}. check id="${fd.check}" 의 현재 status를 판정하라. 읽기전용 — 구현자 주장 불신, 코드 직접 확인.\n` +
      `JSON: {"module":"${fd.module}","check":"${fd.check}","status":"pass|warn|fail|na","evidence","closed"}`,
      { label:`reaudit:${fd.module}`, phase:'Fan-out', schema:REAUDIT_SCHEMA })
    if (!reaudit.closed || reaudit.status !== 'pass') return { ...fd, stage:'re-audit', escalate:true, reason:`closure 실패 ${fd.check}=${reaudit.status}`, impl, reaudit, branch, wt }

    const pr = await agent(
      `작업 트리 ${wt}, 브랜치 ${branch}. PR을 열어라(머지 금지, worktree 제거도 하지 마라 — 정리는 호출자가 한다):\n` +
      `1. cd ${wt}; \`git push -u origin ${branch}\`.\n2. \`gh pr create\` 제목 "fix(${fd.module}): ${fd.check} 자율 수정 (Phase 2b-1)", 본문에 finding·한 일(${impl.testSummary})·re-audit closure(${reaudit.evidence})·"🤖 자율 fleet 산출, 머지는 사람" 명시.\n` +
      `JSON: {"prUrl","pushed"}`,
      { label:`pr:${fd.module}`, phase:'Fan-out', schema:PR_SCHEMA })
    return { ...fd, stage:'PR', escalate:false, impl, reaudit, pr, branch, wt }
  } finally {
    // 어느 경로(성공·escalate·예외)든 worktree 정리. push된 브랜치 ref는 보존됨.
    await agent(
      `메인 레포 ${REPO} 에서 \`git worktree remove ${wt} --force\` 를 실행해 임시 worktree를 정리하라(이미 없으면 무시). JSON: {"removed":true|false}`,
      { label:`cleanup:${fd.module}`, phase:'Fan-out', schema:{ type:'object', properties:{ removed:{type:'boolean'} } } }).catch(() => {})
  }
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

phase('Auto-merge')
// 보수 게이트(전부 AND): ① gatekeeper mechanical+high+apiImpact none ② re-audit closed
// ③ scope test/docs(merge-gate scopeOk) ④ merge-gate approve ⑤ CI는 gh --auto 위임.
// autoMerge 기본 false = 2b-1 동작(사람 머지) 보존. 어느 단계든 실패/의심이면 escalate.
const prRef = r => ({ module:r.module, check:r.check, prUrl:r.pr && r.pr.prUrl })
const autoMerge = []
if (a.autoMerge) {
  const CAP = a.mergeCapN || 3
  let merged = 0
  for (const r of fixed.filter(x => !x.escalate && x.pr && x.pr.prUrl)) {
    if (!(r.class === 'mechanical' && r.confidence === 'high' && r.apiImpact === 'none' && r.reaudit && r.reaudit.closed)) {
      autoMerge.push({ ...prRef(r), action:'escalate', reason:'게이트①② 미충족(mechanical/high/apiImpact-none/closed)' }); continue
    }
    if (merged >= CAP) { autoMerge.push({ ...prRef(r), action:'escalate', reason:`per-run 캡(${CAP}) 도달` }); continue }
    const review = await agent(
      `${MERGE_GATE_CRITERIA}\n\n작업 디렉토리 ${REPO}. PR 브랜치 ${r.branch}(base main).\n` +
      `\`git diff --name-only main..${r.branch}\` 와 \`git diff main..${r.branch}\` 를 읽어 판정.\n` +
      `finding: ${JSON.stringify({ module:r.module, check:r.check, direction:r.direction })}\n` +
      `JSON: {"verdict":"approve|block","scopeOk":bool,"fakeGreenRisk":bool,"scopeCreep":bool,"reasons":[]}`,
      { label:`merge-gate:${r.module}`, phase:'Auto-merge', schema:MERGE_GATE_SCHEMA })
    if (review.verdict !== 'approve' || !review.scopeOk) {
      autoMerge.push({ ...prRef(r), action:'escalate', reason:`merge-gate=${review.verdict} scopeOk=${review.scopeOk}: ${(review.reasons || []).join('; ')}` }); continue
    }
    if (a.dryRun) { autoMerge.push({ ...prRef(r), action:'would-merge', reason:'dry-run(머지 안 함)' }); merged++; continue }
    // ⑤ CI 게이트: 진짜 게이트인 'build' 체크만 본다(atlantis 등 무관·non-required 실패는 무시).
    // build pass면 --auto 시도, 레포에 auto-merge 미허용이면 직접 머지로 폴백.
    const m = await agent(
      `작업 디렉토리 ${REPO}. PR ${r.pr.prUrl} 자동머지 절차:\n` +
      `1. \`gh pr checks ${r.pr.prUrl}\` 실행 → 'build' 체크가 pass인지 확인. build가 pass가 아니면(fail/pending) 머지하지 말고 {"merged":false,"method":"skipped","reason":"build 체크 미통과"} 반환.\n` +
      `   (atlantis 등 build 외 체크 실패는 무시 — 이 레포의 required 게이트는 build 뿐.)\n` +
      `2. build pass면 \`gh pr merge ${r.pr.prUrl} --auto --squash --delete-branch\` 시도. ` +
      `'auto merge is not allowed' 류 에러면 \`gh pr merge ${r.pr.prUrl} --squash --delete-branch\` 로 직접 머지(폴백).\n` +
      `JSON: {"merged":bool,"method":"auto|direct|skipped","reason":""}`,
      { label:`merge:${r.module}`, phase:'Auto-merge', schema:{ type:'object', required:['merged'], properties:{ merged:{type:'boolean'}, method:{enum:['auto','direct','skipped']}, reason:{type:'string'} } } }).catch(() => ({ merged:false, method:'skipped', reason:'agent error' }))
    autoMerge.push({ ...prRef(r), action: m.merged ? `merged(${m.method})` : 'escalate', reason: m.merged ? `${m.method} 머지` : `머지 안 함: ${m.reason}` })
    if (m.merged) merged++
  }
}

return {
  gatekeeper: { mechanical: mechanical.length, designForks, skipped },
  results: fixed,
  prs: fixed.filter(r => !r.escalate && r.pr).map(r => ({ module:r.module, check:r.check, prUrl:r.pr.prUrl })),
  escalations: fixed.filter(r => r.escalate).map(r => ({ module:r.module, check:r.check, stage:r.stage, reason:r.reason })),
  autoMerge,
}
