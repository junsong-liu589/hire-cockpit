<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from './api'
import { parsePreferenceList, parseJobPosting, scoreJob, type JobFit, type MatchPreferences } from './match-agent'

type Job = { id: string; companyId: string; companyName: string; title: string; city?: string; salary?: string; degreeRequirement?: string; majorRequirement?: string; skillRequirement?: string; originalText?: string; notes?: string; sourceUrl?: string; favorite?: boolean }
const emit = defineEmits<{ saved: [] }>()
const jobs = ref<Job[]>([])
const filter = ref<'全部'|'优先查看'|'需要核对'|'暂不匹配'>('全部')
const preferences = reactive<MatchPreferences>({ targetTitles: [], targetCities: [], requiredKeywords: [], preferredKeywords: [], excludedKeywords: [], minimumSalary: null, maximumSalary: null, minimumEducation: '不限' })
const titleText = ref(''), cityText = ref(''), requiredText = ref(''), preferredText = ref(''), excludedText = ref('')
const minimumSalaryText = ref(''), maximumSalaryText = ref(''), education = ref('不限')
const postingText = ref('')
const draft = reactive({ companyName: '', title: '', city: '', salary: '', degreeRequirement: '', majorRequirement: '', skillRequirement: '', sourceUrl: '', originalText: '' })
const draftFit = ref<JobFit>()
const overrideExclusion = ref(false)
const busy = ref(false)
const duplicate = computed(() => jobs.value.find(job => job.title.trim().toLocaleLowerCase() === draft.title.trim().toLocaleLowerCase() && job.companyName.trim().toLocaleLowerCase() === draft.companyName.trim().toLocaleLowerCase()))
const assessedJobs = computed(() => jobs.value.map(job => ({ ...job, fit: scoreJob(job, preferences) })).sort((a,b) => b.fit.score - a.fit.score))
const filteredJobs = computed(() => assessedJobs.value.filter(job => filter.value === '全部' || job.fit.decision === filter.value))

