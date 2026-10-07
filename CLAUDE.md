# AI-Agent 项目规则

> 约束性规则文档。AI 在 `ai-agent` 项目里生成代码必须遵守，违反视为不合格。
> **维护（活系统）**：代码改动先交付验证，**用户确认无误后**再统一同步文档——规则/要点追加到 AGENTS.md 对应章节；踩坑详情追加到 `docs/known-pitfalls.md` 并在索引补行；`README.md` / `docs/*.md` 中涉及本次改动的部分同步更新；**AGENTS.md 有改动必须同步 `CLAUDE.md`（两份保持一致）**。避免每次小改动频繁动文档，未确认前不更新。

---

## 🎯 项目身份

全栈 AI Agent 平台：Spring Boot 3.5.10 + Java 17（必须 `--enable-preview`）+ Vue 3，Spring AI 1.0.0-M6。
对话模型 **4 个可切**：deepseek-flash / deepseek-v4-pro / qwen3.7-plus / qwen3.8-flash（`MyChatClientConfig` 一模型一 ChatClient Bean，Bean 名 = 模型 key，清单与元数据在 `constant/ChatModelCatalog`）；向量模型 **千问 Qwen-Plus**（DashScope，1536 维）；RAG 用自研 MongoDB 向量库；JWT 鉴权。
超级智能体 **YuManus（任务制 ReAct）**：提交任务 → 规划分解 → 逐步执行 → 结构化事件流 → 任务报告 + 交付物；主模型千问（`dashscopeChatModel`），子智能体（researcher / knowledgeResearcher / writer，agent-as-tool 派发）用 DeepSeek（`openAiChatModel`）；事件协议与接口见 `ManusTaskService` / `ManusController`。
三端：**Web**（frontend/）· **微信小程序**（miniprogram/，独立 uni-app 工程）· **安卓壳**（android/，WebView 远程加载已部署 H5，前端零改动）。

---

## 🏗 目录结构（代码该放哪）

| 包 | 职责 |
|---|---|
| `advisor/` | Advisor 横切逻辑（日志、提示词优化） |
| `agent/` | Agent 核心（BaseAgent→ReActAgent→ToolCallAgent→Manus） |
| `app/` · `chatmemory/` | 业务应用（KnowledgeApp）· 聊天记忆 |
| `bootstrap/` | 启动自检/初始化（ApplicationRunner、CommandLineRunner 等启动期逻辑） |
| `config/` · `filter/` | 全局配置（CORS/JWT/Auth/McpFallbackConfig/MyChatClientConfig 四模型 ChatClient/ToolCallRepairingManager）· JWT 过滤器 |
| `constant/` | 常量与静态配置数据（目录常量、黑名单、角色取值表、ChatModelCatalog 模型目录等） |
| `controller/` | REST 控制器（**只做转发，不含业务**） |
| `degradation/` | 统一降级框架（@Degradable 注解 + AOP 切面） |
| `demo/` | 学习/示例代码，非生产逻辑，勿在此新增业务代码 |
| `model/` · `repository/` | 数据模型/DTO · 数据访问层 |
| `rag/` | 向量存储/文档加载/查询改写等技术实现（**不放对外 service**） |
| `service/` · `tool/` | 业务逻辑（含面向 RAG/知识库的服务）· Agent 工具 |
| `notes/` · `docs/` | 知识库素材（处理后作 RAG）· 文档 |

新增功能按职责归类：配置→config/，工具→tool/，检索→rag/，DTO→model/，常量→constant/，业务逻辑→service/，启动自检→bootstrap/。
**分层优先**：对外 service 一律放 `service/`，即使它服务于 RAG/知识库领域也不放进 `rag/`（`rag/` 只放技术实现）。
**项目结构尚未完善**：新增代码若没有对应职责的包，**直接新建即可**（如已新建的 `bootstrap/`；跨功能共用的枚举变多时可新建 `enums/`），逐步补齐结构，不要为了"不新建包"把类塞进不合适的包。
枚举归属现状：优先随所属功能包（`agent/model/AgentState`、`degradation/FallbackStrategy`、`model/DocumentStatus`）。

---

## 📚 文档地图（按需读取）

> 动手前先 Read 对应文档获取最新知识，禁止凭记忆猜。

