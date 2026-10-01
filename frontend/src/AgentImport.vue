<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from './api'
import { classifyDocumentText, extractDocumentText, normalizeImportValue, type ImportSuggestion } from './career-agent'
import { stableStage } from './workflow'

const emit = defineEmits<{ saved: [] }>()
type Company = { id: string; name: string }
type Job = { id: string; companyId: string; companyName: string; title: string }
type Application = { id: string; jobId: string; jobTitle: string; companyName: string; status: string; stage: string; appliedAt?: string; channel?: string }
const file = ref<File>()
const rawText = ref('')
const busy = ref(false)
const suggestions = ref<ImportSuggestion[]>([])
const warnings = ref<string[]>([])
const companies = ref<Company[]>([])
const jobs = ref<Job[]>([])
const applications = ref<Application[]>([])
const selectedJobs = ref<Record<string, string>>({})
const selectedApplications = ref<Record<string, string>>({})
const companyChoices = ref<Record<string, string>>({})
const textLength = ref(0)
const selectedCount = computed(() => suggestions.value.filter(item => item.selected).length)
const categoryLabels: Record<string, string> = { company: '企业', job: '岗位', application: '投递进展', task: '待办', profile: '个人资料' }

function updateField(item: ImportSuggestion, field: string, value: string) { item.fields[field] = value }
function setFile(event: Event) { file.value = (event.target as HTMLInputElement).files?.[0] }
function normalize(value: string) { return normalizeImportValue(value).toLocaleLowerCase() }

async function loadReferences() {
  const [companyResult, jobResult, appResult] = await Promise.all([api.get('/companies'), api.get('/jobs'), api.get('/applications')])
  companies.value = companyResult.data
  jobs.value = jobResult.data
  applications.value = appResult.data
}

function detectRelations() {
  for (const item of suggestions.value) {
    if (item.category === 'company') {
      const match = companies.value.find(company => normalize(company.name) === normalize(item.fields.name || ''))
      if (match) { item.selected = false; item.reasons.push(`本机已有同名企业“${match.name}”，为避免重复已取消此项。`) }
    }
    if (item.category === 'job') {
      const companyMatch = companies.value.find(company => normalize(company.name) === normalize(item.fields.companyName || ''))
      if (companyMatch) companyChoices.value[item.id] = companyMatch.id
      const duplicate = jobs.value.find(job => normalize(job.title) === normalize(item.fields.title || '') && (!companyMatch || job.companyId === companyMatch.id))
      if (duplicate) { item.selected = false; item.reasons.push(`本机已有相同岗位“${duplicate.companyName} · ${duplicate.title}”，为避免重复已取消此项。`) }
    }
    if (item.category === 'application') {
      const match = jobs.value.find(job => normalize(job.title) === normalize(item.fields.jobTitle || '') && (!item.fields.companyName || normalize(job.companyName) === normalize(item.fields.companyName)))
      if (match) selectedJobs.value[item.id] = match.id
      const existingApplication = match && applications.value.find(application => application.jobId === match.id)
      if (existingApplication) selectedApplications.value[item.id] = existingApplication.id
      if (existingApplication && !item.fields.status) item.selected = false
    }
    if (item.category === 'task') {
      const match = jobs.value.find(job => normalize(job.title) === normalize(item.fields.jobTitle || '') && (!item.fields.companyName || normalize(job.companyName) === normalize(item.fields.companyName)))
      if (match) selectedJobs.value[item.id] = match.id
    }
  }
}

async function parse() {
  busy.value = true
  try {
    await loadReferences()
    const text = file.value ? await extractDocumentText(file.value) : rawText.value
    if (!text.trim()) { ElMessage.warning('文档没有可读取的文字。扫描版 PDF 请先使用文字识别或复制文本。'); return }
    const result = classifyDocumentText(text)
    suggestions.value = result.suggestions
    warnings.value = result.warnings
    textLength.value = result.characterCount
    selectedJobs.value = {}; selectedApplications.value = {}; companyChoices.value = {}
    detectRelations()
    if (!suggestions.value.length) ElMessage.warning('没有识别到可归类内容，请使用文本框补充字段后重试。')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '文档解析失败')
  } finally { busy.value = false }
}

