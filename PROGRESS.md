# 项目进度（PROGRESS）

> 开发/运维过程中持续更新，作为 AI 会话间衔接的"记忆"。每次开发结束必须更新本文件。

## 项目状态

- 当前分支：`test`（工作分支，`PROGRESS.md` 只在此分支维护）· `master`（稳定主干，不放文档类文件）。版本 `0.2.0`（`pom.xml` 与 `CHANGELOG.md` 一致）。两分支关系是**分叉**：不直接 merge（会把 `master` 已删除的语料与 skills 复活），只把 `test` 的功能提交 **cherry-pick** 过去——约定与实践命令见「下一步」与「风险 / 遗留问题」。两分支均已推送、本地与远端同步；临时分支 `knowledge-user-manage` 待删除（远端同理）。
- 一句话现状：规则体系、子智能体、进度/版本管理均已落地；Web 端「知识库文档管理」（`0.1.0`）与「用户管理」（`0.2.0`）完成开发 + 自测 + 独立审查并经用户验收，已合入 `test` 并 cherry-pick 到 `master`。**当前需求「大模型切换 + 大模型展示页」三端已验收、文档已同步、改动尚未提交**（分支 `knowledge-switch-model`，基于 `test`）：Web 端 4 个模型可切（deepseek-flash / deepseek-v4-pro / qwen3.7-plus / qwen3.8-flash）+ `/models` 展示页；安卓为 WebView 壳（部署新 H5 即生效、免重打包）；小程序同步实现模型切换。期间顺带修掉 `qwen3.8-flash` 带工具时的流式工具调用报错（Spring AI M6 缺陷，见陷阱 62）。仍待推进的事项见「后续优化（待办）」与「下一步」。

## 需求 / 任务清单

| 状态 | 任务 | 说明 / 验收点 |
|------|------|--------------|
| [x] 完成 | Claude Code 上下文窗口放大 | `deepseek-v4-flash[1m]`，/context 显示 1M |
| [x] 完成 | 规则文档体系重构 | CLAUDE.md 精简（467→215 行）、陷阱移入 docs/known-pitfalls.md、删除 RULES.md |
| [x] 完成 | 通用规则模板 | docs/rules-template.md（含 AI 行为红线/命名/依赖/安全/健壮性通用层） |
| [x] 完成 | 功能测试子智能体 | Zcode `code-test.yaml` + Claude Code `code-test.md` |
| [x] 完成 | 项目进度文档 | PROGRESS.md + docs/PROGRESS-template.md |
| [x] 完成 | 新会话验证整套流程 | 违规诱捕自测命中 request.js 陷阱 38（P1）；code-test 子智能体独立复测一致，规则/子智能体确认生效 |
| [x] 完成 | 规划/架构子智能体 | Zcode `planner.yaml` + Claude Code `planner.md` |
| [x] 完成 | CHANGELOG 收尾 | 创建根目录 `CHANGELOG.md` |
| [x] 完成 | remote URL 更新 | 已改为 shuidoufu/knowledge-ai-agent，a9c6e55 推送成功 |
| [x] 完成 | web: 语音识别友好错误提示 | 过短(<0.6s)前端拦截 + 后端 400 带 message + 前端映射中文提示 |
| [x] 完成 | web: 历史对话滚动条与收起按钮重叠 | 收起按钮置于侧边栏右缘外侧(left:260px 不居中)，滚动条贴右侧边框 |
| [x] 完成 | web: 隐藏历史对话中的个人信息组件 | /knowledge 页 user-dock 恒隐藏（临时方案，见后续优化） |

### 本次需求（仅 Web 端）—— 已完成并通过验收（2026/9/16 合并到 `test` 并推送）

| 状态 | 任务 | 说明 / 验收点 |
|------|------|--------------|
| [x] 完成 | 管理员身份 | `User` 加 `role`（0 普通 / 1 管理员，默认 0）；登录与 `/auth/me` 返回 `isAdmin`；6 个管理接口在 service 层查库校验，非管理员 403、无 token 401 |
| [x] 完成 | 文档上传 | 仅 `.md` + 校验（扩展名/大小/UTF-8/文件名净化/重名拒绝）；后台单线程串行执行 预处理 → 落盘 → 原地覆盖为预处理结果 → 分块 → 向量化 |
| [x] 完成 | 文档删除 / 批量删除 | 先删该文档向量再删文件；提示含清理的切片数 |
| [x] 完成 | 文档列表 / 查看 | 名称+文件名 / 大小 / 状态 / 上传时间 / 操作；模糊搜索 + 时间与名称排序 + 分页（每页 10 条）；详情弹窗展示预处理正文与分块结果 |
| [x] 完成 | 状态实时可见 | 预处理中 / 向量化中 / 已完成 / 未入库 / 预处理失败 / 向量化失败 + 失败原因；前端 1.5s 轮询、终态弹提示；未入库与失败可「重新入库」 |
| [x] 完成 | 个人中心入口 | 头像下拉「知识库管理」仅管理员可见；支持返回上一级；非管理员直连 URL 被守卫拦回首页并提示 |
| [x] 完成 | 页面布局 | 按确认结论：不做统计卡、不做分类列；底部「共 N 条 · 第 X/Y 页」分页 |
| [x] 完成 | 自测 | 后端全链路 + 失败路径 + 403/401 + 回归；前端 build + 浏览器实操；独立代码审查 + 独立功能交叉验证（见改动记录） |

### 本次需求（仅 Web 端）—— 用户管理（管理员授权）｜已完成并通过验收（2026/9/21，分支 `knowledge-user-manage`，功能提交 `f6a5a30`）

> 计划清单：`docs/plans/user-manage.md`（未纳入版本管理）

| 状态 | 任务 | 说明 / 验收点 |
|------|------|--------------|
| [x] 完成 | 用户列表 | 分页（每页 10 条、显示总数与当前页，参考知识库管理页）+ 用户名模糊搜索 + 排序下拉（注册时间倒序/正序、用户名升序）；展示用户名 / 用户ID / 权限 / 注册时间 / 最后登录 / 更新时间，**密码不进接口** |
| [x] 完成 | 批量改权限 | 批量模式勾选 → 底部操作条「设为管理员(n) / 取消管理员(n)」+ 二次确认弹窗；**禁止改自己**（前端勾选框禁用 + 后端拒绝）；后端兜底「至少保留 1 个管理员」 |
| [x] 完成 | 页面入口 | 个人中心菜单「用户管理」置于「知识库管理」**上方**，仅管理员可见；非管理员直连 URL 被守卫拦回首页并提示 |
| [x] 完成 | 自测 | 后端 curl 全链路（401 / 403 / 分页 / 搜索 / 排序 / 两项防护 / 权限即时生效）+ 回归；前端 build + 浏览器实操；独立审查（详见改动记录：另修复审查发现的 4 项缺陷） |

### 本次需求—— 大模型切换 + 大模型展示页｜**三端已验收、文档已同步，改动待提交**（2026/9/28–29，分支 `knowledge-switch-model`）

> 计划清单：`docs/plans/switch-model.md`（未纳入版本管理）

