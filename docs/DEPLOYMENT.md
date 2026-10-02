# PWA 发布与运行

## 目标使用方式

首选 GitHub Pages 静态托管：无需常驻服务器、数据库、Docker 或用户账号。Pages 只提供版本化 HTML/JS/CSS 文件；每个人的业务记录由浏览器端 IndexedDB 保存。发布的代码仓库是公开可读的，但业务记录、附件和 JSON 备份不会经由本应用上传。

## 管理员首次设置

1. 先确认仓库内不含 `.env`、真实简历、备份或用户资料。
2. 在 GitHub 仓库 Settings → Pages 中，将 Build and deployment 的 Source 设为 **GitHub Actions**。
3. 向 `main` 推送后，检查 `Deploy local-first PWA` workflow 的 build/deploy 两个 job。
4. 在 Settings → Pages 取得发布网址（项目 Pages 通常形如 `https://<owner>.github.io/hire-cockpit/`），HTTPS 页面是安装 PWA 和使用持久存储 API 的环境。
5. 管理员把固定网址发给使用者。第一次打开需联网；安装后已缓存的应用外壳支持离线启动。

此仓库尚未从本机对 GitHub Pages 完成设置或真实公网发布；需要仓库 Settings 权限和成功运行的 GitHub Actions。GitHub Pages 免费方案面向公开仓库；仓库公开意味着源代码公开，不意味着本地 IndexedDB 数据公开。

## 发布配置

- workflow：`.github/workflows/pages.yml`
- 构建命令：`npm ci && npm run lint && npm run typecheck && npm test && npm run build`
- GitHub Pages 项目路径：`/hire-cockpit/`，通过 `PWA_BASE_PATH` 配置 Vite；仓库改名时同步修改 workflow 的路径。
- 发布权限限于 `contents: read`、`pages: write`、`id-token: write`；使用官方 Pages artifact/deploy Actions。

## 本地验收

```powershell
cd frontend
npm ci
npm run lint
npm run typecheck
npm test
npm run build
npx playwright install chromium
npm run e2e
```

PWA 主体不需后端。根目录 Docker Compose 和 Java 后端为旧服务端架构/兼容开发环境保留，不能用它们来判断 Pages 已公网部署。

## 浏览器和持久数据

本地记录绑定到站点来源、设备和浏览器配置文件，不能跨设备自动同步。可用时应用会申请持久存储，但用户仍应在“设置 → 备份与恢复”导出 JSON，并把备份保存到不同位置。清除网站数据前先导出。备份含个人信息和简历附件，应按敏感文件保管；切勿提交到 GitHub。

已安装 PWA 的浏览器可能保留旧静态资源一段时间。发布新版本时 Service Worker 的缓存版本会更新；用户联网重新访问可加载新版本。不要要求用户清除整个站点数据，因为那会同时清除 IndexedDB 业务记录。

## 能力边界

- PWA 不提供账号、远程协作、云同步或中心化恢复；同一链接的不同浏览器数据独立。
- 纯浏览器端不能任意读取其他招聘网站的 HTML。采集流程要求用户打开招聘链接、复制内容、人工审核并确认入库。
- 提醒在应用打开时生成；关闭应用时没有后台定时器，也不保证系统推送。
- 本地资料与备份不做服务端加密。浏览器扩展、设备恶意软件或该站点来源下的恶意代码可能访问本地数据。公开部署前需审阅代码和依赖，并避免同源承载不可信内容。
- 无 AI 密钥时，企业/岗位/投递等记录、关键词匹配和分析仍可运行。
