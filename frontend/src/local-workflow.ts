export type WorkflowStepKind = 'discover' | 'evaluate' | 'prepare_materials' | 'manual_approval' | 'record_application' | 'follow_up'
export type WorkflowStepDefinition = { key: WorkflowStepKind; title: string; enabled: boolean; required: boolean }
export type WorkflowDefinition = { id: string; name: string; followUpDelayDays: number; steps: WorkflowStepDefinition[]; updatedAt?: string }
export type WorkflowStepState = WorkflowStepDefinition & { state: 'done' | 'pending'; completedAt?: string; note?: string; resultId?: string }
export type WorkflowRun = {
  id: string; jobId: string; companyName: string; jobTitle: string; definitionId: string; definitionName: string
  followUpDelayDays: number
  status: 'running' | 'paused' | 'cancelled' | 'completed'; steps: WorkflowStepState[]; currentStepIndex: number
  matchSnapshot: { score: number; decision: string; reasons: string[]; missingConditions: string[]; uncertainties: string[]; exclusionReasons: string[] }
  history: Array<{ at: string; action: string; detail: string }>; createdAt: string; updatedAt: string
}

export const defaultWorkflowDefinition: WorkflowDefinition = {
  id: 'standard-application', name: '标准岗位投递流程', followUpDelayDays: 3,
  steps: [
    { key: 'discover', title: '选择已保存岗位', enabled: true, required: true },
    { key: 'evaluate', title: '本地匹配评估', enabled: true, required: true },
    { key: 'prepare_materials', title: '准备申请材料', enabled: true, required: false },
    { key: 'manual_approval', title: '人工批准', enabled: true, required: true },
    { key: 'record_application', title: '人工提交并登记投递', enabled: true, required: true },
    { key: 'follow_up', title: '安排后续跟进', enabled: true, required: true },
  ],
}
const order: WorkflowStepKind[] = ['discover', 'evaluate', 'prepare_materials', 'manual_approval', 'record_application', 'follow_up']
const required = new Set<WorkflowStepKind>(['discover', 'evaluate', 'manual_approval', 'record_application', 'follow_up'])
const nowIso = () => new Date().toISOString()

export function normalizeWorkflowDefinition(input?: Partial<WorkflowDefinition>): WorkflowDefinition {
  const defaults = new Map(defaultWorkflowDefinition.steps.map(step => [step.key, step]))
  const supplied = new Map((input?.steps || []).map(step => [step.key, step]))
  const steps = order.map(key => {
    const base = defaults.get(key)!
    const candidate = supplied.get(key)
    return { key, title: candidate?.title?.trim().slice(0, 80) || base.title, enabled: required.has(key) || candidate?.enabled !== false, required: required.has(key) }
  })
  const delay = Number(input?.followUpDelayDays)
  return { id: defaultWorkflowDefinition.id, name: input?.name?.trim().slice(0, 80) || defaultWorkflowDefinition.name, followUpDelayDays: Number.isFinite(delay) ? Math.max(0, Math.min(90, Math.round(delay))) : defaultWorkflowDefinition.followUpDelayDays, steps, updatedAt: input?.updatedAt }
}

export function createWorkflowRun(input: { id: string; jobId: string; companyName: string; jobTitle: string; definition: WorkflowDefinition; matchSnapshot: WorkflowRun['matchSnapshot']; now?: string }): WorkflowRun {
  const now = input.now || nowIso()
  const steps = input.definition.steps.filter(step => step.enabled).map(step => {
    const completed = step.key === 'discover' || step.key === 'evaluate'
    return { ...step, state: completed ? 'done' as const : 'pending' as const, ...(completed ? { completedAt: now, note: step.key === 'discover' ? '用户选择了本机已保存岗位。' : '使用当前浏览器的偏好在本地计算，未调用外部服务。' } : {}) }
  })
  const nextPending = steps.findIndex(step => step.state === 'pending')
  const currentStepIndex = nextPending < 0 ? steps.length : nextPending
  return {
    id: input.id, jobId: input.jobId, companyName: input.companyName, jobTitle: input.jobTitle, definitionId: input.definition.id, definitionName: input.definition.name, followUpDelayDays: input.definition.followUpDelayDays,
    status: nextPending < 0 ? 'completed' : 'running', steps, currentStepIndex,
    matchSnapshot: structuredClone(input.matchSnapshot), history: [
      { at: now, action: '启动工作流', detail: `岗位：${input.companyName} · ${input.jobTitle}` },
      { at: now, action: '完成：选择已保存岗位', detail: '使用本机岗位记录作为流程起点。' },
      { at: now, action: '完成：本地匹配评估', detail: `本地评分 ${input.matchSnapshot.score} 分 · ${input.matchSnapshot.decision}` },
    ], createdAt: now, updatedAt: now,
  }
}

export function completeCurrentWorkflowStep(run: WorkflowRun, input: { detail: string; resultId?: string; now?: string }): WorkflowRun {
  if (run.status !== 'running') throw new Error('只有运行中的工作流可以完成步骤。')
  const step = run.steps[run.currentStepIndex]
  if (!step || step.state !== 'pending') throw new Error('当前没有可完成的工作流步骤。')
  const now = input.now || nowIso()
  const steps = run.steps.map((item, index) => index === run.currentStepIndex ? { ...item, state: 'done' as const, completedAt: now, note: input.detail, ...(input.resultId ? { resultId: input.resultId } : {}) } : item)
  const currentStepIndex = steps.findIndex(item => item.state === 'pending')
  const status = currentStepIndex < 0 ? 'completed' : 'running'
  return { ...run, steps, currentStepIndex: currentStepIndex < 0 ? steps.length : currentStepIndex, status, updatedAt: now, history: [...run.history, { at: now, action: `完成：${step.title}`, detail: input.detail }] }
}

export function changeWorkflowStatus(run: WorkflowRun, action: 'pause' | 'resume' | 'cancel', now = nowIso()): WorkflowRun {
  const allowed = action === 'pause' ? run.status === 'running' : action === 'resume' ? run.status === 'paused' : run.status === 'running' || run.status === 'paused'
  if (!allowed) throw new Error(action === 'cancel' ? '已完成或已撤销的工作流不能再次撤销。' : `当前状态“${run.status}”不能执行此操作。`)
  const status = action === 'pause' ? 'paused' : action === 'resume' ? 'running' : 'cancelled'
  const actionLabel = action === 'pause' ? '暂停工作流' : action === 'resume' ? '恢复工作流' : '撤销工作流'
  const detail = action === 'cancel' ? '流程已停止；已经建立的投递或待办记录会保留，不会被自动删除。' : actionLabel
  return { ...run, status, updatedAt: now, history: [...run.history, { at: now, action: actionLabel, detail }] }
}

export function workflowStatusLabel(status: WorkflowRun['status']) { return ({ running: '进行中', paused: '已暂停', cancelled: '已撤销', completed: '已完成' })[status] }

export function validWorkflowStatusTransition(from: WorkflowRun['status'], to: WorkflowRun['status']) {
  return from === to || from === 'running' && ['paused', 'cancelled', 'completed'].includes(to) || from === 'paused' && ['running', 'cancelled'].includes(to)
}