| 状态 | 任务 | 说明 / 验收点 |
|------|------|--------------|
| [x] 完成 | 模型清单（4 个） | `deepseek-flash` / `deepseek-v4-pro` / `qwen3.7-plus` / `qwen3.8-flash`，一个模型一个 ChatClient Bean，Bean 名 = 模型 key；默认 `deepseek-flash`（与改动前行为一致） |
| [x] 完成 | 聊天框模型下拉框 | 位置在输入框工具栏、**RAG 知识库开关左侧**；收起态显示当前模型名，展开态列出全部并给当前项打勾；不可用项置灰 +「未配置密钥」提示 |
| [x] 完成 | 模型选择的保持 | 仅存浏览器本地（localStorage）全局生效，刷新/重开页面不丢；对话记忆按 chatId 存在于 MongoDB、与模型无关，**切换后上下文天然连续** |
| [x] 完成 | 后端切换能力 | 两个流式接口（无 RAG / 有 RAG）加可选 `model` 参数，未知或不可用模型返回 400 + 中文提示；不传时代码路径与行为完全同改动前（App / 小程序零改动） |
| [x] 完成 | 模型展示页 `/models` | 纯展示无操作：官方名 / 型号 key / 厂商 / 简介 / 技术参数（上下文·最大输入·最大输出·最大思维链·限流）/ 价格（按各厂商口径分档）/ 能力标签 / 数据来源链接 + 核对日期；未配密钥打「未配置密钥」标签 |
| [x] 完成 | 页面入口 | 个人中心下拉新增「大模型」，**所有登录用户可见**（不设管理员门槛），入口排在「修改密码」之前；桌面端 `/knowledge` 因既有临时隐藏 dock（陷阱 51）看不到该入口 |
| [x] 完成 | 新旧两模式可切换 | `KnowledgeApp` 原构造器整体注解保留标注「模式一（备用）」，新增「模式二」构造器；唯一取用点 `resolveChatClient(String model)`，切换只需注释/放开构造器 + 一行 return，注释里附「切回模式一」步骤清单 |
| [x] 完成 | 自测 | 后端：`mvnw -o test-compile` 通过、启动无 Bean 冲突、4 模型逐个 curl（无 RAG + RAG）、**同一 chatId 跨模型追问验证记忆连续**、未知模型 400、旧 `sse`/`sse/emitter` 回归、登录/历史/文档/用户接口回归；前端 `npm run build` + 浏览器实操（下拉开合 3 条关闭路径、↑↓/Enter 键盘操作、localStorage 持久化、请求 URL 确认带 `model=`、320/375/1280 三档无横向溢出、展示页 4 张卡与入口跳转） |
| [x] 完成 | 独立验证 + 审查 | 独立功能验证 A–K 全通过（含跨模型记忆连续：deepseek 记 4271 → 切千问答出 4271）；独立代码审查无 P0/P1，2 个 P2（不可用模型不回退、清单晚到不重定位/无高亮）+ 若干 P3 已按条修复，1 条不成立（App.vue 缩进经逐字符核对与相邻按钮一致）已驳回 |
| [x] 完成 | 用户验收发现的缺陷修复 | **`qwen3.8-flash` + 工具报 `toolInput cannot be null or empty`**：根因是 Spring AI 1.0.0-M6 的流式合并缺陷（官方 PR #6381 已修）把该模型的工具调用劈成两半，新增 `config/ToolCallRepairingManager` 拼接修复并挂在千问 ChatModel 上；原失败场景两条、3 个模型回归、RAG、跨模型记忆连续全部复测通过，后端日志零 ERROR |

### 前端问题修复（2026/9/16）

| 状态 | 问题现象 | 根因 | 处理方式 |
|------|----------|------|----------|
| [x] 完成 | 排序下拉窗口 UI 与页面主体风格不符（原生控件默认外观） | 用的是原生 `<select class="sort-select">`，未做外观定制（无 `appearance:none`、无自定义箭头），浏览器默认渲染风格与页面玻璃拟态不一致 | 改为自定义下拉：触发按钮对齐搜索框视觉（44px/圆角 12px/聚焦绿光晕），面板 Teleport 到 body + fixed 定位 + 玻璃拟态；点击外部、Esc、Tab、页面滚动均关闭 |
| [x] 完成 | 查看文档内容时「分块结果」显示异常——内容过多不显示、全挤在一页 | `.chunk-list` 是 `display:flex; flex-direction:column` 容器，而 `.chunk-item` 自带 `overflow:hidden`；按 Flexbox 规范，子项 `overflow` 非 `visible` 时自动最小尺寸为 **0**，于是分块项被压缩、内容被裁切 | 分块项加 `flex-shrink: 0` 消除根因；卡片高度固定、只展示部分内容（2 行预览），详情由点击展开 |
| [x] 完成 | 分块详情显示不全，且「有的有滚动条、有的没有」 | 与上一条同源（被压扁裁切，所以看不到滚动条）；叠加 `.chunk-text` 只设 `max-height:320px`，形成"外层列表挤压 + 内层各自滚动"的双层滚动：短内容不滚、长内容滚 | 展开的分块固定高度（320px）+ 内部滚动，并补齐与 `.pane-body` 一致的 6px 细滚动条样式，使滚动表现统一 |

- 呈现方式（用户确认）：**不用弹窗**——分块折叠式，每个分块最右侧空心三角箭头（Lucide `TriangleRight`，展开时旋转 90°）指示开合，点击展开显示详情，展开的分块大小固定、超长内容内部滚动，其余分块依次往下排。
- 验收点：下拉开合/选中/点外部关闭/Esc 关闭、面板不被裁剪；分块卡片高度一致、列表可滚动；展开后高度固定、超长内容内部滚动、再点收起；搜索/排序/分页/批量/上传/删除/重新入库与左侧「预处理后正文」栏不回归。
- 改动范围：仅前端（`frontend/src/views/KnowledgeDocuments.vue`）+ 本文件；后端经核查对分块正文无截断、无数量上限，不动。
- 已实测（浏览器）：76 分块文档下每张卡片高度一致（87px）、零裁切，连 9 字的最短分块也是 87px；展开后卡片 363px＝头部 41px + 正文固定 320px，3124 字分块内部滚动（scrollHeight 1171）、短分块不滚，箭头旋转 90°、`aria-expanded` 正确；下拉面板 `position:fixed`、Teleport 到 body、`z-index:3000`、玻璃拟态生效、与触发按钮对齐且不出视口，选中/点外部/Esc/滚动均关闭；375px 宽度下不溢出、卡片仍统一 87px。

## 后续优化（待办）

> 已确认但暂不做的优化，集中登记，后续需求按项处理。

- **用户管理页的批量机制与「批量改用户名」前置条件（2026/9/21 用户提出，评估后暂不改代码）**：用户指出当前批量条"针对性太强"（按钮写死「设为管理员 / 取消管理员」），将来若要支持批量修改用户名会不适用。评估结论：**批量 UI 可以描述符化**（前端抽一个 `BATCH_ACTIONS` 数组：label / tone / 确认文案 / `excludeSelf` / `submit`，模板 `v-for` 渲染并按描述符分发，将来加操作=加一条描述符 + 一个 api 函数）；**后端建议保持一操作一接口**（`batch-role`、未来的 `batch-username`），不做通用 `batch-update { patch }` 白名单接口——role 与 username 的不变量完全不同，泛化会放大"误暴露可写字段"的风险且错误信息会变模糊。**批量改用户名的真正拦路虎是 `username` 已被当业务键用**，动手前必须先定四件事：①**会话归属**——会话 ID 是 `know_{username}_{chatId}`，历史列表按 `^know_{username}_` 过滤、逐条操作按 `startsWith("know_" + username + "_")` 校验（`AiController.java:74/110/131/155/183`），改名后老会话全部失联，且**旧用户名被他人注册后会"继承"原用户的历史**；②**鉴权**——JWT 的 `sub` 就是用户名（`AuthService.java:38/56`），`AuthFilter.java:44` 只校验 token 有效、**不校验用户是否存在**，改名后旧 token 仍以旧用户名继续工作（新建会话还挂旧前缀），需定"改名后强制重登"并考虑补"用户仍存在"校验；③**唯一性与格式**——`User.username` 的 `@Indexed(unique = true)` 在 Boot 3 默认 `auto-index-creation=false` 下可能并未真正建索引，需应用层查重（含批内互相冲突）+ 明确格式/长度规则；④**数据迁移**——迁历史要批量重写 `chat_memory.conversationId`（唯一索引，需处理冲突），不迁就要接受老会话丢失并告知用户。**"不含 ID"帮不上忙：引用关系挂的是 username，不是 userId。**
- **首次部署的管理员引导**：用户管理页已可自助授权，日常无需改库；但**库中一个管理员都没有时页面无法自助授权**（先有鸡还是先有蛋），此时只能用 MongoDB Compass 手工执行 `db.users.updateOne({username:"admin"},{$set:{role:NumberInt(1)}})`（本机未装 mongosh，`NumberInt` 用于明确 Int32）。取值约定：`0` 普通用户 · `1` 管理员（`role` 为 null 或非 1 一律视为普通用户）。
- **web 历史对话页、知识库管理页与用户管理页个人信息入口重构**：为修复与历史列表/批量操作条重叠，`/knowledge`、`/knowledge-documents`、`/user-manage` 三页暂时隐藏左下角 user-dock（个人信息组件）。副作用是这三页无法再从该组件进入"修改密码/退出登录"（两个管理页可经「返回」回首页使用）。后续需重新设计个人信息入口（如侧边栏底部并入个人卡片、或放头部菜单），需同时考虑桌面端与移动端一致性，改完移除 `showDock` 中对这三条路径的临时隐藏分支。**2026/9/21 更新**：`/user-manage` 加入隐藏名单的原因与 `/knowledge-documents` 相同——该页底部同样有固定批量操作条（`z-index:100`），会被 dock（`z-index:999`）压住左端「取消」按钮。
  **2026/9/20 补充核实（区分两个组件）**：被隐藏的是桌面端固定左下角的 `.user-dock`（`App.vue` 51 行，靠 `dock-hidden` 类做 opacity/transform/pointer-events，DOM 仍在）。移动端 /knowledge 的个人信息入口是另一个组件——`KnowledgeChat.vue` 41 行侧边栏底部的 `.sidebar-footer`，条件是 `v-if="isMobile && !batchMode"`：移动端非批量模式下正常显示、进入批量管理时自动隐藏（用的是本地 `batchMode`，机制有效）。因此"无入口"这一副作用只落在**桌面端 /knowledge** 与**两平台的 /knowledge-documents**；**移动端 /knowledge 仍有入口**
