<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from './api'
import { changeWorkflowStatus, completeCurrentWorkflowStep, createWorkflowRun, defaultWorkflowDefinition, normalizeWorkflowDefinition, workflowStatusLabel, type WorkflowDefinition, type WorkflowRun, type WorkflowStepKind } from './local-workflow'
import { scoreJob, type MatchPreferences } from './match-agent'

type Job = { id: string; title: string; companyId: string; companyName: string; city?: string; salary?: string; degreeRequirement?: string; majorRequirement?: string; skillRequirement?: string; originalText?: string }
type Application = { id: string; jobId: string; status: string }
type Resume = { id: string; name: string }
const emit = defineEmits<{ saved: [] }>()
const jobs = ref<Job[]>([]), applications = ref<Application[]>([]), resumes = ref<Resume[]>([]), runs = ref<WorkflowRun[]>([])
const definition = ref<WorkflowDefinition>(normalizeWorkflowDefinition(defaultWorkflowDefinition))
const selectedJobId = ref(''), definitionEditor = ref(false), busy = ref(false)
const resumeForStep = ref<Record<string, string>>({}), noteForStep = ref<Record<string, string>>({}), channelForRun = ref<Record<string, string>>({}), followUpDateForRun = ref<Record<string, string>>({})
const statusFilters = ['全部', '进行中', '已暂停', '已撤销', '已完成']
const statusFilter = ref('全部')
const availableJobs = computed(() => jobs.value.filter(job => !applications.value.some(application => application.jobId === job.id)))
const visibleRuns = computed(() => runs.value.filter(run => statusFilter.value === '全部' || workflowStatusLabel(run.status) === statusFilter.value))
const stepLabels: Record<WorkflowStepKind, string> = { discover: '发现职位', evaluate: '评估职位', prepare_materials: '准备材料', manual_approval: '人工批准', record_application: '记录投递', follow_up: '设定跟进' }

function dateValue(value: Date) { return `${value.getFullYear()}-${String(value.getMonth()+1).padStart(2,'0')}-${String(value.getDate()).padStart(2,'0')}` }
function defaultFollowUpDate(run: WorkflowRun) {
  const date = new Date(run.createdAt)
  date.setDate(date.getDate() + run.followUpDelayDays)
  return dateValue(date)
}
function setFollowUpDate(runId: string, event: Event) { followUpDateForRun.value[runId] = (event.target as HTMLInputElement).value }
function stepFor(run: WorkflowRun) { return run.steps[run.currentStepIndex] }
function isCurrent(run: WorkflowRun, key: WorkflowStepKind) { return run.status === 'running' && stepFor(run)?.key === key }
function historyTime(value: string) { return new Date(value).toLocaleString() }

async function load() {
  busy.value = true
  try {
    const [jobResult, appResult, resumeResult, runResult, definitionResult, preferenceResult] = await Promise.all([
      api.get('/jobs'), api.get('/applications'), api.get('/resumes'), api.get('/career-workflows/runs'), api.get('/career-workflows/definitions'), api.get('/match-preferences'),
    ])
    jobs.value = jobResult.data; applications.value = appResult.data; resumes.value = resumeResult.data; runs.value = runResult.data
    definition.value = normalizeWorkflowDefinition(definitionResult.data[0] || defaultWorkflowDefinition)
    preferences.value = preferenceResult.data
    for (const run of runs.value) if (run.status === 'running' && run.steps[run.currentStepIndex]?.key === 'follow_up' && !followUpDateForRun.value[run.id]) followUpDateForRun.value[run.id] = defaultFollowUpDate(run)
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '本地工作流加载失败') }
  finally { busy.value = false }
}
const preferences = ref<MatchPreferences>({ targetTitles: [], targetCities: [], requiredKeywords: [], preferredKeywords: [], excludedKeywords: [], minimumSalary: null, maximumSalary: null, minimumEducation: '不限' })

async function saveDefinition() {
  const cleaned = normalizeWorkflowDefinition(definition.value)
  try {
    const result = await api.put('/career-workflows/definitions', cleaned)
    definition.value = normalizeWorkflowDefinition(result.data)
    definitionEditor.value = false
    ElMessage.success('工作流模板已保存在此浏览器。')
  } catch { ElMessage.error('工作流模板保存失败。') }
}