function loadPreferenceInputs() {
  titleText.value = preferences.targetTitles.join('、'); cityText.value = preferences.targetCities.join('、')
  requiredText.value = preferences.requiredKeywords.join('、'); preferredText.value = preferences.preferredKeywords.join('、'); excludedText.value = preferences.excludedKeywords.join('、')
  minimumSalaryText.value = preferences.minimumSalary === null ? '' : String(preferences.minimumSalary)
  maximumSalaryText.value = preferences.maximumSalary === null ? '' : String(preferences.maximumSalary)
  education.value = preferences.minimumEducation
}
async function load() {
  busy.value = true
  try {
    const [jobResult, preferenceResult] = await Promise.all([api.get('/jobs'), api.get('/match-preferences')])
    jobs.value = jobResult.data
    Object.assign(preferences, preferenceResult.data)
    loadPreferenceInputs()
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '岗位匹配信息加载失败') }
  finally { busy.value = false }
}
async function savePreferences() {
  const minimumSalary = minimumSalaryText.value.trim() ? Number(minimumSalaryText.value) : null
  const maximumSalary = maximumSalaryText.value.trim() ? Number(maximumSalaryText.value) : null
  if (minimumSalary !== null && (!Number.isFinite(minimumSalary) || minimumSalary < 0) || maximumSalary !== null && (!Number.isFinite(maximumSalary) || maximumSalary < 0)) { ElMessage.warning('薪资范围只能填写不小于 0 的数字。'); return }
  if (minimumSalary !== null && maximumSalary !== null && minimumSalary > maximumSalary) { ElMessage.warning('期望最低薪资不能高于最高薪资。'); return }
  try {
    const result = await api.put('/match-preferences', { targetTitles: parsePreferenceList(titleText.value), targetCities: parsePreferenceList(cityText.value), requiredKeywords: parsePreferenceList(requiredText.value), preferredKeywords: parsePreferenceList(preferredText.value), excludedKeywords: parsePreferenceList(excludedText.value), minimumSalary, maximumSalary, minimumEducation: education.value })
    Object.assign(preferences, result.data)
    ElMessage.success('求职偏好已保存到本机。')
  } catch { ElMessage.error('保存求职偏好失败。') }
}
function evaluatePosting() {
  if (!postingText.value.trim()) { ElMessage.warning('请粘贴职位描述或使用结构化字段填写。'); return }
  Object.assign(draft, parseJobPosting(postingText.value))
  draftFit.value = scoreJob(draft, preferences)
  overrideExclusion.value = false
}
function updateDraftAndScore() { if (draft.title || draft.originalText) draftFit.value = scoreJob(draft, preferences) }
async function saveDraftJob() {
  if (!draft.companyName.trim() || !draft.title.trim() || !draftFit.value) return
  busy.value = true
  try {
    let company = (await api.get('/companies')).data.find((item: { name: string }) => item.name.trim().toLocaleLowerCase() === draft.companyName.trim().toLocaleLowerCase())
    if (!company) company = (await api.post('/companies', { name: draft.companyName.trim(), notes: '由岗位匹配 Agent 创建；用户审核职位后确认。' })).data
    const result = await api.post('/jobs', { ...draft, companyId: company.id, deadline: null, recruitmentType: '外部职位手动导入', priority: draftFit.value.decision === '优先查看' ? 'A' : 'B', favorite: false, notes: `岗位匹配 Agent 本地评分 ${draftFit.value.score} 分；入库前由用户审核确认。` })
    jobs.value = [...jobs.value, { ...result.data, companyName: company.name }]
    postingText.value = ''; draftFit.value = undefined
    Object.assign(draft, { companyName: '', title: '', city: '', salary: '', degreeRequirement: '', majorRequirement: '', skillRequirement: '', sourceUrl: '', originalText: '' })
    emit('saved')
    ElMessage.success('已确认并加入本机岗位库。')
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '岗位保存失败') }
  finally { busy.value = false }
}
async function toggleFavorite(job: Job) {
  try { await api.post(`/jobs/${job.id}/favorite`, { favorite: !job.favorite }); job.favorite = !job.favorite; ElMessage.success(job.favorite ? '已收藏岗位' : '已取消收藏') }
  catch { ElMessage.error('岗位收藏状态更新失败') }
}
onMounted(load)
</script>