- **manus 超级智能体**：后续慢慢实现该功能
- **知识库管理页遗留问题（2026/9/16 独立审查发现，存量）**：
  ① **应用外壳在较矮视口下不滚动**：`html, body { height:100% }` + `html, body { overflow-x:hidden }`（后者按 CSS 规范把 `overflow-y` 计算成 `auto`，body 成为滚动容器），配合 `.main-content { flex:1; min-height:0 }`（`overflow:visible`），实测 1280×720 下 `document.scrollingElement.scrollHeight === innerHeight === 720`、设 `scrollTop` 无效，列表第 10 行与底部分页不可达。需单独排查外壳滚动方案（影响面覆盖全部页面，不在本页内改）。
  ② ~~**批量操作条与左下角 user-dock 重叠**：视口宽约 769–850px 时 dock（`z-index:999`，`left:20px; bottom:20px`）压住批量条"取消"按钮文字并抢走点击。~~ **已于 2026/9/16 修复**：按用户要求直接在 `/knowledge-documents` 隐藏 user-dock（见改动记录）；`App.vue` 中 `provide('chatBatchMode')` 经 2026/9/20 核查确认为**只写不读的死代码**（`App.vue` 只 provide 不读，`KnowledgeChat.vue` 只在进入/退出批量与卸载三处写值），并已于同日**按用户决定删除**（详见改动记录）。将来重构时若要让桌面端 dock 回来并按批量模式隐藏，两条可选路子：① 重建等价的跨组件信号（照 `isSidebarOpen` 那套 provide/inject 写）；② 让桌面端也复用侧边栏底部个人信息卡片（`v-if` 去掉 `isMobile`，它读本地 `batchMode`、批量模式自动隐藏，**无需信号**；代价是桌面端侧边栏收起时该入口随之消失）。
  ③ ~~**`canReindex` 与注释不符**：注释写"未入库与失败状态可重新入库"，实现为 `!isRunning(doc)`，导致已完成文档也显示「重新入库」按钮。~~ **2026/9/16 二次修正**：先按"白名单（未入库/失败）"收紧，实测后用户反馈**日常用的重跑入口被一起收掉了**（"重新入库的按钮怎么没了"）——说明该按钮既是失败恢复也是主动重跑入口。最终方案：`canReindex` 恢复为 `!isRunning(doc)`（预处理中/向量化中不可点），并对**已完成**文档增加二次确认弹窗（重跑先删旧切片，失败会掉到「失败」态）；未入库与失败无切片可丢，直接重跑不确认。
  ④ ~~**`openDetail` 无请求时序保护**：先点 A→关闭→点 B 时，A 的响应后到会覆盖 B 的内容，失败分支还会关掉刚打开的 B 弹窗。~~ **已于 2026/9/16 修复**：`openDetail` 引入递增请求序号，仅采纳最新请求的响应；`closeDetail` 使进行中请求失效。已用"延迟首个请求造成乱序"的方式验证（见改动记录）。
  ⑤ ~~**存量触控目标 <44px**：`.row-btn` 36×36（≤768px 才升 44）、`.pager-btn` 40×40、`.icon-btn` 40×40、`.check-circle` 22×22，属项目既有模式。~~ **已于 2026/9/20 优化**：`.row-btn` / `.pager-btn` / `.icon-btn` 统一为 44×44（`.row-btn` 加 `flex-shrink: 0` 防止被固定列宽压扁），`.check-circle` 保持 22px 视觉、用 `::before`（显式 `width/height: 44px` + 负 margin 居中）把可点区域扩到 44×44；勾选列 28→44px、操作列 128→144px（3 个 44px 按钮 + 2 个 6px 间距），并清掉 ≤768px 的 `.row-btn` 44px 与 ≤1024px 的 `.col-actions` 116px 两条已冗余的覆盖。**注意**：伪元素若用 `inset: -11px` 扩展，因绝对定位的包含块是 padding box，会被 `.check-circle` 的 1.5px 边框吃掉，实测只有 41.33px；改用显式 44×44 + `margin: -22px` 才是准确尺寸
- **其它页面触控目标 <44px（2026/9/20 核查，未处理）**：遗留 ⑤ 只覆盖了知识库管理页，实测其它页面仍有真实可点元素低于 44px——`frontend/src/views/KnowledgeChat.vue` 的 `.history-more-btn` 36×36、`frontend/src/views/ChangePassword.vue` 的密码显示/隐藏按钮 32×32；`ManusChat.vue` / `KnowledgeChat.vue` 里 40px 的那些是聊天头像、不参与点击，不算问题。要做成"全站无障碍一致"需再来一轮，范围比本次大（改前须加载 `/ui-ux-pro-max`）
- **存量空白 / 行尾统一（已同意，用户 2026/9/20 决定暂缓）**：清掉 `.editorconfig` 落地前的历史差异——制表符缩进 `App.vue`(148 处) / `Login.vue`(120) / `KnowledgeChat.vue`(379) / `ManusChat.vue`(185) / `ChangePassword.vue`(115)，LF 行尾 `frontend/src/main.js` / `frontend/vite.config.js` / `AiAgentApplication.java`，以及 `DocumentPreprocessor` 的行尾空格。用户暂时不想改动现有格式，需要时再作为独立提交处理（执行时须 build + 抽查 Vue 渲染，`application.yml` 被 `.gitignore` 忽略、不入库）


## 本次改动记录（最新在前）