| 场景 | 读什么 |
|------|--------|
| 项目总览 / 快速开始 / API / 构建部署 / 运维 | `README.md` |
| 版本变更记录（发版时更新） | `CHANGELOG.md` |
| 安卓 APP 构建、安装、部署 | `docs/android-app.md` |
| 微信小程序开发、真机调试 | `docs/miniprogram.md` |
| 踩坑详情（索引编号对应） | `docs/known-pitfalls.md` |
| 实施计划（过程文档，未入库） | `docs/plans/*.md` |
| 通用规则模板（新建项目起步） | `docs/rules-template.md` |
| 项目进度（每次开发后更新） | `PROGRESS.md` |
| 项目进度模板（新项目起步） | `docs/PROGRESS-template.md` |

> 规则本体：`AGENTS.md`（主）+ `CLAUDE.md`（镜像，改一份必须同步另一份）。
> 非文档、勿提交（均已列入 `.gitignore`）：`.zcode/` · `.agents/` · `.claude/`（AI 工具配置）、`.mimosa/`（Mimosa 安全插件状态目录）、`AGENTS_BAK.md`（个人备份）、`HELP.md`（Spring 脚手架残留）。

---

## 📐 代码生成强制规则

### 通用
- ✅ **先读文档再动手**：开发/运维前，先 Read「📚 文档地图」对应文档获取最新知识，禁止凭记忆猜
- ✅ **拿不准就问**：需求理解、技术方案、代码行为有疑问，必须向用户提出，禁止自行猜测
- ✅ **遵循目录结构**；**该拆类就拆类**，禁止把多职责堆进一个大而全的类
- ✅ **保留无关的原有注释/注释代码**，只改动主题相关的必要部分
- ✅ **新功能先规划后开发**：新功能/跨模块改动前，除简单功能外先出方案（技术选型/模块划分/任务拆解/验收标准）给用户确认；问题修复可直接动手
- ✅ **文档随改动同步**：时机与范围见文首「维护（活系统）」；同步时若文档地图的描述已过期，一并修正

### AI 行为红线
- ❌ **不编造**：不虚构文件路径、类名、API、版本号；不确定的内容先用工具验证（Read/Grep/curl）再写
- ❌ **不臆测**：需求理解、技术方案有疑问必须先问用户，不自行假设后交付
- ✅ **最小改动**：只改任务相关代码；不顺手重构无关模块、不留 `console.log`/调试代码
- ✅ **复用优先**：已有函数、组件、工具必须复用，不另写一套
- ✅ **完整交付**：不留 TODO/FIXME 占位、不半途交付；做不完要说明原因 + 替代方案
- ✅ **诚实报告**：未验证、测试失败、跳过的步骤必须如实告知，不虚报"已完成"
- ✅ **破坏性操作先确认**：删除/覆盖/批量修改/发布前必须征得用户确认

### 命名与代码风格
- ✅ 命名规范：类/组件大驼峰，方法/变量小驼峰，常量全大写，包/目录全小写，见名知意禁随意缩写
- ✅ 格式统一：缩进、引号、分号、行宽按项目约定；文件 UTF-8
- ✅ 编辑器格式约定见根目录 `.editorconfig`（UTF-8 / CRLF，Java 4 空格、前端与配置 2 空格，`.sh` 用 LF，`.bat`/`.cmd` 用 CRLF，末行换行）；**不引入** Spotless、Prettier 等强制校验工具
- ✅ 行尾规则写进**受版本管理**的 `.gitattributes`（它被 `.gitignore` 忽略时只对本机生效，`core.autocrlf=true` 还会在检出时把 CRLF 写回工作区）
- ✅ 新代码模仿项目现有写法，保持风格一致，不引入异类写法

### 依赖与配置
- ✅ 不随意新增依赖：引入前确认必要性、兼容性、版本并锁版本；移除依赖同步清理引用
- ✅ 配置外置：端口/密钥/URL/账号走配置文件 + 环境变量，本地/测试/生产环境隔离
- ✅ 敏感配置不入库：含密钥的配置文件一律列入 `.gitignore`（排除清单见后文 Git 章节）

