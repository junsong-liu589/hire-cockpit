export type MatchPreferences = {
  targetTitles: string[]
  targetCities: string[]
  requiredKeywords: string[]
  preferredKeywords: string[]
  excludedKeywords: string[]
  minimumSalary: number | null
  maximumSalary: number | null
  minimumEducation: string
}
export type MatchJob = { title?: string; city?: string; salary?: string; degreeRequirement?: string; majorRequirement?: string; skillRequirement?: string; originalText?: string; notes?: string }
export type JobFit = { score: number; decision: '优先查看' | '需要核对' | '暂不匹配'; reasons: string[]; missingConditions: string[]; uncertainties: string[]; exclusionReasons: string[] }

const educationRanks: Record<string, number> = { 不限: 0, 中专: 1, 高中: 1, 大专: 2, 本科: 3, 硕士: 4, 博士: 5 }
const normalize = (value: string) => value.toLocaleLowerCase().replace(/[\s，,;；、/|·_-]+/g, '')
const contains = (text: string, term: string) => normalize(text).includes(normalize(term))

export function parsePreferenceList(value: string): string[] {
  return [...new Set(value.split(/[\n,，;；、|]+/).map(item => item.trim()).filter(Boolean))]
}

export function parseSalaryRange(value: string): [number, number] | null {
  const text = value.replace(/,/g, '').trim()
  const sharedUnitRange = text.match(/(\d+(?:\.\d+)?)\s*(?:万|k|千)?\s*[-~至]\s*(\d+(?:\.\d+)?)\s*(万|k|千)(?:\s*\/\s*月)?/i)
  if (sharedUnitRange) {
    const multiplier = sharedUnitRange[3].toLowerCase() === '万' ? 10_000 : 1_000
    return [Number(sharedUnitRange[1]) * multiplier, Number(sharedUnitRange[2]) * multiplier]
  }
  const matches = [...text.matchAll(/(\d+(?:\.\d+)?)\s*(万|k|千)?/gi)]
  if (!matches.length) return null
  const values = matches.slice(0, 2).map(match => {
    const amount = Number(match[1])
    const unit = match[2]?.toLowerCase()
    return unit === '万' ? amount * 10_000 : unit === 'k' ? amount * 1_000 : unit === '千' ? amount * 1_000 : amount
  })
  return [Math.min(...values), Math.max(...values)]
}