| 日期 | 改动 | 涉及文件/模块 | 是否已测/已审 |
|------|------|--------------|--------------|
| 2026/9/30 | **规则文档审计（查漏补缺 + 去重）**：修正 4 处过期内容——陷阱 2「JWT Secret 长度」改写为实际行为（密钥启动随机生成、`app.jwt.secret` 未被使用、重启即失效），小程序文档仍写着用游客 `touristappid`（与真机要求矛盾），安卓文档称本机无 SDK/JDK 1.8（实际已装且已出 APK），陷阱 47 的「user-dock 遮挡」与陷阱 51 重复；删除 rules-template 的 `CLAUDE.md` 单文件旧称谓（改为 AGENTS.md 主 + CLAUDE.md 镜像）；AGENTS 去重（文档同步规则、敏感配置与 Git 排除清单、自检清单 4 项并为 2 项）并补入「新功能先规划后开发」；本文件合并「项目状态」两段重复叙述、清理已闭环的「下一步」条目 | AGENTS.md / CLAUDE.md / docs/known-pitfalls.md / docs/miniprogram.md / docs/android-app.md / docs/rules-template.md / PROGRESS.md | 文档类改动（陷阱编号交叉校验 65↔65、AGENTS 与 CLAUDE 逐字节一致；无 git 操作） |
| 2026/9/29 | **文档审计与同步（按你确认范围）**：修正四处事实错误——AGENTS/README 头部的单模型表述、android-app 播报三处过期描述、播报按钮移动端点击区实为 38px（CSS 补到 44px）；以 AGENTS.md 为准重建 CLAUDE.md 并把「同步 CLAUDE」「PROGRESS 记录抓重点」写进维护规则；本文件 9/28–29 记录压缩为要点 | 改 AGENTS/CLAUDE/README/android-app/miniprogram/known-pitfalls/PROGRESS 与 Web 聊天页 CSS | 是（陷阱编号交叉校验 65↔65 对齐；前端构建通过；无 git 操作） |
| 2026/9/29 | **语音播报放出 + 三端 UI 统一**：Web 端去掉播报按钮的 `v-if="false"` 并去掉文字，三端统一为仅图标、无边框无底色；播报中图标转红（Web 换 VolumeX、小程序换新增的 volume-off.svg）表示可停止，纯图标按钮补 aria-label。安卓随 H5 生效、壳层零改动 | 改 Web 聊天页、小程序消息气泡；新增小程序 volume-off.svg | 是（TTS 接口 curl 冒烟 200；Web 实测播报、停止、复位正常；小程序编译通过，真机听感待验） |
| 2026/9/29 | **小程序输入框一行溢出修复**：模型名用 `<text>` 导致省略号不生效、整行被撑宽，把语音/发送挤出输入框；改为 `<view>` 并允许控件组收缩，名字超长走省略号（见陷阱 65） | 改小程序聊天页输入区 | 是（小程序编译与产物核对通过；按 375px 计算名字可用约 120px；真机待验） |
| 2026/9/29 | **Web 端输入区压低（三轮）**：输入框改单行起始 + 高度自适应（随内容长高、上限 30vh、清空回落），控件与内边距逐轮收紧，整高 120px→75px；期间一度加宽屏限宽居中，按反馈撤销；另修「文字超长后顶部留白被滚掉」（顶部留白移出滚动区，见陷阱 63） | 改 Web 聊天页 | 是（三档宽屏 + 手机宽度实测无溢出；自适应与留白恒定验证通过；构建通过） |
| 2026/9/28 | **千问模型名去前缀 + 移动端四控件收进输入框**：后端千问 displayName 去掉「千问」（重启生效）；移动端 RAG 文案改「RAG」，四个控件全收进输入框——左下 RAG 与模型并列、右下语音/发送去边框同底色；中间一版曾按框选移到输入框外左下角纵排，随后按新要求收回框内；修掉工具条换行与按钮溢出两个布局问题 | 改后端模型目录、Web 聊天页、小程序聊天页；新增小程序发送图标 | 是（后端编译、小程序编译、手机与桌面实测；布局要点见陷阱 64） |
| 2026/9/28 | **安卓端与小程序端同步多模型切换**：安卓为 WebView 壳零改动（部署新 H5 即可，无需重打包）；小程序新增模型状态管理、底部选择弹层与输入框切换，发送带 model 参数（空值不下发） | 小程序新增 5 个文件，改 Web/小程序共 6 个文件 | 部分（小程序编译与产物自查通过、真机待验；手机宽度 H5 实测通过） |
| 2026/9/28 | **Web 聊天输入区结构改造**：模型切换与知识库检索从输入框上方工具栏移入输入框内部（输入框在上、底部工具条在下，共用边框聚焦高亮），模型面板改与按钮右缘对齐 | 改 Web 聊天页 | 是（构建通过，桌面与手机宽度实测） |
| 2026/9/28 | **修复 qwen3.8-flash + 工具报 toolInput cannot be null or empty**：根因是 Spring AI M6 流式合并缺陷把一个工具调用拆成两条（官方 PR #6381 已修），新增拼接修复类挂千问模型（DeepSeek 不分片未挂），升级 Spring AI 后可删 | 新增后端 ToolCallRepairingManager；改模型配置 | 是（原失败场景转 200 且工具真实执行；三模型带工具、RAG、跨模型记忆回归全过；详见陷阱 62） |
| 2026/9/28 | **Web 端多模型切换 + 大模型展示页落地**（本次需求主体）：后端一模型一 ChatClient Bean + 模型目录/服务/接口，KnowledgeApp 按模型解析（原构造器注解保留为模式一备用）；前端模型下拉、/models 展示页与个人中心入口；关键修正：千问两款改走 DashScope OpenAI 兼容模式端点（原生端点不支持，见陷阱 61） | 新增后端 4 类、前端 2 文件；改 7 个既有文件 | 是（后端全链路 curl + 跨模型记忆连续；前端构建 + 浏览器实操；独立测试与审查通过，2 个 P2 已修） |
| 2026/9/21 | **`test` → `master` cherry-pick 并推送 `master`**：把 `ce554e2` 之后的 6 条提交 pick 到 `master`，落地 4 条（`c74e0b6` 删除 chatBatchMode 死代码 / `ed9dd7a` 用户管理功能 / `0fe4cd5` 文档同步 + 两张管理页截图 / `e0ba3d0` 提交信息规则），`0331166` 与 `d4a408e`（只改 `PROGRESS.md`）在冲突解决后为空、按 `--skip` 跳过；`PROGRESS.md` 在 `master` 上不存在，4 次 modify/delete 冲突一律保持删除（与 9/20 同法）。核对：`git diff --name-status test master` 排除删除类后为空、差异恰为既定的 71 个被删文件（22586 行）、8 个关键文件（含新截图）逐字节一致、`mvnw -o compile` 退出码 0、`npm run build` 产物哈希与 test 一致。`master` 已推送 `origin/master`（`95c798a..e0ba3d0`，非 force） | `master` 分支、`PROGRESS.md` | 是（差异核对 + 编译构建 + 产物哈希比对；按既定安排 master 不含 skills / notes / document / PROGRESS.md 四类文件，切到 master 时工作区会缺这些文件、切回 test 自动恢复） |
| 2026/9/21 | **提交信息规则修订（按用户反馈，两条）**：① 首行与第 1 条之间**不要空行**——我套用了英文"标题 + 空行 + 正文"的习惯，而项目既有提交（`aeb5c30` / `ce554e2` / `8c0b116`）都是首行紧接 `1.`；② 提交信息**要简洁、只写重点**——我此前的信息过长、堆了不少类名/方法与操作细节。两条规则已写入 `AGENTS.md`「Git 提交规则」并各附一组 ❌/✅ 对照示例，`CLAUDE.md` 同步（两份规则文档需保持一致）。按用户要求，先前那条**未推送**的规则提交已 `git reset --soft` 撤回，与本次合并为一条简洁提交（远端历史未受影响，无需 force push）。**历史提交不改写**：本会话的 `f6a5a30` / `b60b2b2` / `8a7ba42` / `63cdeab` / `d4a408e` 与历史 `cd6be5b` 仍带空行且信息偏长，改写需 rebase + force push（用户要求禁止），故保持原样 | `AGENTS.md`、`CLAUDE.md`、`PROGRESS.md` | 文档类改动（纯格式规则，不影响代码与功能） |
| 2026/9/21 | **用户管理功能文档同步（按用户要求，未做 git 操作）**：① `README.md`——「界面预览」在 Web 端下新增「Web 端管理页（仅管理员可见）」两张截图（`docs/screenshots/web-knowledge-documents.png` / `web-user-manage.png`，由用户提供的 JPG 用 PIL 转 PNG 入库、保持 2560px 原分辨率）；「功能特性」表新增「用户管理（管理员）」一行；新增「👥 用户管理（管理员）」章节（入口/列表字段/批量授予与撤销/自我保护与保底/权限即时生效 + 首次部署无管理员时的手工引导）；② `AGENTS.md`——陷阱 51 由"两页隐藏 dock"扩为三页（补 `/user-manage`），索引新增 57（Mongo 分页须加唯一二级键）/ 58（超大页码偏移溢出）/ 59（禁止改自己≠至少留一个管理员）/ 60（后台标签页节流影响验证）；③ `docs/known-pitfalls.md`——详情 51 同步、新增 57–60 详情（含实测数据、边界值与未实测部分的说明）；④ `PROGRESS.md`——需求清单置为完成、改动记录、后续优化（批量机制描述符化 + 批量改用户名四项前置条件）、遗留与下一步同步 | `README.md`、`AGENTS.md`、`docs/known-pitfalls.md`、`PROGRESS.md`、`docs/screenshots/web-knowledge-documents.png`、`docs/screenshots/web-user-manage.png` | 文档类改动（截图取自用户实测页面；待用户核查后再做提交） |
| 2026/9/20 | **Web 端「用户管理」（管理员授权）落地**（分支 `knowledge-user-manage`，功能提交 `f6a5a30`，12 个文件）：① 后端新增 `GET /api/ai/user/list`（分页 + 用户名模糊搜索 + 排序；`Pattern.quote` 转义正则防注入；`size` clamp ≤50、`page` 上限 100 万防偏移溢出）与 `POST /api/ai/user/batch-role`（批量改角色：禁止改自己、写入后复查管理员数并在为 0 时回滚），鉴权在服务层（非管理员 403、无 token 401），返回白名单 DTO（密码不进接口）；② 前端新增 `/user-manage` 页（搜索 / 自定义排序下拉 / 分页 / 批量模式 / 二次确认弹窗，视觉与知识库管理页一致）与个人中心「用户管理」入口（位于「知识库管理」上方、仅管理员可见），路由守卫提示改为按页面标题生成，该页隐藏左下角 dock；③ 版本号 `0.1.1` → `0.2.0` + CHANGELOG 条目 | 新增 `model/UserDTO`、`model/UserPageDTO`、`service/UserManageService`、`controller/UserController`、`frontend/src/views/UserManage.vue`；改 `repository/UserRepository`、`frontend/src/App.vue`、`frontend/src/router/index.js`、`frontend/src/api/request.js`、`pom.xml`、`CHANGELOG.md`、`PROGRESS.md` | 是（后端 curl 20+ 用例：401 / 403 / 分页与 size clamp / 搜索（含 `.*` 转义）/ 三种排序与非法值回落 / 批量校验矩阵 / 自我防护 / 权限即时生效（同一旧 token 403→200→403）/ 幂等与去重 / 回归；前端 `npm run build` + 浏览器实测：菜单顺序与仅管理员可见、非管理员直连被拦回首页、搜索/排序/翻页（临时把 `PAGE_SIZE` 调 3 验证两页后改回）、全选排除自己、确认弹窗（授予绿 / 撤销红）、Esc 关闭、取消不生效、375px 无横向溢出、dock 隐藏、控制台无报错；**两轮独立审查**：接口交叉验证约 100 个子用例（1 项失败）+ 缺陷优先代码审查（P0 无）；据审查修复 4 项缺陷——排序切换未清空勾选、列表请求无时序保护、并发互降可清零管理员、超大页码偏移溢出，另修加载失败误显示"暂无用户数据"、批量失败残留勾选、返回按钮 40→44px、弹窗补 `role="dialog"`、错误文案英文泄漏） |
| 2026/9/20 | **删除 `chatBatchMode` 死代码（按用户决定）**：移除 `App.vue` 的注释 + `const chatBatchMode = ref(false)` + `provide('chatBatchMode', chatBatchMode)`，以及 `KnowledgeChat.vue` 的注释 + `inject('chatBatchMode', ref(false))` 与 `onBatchManage` / `exitBatchMode` / `onUnmounted` 三处赋值（合计 8 行：`App.vue` 3 行 + `KnowledgeChat.vue` 5 行）。它原本用于「进入历史对话批量管理模式时隐藏桌面端 user-dock」，唯一的读取点在 2026/9/1 被改成"`/knowledge` 整页隐藏 dock"后消失，成为只写不读的死代码；移动端那张个人信息卡片读的是本地 `batchMode`、与它无关，所以行为不变 | `frontend/src/App.vue`、`frontend/src/views/KnowledgeChat.vue`、`PROGRESS.md` | 是（`npm run build` 通过且包体积相应变小：KnowledgeChat 23.91→23.85 kB、index 159.30→159.27 kB；全仓 grep `chatBatchMode` 已无残留；浏览器回归——桌面端 /knowledge 进入/退出批量模式正常（12 个勾选圈、取消按钮在位），移动端 375px 下"打开侧边栏→卡片在 x=0 可见（admin / 修改密码 / 退出登录）→进入批量管理卡片消失且出现 12 个勾选圈→取消后卡片恢复"；首页 `/` 的 `.user-dock` 仍为 `user-dock`（无 `dock-hidden`、opacity 1、显示 admin）未受影响；全程注入的 error / unhandledrejection / console.error / console.warn 采集结果为空。注：验证中途 Vite HMR 整页刷新过一次导致采集器丢失，已重新注入，非应用报错） |
| 2026/9/20 | **遗留问题 ⑤ 触控目标优化 + 版本号升级 + `master` 同步（cherry-pick）**：① 知识库管理页触控目标统一到 ≥44×44——`.row-btn` 36→44 并补 `flex-shrink: 0`（防止被固定列宽的 `.col-actions` 压扁）、`.pager-btn` 与 `.icon-btn` 40→44、`.check-circle` 保持 22px 视觉并用 `::before` 把可点区域扩到 44×44（显式 `width/height: 44px` + `top/left:50%` + `margin:-22px` 居中；最初写的 `inset:-11px` 实测只有 41.33px——绝对定位伪元素的包含块是 padding box，会被 1.5px 边框吃掉）；列宽同步调整：勾选列 28→44px、操作列 128→144px（3×44 + 2×6），并删掉 ≤768px 的 `.row-btn` 44px、≤1024px 的 `.col-actions` 116px 两条已冗余覆盖；② 版本号 `0.0.1-SNAPSHOT` → `0.1.1`：`CHANGELOG.md` 中 2026-09-16 的功能条目改记为 `0.1.0`，顶部新增 `0.1.1 / 2026-09-20`；③ 按用户决定把 `test` 的功能提交 cherry-pick 到 `master`（不用 merge，避免复活 `master` 已删的语料与 skills 文件；实际 pick 5 条 = `044c12b` / `02237c9` / `e9a038e` / `d6bd839` / `95c798a`，仅 `PROGRESS.md` 一处 modify/delete 冲突并按"保持删除"处理，`master` 已编译通过、未推送） | `frontend/src/views/KnowledgeDocuments.vue`、`pom.xml`、`CHANGELOG.md`、`PROGRESS.md`、`master` 分支 | 是（前端 `npm run build` 通过；浏览器实测：30 个 `.row-btn` 与 `.pager-btn` / `.icon-btn` 均 44×44 且 `flex-shrink` 为 0；`.check-circle` 视觉 22×22、`::before` 计算值恰为 44×44，`elementFromPoint` 在中心 ±21px 命中按钮自身、±22px 落空（不外溢到名称列，名称列在 +30px 仍为 `col-name`）；1280 与 375 宽度下页面与表格行均无横向溢出、批量模式下勾选列 44px 仍不挤破卡片；回归通过：批量模式进出/单选/全选、详情弹窗开关（关闭按钮 44×44）、分块 14 项高度统一 87.3px、排序下拉三项与选中生效、翻页 1↔2 页） |
| 2026/9/16 | **按用户反馈恢复「重新入库」入口 + 二次确认**：先前的收紧把管理员日常用的重跑入口一起收掉了（用户反馈"重新入库的按钮怎么没了"），故 ① `canReindex` 从白名单（仅未入库/失败）恢复为 `!isRunning(doc)`，预处理中/向量化中不可点、其余状态均可重跑；② 新增 `confirmReindexDoc` 状态与 `askReindex`/`confirmReindex` 函数 + 确认弹窗（复用既有 `.modal-content` 与 `.modal-btn.confirm.green`，文案含文件名与待删切片数），**已完成**文档点重跑先确认（重跑先删旧切片，失败会从「已完成」掉到「失败」），未入库/失败无切片可丢则直接重跑 | `frontend/src/views/KnowledgeDocuments.vue`、`AGENTS.md`（陷阱 55 重写）、`docs/known-pitfalls.md`（详情 55 重写）、`CHANGELOG.md`、`PROGRESS.md` | 是（前端 build 通过 + 格式核查；浏览器实测：10 行「已完成」显示 10 个重新入库按钮；点按钮弹确认框、文案为"将先删除已有 2 个切片"；**取消** → 0 次 reindex 请求、状态仍「已完成」；**确认重跑** → 1 次 `POST .../reindex`、状态转「预处理中」、toast 正确，轮询后回到「已完成」，接口复查 `COMPLETED` + `chunkCount: 2`（对切片最少的一篇 Git 文档做了真实重跑，幂等））；**副作用已处理**：该文档受版本管理，重跑会原地覆盖磁盘文件（本次多出 2 个尾部空行），已 git checkout 还原，工作区无残留；该行为已记入陷阱 56 |
| 2026/9/16 | **遗留问题 ③④ 修复 + `.gitattributes` 纳入版本管理**：① `canReindex` 由 `!isRunning(doc)` 收紧为白名单 `REINDEXABLE_STATUSES`（未入库 / 预处理失败 / 向量化失败），已完成文档不再显示「重新入库」按钮（**该收紧随后按用户实际使用反馈恢复，见上一条改动记录**）；② `openDetail` 增加递增请求序号 `detailRequestId`，仅采纳最新请求的响应，`closeDetail` 使进行中请求失效，修掉"先点 A → 关闭 → 点 B 时，A 的响应后到覆盖 B 内容 / A 失败时关掉 B 弹窗"的时序竞态；③ `.gitignore` 移除 `.gitattributes`，该文件（`/mvnw text eol=lf`、`*.cmd text eol=crlf`、`*.sh text eol=lf`）改为纳入版本管理，行尾规则从此对所有克隆生效 | `frontend/src/views/KnowledgeDocuments.vue`、`.gitignore`、`.gitattributes`（转为受版本管理）、`PROGRESS.md` | 是（前端 build 通过；③ 浏览器实测：10 行全部"已完成"时「重新入库」按钮 0 个、查看内容按钮完好；④ 用"延迟首个 `/content` 请求 2.5s"制造真实乱序，**反证**——撤掉守卫时最终显示 A（A 响应 5276ms 晚于 B 的 3906ms）、恢复守卫后显示 B，失败路径另测：A 请求报错时 B 弹窗仍在、内容为 B、无误报提示） |
| 2026/9/16 | **知识库管理页收尾项（按用户确认执行）**：① `/knowledge-documents` 隐藏左下角 user-dock——`App.vue` 的 `showDock` 增加该路径，修掉 769–850px 视口下 dock（`z-index:999`）压住批量操作条"取消"按钮文字并抢走点击；② `script/html-to-md.sh`、`script/preprocess-docs.sh` 由 CRLF 转 LF，消除 Git Bash 下 `bad interpreter: /bin/bash^M` 隐患（HEAD 中本就存 LF，故工作区转换后 `git diff` 为空、无需提交）；③ `.gitattributes` 补 `*.sh text eol=lf`，否则 `core.autocrlf=true` 会在下次检出时把脚本改回 CRLF；④ `AGENTS.md`「命名与代码风格」增加一行指向根目录 `.editorconfig`，并写明不引入 Spotless/Prettier | `frontend/src/App.vue`、`script/html-to-md.sh`、`script/preprocess-docs.sh`、`.gitattributes`（当时本机未跟踪且在 `.gitignore` 中，随后已按用户确认移出并纳入版本管理）、`AGENTS.md`、`PROGRESS.md` | 是（前端 build 通过；浏览器实测 800px 视口下批量条"取消"按钮 `elementFromPoint` 命中按钮自身、dock `opacity:0` + `pointer-events:none`；`/` 首页 dock 仍正常显示，`/knowledge`、`/knowledge-documents` 隐藏；两个脚本行尾复核 CRLF=0） |
| 2026/9/16 | **知识库管理页前端三处问题修复**（同分支 `knowledge-doc-manage`）：①排序下拉由原生 `<select class="sort-select">` 改为自定义下拉（触发按钮对齐搜索框视觉、`ChevronDown` 展开旋转，面板 `<Teleport to="body">` + `position:fixed` + 玻璃拟态 `blur(20px)` + `z-index:3000`，按触发按钮视口坐标定位、下方空间不足向上翻转、面板最小宽度对齐触发按钮；点击外部/Esc/Tab/页面滚动关闭；补 `aria-haspopup`/`aria-controls`/`role=listbox`/`aria-activedescendant` + 选项 `tabindex="-1"`，↑↓ 移动高亮、Enter 选中、选中后焦点交还触发按钮）；②③分块结果"挤在一页/内容被裁切、滚动条时有时无"：根因是 `.chunk-list` 为 flex 列容器且 `.chunk-item` 自带 `overflow:hidden`，按 Flexbox 规范子项自动最小尺寸为 0 → 被压扁裁切（实测 76 个分块项各 `offsetHeight:1px` 而 `scrollHeight:44px`），叠加 `.chunk-text` 仅 `max-height:320px` 形成双层滚动；修复为 `.chunk-item{flex-shrink:0}` + 折叠态卡片固定高度（头行 + 2 行预览，`-webkit-line-clamp:2` + `min-height:2.9em` 预留两行）+ 展开态正文固定 `height:320px` 内部滚动（补齐 6px 细滚动条样式），最右侧 Lucide `TriangleRight` 空心三角指示开合（展开 rotate(90deg)），展开后 `scrollIntoView` 保证可见；**顺带修一处本文件既有缺陷**：`watch(sort)` 原来在非首页时会重复发一次列表请求（应与 `reloadFromFirstPage()` 一致），实测由 2 次降为 1 次 | `frontend/src/views/KnowledgeDocuments.vue`、`PROGRESS.md` | 是（`npm run build` 通过；浏览器实测：修复前复现 76×1px、修复后 76 张卡片统一 87px 且零裁切、连 9 字最短分块也 87px；展开 363px＝头 41px+正文 320px，3124 字内部滚动、短分块不滚、箭头旋转与 `aria-expanded` 正确；下拉面板 fixed/Teleport/z-index/玻璃拟态/对齐/不出视口、选中/外部点击/Esc/滚动关闭均通过；排序请求次数由 2 降为 1、焦点回归触发按钮；375px 无横向溢出且卡片仍 87px；回归搜索/分页/批量/上传/删除确认/左侧正文栏通过；独立代码审查无 P0/P1，已采纳其 P2/P3 建议） |
| 2026/9/13 | **知识库文档管理（Web 端）落地**：①管理员 `role` 字段与门控（登录/me 返回 isAdmin，管理接口 service 层查库校验，非管理员 403）；②文档上传（仅 .md + 校验，后台异步三阶段流水线：预处理→原地覆盖→分块→向量化）；③列表（模糊搜索 / 时间与名称排序 / 分页）、状态实时轮询（预处理中/向量化中/已完成/未入库/失败+原因）、详情弹窗（预处理正文 + 分块结果）；④删除 / 批量删除（先删向量再删文件）与「重新入库」；⑤头像下拉「知识库管理」入口（仅管理员，页面可返回上一级，非管理员直连被拦截）；⑥加载器改为「源码目录优先 + classpath 回退」使运行时上传立即生效，并抽出稳定 id 与切片解析复用；⑦`DocumentPreprocessor` 分割线插入改为幂等（重跑不再累积 `---`）；⑧修复自测发现的缺陷：子目录 `yuque-sync/` 文档无法查看/删除/重入库、详情接口缺 `chunkCount`、重名判断大小写敏感、失败记录残留锁死文件名、前端勾选未随筛选/排序清理、轮询重叠、上传文件重选不生效；⑨格式化核查（机械化扫描本功能全部改动文件）：修复 `KnowledgeAppDocumentLoader.extractTopic` 方法签名与首句代码粘连、`KnowledgeDocumentService` 3 处超长行，补 `model/User` 文件末尾换行，`.editorconfig` 自身行尾统一为 CRLF；发现并补上一处检查盲区——未跟踪目录（`bootstrap/`）最初未被扫描覆盖，改为展开未跟踪目录后重扫；经与 HEAD 逐条比对，`App.vue`/`Login.vue` 的制表符与字体 `@import` 长行、`AiController` 长行、`DocumentPreprocessor` 行尾空格均属存量问题，未顺手改动以免污染本功能 diff | 新增 `constant/UserRole`、`model/DocumentStatus`、`model/KnowledgeDocumentDTO`/`DetailDTO`/`PageDTO`、`service/KnowledgeDocumentService`、`controller/KnowledgeDocumentController`、`config/KnowledgeDocumentTaskConfig`、`bootstrap/AdminAccountChecker`、`frontend/src/views/KnowledgeDocuments.vue`；改 `model/User`、`service/UserService`、`service/AuthService`、`repository/UserRepository`、`controller/AuthController`、`controller/AiController`、`controller/GlobalExceptionHandler`、`rag/KnowledgeAppDocumentLoader`、`rag/MongoVectorStoreConfig`、`rag/DocumentPreprocessor`、`application.yml`、`utils/auth.js`、`api/request.js`、`router/index.js`、`App.vue`、`views/Login.vue`、`AGENTS.md`（目录结构章节）、`PROGRESS.md`、新增根目录 `.editorconfig`（编辑器约定：UTF-8 / CRLF / Java 4 空格 / 前端与配置 2 空格 / Kotlin 4 空格 / 末行换行 / 去行尾空格，并按用户要求**不引入** Spotless、Prettier 等强制校验）；**包结构调整（按用户要求）**：`KnowledgeDocumentService` 与既有 `YuqueDocumentSyncService` 从 `rag/` 迁入 `service/`（分层优先），`AdminAccountChecker` 从 `config/` 迁入新建的 `bootstrap/`，`UserRole` 从 `model/` 迁入 `constant/`；为跨包复用把 `DocumentPreprocessor.processContent` 与 `KnowledgeAppDocumentLoader.extractTopic` 放开为 public | 是（后端 curl 全链路 + 失败路径 + 403/401 + 登录/历史/RAG 回归全部通过；前端 build 通过 + 浏览器实操；独立代码审查无 P0/P1 并已修 P2；独立功能交叉验证 10 项全通过，23 篇内置文档 MD5/切片数与测试前一致；包迁移后重新编译 + 启动自检 + 接口自检通过） |
| 2026/9/1 | README 新增「📸 界面预览」章节：Web / 安卓 APP / 微信小程序三端各 2 张运行截图（docs/screenshots/，英文命名） | README.md、docs/screenshots/（6 张图）、PROGRESS.md | 文档类改动，路径已验证 |
| 2026/9/1 | web 端交互优化修正：收起按钮移到侧边栏右缘外侧（left:260px 不居中），滚动条恢复贴右侧边框（撤销 .history-list margin-right:18px 让位方案，避免滚动条离边框太远）。陷阱 50 修复方案同步 | KnowledgeChat.vue、AGENTS.md、CLAUDE.md、docs/known-pitfalls.md、PROGRESS.md | 未测试（用户声明无需测试） |
| 2026/9/1 | web 端交互问题修复：①语音识别友好提示（前端过短拦截<0.6s + sttErrorMessage 映射 + 后端 400 附 message）；②历史对话滚动条与收起按钮重叠（.history-list 预留右侧 margin 18px）；③隐藏 /knowledge 页个人信息组件（showDock 恒 false，待优化）。文档同步 49/50/51 | KnowledgeChat.vue、App.vue、SpeechController.java、AGENTS.md、docs/known-pitfalls.md、PROGRESS.md | 前端 build 通过；后端 compile 通过（页面交互未 headless 验证） |
| 2026/8/30 | 验证流程闭环：违规诱捕自测 + code-test 子智能体实跑 | request.js（捕获 P1：生产 BASE_URL 命中陷阱 38）、PROGRESS.md | 是（前端构建 / 后端接口 11 项 / 消费方回归通过；浏览器 UI 无 headless 跳过） |
| 2026/8/30 | 规则文档体系重构 + 通用模板 + 测试子智能体 | CLAUDE.md、AGENTS.md、docs/known-pitfalls.md、docs/rules-template.md、docs/PROGRESS-template.md、PROGRESS.md、RULES.md(删)、~/.claude/agents/code-test.md、~/.zcode/agents/code-test.yaml | 文档类改动，结构已验证 |
| 2026/8/30 | Claude Code 上下文窗口放大 + 子代理模型修复 | ~/.claude/settings.json | 是 |

