import { describe, expect, it } from 'vitest'
import { stableStage } from './workflow'

describe('stableStage', () => {
  it('maps editable Chinese statuses to stable reporting stages', () => {
    expect(stableStage('面试中')).toBe('INTERVIEW')
    expect(stableStage('已拿 Offer')).toBe('OFFER')
    expect(stableStage('主动放弃')).toBe('CLOSED')
  })
  it('refuses statuses that have not been explicitly mapped', () => {
    expect(stableStage('自定义标签')).toBeUndefined()
  })
})