async function startRun() {
  const job = jobs.value.find(item => item.id === selectedJobId.value)
  if (!job) { ElMessage.warning('请先选择一个还没有登记投递的本机岗位。'); return }
  const fit = scoreJob(job, preferences.value)
  const run = createWorkflowRun({ id: crypto.randomUUID(), jobId: job.id, companyName: job.companyName, jobTitle: job.title, definition: definition.value, matchSnapshot: fit })
  busy.value = true
  try {
    const result = await api.post('/career-workflows/runs', run)
    runs.value.unshift(result.data)
    followUpDateForRun.value[run.id] = defaultFollowUpDate(run)
    selectedJobId.value = ''
    ElMessage.success('本地工作流已启动；职位匹配在当前浏览器中完成。')
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '工作流启动失败') }
  finally { busy.value = false }
}

async function saveRun(run: WorkflowRun) {
  const result = await api.put(`/career-workflows/runs/${encodeURIComponent(run.id)}`, run)
  runs.value = runs.value.map(item => item.id === run.id ? result.data : item)
  emit('saved')
  return result.data as WorkflowRun
}

async function completeStep(run: WorkflowRun, detail: string, resultId?: string) {
  try { await saveRun(completeCurrentWorkflowStep(run, { detail, resultId })); ElMessage.success('工作流步骤已记录。') }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '工作流步骤更新失败') }
}

async function completeMaterials(run: WorkflowRun) {
  const resumeId = resumeForStep.value[run.id] || ''
  const note = noteForStep.value[run.id]?.trim() || ''
  if (!resumeId && !note) { ElMessage.warning('请选择申请材料版本，或填写准备说明后再完成此步骤。'); return }
  const resumeName = resumes.value.find(item => item.id === resumeId)?.name
  await completeStep(run, [resumeName ? `已选择材料：${resumeName}` : '', note].filter(Boolean).join('；'), resumeId || undefined)
}

async function approve(run: WorkflowRun) {
  if (!window.confirm('确认批准继续此流程？批准只记录在本机工作流中，不会登录招聘平台或替你提交。')) return
  await completeStep(run, '用户确认进入人工投递阶段。')
}

async function recordApplication(run: WorkflowRun) {
  const channel = channelForRun.value[run.id]?.trim()
  if (!channel) { ElMessage.warning('请填写实际投递渠道，例如招聘官网、BOSS直聘或内推。'); return }
  if (!window.confirm('请确认你已经亲自在招聘平台完成投递。系统接下来只会在本机建立投递记录，不会向平台发送任何内容。')) return
  const job = jobs.value.find(item => item.id === run.jobId)
  if (!job) { ElMessage.error('原岗位记录不存在，无法关联投递。'); return }
  busy.value = true
  try {
    const materialsStep = run.steps.find(item => item.key === 'prepare_materials' && item.state === 'done')
    const result = await api.post('/applications', {
      jobId: run.jobId, status: '已投递', stage: 'APPLICATION', appliedAt: new Date().toISOString(), channel,
      resumeVersionId: materialsStep?.resultId || null, notes: `通过本地工作流“${run.definitionName}”登记；请查看工作流步骤和人工确认记录。`, workflowRunId: run.id,
    })
    const updated = completeCurrentWorkflowStep(run, { detail: `用户确认已在外部平台人工提交；在本机登记投递渠道“${channel}”。`, resultId: result.data.id })
    await saveRun(updated)
    applications.value = (await api.get('/applications')).data
    ElMessage.success('本机投递记录已创建；招聘平台提交仍由你亲自完成。')
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '登记投递失败') }
  finally { busy.value = false }
}