### 安全基线
- ✅ 输入校验：所有外部输入校验（长度/类型/白名单），防注入（SQL/命令/XSS）
- ✅ 敏感信息三不：不进代码、不进日志、不进前端响应
- ✅ 接口鉴权：受保护资源必须校验身份/权限，禁止绕过
- ✅ 文件与 URL 安全：上传限制类型/大小；下载/代理 URL 防路径穿越与 SSRF
- ✅ **凭据零外泄**：Agent 不读取、不展示含密文件内容（`application*.yml`、`.env`、keystore、`mcp-servers.json` 等只查结构、不输出取值）；配置引用一律走环境变量/占位符
- ✅ **对外动作先确认**：发送、上传、发布、推送到远端等动作**逐次征得用户确认**

### 健壮性与性能
- ✅ 资源释放：流/连接/订阅/定时器用后必关；Blob URL 成对 revoke
- ✅ 外部调用（网络/LLM/DB）：设超时 + 有限重试 + 降级兜底
- ✅ 数据访问：避免 N+1、合理索引、事务边界明确；大数据分批处理，不一次塞内存
- ✅ 错误处理：用户可见错误友好；不静默吞异常（catch 必有日志/提示）；不把内部堆栈暴露给用户

### Java
- ✅ Lombok（`@Slf4j` / `@RequiredArgsConstructor` / `@Data` / `@Builder`）；依赖注入用构造方法（`private final`）
- ✅ Controller 只做转发；public 方法写 Javadoc（纯文本，禁 `<p>`/`<ul>`/`<code>` 等 HTML 标签）
- ✅ 日志用 SLF4J 占位符：`log.info("action={}", action)`
- ✅ 异常抛给 `GlobalExceptionHandler` 统一处理
- ✅ 注释只写功能逻辑，禁止写"为什么这么改/踩坑原因/背景"（踩坑详情进 `docs/known-pitfalls.md`）
- ❌ 硬编码敏感信息（API Key、密码、JWT Secret）；字段注入 `@Autowired`

### Vue 3
- ✅ `<script setup>` + Composition API；样式 `scoped`；组件名大驼峰
- ✅ API 调用统一走 `src/api/request.js`；AI 的 Markdown 回复用 `marked` + `DOMPurify` 渲染
- ✅ 图标统一 `@lucide/vue` SVG，规格见「🎨 前端 UI/UX 硬约束」（禁止 emoji 作 UI 图标）
- ✅ 认证状态：从 `auth.js` 导入响应式 ref，禁止在 computed 里直接读 localStorage/`getUsername()`；`setToken`/`removeToken` 同步更新 localStorage 与 ref
- ✅ 密码框必须有显示/隐藏切换（眼睛 SVG，`position:absolute`，`tabindex="-1"`）；表单校验失败用 shake 动画 + 红色提示
- ✅ blur/click 竞态：操作按钮用 `@mousedown.prevent.stop`，blur 回调加编辑状态守卫
- ✅ 跨组件状态用 `provide`/`inject`（禁止 Pinia/Vuex）
- ✅ CSS 变量定义在 `App.vue` `:root`（`--bg-*` / `--text-*` / `--border-*` / `--shadow-*`）
- ✅ 返回按钮胶囊 `border-radius:999px` + SVG 箭头 + 玻璃拟态；对话头像 40×40 等大（margin-top 对齐首行文字）；侧边栏 260px、历史项右 padding ≥72px

---

## 🎨 前端 UI/UX 硬约束

**任何 UI/UX 改动前必须先加载 `/ui-ux-pro-max` 技能**获取设计规范。

- ✅ 可点击元素 `cursor-pointer` + hover 反馈（150-300ms 过渡）
- ✅ 触控目标 ≥44×44px；文本对比度 ≥4.5:1；focus 状态可见
- ✅ 动效用 `transform`/`opacity`（非 width/height）；响应式断点 375/768/1024/1440px
- ✅ 卡面/弹窗优先玻璃拟态（`backdrop-filter: blur` + 半透明 + 柔和阴影）
- 配色：主 `#10B981` · 辅 `#34D399` · 强调 `#F59E0B` · 背景 `#ECFDF5` · 文字 `#064E3B`
- 字体：正文 Plus Jakarta Sans · 装饰标题 Playfair Display · 书法 Ma Shan Zheng
- 图标：`@lucide/vue`，24×24 viewBox / 1.5px 描宽 / 圆角端点

