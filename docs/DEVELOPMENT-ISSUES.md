# 开发问题记录

截至 2026-10-02。这里记录开发和发布时实际遇到的问题，区分已解决的故障与仍需维护的事项。命令、运行编号和链接用于复查；不记录凭据、个人求职数据或代理账号信息。

| 编号 | 问题及定位 | 处理与验证 | 状态 |
| --- | --- | --- | --- |
| DEV-01 | 受限终端无法连接 Docker Engine 管道，曾误判为 Docker Desktop 未运行。桌面程序实际显示 Engine running。 | 在获得 Docker 管道访问权限后，确认 Engine 和 Compose 可用；使用独立 Compose 项目及端口验收，结束时只清理测试容器和卷，保留原项目数据。见 [ROADMAP 的运行环境与 CI 记录](ROADMAP.md)。 | 已解决 |
| DEV-02 | GitHub 的浏览器/API 通道出现 `SEC_E_NO_CREDENTIALS`，`gh` 在该终端不可用；这不能证明仓库或 GitHub 不可访问。代理端口也可能与之前的本地设置不同。 | `git ls-remote origin` 和 `git push` 成功。该次会话确认 HTTP 代理为 `127.0.0.1:7892`；Node HTTPS 经代理并在进程内读取现有 Git Credential Manager 授权后，可读取 Actions 日志、创建及合并 PR。凭据未写入仓库或日志。以后先检查当前代理设置与 Git/GCM 状态，不假定端口固定。 | 已解决；环境变化时重查 |
| DEV-03 | PR #6 的 CI 中，`backend` 通过，但 `frontend` 和 `compose-smoke` 在构建时失败。失败步骤后的健康检查被跳过，不能当作根因。 | 读取失败 job 日志并在 Linux 容器复现：Windows 生成的 npm 锁文件缺少 `@rollup/rollup-linux-x64-gnu`、`@rollup/rollup-linux-x64-musl` 等可选原生包。补齐带完整性校验的 Linux x64 锁文件记录，并在 CI、Pages、Docker 构建中使用 `npm ci`。PR [#6](https://github.com/junsong-liu589/hire-cockpit/pull/6) 的 [CI run #36955846361](https://github.com/junsong-liu589/hire-cockpit/actions/runs/36955846361) 三个 job 均通过；合并后的 [main CI](https://github.com/junsong-liu589/hire-cockpit/actions/runs/36956368366) 和 [Pages 发布](https://github.com/junsong-liu589/hire-cockpit/actions/runs/36956368242) 成功。 | 已解决 |
| DEV-04 | 早期浏览器 E2E 使用过期的日程按钮文案，并在 IndexedDB 写入完成前刷新页面，造成断言和时序失败。 | 更新按钮断言，等待记录可见后再刷新验证；[CI run #36858237622](https://github.com/junsong-liu589/hire-cockpit/actions/runs/36858237622) 通过。 | 已解决 |
| DEV-05 | 投递工作流把 Vue 响应式代理对象直接交给 IndexedDB，触发 structured clone 错误。 | 本地持久化适配器先转成普通 JSON 数据；[PR #6 的 CI](https://github.com/junsong-liu589/hire-cockpit/actions/runs/36955846361) 通过。 | 已解决 |
| DEV-06 | 文档在 PR #6 合并后仍写着“远端 CI、合并和发布待完成”，与实际状态不符。 | [PR #7](https://github.com/junsong-liu589/hire-cockpit/pull/7) 更新 ROADMAP 验证链接；其 [PR CI](https://github.com/junsong-liu589/hire-cockpit/actions/runs/36956698796)、[main CI](https://github.com/junsong-liu589/hire-cockpit/actions/runs/36956951352) 和 [Pages 发布](https://github.com/junsong-liu589/hire-cockpit/actions/runs/36956951386) 均通过。 | 已解决 |

## 待维护事项

| 编号 | 现状 | 后续处理 |
| --- | --- | --- |
| DEV-07 | 前端 lint 能以 0 错误退出，但本轮本地运行仍有约 1,300 条警告，主要来自既有 Vue 格式和 `any` 类型。生产构建也提示主 bundle 超过 Vite 的 500 kB 提示阈值。这些提示没有阻断 CI。 | 在后续功能开发时逐步收敛类型与格式警告，并按路由/功能拆分主 bundle；以真实页面加载表现判断优化优先级。 |
| DEV-08 | Windows 上本地 Playwright 运行结束后，Vite 预览子进程曾继续占用测试端口；当时人工停止了该进程。Linux CI 的 E2E 已通过。 | 下次修改 E2E 启动方式时验证 Windows 子进程清理；复跑前检查测试端口，避免误用旧服务。 |
| DEV-09 | GitHub Actions 曾显示 Node.js 20 运行时弃用提示，提示来自所用 Action 的运行时，不是项目的 Node 22 构建步骤；本轮 CI 均通过。 | 在所用官方 Action 提供适配版本后升级，再通过 CI 验证。 |

产品边界另见 [用户手册](PWA-USER-GUIDE.md) 和 [部署说明](DEPLOYMENT.md)：数据保存在各自浏览器，文档提取没有扫描件 OCR，招聘网站连接器尚未实现。这些是当前交付范围，并非上述 CI 故障。
