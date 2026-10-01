import { describe, expect, it } from 'vitest'
import { parseJobPosting, parsePreferenceList, parseSalaryRange, scoreJob, type MatchPreferences } from './match-agent'

const preferences: MatchPreferences = { targetTitles: ['后端研发'], targetCities: ['杭州'], requiredKeywords: ['Java'], preferredKeywords: ['Spring', '微服务'], excludedKeywords: ['外包'], minimumSalary: 15_000, maximumSalary: 30_000, minimumEducation: '本科' }

describe('local job match agent', () => {
  it('parses preference lists and salary notations without a remote service', () => {
    expect(parsePreferenceList('Java，Spring\nJava; SQL')).toEqual(['Java', 'Spring', 'SQL'])
    expect(parseSalaryRange('15k-25k')).toEqual([15_000, 25_000])
    expect(parseSalaryRange('1.5-2万/月')).toEqual([15_000, 20_000])
    expect(parseSalaryRange('薪资面议')).toBeNull()
  })

  it('explains evidence and missing conditions for a strong local match', () => {
    const fit = scoreJob({ title: '后端研发工程师', city: '杭州', salary: '18k-28k', degreeRequirement: '本科及以上', skillRequirement: 'Java、Spring、微服务' }, preferences)
    expect(fit.score).toBeGreaterThanOrEqual(80)
    expect(fit.decision).toBe('优先查看')
    expect(fit.reasons.join(' ')).toContain('已找到必需条件')
    expect(fit.uncertainties).toEqual([])
  })

  it('marks missing data as uncertain and a blocked keyword as excluded', () => {
    const fit = scoreJob({ title: '后台开发', originalText: '该岗位薪资面议，工作地点可协商，外包项目' }, preferences)
    expect(fit.decision).toBe('暂不匹配')
    expect(fit.exclusionReasons.join(' ')).toContain('外包')
    expect(fit.uncertainties.join(' ')).toContain('薪资未识别')
    expect(fit.missingConditions.some(item => item.includes('Java'))).toBe(true)
  })

  it('parses a pasted listing into an editable local job draft', () => {
    expect(parseJobPosting('公司：海风科技\n职位：Java 后端工程师\n工作地点：杭州\n薪酬：15k-25k\n学历要求：本科\n技能：Java、Spring\n招聘链接：https://example.com/job/1')).toMatchObject({
      companyName: '海风科技', title: 'Java 后端工程师', city: '杭州', salary: '15k-25k', degreeRequirement: '本科', sourceUrl: 'https://example.com/job/1',
    })
  })
})
