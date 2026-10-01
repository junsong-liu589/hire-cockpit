import pdfWorkerUrl from 'pdfjs-dist/build/pdf.worker.min.mjs?url'

export type ImportCategory = 'company' | 'job' | 'application' | 'task' | 'profile'
export type ImportSuggestion = {
  id: string
  category: ImportCategory
  label: string
  confidence: '高' | '中' | '低'
  fields: Record<string, string>
  sourceExcerpt: string
  reasons: string[]
  selected: boolean
}
export type ExtractionResult = { suggestions: ImportSuggestion[]; warnings: string[]; characterCount: number }

const MAX_FILE_BYTES = 25 * 1024 * 1024
const MAX_TEXT_CHARACTERS = 300_000
const MAX_PDF_PAGES = 200

export async function extractDocumentText(file: File): Promise<string> {
  if (file.size > MAX_FILE_BYTES) throw new Error('文件超过 25 MiB，请先拆分或压缩后再导入。')
  const extension = file.name.split('.').pop()?.toLowerCase()
  let text = ''
  if (extension === 'txt' || extension === 'md' || extension === 'markdown' || extension === 'csv') {
    text = await file.text()
  } else if (extension === 'docx') {
    const mammoth = await import('mammoth')
    const result = await mammoth.extractRawText({ arrayBuffer: await file.arrayBuffer() })
    text = result.value
  } else if (extension === 'pdf') {
    const pdfjs = await import('pdfjs-dist')
    pdfjs.GlobalWorkerOptions.workerSrc = pdfWorkerUrl
    const document = await pdfjs.getDocument({ data: new Uint8Array(await file.arrayBuffer()) }).promise
    if (document.numPages > MAX_PDF_PAGES) {
      await document.destroy()
      throw new Error(`PDF 超过 ${MAX_PDF_PAGES} 页，请先拆分后再导入。`)
    }
    const pages: string[] = []
    try {
      for (let pageNumber = 1; pageNumber <= document.numPages; pageNumber++) {
        const page = await document.getPage(pageNumber)
        const content = await page.getTextContent()
        pages.push(content.items.map(item => 'str' in item ? item.str : '').join(' '))
        if (pages.join('\n').length > MAX_TEXT_CHARACTERS) break
      }
    } finally {
      await document.destroy()
    }
    text = pages.join('\n')
  } else {
    throw new Error('支持 DOCX、PDF、TXT、Markdown 或 CSV 文件。')
  }
  return text.slice(0, MAX_TEXT_CHARACTERS)
}

const clean = (value: string) => value.trim().replace(/^[-*•\s]+/, '').replace(/[；;，,。]+$/, '').trim()
const normKey = (value: string) => value.toLowerCase().replace(/[\s:：=_-]/g, '')

function labeledValues(lines: string[]) {
  const values = new Map<string, string>()
  for (const line of lines) {
    const match = line.match(/^\s*[*•-]?\s*([^：:=]{1,24})\s*[：:=]\s*(.+?)\s*$/)
    if (match) values.set(normKey(match[1]), clean(match[2]))
  }
  return values
}

function pick(values: Map<string, string>, ...labels: string[]) {
  for (const label of labels) {
    const value = values.get(normKey(label))
    if (value) return value
  }
  return ''
}

function isoDate(value: string) {
  const match = value.match(/(20\d{2})[年./-]\s*(\d{1,2})[月./-]\s*(\d{1,2})/)
  if (!match) return ''
  return `${match[1]}-${match[2].padStart(2, '0')}-${match[3].padStart(2, '0')}`
}