---

## ⚠️ 已知陷阱（索引）

> 编号 + 一句话摘要。写代码遇相似报错/场景先看编号命中，再查 **`docs/known-pitfalls.md`** 详情。

### 后端陷阱（Spring AI / Spring Boot / Java）
1. **PGVector 二选一**：pgvector-store 手动/自动构件不可同时使用
2. **JWT 签名密钥是随机的**：`AuthService` 用 `Jwts.SIG.HS256.key().build()` 启动时随机生成，`app.jwt.secret` 未被使用 → **每次重启后端旧 token 全部失效**；若改为读配置，secret 必须 ≥256 bits
3. **iText 中文 PDF 字体**：优先用系统字体 `C:/Windows/Fonts/msyh.ttc,0`（iText 9 仅支持 2 参 createFont）
4. **LLM 调用限流**：调用方必须做重试与降级
5. **MCP 客户端默认禁用**：勿删改 `McpFallbackConfig.java`；**local profile 的 stdio 配置会覆盖 `enabled`**（须显式写 `false`，否则首次 npx 下载超时会让启动直接失败）
9. **DashScope Bean 名全小写**：`dashscopeChatModel` / `dashscopeEmbeddingModel`（非驼峰）
10. **@Resource 字段名大小写敏感**：字段名必须与 bean 名完全一致
11. **多模型共存用 @Qualifier 区分**：DeepSeek 对话→openAiChatModel，千问对话→qwenChatModel（兼容模式），向量化→dashscopeEmbeddingModel
12. **YAML 缩进严格**：禁止混用 Tab/空格，注意多 profile 覆盖
13. **embedding 需独立配置**：DeepSeek embedding 可能 404，保留 DashScope 专门向量化
14. **@Tool 方法名不能重载**：同名重载启动报错，用多参数方法替代
15. **ToolRegistration 循环依赖**：WorkflowEngine 本地 new（推荐）或 @Lazy
16. **MongoDB 社区版无 Atlas Search**：用自研 `MongoVectorStore`，勿引入 atlas-store 构件
17. **DashScope embedding 单次上限 25 条**：自定义按条数分批的 BatchingStrategy
18. **Spring AI M6 Document 不可变**：builder 重建；VectorStore 实现 4 个方法
19. **向量库幂等增量加载**：documentId=文件名#内容MD5，upsert 防重复向量化
20. **标题/metadata 不参与向量化**：标题须拼入正文再入库（检索不准头号原因）
21. **MarkdownDocumentReader 按标题切片**：勿重复实现补分割线逻辑
22. **status 分类标签语义化**：用 extractTopic 去序号前缀，勿取文件名中间字符
23. **中文长文本相似度偏低**：similarityThreshold 经验值 0.5（0.6 会漏召回）
24. **ChatClient.user(String) 走模板渲染**：prompt 禁止出现 `{}` 字面量
25. **LLM 不调预设工具 / 误走 RAG**：工具描述强引导 + 单一判据 + 阈值兜底
26. **WorkflowEngine SPEL**：变量须带 `#` 前缀；工具输出无法属性访问，需注册 SPEL 函数
27. **tool.call() 返回值被 JSON 序列化**：需 readValue 还原（normalizeToolOutput）
28. **图片下载必须走 ImageProxyService**：防盗链需完整浏览器请求头
29. **防盗链素材站源头过滤**：双层防线（搜索排除+下载前拦截），含主站+CDN 域名
30. **长文本禁止放 GET query**：>8KB 触发 Tomcat 400，一律 POST + JSON body
31. **Spring 6 无 AUDIO_MPEG 常量**：用 MediaType.parseMediaType("audio/mpeg")
32. **DashScope TTS 接入要点**：Bean 驼峰命名；模型/音色须匹配；必须重试 + 缓存（tmp/tts）
34. **DashScope STT 接入要点**：离线识别仅公网 URL；本地走实时 WS；需超时保护 + 重试
37. **Knife4j 4.5.0 + springdoc 2.8.9**：Spring Boot 3.5 需显式钉版本，勿误删
40. **AI 误把 tmp/file 当知识库**：prompt.yml 明确"工作缓存非知识库"，禁止主动 listFiles
45. **历史会话按 updatedAt 排序**：null 回退 createdAt 且排最后，勿在 MongoDB 层 Sort
46. **RAG 引用门控**：仅当回复含 [n] 标注才下发 references
56. **知识库文档目录写入限制**：上传写在 `app.knowledge.document-dir`，jar 部署该目录只读、上传必失败；加载器须「真实目录优先 + classpath 回退」；预处理分割线插入须幂等；重新入库先删旧向量并**原地覆盖磁盘文件**（受版本管理的文档重跑后会在 git 工作区变成已修改）
57. **MongoDB 分页排序须加唯一二级键**：主排序字段有同值时顺序不稳定，翻页会重复或漏行（按注册时间排序时须再按 username 排序）
58. **超大页码会让分页偏移溢出**：`page * size` 超出 int 上限后 skip 被截断，`total` 变成垃圾值并返回首页数据，page 必须设上限
59. **「禁止改自己」不等于「至少留一个管理员」**：无事务时两个管理员并发互降会把管理员清零，须写入后复查管理员数并在为 0 时回滚
61. **千问两款必须走 OpenAI 兼容模式端点**：原生文本端点不支持 qwen3.7-plus / qwen3.8-flash（`InvalidParameter: url error` → SDK 聚合分片 NPE → 500），用 `OpenAiChatModel` 指向 dashscope 兼容模式
62. **Spring AI M6 会把流式工具调用拆成两条**：DashScope 给 3.8 系列续传分片带 `id:""`，表现为 `toolInput cannot be null or empty`(400) / `toolName is null`(500)；用 `ToolCallRepairingManager` 拼接（官方 PR #6381 已修，升级 Spring AI 后可删）
66. **自写 Agent 执行循环必须复刻 run() 的消息初始化**：绕过 `BaseAgent.run` 自己写循环时漏了把用户任务 `messageList.add(new UserMessage(任务))` → 模型拿不到任务全程空转；手写循环与 run() 的差异要逐项核对
67. **Agent 最后一条消息 ≠ 面向用户的交付物**：模型常把"生成报告"当计划步骤，最后一条只剩"现在调用 doTerminate"类过程独白——提示词补丁管不住；任务收尾必须做一次**专职无工具的报告生成调用**（输入=用户问题+执行记录摘要，输出直接作 finalReport），失败才回退思考文本
68. **文件路径白名单校验必须用 Path 组件级比较**：字符串 `startsWith` 会被同级目录（`tmp2`、`tmp-backup`）绕过；`Path.startsWith(根目录)` + `relativize` 推导相对路径，登记与下载/预览两处都要校验
69. **spring-boot:run 的 jvmArguments 值含空格只生效第一段**：`-Dspring-boot.run.jvmArguments="a -Db -Dc"` 中 b、c 被 Maven 吞成自身属性不进应用 JVM；多参数传递改用环境变量（`@Value("${manus.agent.pool-size:4}")` 可由 `MANUS_AGENT_POOL_SIZE` 映射）
70. **对 MongoDB 库内字段/键名的假设必须直查验证**：向量库切片 metadata 以为有 `title` 实为 `filename`（pymongo/Compass 一查便知）；写取值逻辑前先查真实存储结构
72. **交付物登记须认识生成工具返回的下载 URL**：模型把 `/api/files/...` 原样传给 register 会"文件不存在"，解析要按文件名兜底匹配
73. **自动收尾计划后必须补发 plan_updated 事件**：只写库不补事件 → 前端计划面板一直转圈（库内终态与事件流视图是两条路径）
74. **报告"声称与实际不符"要清单注入 + 句级校验兜底**：prompt 注入真实交付物清单；落库前句级校验（声称词+未登记文件名）追加更正段

