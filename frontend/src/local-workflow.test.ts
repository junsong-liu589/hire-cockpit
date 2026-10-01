import { describe, expect, it } from 'vitest'
import { changeWorkflowStatus, completeCurrentWorkflowStep, createWorkflowRun, defaultWorkflowDefinition, normalizeWorkflowDefinition, validWorkflowStatusTransition, type WorkflowRun } from './local-workflow'

const matchSnapshot = { score: 80, decision: '优先查看', reasons: ['命中后端方向'], missingConditions: [], uncertainties: [], exclusionReasons: [] }
function run(): WorkflowRun {
  return createWorkflowRun({ id: 'run-1', jobId: 'job-1', companyName: '测试企业', jobTitle: '后端工程师', definition: defaultWorkflowDefinition, matchSnapshot, now: '2026-10-01T10:00:00.000Z' })
}

describe('local application workflow', () => {
  it('locks the manual approval and submission gates into the configurable template', () => {
    const definition = normalizeWorkflowDefinition({ ...defaultWorkflowDefinition, followUpDelayDays: 300, steps: defaultWorkflowDefinition.steps.map(step => ({ ...step, enabled: false, title: '' })) })
    expect(definition.followUpDelayDays).toBe(90)
    expect(definition.steps.find(step => step.key === 'manual_approval')).toMatchObject({ enabled: true, required: true })
    expect(definition.steps.find(step => step.key === 'record_application')).toMatchObject({ enabled: true, required: true })
    expect(definition.steps.find(step => step.key === 'prepare_materials')?.enabled).toBe(false)
  })

  it('records discovery and evaluation before any effect, then completes steps in sequence', () => {
    const started = run()
    expect(started.steps.slice(0, 2).every(step => step.state === 'done')).toBe(true)
    expect(started.steps[started.currentStepIndex].key).toBe('prepare_materials')
    const prepared = completeCurrentWorkflowStep(started, { detail: '已选简历版本 v2', resultId: 'resume-v2', now: '2026-10-01T10:10:00.000Z' })
    expect(prepared.steps[2]).toMatchObject({ state: 'done', resultId: 'resume-v2' })
    expect(prepared.steps[prepared.currentStepIndex].key).toBe('manual_approval')
    const approved = completeCurrentWorkflowStep(prepared, { detail: '用户批准', now: '2026-10-01T10:15:00.000Z' })
    expect(approved.steps[3].state).toBe('done')
    expect(approved.steps[approved.currentStepIndex].key).toBe('record_application')
  })

  it('allows pause, resume, and cancel while preserving an immutable audit trail', () => {
    const started = run()
    const paused = changeWorkflowStatus(started, 'pause', '2026-10-01T10:20:00.000Z')
    expect(paused.status).toBe('paused')
    expect(() => completeCurrentWorkflowStep(paused, { detail: 'must not advance' })).toThrow('只有运行中的工作流可以完成步骤。')
    const resumed = changeWorkflowStatus(paused, 'resume', '2026-10-01T10:30:00.000Z')
    const cancelled = changeWorkflowStatus(resumed, 'cancel', '2026-10-01T10:40:00.000Z')
    expect(cancelled.status).toBe('cancelled')
    expect(cancelled.history.map(entry => entry.action)).toEqual(['启动工作流', '完成：选择已保存岗位', '完成：本地匹配评估', '暂停工作流', '恢复工作流', '撤销工作流'])
    expect(cancelled.history.at(-1)?.detail).toContain('已经建立的投递或待办记录会保留')
    expect(validWorkflowStatusTransition('paused', 'running')).toBe(true)
    expect(validWorkflowStatusTransition('cancelled', 'running')).toBe(false)
  })
})