export function classifyDocumentText(rawText: string): ExtractionResult {
  const text = rawText.replace(/\r/g, '').slice(0, MAX_TEXT_CHARACTERS)
  const lines = text.split('\n').map(line => line.trim()).filter(Boolean)
  const values = labeledValues(lines)
  const warnings: string[] = []
  const suggestions: ImportSuggestion[] = []
  const company = pick(values, '企业', '企业名称', '公司', '公司名称', 'company', 'employer')
  const title = pick(values, '岗位', '岗位名称', '职位', '职位名称', 'job', 'job title', 'position')
  const city = pick(values, '城市', '工作地点', '地点', 'city', 'location')
  const deadline = isoDate(pick(values, '截止日期', '申请截止', '报名截止', 'deadline'))
  const sourceUrl = pick(values, '招聘链接', '岗位链接', '职位链接', 'url', 'link')
  const description = pick(values, '职位描述', '岗位描述', 'job description')
  const status = pick(values, '投递状态', '申请状态', '当前状态', '状态', 'application status')
  const appliedAt = isoDate(pick(values, '投递日期', '申请日期', 'applied at'))
  const channel = pick(values, '投递渠道', '申请渠道', '渠道', 'channel')
  const nextAction = pick(values, '待办', '下一步', '下一步行动', '跟进事项', 'action item', 'next action')
  const dueAt = isoDate(pick(values, '完成日期', '提醒日期', '跟进日期', 'due date'))
  const profileAliases: Record<string, string> = {
    姓名: '姓名', 手机号: '手机号', 电话: '手机号', 邮箱: '邮箱', 学校: '学校', 院校: '学校', 专业: '专业', 学历: '学历',
    毕业时间: '毕业时间', 技能: '技能', 核心技能: '技能', 项目经历: '项目经历', 实习经历: '实习经历', 工作经历: '工作经历',
    证书: '证书', 获奖: '获奖', 求职方向: '求职方向',
  }
  const profileFields: Record<string, string> = {}
  for (const [key, value] of values) {
    const label = Object.keys(profileAliases).find(alias => normKey(alias) === key)
    if (label && value) profileFields[profileAliases[label]] = value
  }
  const excerpts = (keys: string[]) => lines.filter(line => keys.some(key => normKey(line.split(/[：:=]/, 1)[0] || '') === normKey(key))).slice(0, 6).join('\n')
  const add = (category: ImportCategory, label: string, fields: Record<string, string>, confidence: ImportSuggestion['confidence'], reasons: string[], sourceExcerpt: string) => {
    if (Object.values(fields).every(value => !value)) return
    suggestions.push({ id: `${category}-${suggestions.length + 1}`, category, label, confidence, fields, sourceExcerpt: sourceExcerpt || text.slice(0, 240), reasons, selected: true })
  }
  if (company) add('company', '企业', { name: company }, '高', ['从企业/公司名称字段提取'], excerpts(['企业', '企业名称', '公司', '公司名称']))
  if (title) add('job', '岗位', { companyName: company, title, city, deadline, sourceUrl, originalText: description }, company ? '高' : '中', [company ? '岗位和企业均有明确字段' : '识别到岗位名称，企业需要你补选'], excerpts(['岗位', '岗位名称', '职位', '职位名称', '城市', '截止日期', '招聘链接', '职位描述']))
  if (status || appliedAt || channel) add('application', '投递进展', { companyName: company, jobTitle: title, status, appliedAt, channel }, company && title && status ? '高' : '中', ['识别到投递状态或投递信息；保存前需关联现有岗位'], excerpts(['投递状态', '申请状态', '当前状态', '状态', '投递日期', '申请日期', '投递渠道', '渠道']))
  if (nextAction) add('task', '待办 / 跟进', { title: nextAction, dueAt, companyName: company, jobTitle: title }, '中', ['识别到下一步行动；日期和关联岗位需复核'], excerpts(['待办', '下一步', '下一步行动', '跟进事项', '完成日期', '提醒日期', '跟进日期']))
  if (Object.keys(profileFields).length) add('profile', '个人资料', profileFields, '中', ['从常见简历字段抽取；请核对字段和值'], lines.filter(line => Object.keys(profileFields).some(key => line.startsWith(`${key}：`) || line.startsWith(`${key}:`) || line.startsWith(`${key}=`))).slice(0, 8).join('\n'))
  if (!suggestions.length) {
    const emails = [...new Set(text.match(/[\w.+-]+@[\w.-]+\.[A-Za-z]{2,}/g) || [])]
    const urls = [...new Set(text.match(/https?:\/\/[^\s)>]+/g) || [])]
    if (emails.length || urls.length) add('profile', '未分类联系方式', { ...(emails.length ? { 邮箱: emails.join('、') } : {}), ...(urls.length ? { 链接: urls.join('、') } : {}) }, '低', ['只识别到联系方式或链接，需手动判断归属'], text.slice(0, 240))
    else warnings.push('没有识别到明确的企业、岗位、投递、待办或个人资料字段。可先把文档改成“字段：内容”格式，或复制文本到下方手动补充。')
  }
  if (!company && title) warnings.push('岗位没有明确企业名称；需要在确认面板中选择现有企业。')
  if (suggestions.some(item => item.confidence === '低')) warnings.push('低置信度结果只作为线索，不会自动写入。')
  return { suggestions, warnings, characterCount: text.length }
}

export function normalizeImportValue(value: string) { return clean(value) }