export function scoreJob(job: MatchJob, preferences: MatchPreferences): JobFit {
  const allText = [job.title, job.city, job.salary, job.degreeRequirement, job.majorRequirement, job.skillRequirement, job.originalText, job.notes].filter(Boolean).join(' ')
  let points = 0
  const reasons: string[] = []
  const missingConditions: string[] = []
  const uncertainties: string[] = []
  const exclusionReasons = preferences.excludedKeywords.filter(term => contains(allText, term)).map(term => `命中排除词“${term}”`)

  if (preferences.targetTitles.length) {
    const matches = preferences.targetTitles.filter(term => contains(job.title || '', term))
    if (matches.length) { points += 25; reasons.push(`岗位名称符合方向：${matches.join('、')}`) }
    else { missingConditions.push(`岗位名称与目标方向（${preferences.targetTitles.join('、')}）未直接匹配，需人工判断可迁移性`) }
  }
  if (preferences.targetCities.length) {
    if (!job.city?.trim()) uncertainties.push(`职位未写明工作城市（偏好：${preferences.targetCities.join('、')}）`)
    else {
      const matches = preferences.targetCities.filter(term => contains(job.city || '', term))
      if (matches.length) { points += 15; reasons.push(`工作地点符合：${matches.join('、')}`) }
      else missingConditions.push(`工作地点“${job.city}”不在目标城市（${preferences.targetCities.join('、')}）内`)
    }
  }
  const salary = parseSalaryRange(job.salary || '')
  if (preferences.minimumSalary !== null || preferences.maximumSalary !== null) {
    if (!salary) uncertainties.push('职位薪资未识别，无法核验薪资范围')
    else if (preferences.minimumSalary !== null && salary[1] < preferences.minimumSalary) missingConditions.push(`职位薪资最高 ${salary[1]} 元，低于期望下限 ${preferences.minimumSalary} 元`)
    else if (preferences.maximumSalary !== null && salary[0] > preferences.maximumSalary) missingConditions.push(`职位薪资最低 ${salary[0]} 元，高于期望上限 ${preferences.maximumSalary} 元`)
    else { points += 15; reasons.push(`薪资范围符合：${job.salary}`) }
  }
  if (preferences.minimumEducation && preferences.minimumEducation !== '不限') {
    const required = job.degreeRequirement || job.originalText || ''
    const found = Object.keys(educationRanks).filter(level => level !== '不限' && contains(required, level)).sort((a, b) => educationRanks[b] - educationRanks[a])[0]
    if (!found) uncertainties.push(`职位未明确学历要求，无法确认是否满足${preferences.minimumEducation}`)
    else if (educationRanks[found] > educationRanks[preferences.minimumEducation]) missingConditions.push(`职位要求${found}，高于当前学历偏好${preferences.minimumEducation}`)
    else { points += 10; reasons.push(`学历要求（${found}）不高于偏好`) }
  }
  if (preferences.requiredKeywords.length) {
    for (const term of preferences.requiredKeywords) {
      if (contains(allText, term)) { points += 15 / preferences.requiredKeywords.length; reasons.push(`已找到必需条件：“${term}”`) }
      else missingConditions.push(`职位描述未找到必需条件“${term}”；也可能是描述未写明，建议核实`)
    }
  }
  if (preferences.preferredKeywords.length) {
    const matches = preferences.preferredKeywords.filter(term => contains(allText, term))
    points += (matches.length / preferences.preferredKeywords.length) * 20
    if (matches.length) reasons.push(`加分技能/关键词：${matches.join('、')}`)
    const absent = preferences.preferredKeywords.filter(term => !matches.includes(term))
    if (absent.length) missingConditions.push(`未提及加分关键词：${absent.join('、')}`)
  }
  if (!reasons.length && !missingConditions.length && !uncertainties.length && !exclusionReasons.length) uncertainties.push('尚未设置匹配偏好，请先填写偏好后查看匹配结论。')
  const score = Math.round(Math.max(0, Math.min(100, points)))
  const decision = exclusionReasons.length ? '暂不匹配' : score >= 60 && !uncertainties.length ? '优先查看' : '需要核对'
  return { score, decision, reasons, missingConditions, uncertainties, exclusionReasons }
}

export function parseJobPosting(text: string): Partial<MatchJob> & { companyName: string; sourceUrl: string } {
  const lines = text.split(/\r?\n/).map(line => line.trim()).filter(Boolean)
  const fields = new Map<string, string>()
  for (const line of lines) {
    const match = line.match(/^\s*([^：:=]{1,24})\s*[：:=]\s*(.+?)\s*$/)
    if (match) fields.set(normalize(match[1]), match[2].trim())
  }
  const value = (...keys: string[]) => keys.map(key => fields.get(normalize(key))).find(Boolean) || ''
  const urls = text.match(/https?:\/\/[^\s)>]+/g) || []
  return {
    companyName: value('企业', '企业名称', '公司', '公司名称', 'company', 'employer'),
    title: value('岗位', '岗位名称', '职位', '职位名称', 'job title', 'position') || lines[0] || '',
    city: value('城市', '工作地点', '地点', 'city', 'location'),
    salary: value('薪资', '薪酬', '薪资范围', 'salary', 'compensation'),
    degreeRequirement: value('学历要求', '学历', 'education'),
    majorRequirement: value('专业要求', '专业', 'major'),
    skillRequirement: value('技能要求', '技能', '关键词', 'skills'),
    originalText: text.slice(0, 200_000),
    sourceUrl: value('招聘链接', '岗位链接', '职位链接', 'url', 'link') || urls[0] || '',
  }
}