## 风险 / 遗留问题

- 🔴 **打包成 jar 部署后无法在线管理知识库文档**：`app.knowledge.document-dir` 默认指向源码目录 `src/main/resources/document`，jar 内该目录只读 → 上传与删除会失败（列表、查看仍正常）。要支持线上管理，把该配置指向可写目录即可，无需改代码。注意该配置写在 `src/main/resources/application.yml`（该文件被 `.gitignore` 忽略、不入库），代码里 `@Value` 已带同名默认值，因此换机器也能正常工作
- ⚠️ **千问端点差异（本次踩到的硬约束，后续加模型必看）**：`dashscopeChatModel`（Spring AI Alibaba 的 `DashScopeChatModel`）走千问**原生文本端点** `/api/v1/services/aigc/text-generation/generation`，实测该端点**只服务纯文本模型**（可用：`qwen-plus`/`qwen-max`/`qwen-turbo`/`qwen3-max`/`qwen3.7-max`）；千问 3.7/3.8 系列的 **Plus/Flash 是多模态模型**，只在 `multimodal-generation` 与 **OpenAI 兼容模式**端点上提供，原生端点对它们返回 `InvalidParameter: url error`（SDK 随后在聚合流式分片时 `output()` 为 null 抛 NPE → 接口 500）。本次千问两款因此改用 `OpenAiChatModel` 指向 `https://dashscope.aliyuncs.com/compatible-mode`（复用 `DASHSCOPE_API_KEY`，可用 `spring.ai.dashscope.compatible-base-url` 覆盖），顺带与 DeepSeek 统一为 OpenAI 协议；**将来新增千问型号前先确认它在哪个端点**，别再往原生端点加
- ⚠️ **`available` 降级分支在当前环境实际不可达**：`spring.ai.dashscope.api-key: ${DASHSCOPE_API_KEY}` 无默认值，未配置时应用启动即失败，所以「清单标记为不可用」只在密钥被显式置空时才可能触发；该分支仅做了代码级防御，未做运行时验证
- ⚠️ **回滚到「模式一」须知**：模式一是单一模型固定 DeepSeek，`model` 参数被忽略，**前端模型下拉框不会真的切换模型**（会显示已选但实际仍走 DeepSeek）。切换步骤写在 `KnowledgeApp` 的注释块里（放开模式一构造器 + `chatClient` 字段 → 注释模式二构造器 → 放开 `resolveChatClient` 里的模式一 return）
- ⚠️ **既有缺陷（本次发现，与本次改动无关、未修）**：`service/AuthService.java:29` 用 `Jwts.SIG.HS256.key().build()` 每次启动随机生成签名密钥，`application.yml:95` 的 `app.jwt.secret` **根本没被使用** → **每次重启后端，所有已签发 token 立即失效、用户必须重新登录**（本机自测期间反复遇到）。是否修由用户决定（详见 `docs/known-pitfalls.md` 陷阱 2）
- ⚠️ **陷阱 60 影响了本次浏览器验证**：验证期间 IAB 页面 `document.visibilityState === "hidden"`（从终端驱动时无法置为前台），后台标签页会节流 `requestAnimationFrame`，导致 Vue `<Transition>` 的帧回调不执行 → 面板元素以 `opacity: 0` + `enter-from` 卡在 DOM 里（**用户不可见**）。已用页面可见时的那轮实测证伪了"代码缺陷"：三条关闭路径（再次点击/点外部/Esc）当时均读到元素被移除。**结论：后续验证页面过渡/动画行为前必须先确认 `document.visibilityState`，否则会误判成代码 bug**
- ⚠️ **Spring AI 1.0.0-M6 流式工具调用合并缺陷（已用 `ToolCallRepairingManager` 绕过，升级 Spring AI 后可删）**：M6 的流式合并把"任何带 id 的分片"当作新工具调用的开始（官方 **PR #6381** 已改为按必填的 `index` 合并）。DashScope 兼容模式对 **qwen3.8 系列**的续传分片发 `"id": ""`（空串，而非 3.7 系列的 `null`），导致**同一个工具调用被劈成两条**：「有名称无参数」+「无名称有参数」。前者被 `MethodToolCallback` 的 `toolInput cannot be null or empty` 断言拦下（→ 前端显示"回复失败：toolInput cannot be null or empty"，HTTP 400），把前者过滤掉后后者又因 `toolName is null` 抛 NPE（HTTP 500）——所以**纯过滤不够，必须按顺序把两半拼回一条**。当前实现挂在千问 ChatModel 上（DeepSeek 未受影响故未挂）；**将来升级 Spring AI 到含 #6381 的版本后，本类与其挂载应一并删除**。另外注意：本缺陷只在模型**真的决定调用工具**时触发，不调工具时该模型一切正常
- ⚠️ 长文本走 GET query 被 Tomcat 拦截（陷阱 30）为**既有问题**，本次未改：约 900 个中文字（URL 编码约 8KB）起会被 Tomcat 返回 HTML 400；前端新增的 `readErrorMessage` 会把它兜底成中文「请求失败（400）」，比改动前的英文 `statusText` 有改善
- ⚠️ 千问 RAG 回复是否展示引用切片，取决于该模型自身是否输出 `[n]` 标注（既有门控逻辑，陷阱 46）：实测千问本次未打标，故未下发引用区——不是本次改动引入
- ⚠️ 管理页「已入库」切片数来自 MongoDB 向量集合；若把 `conditionProperty.ai.bean-type` 切到 `memoryVectorStore`，状态会全部显示「未入库」
- ⚠️ 主目录与 `yuque-sync/` 若出现同名 `.md`（只能由手工放置产生，上传会被重名校验拒绝），列表会出现两行同名、操作只作用于主目录那份。彻底修需把文档标识升级为「相对路径」，本次记为已知限制
- ⚠️ 上传的文档会被预处理**就地覆盖**（去 HTML 内联标签 + `##` 前插分割线），上传弹窗已提示。注意 `DocumentPreprocessor` 的 HTML 标签正则会吞掉尖括号内容（如 `List<String>` → `List`），这是既有离线流水线的同一行为，本次未改其语义（如需改进属另一需求）
- ⚠️ 任务状态存内存：后端重启后「失败原因」丢失，降级为「未入库」（文件与向量不受影响，点「重新入库」可恢复）
- ⚠️ 本机未安装 mongosh：管理员授权已可在「用户管理」页自助完成，只有"库中一个管理员都没有"的首次引导需要 MongoDB Compass 手工执行（命令见「后续优化（待办）」）
- ⚠️ 本次自测留下的测试账号：`test_usermgmt`（role=0，密码 `TestUsermgmt2026`）——注册接口需要图片验证码、无法脚本批量造号，为验证"非管理员 403 / 权限即时生效"只能真注册一个；用户自行注册的 `test123123` 同为测试账号。应用未提供删除用户功能，清理需手工操作数据库
- AGENTS_BAK.md 是用户自己的备份文档（66 KB、未跟踪）——**已明确（2026/9/20）：直接忽略**，不作为待办、不进库、不参与差异对比
- `master` 与 `test` 的差异——**已明确（2026/9/20）：对比时直接无视**。差异只来自既定安排：`master` 不放文档类文件（`.agents/skills/**` 28 个、`notes/` 19 个、`src/main/resources/document/` 语料 23 个，共 70 个），也不跟踪 `PROGRESS.md`，都不是问题。实践约定：① 对比两分支时把这些文档路径排除，例如 `git diff test master -- . ':(exclude).agents' ':(exclude)notes' ':(exclude)src/main/resources/document' ':(exclude)PROGRESS.md'`；② `PROGRESS.md` 只在 `test` 上维护；③ 功能代码改动仍需 cherry-pick 到 `master`（文档差异之外的代码，merge 不会带过去）
- ⚠️ 工作区未提交改动：`.gitignore`（用户自行调整格式 + 追加 `.mimosa/`，按用户要求未核查其内容、也未纳入提交）与多模型功能的三端源码；本轮按用户要求未做任何 git 操作
- 自动化测试框架（JUnit/vitest）已决定不引入（P2 评估后放弃），测试维持手动 curl/页面
- MCP 服务暂不配置（后续可能接数据库 MCP，待定）
- ✅ **已修复（2026/9/30）：`local` profile 下 `npx` 拉起高德地图 MCP server 导致启动失败**——根因是该 profile 未显式写 `mcp.client.enabled`（默认 true）从而覆盖主配置的 `false`，首次 npx 下载超 20s 初始化超时；现已在 `application-local.yml` 写 `enabled: false`，启动耗时 26s→5.97s、日志零 MCP 关键词（详见陷阱 5）
- ⚠️ 本次 web 端 3 处修复已完成**浏览器级验证**：分块列表修复前后逐项量过尺寸（修复前 76 个分块各被压成 1px、修复后统一 87px 零裁切）、下拉面板定位与关闭路径、排序请求次数由 2 降 1、375px 无横向溢出；遗留 ③④ 亦用"注入延迟制造乱序 + 撤守卫反证"的方式验证。历史遗留的录音/滚动条类修复仍建议本地 `npm run dev` 复核（需真实麦克风）