async function scheduleFollowUp(run: WorkflowRun) {
  const date = followUpDateForRun.value[run.id] || defaultFollowUpDate(run)
  if (!/^\d{4}-\d{2}-\d{2}$/.test(date) || Number.isNaN(Date.parse(`${date}T09:00:00`))) { ElMessage.warning('请选择有效的跟进日期。'); return }
  busy.value = true
  try {
    const task = await api.post('/tasks', { title: `跟进 ${run.companyName} · ${run.jobTitle} 投递`, taskType: '联系招聘者/内推人', priority: 'B', dueAt: new Date(`${date}T09:00:00`).toISOString(), timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone, jobId: run.jobId, completed: false, workflowRunId: run.id })
    await saveRun(completeCurrentWorkflowStep(run, { detail: `已在本机创建跟进待办，计划日期：${date}。`, resultId: task.data.id }))
    ElMessage.success('跟进待办已添加到日程。')
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '跟进待办创建失败') }
  finally { busy.value = false }
}

async function setStatus(run: WorkflowRun, action: 'pause'|'resume'|'cancel') {
  if (action === 'cancel' && !window.confirm('撤销此工作流？工作流会停止并保留操作记录；已创建的投递和待办不会自动删除。')) return
  try { await saveRun(changeWorkflowStatus(run, action)); ElMessage.success(action === 'cancel' ? '工作流已撤销，已有业务记录保留。' : action === 'pause' ? '工作流已暂停。' : '工作流已恢复。') }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '工作流状态更新失败') }
}

onMounted(load)
</script>

