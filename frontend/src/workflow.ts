export type StableStage = 'TODO' | 'FAVORITE' | 'APPLICATION' | 'SCREENING' | 'EXAM' | 'INTERVIEW' | 'OFFER' | 'CLOSED'
const stageByStatus: Record<string, StableStage> = {
  '待投递': 'TODO', '已收藏': 'FAVORITE', '已投递': 'APPLICATION', '简历筛选中': 'SCREENING',
  '笔试待考': 'EXAM', '笔试完成': 'EXAM', '面试中': 'INTERVIEW', '一面待面试': 'INTERVIEW',
  '二面待面试': 'INTERVIEW', '三面待面试': 'INTERVIEW', '谈 Offer': 'OFFER', '已拿 Offer': 'OFFER',
  '简历挂': 'CLOSED', '笔试挂': 'CLOSED', '面试挂': 'CLOSED', '拒绝 Offer': 'CLOSED',
  '主动放弃': 'CLOSED', '岗位关闭': 'CLOSED'
}
export function stableStage(status: string): StableStage | undefined { return stageByStatus[status] }