### 前端陷阱（Vue Web）
6. **localStorage 非响应式**：必须用 auth.js 响应式 ref，禁止 computed 里读 getUsername()
7. **blur 与 click 竞态**：操作按钮用 @mousedown.prevent.stop，blur 回调加 editingChatId 守卫
8. **CSS absolute 容器塌陷**：优先 flex + 负边距替代绝对定位
33. **Blob URL 必须成对 revoke**：onended/onerror/主动停止/play reject 四路径
38. **生产 BASE_URL 用相对路径**：`''` + 同源 /api（nginx 反代），index.html 加 no-cache
39. **语音播报按钮**：已展示（原 `v-if="false"` 隐藏已去掉）；仅图标、无边框无底色，播报中换 `VolumeX` 转红表示可停止
41. **移动端适配要点**：16px 防 iOS 放大 / 100dvh / 抽屉侧边栏 / safe-area / ≥44px 触控
42. **flex 子项 min-width:auto 挤出屏幕**：容器加 min-width:0 + 换行布局
43. **/g 正则 lastIndex 残留**：使用前必须重置 lastIndex=0
44. **AI 下载地址需前端链接化**：linkify.js 链接化 /api/ 根相对路径，跳过 pre/code
49. **语音识别(STT)错误提示**：录音过短(<0.6s)前端拦截；后端 400 响应带 message；前端优先取 response.data.message，再按状态码/网络/超时映射中文提示
50. **历史对话滚动条与收起按钮重叠**：收起按钮置于侧边栏右缘外侧（left:260px 不居中），滚动条保持贴右侧边框
51. **user-dock 与页面内容重叠**：`/knowledge`、`/knowledge-documents`、`/user-manage` 三页 `showDock` 恒 false，暂时隐藏个人信息组件（布局待后续优化）
52. **flex 列容器子项被压扁裁切**：子项 `overflow` 非 visible 时「自动最小尺寸」为 0，必须显式 `flex-shrink: 0`
53. **自定义浮层（下拉/菜单）必须 Teleport + fixed**：`html, body { overflow-x: hidden }` 使 body 成为滚动容器，absolute 浮层会被裁剪
54. **详情类请求要做时序保护**：先发的响应后到会覆盖后发的，用递增请求序号只采纳最新
55. **「重新入库」按"非处理中"显示 + 已完成二次确认**：它既是失败恢复也是主动重跑，收紧成"仅失败显示"会砍掉日常重跑入口；已完成重跑会先删旧切片，需确认
60. **后台标签页（`document.hidden`）会被浏览器节流**：CSS 过渡不结束（元素残留 DOM）、定时器（toast 自动消失）延迟、浏览器自动化的可操作性检查超时——验证页面行为前先确认 `document.visibilityState`
63. **textarea 的上内边距在滚动区内**：文字超长内部滚动会把顶部留白顶出可视区（看着"留白越来越少"）；顶部留白要放在外层容器或改用 `margin`
64. **移动端"控件全进输入框"布局**：桌面/移动共用一份 DOM 时，内层容器用 `display: contents` 让出，工具条 `flex: 1 1 0` 吃剩余宽度（否则整行换行）、模型按钮 `width: 100%`（否则按钮是 fit-content 会压到语音按钮）
71. **SSE 客户端帧解析必须逐帧拆分**：按 `lastIndexOf('\n\n')` 整段切分时，一个网络包到达多条帧会拼成一个 JSON 解析失败**静默丢弃** → 事件缺失使增量订阅起点偏移、后续请求被跳过（故障延迟爆发极难排查）；`split('\n\n')` 后最后一段留在 buffer，其余逐帧解析