<template>
  <div class="panel settings-panel application-workflow">
    <h2>投递工作流 · 本机编排</h2>
    <p>工作流只操作当前浏览器已保存的岗位和数据。职位评估使用本地偏好；需要你亲自提交招聘平台申请，并在“人工批准”步骤确认。撤销会停止流程但保留已建立的业务记录。页面关闭后不会后台运行。</p>
    <section class="workflow-start">
      <div class="panel-head">
        <div><h3>从本机岗位启动流程</h3><p>只显示尚未登记投递的岗位</p></div>
      </div>
      <el-select
        v-model="selectedJobId"
        placeholder="选择岗位"
      >
        <el-option
          v-for="job in availableJobs"
          :key="job.id"
          :label="`${job.companyName} · ${job.title}`"
          :value="job.id"
        />
      </el-select>
      <el-button
        type="primary"
        :disabled="!selectedJobId"
        :loading="busy"
        @click="startRun"
      >
        启动本地投递流程
      </el-button>
      <p
        v-if="availableJobs.length===0"
        class="no-deadline"
      >
        没有尚未登记投递的岗位，请先在岗位模块添加，或通过岗位匹配 Agent 解析新职位。
      </p>
    </section>

    <section class="workflow-config">
      <div class="panel-head">
        <div><h3>工作流模板</h3><p>{{ definition.name }} · 跟进默认在 {{ definition.followUpDelayDays }} 天后</p></div><el-button @click="definitionEditor=!definitionEditor">
          {{ definitionEditor ? '收起编辑' : '配置模板' }}
        </el-button>
      </div>
      <div
        v-if="definitionEditor"
        class="workflow-editor"
      >
        <el-form
          label-position="top"
          class="workflow-config-grid"
        >
          <el-form-item label="模板名称">
            <el-input v-model="definition.name" />
          </el-form-item>
          <el-form-item label="默认跟进间隔（天）">
            <el-input-number
              v-model="definition.followUpDelayDays"
              :min="0"
              :max="90"
            />
          </el-form-item>
        </el-form>
        <div
          v-for="step in definition.steps"
          :key="step.key"
          class="workflow-step-config"
        >
          <el-checkbox
            v-model="step.enabled"
            :disabled="step.required"
          >
            <span>{{ stepLabels[step.key] }}</span>
          </el-checkbox>
          <el-input
            v-model="step.title"
            :aria-label="`${stepLabels[step.key]}步骤名称`"
          />
          <small v-if="step.key==='manual_approval'">必须保留人工审批</small>
          <small v-else-if="step.required">核心步骤</small>
        </div>
        <el-button
          type="primary"
          @click="saveDefinition"
        >
          保存模板
        </el-button>
      </div>
      <ol
        v-else
        class="workflow-outline"
      >
        <li
          v-for="step in definition.steps.filter(item=>item.enabled)"
          :key="step.key"
        >
          <b>{{ step.title }}</b><small>{{ step.key==='discover'?'从本机岗位开始':step.key==='evaluate'?'本地偏好和职位文本':step.key==='manual_approval'?'需要用户点击批准':step.key==='record_application'?'你线下提交后，本机记一条投递':step.key==='follow_up'?'创建真实的本机日程待办':'可选：关联简历和准备说明' }}</small>
        </li>
      </ol>
    </section>

    <section class="workflow-runs">
      <div class="panel-head">
        <div><h3>工作流运行记录</h3><p>{{ runs.length }} 条历史流程；完成、暂停和撤销记录会保留</p></div><el-select
          v-model="statusFilter"
          aria-label="工作流状态筛选"
        >
          <el-option
            v-for="status in statusFilters"
            :key="status"
            :label="status"
            :value="status"
          />
        </el-select>
      </div>
      <p
        v-if="!visibleRuns.length"
        class="no-deadline"
      >
        暂无工作流。选择岗位后启动第一条流程。
      </p>
      <article
        v-for="run in visibleRuns"
        :key="run.id"
        class="workflow-run-card"
      >
        <div class="workflow-run-heading">
          <div><b>{{ run.companyName }} · {{ run.jobTitle }}</b><small>{{ run.definitionName }} · 启动于 {{ historyTime(run.createdAt) }}</small></div><el-tag :type="run.status==='completed'?'success':run.status==='cancelled'?'danger':run.status==='paused'?'warning':'primary'">
            {{ workflowStatusLabel(run.status) }}
          </el-tag>
        </div>
        <div class="workflow-progress">
          <div
            v-for="(step,index) in run.steps"
            :key="step.key"
            class="workflow-progress-step"
            :class="{done:step.state==='done',current:run.status==='running'&&index===run.currentStepIndex}"
          >
            <span>{{ step.state==='done'?'✓':index+1 }}</span><small>{{ step.title }}</small>
          </div>
        </div>
        <div class="workflow-fit">
          <b>岗位匹配：{{ run.matchSnapshot.score }} 分 · {{ run.matchSnapshot.decision }}</b><p v-if="run.matchSnapshot.reasons.length">
            原因：{{ run.matchSnapshot.reasons.join('；') }}
          </p><p v-if="run.matchSnapshot.missingConditions.length">
            缺失/待核对：{{ run.matchSnapshot.missingConditions.join('；') }}
          </p><p v-if="run.matchSnapshot.uncertainties.length">
            不确定：{{ run.matchSnapshot.uncertainties.join('；') }}
          </p><p v-if="run.matchSnapshot.exclusionReasons.length">
            排除：{{ run.matchSnapshot.exclusionReasons.join('；') }}
          </p>
        </div>

        <div
          v-if="isCurrent(run,'prepare_materials')"
          class="workflow-action"
        >
          <b>准备申请材料</b><el-select
            v-model="resumeForStep[run.id]"
            clearable
            placeholder="选择用于本次申请的简历版本（可选）"
          >
            <el-option
              v-for="resume in resumes"
              :key="resume.id"
              :label="resume.name"
              :value="resume.id"
            />
          </el-select><el-input
            v-model="noteForStep[run.id]"
            type="textarea"
            :rows="2"
            placeholder="准备说明，如已定制的项目经历、求职信或注意事项"
          /><el-button
            type="primary"
            @click="completeMaterials(run)"
          >
            完成材料准备
          </el-button>
        </div>
        <div
          v-else-if="isCurrent(run,'manual_approval')"
          class="workflow-action"
        >
          <b>人工批准</b><p>确认匹配判断、材料和目标岗位，再批准进入下一步。此操作不会访问招聘平台。</p><el-button
            type="primary"
            @click="approve(run)"
          >
            批准进入人工投递阶段
          </el-button>
        </div>
        <div
          v-else-if="isCurrent(run,'record_application')"
          class="workflow-action"
        >
          <b>你已在招聘平台手动提交了吗？</b><el-input
            v-model="channelForRun[run.id]"
            placeholder="实际投递渠道：招聘官网、BOSS直聘、内推等"
          /><el-button
            type="primary"
            :loading="busy"
            @click="recordApplication(run)"
          >
            确认已人工提交并登记投递
          </el-button>
        </div>
        <div
          v-else-if="isCurrent(run,'follow_up')"
          class="workflow-action"
        >
          <b>安排跟进</b><label>跟进日期 <input
            type="date"
            :value="followUpDateForRun[run.id]||defaultFollowUpDate(run)"
            @input="setFollowUpDate(run.id,$event)"
          ></label><el-button
            type="primary"
            :loading="busy"
            @click="scheduleFollowUp(run)"
          >
            创建跟进待办并完成流程
          </el-button>
        </div>

        <div class="workflow-controls">
          <el-button
            v-if="run.status==='running'"
            @click="setStatus(run,'pause')"
          >
            暂停
          </el-button><el-button
            v-if="run.status==='paused'"
            type="primary"
            @click="setStatus(run,'resume')"
          >
            恢复
          </el-button><el-button
            v-if="run.status==='running'||run.status==='paused'"
            type="danger"
            plain
            @click="setStatus(run,'cancel')"
          >
            撤销工作流
          </el-button>
        </div>
        <details class="workflow-history">
          <summary>查看每一步记录（{{ run.history.length }}）</summary><ol>
            <li
              v-for="(entry,index) in run.history"
              :key="`${entry.at}-${index}`"
            >
              <b>{{ entry.action }}</b><small>{{ historyTime(entry.at) }}</small><p>{{ entry.detail }}</p>
            </li>
          </ol>
        </details>
      </article>
    </section>
  </div>
