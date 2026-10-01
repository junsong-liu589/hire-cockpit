/* Local-first API adapter. All business records stay in this browser's IndexedDB. */
type Row = Record<string, any> & { id: string }
type Table = 'companies'|'jobs'|'applications'|'resumes'|'tasks'|'events'|'exams'|'interviews'|'experienceNotes'|'offers'|'profiles'|'collectionRules'|'files'|'history'|'notifications'|'dictionaries'|'tags'
const tables: Table[] = ['companies','jobs','applications','resumes','tasks','events','exams','interviews','experienceNotes','offers','profiles','collectionRules','files','history','notifications','dictionaries','tags']
const dbName = 'hire-cockpit-local'
let pendingRestore: any
let opening: Promise<IDBDatabase> | undefined
function db() {
  if (!opening) opening = new Promise((resolve, reject) => {
    const request = indexedDB.open(dbName, 1)
    request.onupgradeneeded = () => {
      const d = request.result
      const records = d.createObjectStore('records', { keyPath: ['table', 'id'] })
      records.createIndex('table', 'table')
      d.createObjectStore('settings', { keyPath: 'key' })
    }
    request.onsuccess = () => resolve(request.result)
    request.onerror = () => reject(request.error)
  })
  return opening
}
async function all<T extends Row = Row>(table: Table): Promise<T[]> {
  const d = await db(); return new Promise((resolve, reject) => {
    const r = d.transaction('records').objectStore('records').index('table').getAll(table)
    r.onsuccess = () => resolve(r.result.map((x: any) => x.value as T)); r.onerror = () => reject(r.error)
  })
}
async function get<T extends Row = Row>(table: Table, id: string): Promise<T | undefined> {
  const d = await db(); return new Promise((resolve, reject) => {
    const r = d.transaction('records').objectStore('records').get([table, id])
    r.onsuccess = () => resolve(r.result?.value); r.onerror = () => reject(r.error)
  })
}
async function put(table: Table, value: Row) {
  const d = await db(); return new Promise<void>((resolve, reject) => {
    const t = d.transaction('records', 'readwrite'); t.objectStore('records').put({ table, id: value.id, value });
    t.oncomplete = () => resolve(); t.onerror = () => reject(t.error)
  })
}
async function deleteRows(rows: Array<[Table, string]>, updates: Array<[Table, Row]> = []) {
  const d = await db(); return new Promise<void>((resolve, reject) => {
    const t = d.transaction('records', 'readwrite'), store = t.objectStore('records')
    for (const [table, id] of rows) store.delete([table, id])
    for (const [table, value] of updates) store.put({ table, id: value.id, value })
    t.oncomplete = () => resolve(); t.onerror = () => reject(t.error); t.onabort = () => reject(t.error || new Error('删除事务失败'))
  })
}
async function deleteCascade(table: Table, id: string) {
  if (!await get(table, id)) fail('记录不存在或已删除')
  const rows: Array<[Table, string]> = [[table, id]], removed = new Map<Table, Set<string>>([[table,new Set([id])]])
  const add = (kind: Table, rowId: string) => { const ids=removed.get(kind)||new Set<string>(); ids.add(rowId); removed.set(kind,ids); rows.push([kind,rowId]) }
  const allRows = await Promise.all((['jobs','applications','tasks','exams','interviews','offers','history','experienceNotes','events','notifications','files','resumes'] as Table[]).map(async kind => [kind, await all(kind)] as const))
  const data = new Map<Table, Row[]>(allRows)
  const jobs = new Set<string>(), apps = new Set<string>()
  if (table === 'companies') for (const job of data.get('jobs')!) if (job.companyId === id) jobs.add(job.id)
  if (table === 'jobs') jobs.add(id)
  if (table === 'applications') apps.add(id)
  if (jobs.size) for (const app of data.get('applications')!) if (jobs.has(app.jobId)) apps.add(app.id)
  for (const jobId of jobs) if (table !== 'jobs' || jobId !== id) add('jobs', jobId)
  for (const appId of apps) add('applications', appId)
  for (const task of data.get('tasks')!) if (jobs.has(task.jobId)) add('tasks', task.id)
  for (const kind of ['exams','interviews','offers','history'] as Table[]) for (const item of data.get(kind)!) if (apps.has(item.applicationId)) add(kind,item.id)
  for (const note of data.get('experienceNotes')!) if (jobs.has(note.jobId) || (table==='companies' && note.companyId===id)) add('experienceNotes',note.id)
  for (const event of data.get('events')!) if (jobs.has(event.jobId) || apps.has(event.applicationId) || (table==='companies' && event.companyId===id)) add('events',event.id)
  for (const notice of data.get('notifications')!) if ((notice.sourceType==='JOB'&&jobs.has(notice.sourceId)) || (notice.sourceType==='TASK'&&(removed.get('tasks')||new Set()).has(notice.sourceId))) add('notifications',notice.id)
  const updates: Array<[Table,Row]> = []
  if(table==='offers') {
    const offer=data.get('offers')!.find(x=>x.id===id), application=data.get('applications')!.find(x=>x.id===offer?.applicationId)
    if(application && !data.get('offers')!.some(x=>x.id!==id&&x.applicationId===application.id)) {
      const previous=data.get('history')!.filter(x=>x.applicationId===application.id&&x.stage!=='OFFER').sort((a,b)=>String(b.changedAt||'').localeCompare(String(a.changedAt||'')))[0]
      updates.push(['applications',{...application,status:previous?.status||'已投递',stage:previous?.stage||'APPLICATION',updatedAt:now()}])
    }
  }
  if (table==='resumes') {
    for (const app of data.get('applications')!) if(app.resumeVersionId===id) updates.push(['applications',{...app,resumeVersionId:null,updatedAt:now()}])
    const resume=data.get('resumes')!.find(x=>x.id===id)
    if(resume?.storedFileId && !data.get('resumes')!.some(x=>x.id!==id&&x.storedFileId===resume.storedFileId)) add('files',resume.storedFileId)
  }
  if(table==='files') {
    for(const resume of data.get('resumes')!) if(resume.storedFileId===id) updates.push(['resumes',{...resume,storedFileId:null,updatedAt:now()}])
  }
  await deleteRows(rows,updates)
}
const now = () => new Date().toISOString()
const uuid = () => crypto.randomUUID()
const save = async (table: Table, value: Record<string, any>): Promise<any> => { const row = { ...value, id: value.id || uuid(), createdAt: value.createdAt || now(), updatedAt: now() }; await put(table, row); return row }
const list = async (table: Table) => (await all(table)).sort((a,b) => String(b.updatedAt||'').localeCompare(String(a.updatedAt||'')))
const setting = async <T>(key: string, fallback: T): Promise<T> => {
  const d = await db(); return new Promise((resolve, reject) => { const r=d.transaction('settings').objectStore('settings').get(key); r.onsuccess=()=>resolve(r.result?.value ?? fallback); r.onerror=()=>reject(r.error) })
}
const setSetting = async (key: string, value: any) => { const d=await db(); return new Promise<void>((resolve,reject)=>{const t=d.transaction('settings','readwrite');t.objectStore('settings').put({key,value});t.oncomplete=()=>resolve();t.onerror=()=>reject(t.error)}) }
const joinJobs = async (): Promise<any[]> => { const [jobs, companies] = await Promise.all([list('jobs'),list('companies')]); return jobs.map(j=>({...j,companyName:companies.find(c=>c.id===j.companyId)?.name||''})) }
const joinApps = async (): Promise<any[]> => { const [apps,jobs,companies]=await Promise.all([list('applications'),list('jobs'),list('companies')]);return apps.map(a=>{const j=jobs.find(x=>x.id===a.jobId),c=companies.find(x=>x.id===j?.companyId);return {...a,jobTitle:j?.title||'',companyName:c?.name||''}}) }
const response = <T>(data: T) => ({ data })
function fail(message: string): never { throw Object.assign(new Error(message), { response: { data: { detail: message } } }) }
async function payloadBlob(file: Blob) { return new Promise<string>((resolve,reject)=>{const r=new FileReader();r.onload=()=>resolve(String(r.result));r.onerror=()=>reject(r.error);r.readAsDataURL(file)}) }
async function route(method: string, rawUrl: string, body?: any, config?: any): Promise<any> {
  const url = new URL(rawUrl, location.origin), path = url.pathname.replace(/^\/api\/v1/, '').replace(/\/$/, '') || '/', p = path.split('/').filter(Boolean).map(decodeURIComponent), query=url.searchParams
  if(path==='/workspace/current'&&method==='DELETE'){const d=await db();await new Promise<void>((resolve,reject)=>{const t=d.transaction(['records','settings'],'readwrite');t.objectStore('records').clear();t.objectStore('settings').clear();t.oncomplete=()=>resolve();t.onerror=()=>reject(t.error);t.onabort=()=>reject(t.error||new Error('清空事务失败'))});return response({cleared:true})}
  if (method==='GET' && path==='/workspaces/current') return response({id:'this-browser',name:'本地浏览器',storage:'IndexedDB'})
  if (path.startsWith('/profiles/')) { const section=p[1], id=p[2]; if(method==='GET')return response((await list('profiles')).filter(x=>x.section===section));if(method==='POST')return response(await save('profiles',{...body,section,visibleFields:body.visibleFields||Object.keys(body.fields||[])}));if(method==='PUT'){const old=await get('profiles',id);if(!old)fail('资料不存在');return response(await save('profiles',{...old,...body,section,id}))}if(method==='DELETE'){await deleteCascade('profiles',id);return response(null)} }
  const deletionRoutes: Record<string,Table> = {'companies':'companies','jobs':'jobs','applications':'applications','resumes':'resumes','tasks':'tasks','events':'events','exams':'exams','interviews':'interviews','experience-notes':'experienceNotes','offers':'offers','collection-rules':'collectionRules','files':'files','notifications':'notifications','dictionaries':'dictionaries','tags':'tags'}
  if(method==='DELETE' && p.length===2 && deletionRoutes[p[0]]) { await deleteCascade(deletionRoutes[p[0]],p[1]); return response(null) }
  if (path==='/companies') {if(method==='GET')return response((await list('companies')).filter(x=>!query.get('q')||x.name?.includes(query.get('q')!)));if(method==='POST')return response(await save('companies',body))}
  if (p[0]==='companies' && p[1]) {const id=p[1];if(method==='GET'){const x=await get('companies',id);return x?response(x):fail('企业不存在')}if(method==='PUT'){const old=await get('companies',id);if(!old)fail('企业不存在');return response(await save('companies',{...old,...body,id}))}}
  if (path==='/jobs') {if(method==='GET'){let rows=await joinJobs();if(query.has('favorite'))rows=rows.filter(x=>x.favorite=== (query.get('favorite')==='true'));if(query.has('companyId'))rows=rows.filter(x=>x.companyId===query.get('companyId'));if(query.get('q'))rows=rows.filter(x=>`${x.title} ${x.companyName}`.includes(query.get('q')!));return response(rows)}if(method==='POST'){if(!await get('companies',body.companyId))fail('請先選擇有效企業');return response(await save('jobs',body))}}
  if(p[0]==='jobs'&&p[1]){const id=p[1];if(p[2]==='favorite'&&method==='POST'){const x=await get('jobs',id);if(!x)fail('岗位不存在');await save('jobs',{...x,favorite:body.favorite});return response({id,favorite:body.favorite})}if(method==='GET'){const x=(await joinJobs()).find(j=>j.id===id);return x?response(x):fail('岗位不存在')}if(method==='PUT'){const x=await get('jobs',id);if(!x)fail('岗位不存在');return response(await save('jobs',{...x,...body,id}))}}
  if(path==='/applications'){if(method==='GET')return response(await joinApps());if(method==='POST'){if(!await get('jobs',body.jobId))fail('岗位不存在');const row=await save('applications',{...body,status:body.status||'已投递',stage:body.stage||'APPLICATION',appliedAt:body.appliedAt||now()});await save('history',{applicationId:row.id,status:row.status,stage:row.stage,note:'创建投递',changedAt:now()});return response(row)}}
  if(p[0]==='applications'&&p[1]){const id=p[1];if(p[2]==='history'&&method==='GET')return response((await list('history')).filter(x=>x.applicationId===id).sort((a,b)=>a.changedAt.localeCompare(b.changedAt)));if(p[2]==='status'&&method==='PUT'){const x=await get('applications',id);if(!x)fail('投递不存在');await save('applications',{...x,status:body.status,stage:body.stage,id});await save('history',{applicationId:id,status:body.status,stage:body.stage,note:body.note||'',changedAt:now()});return response({id,status:body.status,stage:body.stage})}if(method==='GET'){const x=(await joinApps()).find(a=>a.id===id);return x?response(x):fail('投递不存在')}}
  if(path==='/resumes'){if(method==='GET')return response(await list('resumes'));if(method==='POST')return response(await save('resumes',body))}
  if(path==='/files'&&method==='POST'){const file=body instanceof FormData?body.get('file'):null;if(!(file instanceof File))fail('请选择文件');if(file.size>20*1024*1024)fail('单个文件不能超过 20 MiB');const signature=new Uint8Array(await file.slice(0,8).arrayBuffer()),pdf=new TextDecoder().decode(signature.slice(0,5))==='%PDF-',png='89504e470d0a1a0a'===Array.from(signature).map(x=>x.toString(16).padStart(2,'0')).join(''),jpeg=signature[0]===0xff&&signature[1]===0xd8&&signature[2]===0xff;if(!(pdf&&file.type==='application/pdf'||png&&file.type==='image/png'||jpeg&&file.type==='image/jpeg'))fail('仅支持内容有效的 PDF、PNG、JPG 文件');const row=await save('files',{name:file.name,type:file.type,size:file.size,data:await payloadBlob(file)});return response({id:row.id,name:row.name,size:row.size})}
  if(p[0]==='files'&&p[1]==='download'&&method==='GET')return fail('文件地址无效');if(p[0]==='files'&&p[1]&&p[2]==='download'){const f=await get('files',p[1]);if(!f)fail('附件不存在');const link=document.createElement('a');link.href=f.data;link.download=f.name;link.click();return response(null)}
  if(path==='/tasks'){if(method==='GET')return response((await list('tasks')).filter(x=>query.get('includeCompleted')==='true'||!x.completed));if(method==='POST')return response(await save('tasks',{...body,completed:false}))}
  if(p[0]==='tasks'&&p[1]&&p[2]==='complete'&&method==='PUT'){const x=await get('tasks',p[1]);if(!x)fail('待办不存在');return response(await save('tasks',{...x,completed:!!body.completed,completedAt:body.completed?now():null}))}
  if(path==='/settings/reminders'){if(method==='GET')return response(await setting('reminders',{days:[7,3,1,0],timeZone:Intl.DateTimeFormat().resolvedOptions().timeZone}));if(method==='PUT'){const val={days:body.days,timeZone:body.timeZone};await setSetting('reminders',val);return response(val)}}
  if(path==='/calendar'&&method==='GET'){const from=Date.parse(query.get('from')||''),to=Date.parse(query.get('to')||'');const events:any[]=[];for(const [table,type] of [['events','EVENT'],['tasks','TASK'],['exams','EXAM'],['interviews','INTERVIEW']] as [Table,string][]){for(const x of await list(table)){const date=Date.parse(x.startsAt||x.dueAt||'');if(date>=from&&date<to)events.push({id:x.id,title:x.title||x.roundName||x.platform||x.taskType,eventType:x.eventType||type,startsAt:x.startsAt||x.dueAt,endsAt:x.endsAt,sourceType:type,timeZone:x.timeZone})}}for(const j of await joinJobs())if(j.deadline){const d=new Date(`${j.deadline}T23:59:59`);if(d.getTime()>=from&&d.getTime()<to)events.push({id:j.id,title:j.title,eventType:'JOB_DEADLINE',startsAt:d.toISOString(),sourceType:'JOB',jobId:j.id})}return response(events.sort((a,b)=>a.startsAt.localeCompare(b.startsAt)))}
  if(path==='/notifications'&&method==='GET'){await refreshLocalReminders();const notices=await list('notifications');return response(query.get('unreadOnly')==='true'?notices.filter(x=>!x.readAt):notices)}if(p[0]==='notifications'&&p[1]&&p[2]==='read'&&method==='PUT'){const x=await get('notifications',p[1]);if(!x)fail('提醒不存在');return response(await save('notifications',{...x,readAt:now()}))}
  if(path==='/exams'||path==='/interviews'||path==='/experience-notes'){const table:Table=path==='/exams'?'exams':path==='/interviews'?'interviews':'experienceNotes';if(method==='GET')return response(await list(table));if(method==='POST')return response(await save(table,body))}
  if(path==='/offers'){if(method==='GET')return response(await offerRows());if(method==='POST'){const a=await get('applications',body.applicationId);if(!a)fail('对应投递不存在');const row=await save('offers',body);await save('applications',{...a,status:'Offer',stage:'OFFER'});await save('history',{applicationId:a.id,status:'Offer',stage:'OFFER',note:'收到 Offer',changedAt:now()});return response(row)}}
  if(path==='/offers/comparison-preferences'){if(method==='GET')return response({weights:await setting('offerWeights',{compensation:.35,growth:.25,role:.2,location:.1,culture:.1})});if(method==='PUT'){await setSetting('offerWeights',body.weights);return response({weights:body.weights})}}
  if(path==='/offers/comparison'&&method==='GET'){const weights=await setting('offerWeights',{compensation:.35,growth:.25,role:.2,location:.1,culture:.1});return response({weights,offers:(await offerRows()).map(o=>({...o,comparisonScore:Math.round((Math.min(100,Number(o.baseSalary||0)/1000)*weights.compensation+Object.entries(weights).filter(([k])=>k!=='compensation').reduce((n,[k,w])=>n+Math.max(0,Math.min(100,Number(o.evaluations?.[k]||0)))*Number(w),0))*100)/100}))})}
  if(path==='/analytics'&&method==='GET'){const apps=await list('applications'),funnel=['TODO','FAVORITE','APPLICATION','SCREENING','EXAM','INTERVIEW','OFFER','CLOSED'].map(stage=>({stage,count:apps.filter(x=>x.stage===stage).length})),months:Record<string,number>={},channels:Record<string,number>={};for(const x of apps){if(x.appliedAt){const m=x.appliedAt.slice(0,7);months[m]=(months[m]||0)+1}const c=x.channel||'未填写';channels[c]=(channels[c]||0)+1}return response({funnel,monthlyApplications:Object.entries(months).sort().map(([month,count])=>({month,count})),channels})}
  if(path==='/collection-rules'){if(method==='GET')return response(await list('collectionRules'));if(method==='POST')return response(await save('collectionRules',body))}
  if(path==='/job-matches'&&method==='GET'){const rules=(await list('collectionRules')).filter(x=>x.active!==false);return response((await joinJobs()).map(j=>{const hay=`${j.title} ${j.originalText||''} ${j.notes||''}`.toLowerCase(),matchReasons:string[]=[];let matchScore=0;for(const r of rules){const hits=(r.keywords||[]).filter((k:string)=>hay.includes(k.toLowerCase()));if(hits.length){matchScore+=hits.length*Number(r.weight||0);matchReasons.push(`${r.name}: ${hits.join(', ')}`)}}return {...j,matchScore,matchReasons}}).sort((a,b)=>b.matchScore-a.matchScore))}
  if(path==='/recruitment-drafts'&&method==='POST'){let parsed:URL;try{parsed=new URL(body.url)}catch{fail('请输入有效的招聘链接')}if(!['http:','https:'].includes(parsed.protocol))fail('仅支持 HTTP/HTTPS 链接');return response({sourceUrl:parsed.href,title:'',companyName:'',city:'',deadline:'',description:'',originalText:'请在新标签页打开招聘链接，复制职位描述后粘贴到此处。静态 PWA 无法读取多数招聘网站页面（浏览器跨域限制）。',manualConfirmationRequired:true})}
  if(path==='/backup/export'&&method==='GET'){const records:any[]=[];for(const t of tables)for(const value of await all(t))records.push({table:t,value});const settingsData=await new Promise<any[]>((resolve,reject)=>db().then(d=>{const r=d.transaction('settings').objectStore('settings').getAll();r.onsuccess=()=>resolve(r.result);r.onerror=()=>reject(r.error)}));return response(new Blob([JSON.stringify({format:'hire-cockpit-local-backup',version:1,createdAt:now(),records,settings:settingsData})],{type:'application/json'}))}
  if(path==='/backup/preview'&&method==='POST'){const file=body instanceof FormData?body.get('file'):null;if(!(file instanceof File))fail('请选择备份文件');if(file.size>250*1024*1024)fail('备份文件不能超过 250 MiB');const backup=await file.text().then(JSON.parse).catch(()=>null);if(backup?.format!=='hire-cockpit-local-backup'||backup.version!==1||!Array.isArray(backup.records)||!Array.isArray(backup.settings)||backup.records.length>100000)fail('备份格式、大小或版本不受支持');for(const x of backup.records){if(!tables.includes(x?.table)||typeof x.value?.id!=='string')fail('备份中包含不支持或不完整的记录');if(x.table==='files'&&(!/^data:(application\/pdf|image\/(png|jpeg));base64,/.test(x.value.data||'')||x.value.size>20*1024*1024))fail('备份附件格式无效或超过 20 MiB')}if(backup.settings.some((x:any)=>typeof x.key!=='string'))fail('备份设置格式无效');const preview={totalRows:backup.records.length,fileCount:backup.records.filter((x:any)=>x.table==='files').length,restoreMode:'恢复将替换本浏览器当前全部业务数据。恢复前请另行导出当前数据。'};pendingRestore=backup;return response(preview)}
  if(path==='/backup/restore'&&method==='POST'){const backup=pendingRestore;if(!backup)fail('请先预览有效备份');const d=await db();await new Promise<void>((resolve,reject)=>{const t=d.transaction(['records','settings'],'readwrite');t.objectStore('records').clear();t.objectStore('settings').clear();for(const x of backup.records)t.objectStore('records').put({table:x.table,id:x.value.id,value:x.value});for(const x of backup.settings)t.objectStore('settings').put(x);t.oncomplete=()=>resolve();t.onerror=()=>reject(t.error);t.onabort=()=>reject(t.error||new Error('恢复事务失败'))});pendingRestore=undefined;return response({restored:true,rows:backup.records.length})}
  return fail(`本地功能尚未实现：${method} ${path}`)
}
async function offerRows(): Promise<any[]>{const [offers,apps,jobs,companies]=await Promise.all([list('offers'),joinApps(),list('jobs'),list('companies')]);return offers.map(o=>{const a=apps.find(x=>x.id===o.applicationId),j=jobs.find(x=>x.id===a?.jobId);return {...o,companyName:a?.companyName||'',jobTitle:a?.jobTitle||'',evaluations:o.evaluations||{}}})}
async function refreshLocalReminders(){const prefs=await setting('reminders',{days:[7,3,1,0],timeZone:Intl.DateTimeFormat().resolvedOptions().timeZone}),today=new Date(),days=Array.isArray(prefs.days)?prefs.days:[7,3,1,0];const sources=[...(await list('tasks')).filter(x=>!x.completed&&x.dueAt).map(x=>({id:x.id,title:x.title,dueAt:x.dueAt,kind:'TASK'})),...(await list('jobs')).filter(x=>x.deadline).map(x=>({id:x.id,title:x.title,dueAt:`${x.deadline}T23:59:59`,kind:'JOB'}))];for(const source of sources){const due=new Date(source.dueAt);const offset=Math.floor((new Date(due.getFullYear(),due.getMonth(),due.getDate()).getTime()-new Date(today.getFullYear(),today.getMonth(),today.getDate()).getTime())/86400000);if(offset<0||offset>30||!days.includes(offset))continue;const id=`${source.kind}:${source.id}:D-${offset}`;if(await get('notifications',id))continue;await save('notifications',{id,sourceType:source.kind,sourceId:source.id,reminderKey:`D-${offset}`,title:source.kind==='TASK'?`待办即将到期：${source.title}`:`岗位截止：${source.title}`,dueAt:due.toISOString()})}}
export const api = {
  get: async (url:string, config?:any): Promise<{data:any}> => { if(config?.params) url += `${url.includes('?')?'&':'?'}${new URLSearchParams(config.params).toString()}`; return route('GET',url,undefined,config) },
  post: async (url:string, body?:any, config?:any): Promise<{data:any}> => route('POST',url,body,config),
  put: async (url:string, body?:any, config?:any): Promise<{data:any}> => route('PUT',url,body,config),
  delete: async (url:string, config?:any): Promise<{data:any}> => route('DELETE',url,undefined,config)
}
export async function downloadStoredFile(id:string) { const f=await get('files',id);if(!f)fail('附件不存在');const a=document.createElement('a');a.href=f.data;a.download=f.name;a.click() }
export async function requestPersistentStorage() { return navigator.storage?.persist ? navigator.storage.persist() : false }
export async function storageEstimate() { return navigator.storage?.estimate ? navigator.storage.estimate() : undefined }