### 脚本工具陷阱（html-to-md / 文档处理）
35. **HtmlToMarkdownConverter 要点**：getWholeText / 递归子节点防自环 / 跳过代码围栏 / 保留原文
36. **书签模式要点**：三种格式识别 / isBookmarksJson 收紧 / 路径引号包裹 / Cookie 绕 521

### 移动端陷阱（安卓 APP + 微信小程序）
47. **安卓 WebView 壳要点**：onCreateWindow 拦截 target=_blank / 三条下载路径 / 录音 HTTPS+双层权限 / 菜单 Teleport 防裁剪
48. **微信小程序要点**：真实 AppID / es6:false / 预览链语法降级 / 图片本地化下载 / 录音参数 / 流式纯文本渲染
65. **小程序 `<text>` 上的 `text-overflow` 不生效**：单行省略号必须用 `<view>`（项目里 `.tool-title`、历史项等能正常省略的都是 view）；否则名字不截断、整行被撑宽把后面的按钮挤出容器
75. **小程序/uni 自定义组件复用时 mp-html 内容不更新**：切换任务后报告区空白（v-for key 同构复用实例）——长内容型组件加**内容相关动态 key** 强制重建
76. **uni H5 的 scroll-view 直接设 scrollTop 无效**：滚动由组件内部状态管理，验证/自动化要用真实滚轮事件（cua.scroll）