function companyFor(jobSuggestion: ImportSuggestion) {
  const id = companyChoices.value[jobSuggestion.id]
  if (id) return id
  const companySuggestion = suggestions.value.find(candidate => candidate.category === 'company' && normalize(candidate.fields.name || '') === normalize(jobSuggestion.fields.companyName || '') && candidate.selected)
  return companySuggestion ? `new:${companySuggestion.id}` : ''
}
function applicationJob(item: ImportSuggestion) {
  const selectedId = selectedJobs.value[item.id]
  if (selectedId) return selectedId
  const candidate = suggestions.value.find(job => job.category === 'job' && normalize(job.fields.title || '') === normalize(item.fields.jobTitle || '') && job.selected)
  return candidate ? `new:${candidate.id}` : ''
}

async function applySuggestions() {
  if (!selectedCount.value) return
  busy.value = true
  const createdCompanies = new Map<string, string>()
  const createdJobs = new Map<string, string>()
  const failures: string[] = []
  let saved = 0
  try {
    for (const item of suggestions.value.filter(candidate => candidate.selected && candidate.category === 'company')) {
      const name = item.fields.name?.trim()
      if (!name) { failures.push('企业名称不能为空'); continue }
      const match = companies.value.find(company => normalize(company.name) === normalize(name))
      if (match) { createdCompanies.set(item.id, match.id); item.selected = false; continue }
      const result = await api.post('/companies', { name, notes: `由资料 Agent 从文档中提取；已由用户确认。\n${item.sourceExcerpt}` })
      createdCompanies.set(item.id, result.data.id); saved++
    }
    for (const item of suggestions.value.filter(candidate => candidate.selected && candidate.category === 'job')) {
      const title = item.fields.title?.trim()
      if (!title) { failures.push('岗位名称不能为空'); continue }
      let companyId = companyFor(item)
      if (companyId.startsWith('new:')) companyId = createdCompanies.get(companyId.slice(4)) || ''
      if (!companyId) { failures.push(`岗位“${title}”尚未关联企业，请在预览卡片中选择企业或保留对应企业条目。`); continue }
      const duplicate = jobs.value.find(job => job.companyId === companyId && normalize(job.title) === normalize(title))
      if (duplicate) { createdJobs.set(item.id, duplicate.id); item.selected = false; continue }
      const result = await api.post('/jobs', { title, companyId, city: item.fields.city || '', deadline: item.fields.deadline || null, sourceUrl: item.fields.sourceUrl || '', originalText: item.fields.originalText || item.sourceExcerpt, priority: 'B', favorite: false, notes: '由资料 Agent 提取并经用户确认' })
      createdJobs.set(item.id, result.data.id); saved++
    }
    for (const item of suggestions.value.filter(candidate => candidate.selected && candidate.category === 'application')) {
      let jobId = applicationJob(item)
      if (jobId.startsWith('new:')) jobId = createdJobs.get(jobId.slice(4)) || ''
      if (!jobId) { failures.push(`投递进展“${item.fields.jobTitle || '未命名'}”没有关联岗位，未写入。`); continue }
      const status = item.fields.status || '已投递', stage = stableStage(status)
      if (!stage) { failures.push(`投递状态“${status}”没有安全的阶段映射，请先改为系统支持的状态。`); continue }
      const applicationId = selectedApplications.value[item.id]
      if (applicationId) {
        await api.put(`/applications/${applicationId}/status`, { status, stage, note: `资料 Agent 提取，人工确认：${item.sourceExcerpt}` })
      } else {
        await api.post('/applications', { jobId, status, stage, appliedAt: item.fields.appliedAt || new Date().toISOString(), channel: item.fields.channel || '文档导入', notes: `资料 Agent 提取并经用户确认：${item.sourceExcerpt}` })
      }
      saved++
    }
    for (const item of suggestions.value.filter(candidate => candidate.selected && candidate.category === 'task')) {
      const title = item.fields.title?.trim()
      if (!title) { failures.push('待办内容不能为空'); continue }
      const jobSelection = applicationJob(item)
      const jobId = jobSelection.startsWith('new:') ? createdJobs.get(jobSelection.slice(4)) || null : jobSelection || null
      await api.post('/tasks', { title, taskType: '其他', priority: 'B', dueAt: item.fields.dueAt ? new Date(`${item.fields.dueAt}T09:00:00`).toISOString() : null, timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone, jobId, completed: false })
      saved++
    }
    for (const item of suggestions.value.filter(candidate => candidate.selected && candidate.category === 'profile')) {
      const { name, ...fields } = item.fields
      const usable = Object.fromEntries(Object.entries(fields).filter(([, value]) => value.trim()))
      if (!Object.keys(usable).length) { failures.push('个人资料没有可保存字段'); continue }
      await api.post('/profiles/personal', { title: name || '文档导入的个人资料', fields: usable })
      saved++
    }
    await loadReferences()
    emit('saved')
    if (failures.length) ElMessage.warning(`已保存 ${saved} 项；${failures.length} 项未写入：${failures.join(' ')}`)
    else { ElMessage.success(`已确认并保存 ${saved} 项到本机工作区。`); suggestions.value = [] }
  } catch (error) {
    ElMessage.error(`${error instanceof Error ? error.message : '保存失败'} 已成功写入 ${saved} 项；请检查已有数据后再重试，避免重复导入。`)
  } finally { busy.value = false }
}
</script>

