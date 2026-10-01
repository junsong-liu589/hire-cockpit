# Hire Cockpit 求职驾驶舱

**立即打开网页版：<https://junsong-liu589.github.io/hire-cockpit/>**

Hire Cockpit 是一个可安装的本地优先 PWA。舍友打开发布网址即可使用，不用安装 Docker、配置数据库或申请 AI 密钥。添加的企业、岗位、投递、笔面试、Offer、日程、附件和个人资料只写入各自设备浏览器的 IndexedDB，不会上传给本项目服务器，也不会自动跨设备同步。

## 使用者如何打开

在电脑或手机浏览器打开[Hire Cockpit 网页版](https://junsong-liu589.github.io/hire-cockpit/)；浏览器支持时，可选择“安装应用”或“添加到主屏幕”。初次加载后应用外壳可离线打开。请优先使用 Chrome、Edge、Safari 或 Firefox 的最新版。保存、导出和恢复方式请看[使用手册](docs/PWA-USER-GUIDE.md)。

## 免费发布到 GitHub Pages

仓库管理员需要将 GitHub Pages 来源设为 **GitHub Actions**，并确认仓库满足 GitHub Free 的 Pages 方案要求。向 `main` 推送后，`.github/workflows/pages.yml` 会构建静态 PWA 并发布；完成后 GitHub Pages 会显示固定 HTTPS 地址。将该地址发给舍友即可。应用程序源代码会公开；求职记录和附件不在代码仓库，仍留在各自浏览器本地。

首次发布需要仓库管理员在 GitHub 仓库 Settings → Pages 允许 GitHub Actions 发布；仅有本地提交不能替管理员修改 GitHub 仓库设置。

## 本地开发

```powershell
cd frontend
npm install
npm run dev
```

验证命令：

```powershell
npm run lint
npm run typecheck
npm test
npm run build
npx playwright install chromium
npm run e2e
```

后端和 Docker Compose 保留为开发/旧服务端架构，不是 PWA 使用者的前置条件。当前静态 PWA 的业务写入不调用后端 API。静态网页无法绕过招聘网站的跨域限制，因此招聘链接流程会提示用户复制职位内容并生成草稿，仍需人工确认才会入库。提醒会在打开应用时更新；应用关闭期间不保证后台推送。

## 数据边界

浏览器配置文件相互隔离；更换设备、浏览器或配置文件不会自动带上原数据。浏览器清除网站数据可能删除记录。IndexedDB 不是云备份，持久存储申请也不能替代定期导出。每位使用者应在“设置 → 备份与恢复”导出 JSON，并自行另存到其他安全位置。备份可能含简历和个人信息，请勿公开分享。

本地业务数据没有服务器端账号或加密。请使用受密码保护的个人设备；共享电脑不要填写不愿被该电脑其他用户读取的敏感信息。

## 项目结构与资料

- `frontend/`：Vue PWA、IndexedDB 数据层、离线缓存与浏览器端端到端验收。
- `backend/`、`db/`、`infra/`、`compose.yaml`：既有服务端部署/开发路线保留；PWA 使用不依赖它们。
- [阶段路线图和验证证据](docs/ROADMAP.md)
- [PWA 使用手册](docs/PWA-USER-GUIDE.md)
- [发布说明](docs/DEPLOYMENT.md)
- [安全说明](docs/SECURITY.md)