## 下一步

> 已闭环的历史步骤（版本号升级、规则文档同步、`chatBatchMode` 死代码、临时分支 `knowledge-doc-manage` 清理、`master` 的 cherry-pick 与推送等）见「本次改动记录」，此处只留仍待推进的事项与长期约定。

1. **`master` / `test` 协作约定（2026/9/20 已定，长期有效）**：① 不 merge，只把 `test` 的功能提交 cherry-pick 到 `master`；② 对比两分支时排除 `master` 本就不放的文件（`.agents/`、`notes/`、`src/main/resources/document/`、`PROGRESS.md`），实践命令见「风险 / 遗留问题」；③ `PROGRESS.md` 只在 `test` 维护；④ `master` 上两条提交信息与内容不符（`e9a038e` / `95c798a`）按用户指示不处理（改写需 force-push）；⑤ 临时分支 `knowledge-user-manage`（本地与远端）待删除。
2. **入库忽略约定（已核实）**：`src/main/resources/application.yml`（含密钥）与 `src/main/resources/document/`（运行时上传的文档）均被 `.gitignore` 忽略；`docs/plans/` 同样不入库（2026/9/21 用户决定）——只在用户**明确要求**时才把计划写入该目录。
3. **后续需求与长期事项**：见「后续优化（待办）」——遗留 ① 应用外壳矮视口不滚动、全站触控目标补齐、个人信息入口重构、用户管理批量机制描述符化与「批量改用户名」前置条件、manus 超级智能体。
4. **本次需求（多模型切换 + 大模型展示页）待你决定**：ⓐ「模式一（备用）」注解保留方案是否合适；ⓑ `AuthService` 随机 JWT 密钥的既有缺陷是否要修（见「风险 / 遗留问题」）；ⓒ 何时写 CHANGELOG 与升版本（建议 `0.2.0` → `0.3.0`，按你指示没动）；ⓓ 是否把 `ToolCallRepairingManager` 也挂到 DeepSeek 两个模型（当前未挂，实测它们不分片）；ⓔ 小程序是否也要「大模型展示页」（原需求定的是"只在 Web 端设计"）；ⓕ 提交与推送时机。另：调试用的 `ChatModelTest.java` 是否入库由你决定；你手写的 `.model-trigger { border: 1px }`（只写宽度没有样式，等于不画边框）保留未动。
