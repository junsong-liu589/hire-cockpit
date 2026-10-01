import { describe, expect, it } from 'vitest'
import { classifyDocumentText } from './career-agent'

describe('local document classification', () => {
  it('classifies labeled career notes into linked, reviewable categories', () => {
    const result = classifyDocumentText('企业：海风科技\n岗位：Java 后端工程师\n城市：杭州\n截止日期：2026-11-20\n招聘链接：https://example.com/jobs/42\n投递状态：已投递\n投递日期：2026-10-01\n投递渠道：官网\n下一步：准备一面项目介绍\n跟进日期：2026年10月5日\n学校：浙江大学\n专业：计算机科学与技术')

    expect(result.suggestions.map(item => item.category)).toEqual(['company', 'job', 'application', 'task', 'profile'])
    expect(result.suggestions.find(item => item.category === 'job')?.fields).toMatchObject({ companyName: '海风科技', title: 'Java 后端工程师', city: '杭州', deadline: '2026-11-20' })
    expect(result.suggestions.find(item => item.category === 'application')?.fields).toMatchObject({ status: '已投递', appliedAt: '2026-10-01', channel: '官网' })
    expect(result.suggestions.every(item => item.selected)).toBe(true)
    expect(result.warnings).toEqual([])
  })

  it('warns when an applicant note has no structured career fields', () => {
    const result = classifyDocumentText('今天回顾了一下求职进度。接下来要认真准备面试。')
    expect(result.suggestions).toEqual([])
    expect(result.warnings.join(' ')).toContain('没有识别到明确的企业')
  })

  it('does not invent a company and flags a job that needs a human company link', () => {
    const result = classifyDocumentText('职位：产品经理\n城市：上海')
    expect(result.suggestions).toHaveLength(1)
    expect(result.suggestions[0]).toMatchObject({ category: 'job', confidence: '中', fields: { companyName: '', title: '产品经理' } })
    expect(result.warnings.join(' ')).toContain('岗位没有明确企业名称')
  })
})