<template>
  <div class="panel settings-panel agent-import">
    <h2>资料 Agent · 本机文档整理</h2>
    <p>文件只在当前浏览器本地读取；不上传服务器，也不调用 AI 或外部 API。识别采用本地规则，对自由叙述的理解有限。任何数据写入前都需要你逐项确认。</p>
    <div class="agent-upload">
      <label class="agent-file">选择 DOCX / PDF / TXT / Markdown / CSV 文档<input
        type="file"
        accept=".docx,.pdf,.txt,.md,.markdown,.csv,application/pdf,text/plain"
        @change="setFile"
      ></label>
      <el-button
        type="primary"
        :loading="busy"
        :disabled="!file"
        @click="parse"
      >
        读取并整理文档
      </el-button>
      <span v-if="file">{{ file.name }} · {{ (file.size / 1024).toFixed(0) }} KiB</span>
    </div>
    <details class="agent-paste">
      <summary>也可以粘贴文本（如聊天记录或面试复盘）</summary><el-input
        v-model="rawText"
        type="textarea"
        :rows="6"
        placeholder="建议使用“企业：公司名”“岗位：职位名”“投递状态：已投递”“下一步：联系招聘者”等清晰字段。"
      /><el-button
        :loading="busy"
        :disabled="!rawText.trim()"
        @click="file=undefined;parse()"
      >
        整理粘贴文本
      </el-button>
    </details>
    <el-alert
      title="本地处理说明"
      type="info"
      :closable="false"
      show-icon
    >
      DOCX/PDF 在此设备浏览器中解析。扫描版 PDF 没有可提取文本；需要先 OCR 或复制文字。文档内容不会发送给模型。
    </el-alert>
    <div
      v-if="suggestions.length"
      class="agent-review"
      aria-live="polite"
    >
      <div class="panel-head">
        <div><h3>确认导入内容</h3><p>共读取 {{ textLength.toLocaleString() }} 字；当前勾选 {{ selectedCount }} 项。每项都可取消或编辑。</p></div><el-button
          type="primary"
          :loading="busy"
          :disabled="!selectedCount"
          @click="applySuggestions"
        >
          确认并写入本机
        </el-button>
      </div>
      <el-alert
        v-for="warning in warnings"
        :key="warning"
        :title="warning"
        type="warning"
        :closable="false"
      />
      <article
        v-for="item in suggestions"
        :key="item.id"
        class="agent-suggestion"
      >
        <div class="agent-suggestion-head">
          <el-checkbox v-model="item.selected">
            <b>{{ categoryLabels[item.category] }} · {{ item.label }}</b>
          </el-checkbox><el-tag :type="item.confidence==='高'?'success':item.confidence==='中'?'warning':'danger'">
            {{ item.confidence }}置信度
          </el-tag>
        </div>
        <p class="agent-reasons">
          {{ item.reasons.join('；') }}
        </p>
        <el-form
          label-position="top"
          class="agent-fields"
        >
          <el-form-item
            v-for="(value,key) in item.fields"
            :key="key"
            :label="String(key)"
          >
            <el-input
              :model-value="value"
              @update:model-value="updateField(item,String(key),String($event))"
            />
          </el-form-item>
          <el-form-item
            v-if="item.category==='job'"
            label="关联企业（确认后才保存）"
          >
            <el-select
              v-model="companyChoices[item.id]"
              clearable
              placeholder="从已有企业中选择；若已勾选文档中的企业，将优先关联该企业"
            >
              <el-option
                v-for="company in companies"
                :key="company.id"
                :label="company.name"
                :value="company.id"
              />
            </el-select><small v-if="item.fields.companyName&&!companies.some(company=>normalize(company.name)===normalize(item.fields.companyName))">文档中识别到新企业“{{ item.fields.companyName }}”；请同时勾选企业条目以先创建。</small>
          </el-form-item>
          <el-form-item
            v-if="item.category==='application'"
            label="关联岗位"
          >
            <el-select
              v-model="selectedJobs[item.id]"
              clearable
              placeholder="选择已有岗位；或选择本次导入的岗位"
            >
              <el-option
                v-for="job in jobs"
                :key="job.id"
                :label="`${job.companyName} · ${job.title}`"
                :value="job.id"
              /><el-option
                v-for="job in suggestions.filter(candidate=>candidate.category==='job'&&candidate.selected)"
                :key="`new-${job.id}`"
                :label="`本次新建：${job.fields.companyName||'待选企业'} · ${job.fields.title}`"
                :value="`new:${job.id}`"
              />
            </el-select>
          </el-form-item>
          <el-form-item
            v-if="item.category==='application'&&applications.length"
            label="更新已有投递（留空则新建）"
          >
            <el-select
              v-model="selectedApplications[item.id]"
              clearable
              placeholder="不覆盖已有投递"
            >
              <el-option
                v-for="application in applications.filter(entry=>entry.jobId===selectedJobs[item.id])"
                :key="application.id"
                :label="`${application.companyName} · ${application.jobTitle}（${application.status}）`"
                :value="application.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item
            v-if="item.category==='task'&&jobs.length"
            label="关联已有岗位（可选）"
          >
            <el-select
              v-model="selectedJobs[item.id]"
              clearable
              placeholder="不关联"
            >
              <el-option
                v-for="job in jobs"
                :key="job.id"
                :label="`${job.companyName} · ${job.title}`"
                :value="job.id"
              /><el-option
                v-for="job in suggestions.filter(candidate=>candidate.category==='job'&&candidate.selected)"
                :key="`new-${job.id}`"
                :label="`本次新建：${job.fields.title}`"
                :value="`new:${job.id}`"
              />
            </el-select>
          </el-form-item>
        </el-form>
        <details><summary>查看来源片段</summary><pre>{{ item.sourceExcerpt }}</pre></details>
      </article>
    </div>
    <p
      v-else-if="warnings.length"
      class="agent-warning"
    >
      {{ warnings.join(' ') }}
    </p>
  </div>
</template>

<style scoped>
.agent-import{display:grid;gap:18px}.agent-upload{display:flex;align-items:center;gap:14px;flex-wrap:wrap}.agent-file{padding:10px 14px;border:1px solid var(--el-border-color);border-radius:8px;cursor:pointer}.agent-file input{display:block;max-width:240px;margin-top:8px}.agent-paste{display:grid;gap:12px}.agent-paste summary,.agent-suggestion details summary{cursor:pointer;color:var(--el-color-primary)}.agent-review{display:grid;gap:14px}.agent-suggestion{border:1px solid var(--el-border-color);border-radius:10px;padding:16px;display:grid;gap:8px}.agent-suggestion-head{display:flex;justify-content:space-between;align-items:center;gap:12px}.agent-reasons,.agent-suggestion small{color:var(--el-text-color-secondary)}.agent-fields{display:grid;grid-template-columns:repeat(auto-fit,minmax(220px,1fr));gap:0 14px}.agent-fields pre,.agent-suggestion pre{white-space:pre-wrap;overflow-wrap:anywhere;background:var(--el-fill-color-light);padding:12px;border-radius:6px}.agent-warning{color:var(--el-color-warning)}
</style>