<template>
  <div class="panel settings-panel match-agent">
    <h2>岗位匹配 Agent · 本地规则评估</h2>
    <p>匹配在当前浏览器本地完成，不调用 AI/API，也不会自动搜索招聘平台。分数是偏好线索，不代表录用概率；未在职位描述中找到的信息会列为“不确定”，不会默认当成不满足。</p>
    <section class="match-preferences">
      <h3>我的求职偏好</h3>
      <el-form
        label-position="top"
        class="preference-grid"
      >
        <el-form-item label="目标岗位方向（逗号或顿号分隔）">
          <el-input
            v-model="titleText"
            placeholder="后端研发、Java 工程师"
          />
        </el-form-item>
        <el-form-item label="目标城市">
          <el-input
            v-model="cityText"
            placeholder="杭州、上海"
          />
        </el-form-item>
        <el-form-item label="必须出现的条件">
          <el-input
            v-model="requiredText"
            placeholder="例如：Java、数据库"
          />
        </el-form-item>
        <el-form-item label="加分关键词">
          <el-input
            v-model="preferredText"
            placeholder="例如：Spring、微服务"
          />
        </el-form-item>
        <el-form-item label="排除关键词">
          <el-input
            v-model="excludedText"
            placeholder="例如：销售、外包"
          />
        </el-form-item>
        <el-form-item label="我的学历">
          <el-select v-model="education">
            <el-option
              v-for="level in ['不限','中专','高中','大专','本科','硕士','博士']"
              :key="level"
              :label="level"
              :value="level"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="期望最低月薪（元，可留空）">
          <el-input
            v-model="minimumSalaryText"
            inputmode="numeric"
            placeholder="例如 15000"
          />
        </el-form-item>
        <el-form-item label="期望最高月薪（元，可留空）">
          <el-input
            v-model="maximumSalaryText"
            inputmode="numeric"
            placeholder="例如 30000"
          />
        </el-form-item>
      </el-form>
      <el-button
        type="primary"
        @click="savePreferences"
      >
        保存匹配偏好
      </el-button>
    </section>

    <section class="posting-evaluator">
      <h3>评估新职位</h3>
      <p>从招聘网页复制职位文本后粘贴。系统在本机解析字段和评分；你核对、编辑并确认后，才会创建企业/岗位记录。</p>
      <el-input
        v-model="postingText"
        type="textarea"
        :rows="7"
        placeholder="示例：公司：海风科技&#10;岗位：Java 后端工程师&#10;城市：杭州&#10;薪资：15k-25k&#10;学历要求：本科&#10;技能要求：Java、Spring&#10;然后粘贴完整职位描述……"
      />
      <el-button
        type="primary"
        :disabled="!postingText.trim()"
        @click="evaluatePosting"
      >
        本地解析并评估
      </el-button>
      <div
        v-if="draftFit"
        class="posting-draft"
      >
        <el-alert
          :title="`本地匹配：${draftFit.score} 分 · ${draftFit.decision}`"
          :type="draftFit.decision==='优先查看'?'success':draftFit.decision==='暂不匹配'?'error':'warning'"
          :closable="false"
        />
        <el-form
          label-position="top"
          class="preference-grid"
        >
          <el-form-item label="企业（新企业会在确认时创建）">
            <el-input
              v-model="draft.companyName"
              @input="updateDraftAndScore"
            />
          </el-form-item>
          <el-form-item label="岗位名称">
            <el-input
              v-model="draft.title"
              @input="updateDraftAndScore"
            />
          </el-form-item>
          <el-form-item label="城市">
            <el-input
              v-model="draft.city"
              @input="updateDraftAndScore"
            />
          </el-form-item>
          <el-form-item label="薪资范围">
            <el-input
              v-model="draft.salary"
              @input="updateDraftAndScore"
            />
          </el-form-item>
          <el-form-item label="学历要求">
            <el-input
              v-model="draft.degreeRequirement"
              @input="updateDraftAndScore"
            />
          </el-form-item>
          <el-form-item label="专业要求">
            <el-input
              v-model="draft.majorRequirement"
              @input="updateDraftAndScore"
            />
          </el-form-item>
          <el-form-item label="岗位链接">
            <el-input
              v-model="draft.sourceUrl"
              @input="updateDraftAndScore"
            />
          </el-form-item>
        </el-form>
        <h4>匹配原因</h4><ul v-if="draftFit.reasons.length">
          <li
            v-for="reason in draftFit.reasons"
            :key="reason"
          >
            {{ reason }}
          </li>
        </ul><p v-else>
          暂无正向匹配证据。
        </p>
        <h4>缺失或不符合项</h4><ul v-if="draftFit.missingConditions.length">
          <li
            v-for="reason in draftFit.missingConditions"
            :key="reason"
          >
            {{ reason }}
          </li>
        </ul><p v-else>
          没有发现明确缺失项。
        </p>
        <h4>不确定项</h4><ul v-if="draftFit.uncertainties.length">
          <li
            v-for="reason in draftFit.uncertainties"
            :key="reason"
          >
            {{ reason }}
          </li>
        </ul><p v-else>
          暂未发现明显不确定项。
        </p>
        <ul
          v-if="draftFit.exclusionReasons.length"
          class="excluded-list"
        >
          <li
            v-for="reason in draftFit.exclusionReasons"
            :key="reason"
          >
            {{ reason }}
          </li>
        </ul>
        <el-input
          v-model="draft.originalText"
          type="textarea"
          :rows="6"
          aria-label="职位描述原文"
          @input="updateDraftAndScore"
        />
        <el-checkbox
          v-if="draftFit.decision==='暂不匹配'"
          v-model="overrideExclusion"
        >
          我已核对排除原因，仍要将此岗位保存为普通记录
        </el-checkbox>
        <el-button
          type="primary"
          :disabled="!draft.companyName.trim()||!draft.title.trim()||!!duplicate||draftFit.decision==='暂不匹配'&&!overrideExclusion"
          :loading="busy"
          @click="saveDraftJob"
        >
          确认并加入本机岗位库
        </el-button>
        <el-alert
          v-if="duplicate"
          :title="`本机已有相同岗位：${duplicate.companyName} · ${duplicate.title}`"
          type="warning"
          :closable="false"
        />
        <p v-if="draftFit.decision==='暂不匹配'">
          职位命中排除条件，可调整偏好或放弃；当前按钮禁止加入，避免误把排除岗位当作推荐结果。
        </p>
      </div>
    </section>

    <section class="job-results">
      <div class="panel-head">
        <div><h3>本机已保存岗位</h3><p>{{ jobs.length }} 个岗位 · 按本地匹配分数排序</p></div><el-select
          v-model="filter"
          aria-label="匹配结果筛选"
        >
          <el-option
            v-for="value in ['全部','优先查看','需要核对','暂不匹配']"
            :key="value"
            :label="value"
            :value="value"
          />
        </el-select>
      </div>
      <p
        v-if="!filteredJobs.length"
        class="no-deadline"
      >
        暂无符合此筛选条件的岗位
      </p>
      <article
        v-for="job in filteredJobs"
        :key="job.id"
        class="job-fit-card"
      >
        <div class="job-fit-heading">
          <div><b>{{ job.companyName }} · {{ job.title }}</b><small>{{ job.city || '城市未写明' }} · {{ job.salary || '薪资未写明' }}</small></div><el-tag :type="job.fit.decision==='优先查看'?'success':job.fit.decision==='暂不匹配'?'danger':'warning'">
            {{ job.fit.score }} 分 · {{ job.fit.decision }}
          </el-tag>
        </div>
        <p v-if="job.fit.reasons.length">
          命中：{{ job.fit.reasons.join('；') }}
        </p><p v-if="job.fit.missingConditions.length">
          缺失/待核实：{{ job.fit.missingConditions.join('；') }}
        </p><p v-if="job.fit.uncertainties.length">
          不确定：{{ job.fit.uncertainties.join('；') }}
        </p><p
          v-if="job.fit.exclusionReasons.length"
          class="excluded-list"
        >
          排除：{{ job.fit.exclusionReasons.join('；') }}
        </p>
        <el-button
          size="small"
          @click="toggleFavorite(job)"
        >
          {{ job.favorite ? '取消收藏' : '收藏此岗位' }}
        </el-button>
      </article>
    </section>
  </div>
</template>

<style scoped>
.match-agent{display:grid;gap:24px}.match-preferences,.posting-evaluator,.job-results{display:grid;gap:14px;border-top:1px solid var(--el-border-color-lighter);padding-top:18px}.preference-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(220px,1fr));gap:0 16px}.posting-draft{display:grid;gap:12px;padding:16px;border:1px solid var(--el-border-color);border-radius:10px}.job-fit-card{display:grid;gap:8px;padding:16px;border:1px solid var(--el-border-color);border-radius:10px}.job-fit-heading{display:flex;align-items:center;justify-content:space-between;gap:16px}.job-fit-heading small{display:block;color:var(--el-text-color-secondary);margin-top:6px}.job-fit-card p{margin:0;color:var(--el-text-color-regular)}.excluded-list{color:var(--el-color-danger)}
</style>
