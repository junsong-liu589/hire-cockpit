<script setup lang="ts">
import { ref } from 'vue'
const active = ref('驾驶舱')
const nav = ['驾驶舱','企业','岗位','投递','日程','简历与材料','个人资料','笔试与面试','Offer','数据分析','设置']
</script>

<template>
  <div class="shell">
    <aside class="sidebar">
      <div class="brand"><span class="brand-mark">H</span><div><b>Hire Cockpit</b><small>秋招工作台</small></div></div>
      <div class="workspace"><span class="avatar">我</span><div><b>我的求职工作区</b><small>仅此浏览器可继续使用</small></div><span class="chevron">⌄</span></div>
      <nav><button v-for="(item,i) in nav" :key="item" :class="{selected:active===item}" @click="active=item"><span class="nav-icon">{{ ['⌂','▤','◈','↗','▦','▱','◉','✳','◇','▥','⚙'][i] }}</span>{{item}}<span v-if="item==='任务'" class="nav-count">3</span></button></nav>
      <div class="sidebar-bottom"><span class="online-dot"></span> 数据保存在安全工作区 <span class="help">?</span></div>
    </aside>
    <main class="main">
      <header class="topbar"><div class="crumb">工作台 <span>/</span> {{active}}</div><div class="top-actions"><button class="search">⌕ <span>搜索企业、岗位、标签...</span><kbd>⌘ K</kbd></button><button class="icon-btn" aria-label="提醒">♧<i></i></button><button class="user-chip"><span class="avatar">我</span> 我的工作区⌄</button></div></header>
      <section class="content" v-if="active==='驾驶舱'">
        <div class="welcome"><div><div class="eyebrow">MONDAY, SEPTEMBER 30, 2026 <span class="sun">☼</span></div><h1>早上好，准备好开启新的一天了吗？</h1><p>每一步积累，都会让理想的机会离你更近。</p></div><button class="primary" @click="active='岗位'">＋ 添加新岗位</button></div>
        <div class="stats"><article class="stat-card"><div class="stat-top"><span>已收藏岗位</span><span class="stat-icon lilac">♡</span></div><div class="stat-value">— <small>个</small></div><div class="stat-note"><span class="muted-dot"></span> 持续发现好机会</div></article><article class="stat-card"><div class="stat-top"><span>已投递</span><span class="stat-icon blue">↗</span></div><div class="stat-value">— <small>个</small></div><div class="stat-note">所有申请，一目了然</div></article><article class="stat-card"><div class="stat-top"><span>面试进行中</span><span class="stat-icon amber">♧</span></div><div class="stat-value">— <small>场</small></div><div class="stat-note">认真准备，从容应对</div></article><article class="stat-card"><div class="stat-top"><span>收获 Offer</span><span class="stat-icon green">◇</span></div><div class="stat-value">— <small>份</small></div><div class="stat-note">每份努力都值得</div></article></div>
        <div class="grid-main"><article class="panel pipeline"><div class="panel-head"><div><h2>求职进展</h2><p>从发现机会到收获 Offer</p></div><button class="text-button" @click="active='数据分析'">查看分析 <span>→</span></button></div><div class="funnel-empty"><div class="funnel-art"><span>♡</span><span>↗</span><span>✳</span><span>◇</span></div><b>你的求职故事，从这里开始</b><p>添加第一个岗位，开启你的求职旅程</p><button class="outline" @click="active='岗位'">去添加岗位 <span>→</span></button></div></article><article class="panel today"><div class="panel-head"><div><h2>今日安排</h2><p>周一 · 9月30日</p></div><button class="round-add" @click="active='日程'">＋</button></div><div class="empty-task"><span>☼</span><b>今天还没有安排</b><p>给自己定个小目标吧</p><button class="text-button" @click="active='日程'">＋ 创建任务</button></div></article></div>
        <div class="bottom-grid"><article class="panel deadlines"><div class="panel-head"><div><h2>近期截止</h2><p>别错过重要的时间节点</p></div><button class="text-button" @click="active='日程'">全部日程 →</button></div><div class="no-deadline">暂无即将截止的事项</div></article><article class="tip"><span class="tip-spark">✦</span><div><small>求职小贴士</small><p>一份有针对性的简历，胜过海投百份。</p><button @click="active='简历与材料'">管理简历版本 →</button></div><span class="tip-orb"></span></article></div>
        <footer>© 2026 Hire Cockpit <span>用心规划，静待花开 <b>✳</b></span></footer>
      </section>
      <section v-else class="content placeholder"><div class="welcome"><div><div class="eyebrow">HIRE COCKPIT</div><h1>{{active}}</h1><p>管理你的求职信息与进展。</p></div><button class="primary">＋ 新建{{active}}</button></div><div class="panel empty-state"><span>✳</span><b>从这里开始记录</b><p>当前工作区还是空的，添加一条记录来开始使用。</p></div></section>
    </main>
  </div>
</template>