---

## 🔑 关键配置

- 端口 `8123` · Context Path `/api` · Swagger `/api/swagger-ui.html` · 默认账号 admin/admin
- 对话：4 个可切模型（`MyChatClientConfig` 一模型一 ChatClient Bean，Bean 名 = 模型 key）——deepseek-flash / deepseek-v4-pro 走 `spring.ai.openai.*`；qwen3.7-plus / qwen3.8-flash 走 dashscope **兼容模式**端点（`spring.ai.dashscope.*`，见陷阱 61）；清单与元数据在 `constant/ChatModelCatalog`
- 向量：qwen-plus（`spring.ai.dashscope.*`，1536 维）
- 向量库：MongoDB（`MongoVectorStore`，集合 `vector_store`；`conditionProperty.ai.bean-type` 可切内存库）
- Manus 超级智能体：任务数据存 `chat_memory_db` 库 `manus_task` 集合（manus_ 前缀与 `chat_memory` 区分）；`manus.agent.max-steps`（默认 20）/ `manus.agent.sub-max-steps`（子智能体，默认 10）/ `manus.agent.pool-size`（默认 4，环境变量 `MANUS_AGENT_POOL_SIZE` 可覆盖，见陷阱 69）；主模型千问、子智能体 DeepSeek；交付物白名单目录 `{user.dir}/tmp/`
- ADVISOR 链：MyLoggerAdvisor → ReReadingAdvisor → RAG Advisors
- MCP 客户端默认禁用（`McpFallbackConfig` 兜底，勿删；`local` profile 须显式写 `enabled: false`，否则启动会失败——见陷阱 5）；JVM 必须 `--enable-preview`

---

## 📦 快速命令

```bash
script\start-backend.bat                    # 后端启动（或 mvnw spring-boot:run 加 --enable-preview）
cd frontend && npm run dev                  # 前端
./mvnw clean package -DskipTests            # 构建
script\html-to-md.bat <网页收藏目录>         # 网页收藏 → 知识库 md
script\preprocess-docs.bat <笔记目录>        # 原始笔记清洗
cd android && gradlew.bat assembleDebug     # 安卓 APK（需 JDK17+SDK，见 docs/android-app.md）
cd miniprogram && npm install && npm run dev:mp-weixin   # 小程序编译
```

---

## 🔧 Git 提交规则

- ✅ 只**精确 `git add <文件>`**；禁止 `git add .` / `-A` / `*`
- ✅ 提交前 `git status` 核对改动列表；提交后 `git push`（先 `git pull`）
- ✅ 一律排除：`frontend/dist/`、`**/__pycache__/`、`.idea/`、`application*.yml`（含密钥）、测试临时文件
- ✅ **提交信息格式**：首行 `YYYY/M/D（操作类型）：`（不补零、全角冒号），操作类型按本次改动归类——**WEB端功能**（前后端改动都归此类）/ 安卓端功能 / 微信端功能 / 脚本文件 / 文档操作 / 配置构建等，**提交信息里只写类型词本身，不带括号说明**（如 `2026/8/30（文档操作、微信端功能）：`）；**多个类型用「、」顿号分隔**；每条一行 `1. 名词+动词；`；多行信息用 heredoc
- ❌ **首行与第 1 条之间不要空行**：不要套用英文提交习惯的"标题 + 空行 + 正文"结构，第 1 条要紧接首行
- ✅ **提交信息要简洁、只写重点**：让人一眼看清"做了什么、影响什么"；不堆专业术语（类名/方法名/参数/常量名）、不写具体实现细节与操作步骤，细节留给代码与 PROGRESS.md
  - ❌ `1. 用 Math.max(1, Math.min(page, MAX_PAGE)) 给页码加上限，避免 page*size 溢出导致 skip 被截断、total 变成垃圾值；`
  - ✅ `1. 修复超大页码导致分页数据错乱；`

提交信息正确格式（第 1 条紧接首行，编号各占一行）：