</template>

<style scoped>
.application-workflow{display:grid;gap:24px}.workflow-start,.workflow-config,.workflow-runs{display:grid;gap:14px;border-top:1px solid var(--el-border-color-lighter);padding-top:18px}.workflow-start .el-select{max-width:680px}.workflow-outline{display:grid;gap:10px;padding-left:22px}.workflow-outline li small,.workflow-run-heading small{display:block;color:var(--el-text-color-secondary);margin-top:5px}.workflow-editor{display:grid;gap:14px;padding:16px;border:1px solid var(--el-border-color);border-radius:10px}.workflow-config-grid{display:grid;grid-template-columns:2fr 1fr;gap:0 16px}.workflow-step-config{display:grid;grid-template-columns:minmax(170px,1fr) 2fr minmax(110px,auto);align-items:center;gap:12px}.workflow-step-config small{color:var(--el-text-color-secondary)}.workflow-run-card{display:grid;gap:16px;padding:18px;border:1px solid var(--el-border-color);border-radius:12px}.workflow-run-heading{display:flex;align-items:center;justify-content:space-between;gap:12px}.workflow-progress{display:flex;gap:6px;overflow-x:auto}.workflow-progress-step{display:grid;justify-items:center;gap:6px;min-width:100px;max-width:150px;text-align:center;color:var(--el-text-color-secondary)}.workflow-progress-step span{width:28px;height:28px;display:grid;place-items:center;border:1px solid var(--el-border-color);border-radius:50%}.workflow-progress-step.done span{background:var(--el-color-success);border-color:var(--el-color-success);color:#fff}.workflow-progress-step.current span{background:var(--el-color-primary);border-color:var(--el-color-primary);color:#fff}.workflow-progress-step small{font-size:12px}.workflow-fit{padding:12px;background:var(--el-fill-color-light);border-radius:8px}.workflow-fit p{margin:7px 0 0;color:var(--el-text-color-regular)}.workflow-action{display:grid;gap:12px;padding:16px;border:1px solid var(--el-color-primary-light-7);border-radius:9px}.workflow-action label{display:flex;align-items:center;gap:10px}.workflow-controls{display:flex;gap:8px}.workflow-history summary{cursor:pointer;color:var(--el-color-primary)}.workflow-history ol{display:grid;gap:8px}.workflow-history li small{display:block;color:var(--el-text-color-secondary);margin-top:4px}.workflow-history li p{margin:4px 0}.workflow-runs>.panel-head .el-select{max-width:180px}@media(max-width:700px){.workflow-config-grid,.workflow-step-config{grid-template-columns:1fr}.workflow-run-heading{align-items:flex-start}.workflow-progress-step{min-width:86px}}
</style>