```
2026/9/21（文档操作）：
1. AGENTS.md 明确提交信息格式：首行后不空行，编号紧接首行；
2. PROGRESS.md 记录本次改动。
```

---

## 🔖 分支 / 版本 / 变更记录

### 分支策略
- `master` 为稳定主干，只接受已测试的功能合并
- 功能/修复从 `master` 拉分支，命名见名知意（如 `knowledge-miniprogram`）
- 完成流程：自测 + 审查 → 合并回 `master` → 删除功能分支

### 版本号（SemVer）
- 格式 `主版本.次版本.修订号`：主版本=不兼容变更，次版本=向后兼容新功能，修订号=向后兼容修复
- Maven 项目版本在 `pom.xml` 维护；安卓版本在 `app/build.gradle.kts`（`versionCode` 整数递增 / `versionName` 如 `1.0.0`）

### 变更记录（CHANGELOG）
- 根目录 `CHANGELOG.md`，按版本号倒序记录
- 每版本条目：`版本号 / 日期` + 分条变更（新增/修复/优化/重构），与 Git 提交信息格式一致
- **更新时机**：用户说「可以提交 / 提交吧」即本次需求**验收通过**——此时一并完成 CHANGELOG 条目与版本号升级（SemVer，见上一节），**不要挂成"待决定"事项**；版本号按改动性质定（新增能力→次版本，修复→修订号）

---

## 🧪 自测试规则

**每次代码修改后必须自测（用户声明"不需要测试"除外）。**

- **后端**：编译 → 启动 → 新改接口 curl 验证 → 查日志 → 回归登录/聊天/历史 → 关闭端口
- **前端**：`npm run build` 无错 + 页面交互验证
- **边界**：空数据/错误输入/极端值要有友好提示
- **🚨 功能回归红线**：改动前梳理关联点（共用组件/请求层/工具函数/事件链/样式/跳转链路）；改动后逐一回归所有受影响功能——最隐蔽风险是"改一个功能悄悄弄坏另一个"
- **测试卫生**：测试文件统一 `tmp/test-*` 且命名带 test 标识；清理只删本次产生文件，**禁止 `rm -rf`/通配符清目录**；测试产物不得进知识库/提交
- **独立功能测试**：开发后调用功能测试子智能体（`code-test`）交叉验证，避免"自己写自己测"的确认偏差
- **Code Review**：自测通过后调用代码审查子智能体（`code-review`）审查（缺陷 + 规则符合性），按 P0-P3 修复并告知用户
- **进度更新（PROGRESS.md）**：开始新需求前，先写入根目录 `PROGRESS.md` 的「需求/任务清单」；开发/运维结束后更新任务状态、本次改动、下一步、风险，保持会话间衔接。**改动记录抓重点：每条 3–5 句（做了什么/影响什么/验证结论），禁止堆类名、参数、操作步骤——实现细节归 `docs/known-pitfalls.md` 与平台文档，PROGRESS 只留结论与指向**

> 编译通过 ≠ 能运行：Bean 冲突、配置问题、工具名重复只在启动时暴露。

---

## 📋 AI 输出前自检清单

- [ ] 包路径 `com.example.aiagent.*`？`@Slf4j`？构造注入？无硬编码？异常走 `GlobalExceptionHandler`？
- [ ] 无编造/未经验证即写的内容？无调试代码、`console.log`、TODO/FIXME 占位残留？
- [ ] 外部输入已校验？敏感信息未泄露？资源（流/连接/Blob URL）已释放？
- [ ] Agent 新增 Tool 已注册？涉及 MCP 改动时 `McpFallbackConfig` 是否需同步？
- [ ] UI/UX 改动前已加载 `/ui-ux-pro-max`？SVG 替代 emoji？密码框有切换？auth.js 响应式 ref？blur 竞态已处理？
- [ ] 文件放对目录？Vue 组件 `scoped`？
- [ ] 已完成自测 + 回归受影响功能 + Code Review？
- [ ] 新发现已写入：规则→AGENTS.md（同步 CLAUDE.md）对应章节，踩坑详情→docs/known-pitfalls.md？
- [ ] 文档已同步：README.md / docs/*.md / 文档地图描述、`PROGRESS.md`；涉及版本变更时 `CHANGELOG.md` 与版本号？
