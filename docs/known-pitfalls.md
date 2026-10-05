# 已知陷阱（踩坑记录）

> 本文件是 `AGENTS.md`（及 `CLAUDE.md` 镜像）「已知陷阱」章节的**详细展开**，由规则文档以索引形式引用。
> 陷阱按领域分为四组（后端 / 前端 / 脚本工具 / 移动端）。编号为**全局递增**（跨组不连续），新增陷阱沿用下一个全局编号，放入对应领域组即可。
> 每条陷阱 = 现象 + 原因 + 修复/规避方案。写代码时遇到同名报错、相似场景，先查本文件；`AGENTS.md`（及 `CLAUDE.md` 镜像）只保留编号 + 一句话索引。

---

## 后端陷阱（Spring AI / Spring Boot / Java）

1. **PGVector 二选一**：`spring-ai-pgvector-store`（手动）与 `spring-ai-starter-vector-store-pgvector`（自动）不可同时使用

2. **JWT 签名密钥是"每次启动随机生成"，`app.jwt.secret` 未被使用**：`AuthService` 构造时执行 `Keys.hmacShaKeyFor(Jwts.SIG.HS256.key().build().getEncoded())` —— 密钥每次启动重新生成；`config/JwtProperties` 虽然定义了 `secret`（默认占位值）并绑定了 `app.jwt`，但全项目**没有任何代码读取它**（`grep "app.jwt"` 只有 `JwtProperties` 一处声明）。**后果：每次重启后端，所有已签发 token 立即失效、用户必须重新登录**（本机自测期间反复遇到，README 与 `docs/miniprogram.md` 按现状记录了这一行为）。若日后改为读取配置密钥：**HS256 要求密钥 ≥256 bits**，长度不足会抛 `WeakKeyException`；且该配置必须走环境变量/占位符，不进仓库

3. **iText 中文 PDF 字体**：`font-asian` 模块的 `STSongStd-Light` 虽然能加载但**不会嵌入 PDF**，导致用户设备无此字体时显示乱码。**修复方案：** 优先使用 Windows 系统字体路径（`C:/Windows/Fonts/msyh.ttc,0`），iText 9 的 `PdfFontFactory.createFont(String, String)` 会自动嵌入系统字体到 PDF 中，文件约 170KB。注意：
   - TTC 文件需加 `,0` 索引后缀（如 `msyh.ttc,0`）
   - iText 9 不支持 `PdfFontFactory.createFont(String, String, boolean)` 3 参数重载，只能用 2 参数版本
   - 禁止在 pom.xml 中声明多个 `font-asian` 版本，否则导致字体注册冲突

4. **LLM 调用限流**：调用方必须做好重试和降级

5. **MCP 客户端**：默认禁用（`spring.ai.mcp.client.enabled: false`）。如需启用，需配置有效的 `mcp-servers.json`。禁用时，由 `config/McpFallbackConfig.java` 提供空的 `ToolCallbackProvider` 替代 Bean，保证 `@Resource` 注入不失败。**不要删除或修改 `McpFallbackConfig.java`，否则启动会失败。**
   - **profile 会覆盖 enabled（2026/9/30 实测踩坑）**：`application-local.yml` 里只写了 `mcp.client.stdio.servers-configuration`、**没写 `enabled`**，而 `mcp.client.enabled` 默认是 **true** → 用 `local` profile 启动（`script\start-backend.bat` 就是这么启的）时 MCP 客户端照样开启，`application.yml` 里那句 `enabled: false` 被 profile 覆盖。表现：启动日志里 npx 拉起「高德地图 MCP Server」，**首次运行因 npx 下载包超过 MCP 客户端 20 秒初始化超时** → `TimeoutException: Did not observe any item or terminal signal within 20000ms` → `mcpSyncClients`/`toolCallbacks` 连锁失败 → **整个 Spring 上下文创建失败、应用退出**（日志里 MCP 服务器稍后才打印 "running on stdio"，属包已下载完的滞后输出，不是"其实是好的"）。
   - **修复**：`application-local.yml` 的 `mcp.client` 下**显式加 `enabled: false`**（本机已加）。修复后实测：启动日志 `mcp/amap/npx/StdioClientTransport` 关键词 **0 行**、无 `TimeoutException`、**无 npx 子进程**、启动耗时 26s→**5.97s**。如确需 MCP，建议预热 npx 缓存后在配置里调大初始化超时

9. **Spring AI Alibaba Bean 命名全是小写 dashscope**：自动注册的 Bean 名称为 `dashscopeChatModel`、`dashscopeEmbeddingModel`（**全小写 dashscope**，不是驼峰 `dashScope`）。使用 `@Qualifier` 时必须拼写准确

10. **@Resource 按字段名匹配，大小写敏感**：`@Resource private ChatModel dashScopeChatModel;`（大写 S）不会匹配 bean 名 `dashscopeChatModel`（小写 s），会回退到按类型匹配。如果类型有多个候选则报错。**字段名必须与 bean 名完全一致**

11. **多模型共存时必须用 @Qualifier 区分**：当 `spring-ai-alibaba-starter`（千问）和 `spring-ai-openai-spring-boot-starter`（DeepSeek）同时存在时，会有 2 个 `ChatModel` Bean 和 2 个 `EmbeddingModel` Bean。每个注入点都必须明确指定用哪个：
    - 对话 → `@Qualifier("openAiChatModel")`（DeepSeek）
    - 向量化 → `@Qualifier("dashscopeEmbeddingModel")`（千问）
    - 查询重写 → `@Qualifier("openAiChatModel")`（DeepSeek）

12. **YAML 配置缩进严格**：YAML 对缩进极其敏感，修改 `application.yml` 等配置文件时必须使用正确层级，禁止混用 Tab 和空格。多 profile（local/prod）可能覆盖主配置

13. **切换模型时 embedding 需独立配置**：DeepSeek 的 embedding API 可能不可用（返回 404），需要保留千问的 DashScope 配置专门用于向量化。`spring.ai.openai.embedding.options.model` 可能需要单独指定

14. **@Tool 方法名不能重载（overload）**：Spring AI 的 `@Tool` 注解使用 Java 方法名作为工具注册名。同名的重载方法会导致启动时报错 `Multiple tools with the same name found in sources`。同一个类中每个 `@Tool` 方法必须有不同的方法名。需要用多参数方法替代重载，AI 模型会通过 `@ToolParam` 的描述决定传参

15. **ToolRegistration 循环依赖**：`ToolRegistration`（`@Configuration`）中通过 `@Resource` 注入 `WorkflowEngine`，而 `WorkflowEngine` 需要注入 `ToolCallback[]`（来自 `ToolRegistration.allTools()`），形成循环依赖。**修复方案**：打破循环有两种方式：
    - 方式一（推荐）：`WorkflowEngine` 不做 Spring Bean，在 `ToolRegistration.allTools()` 方法内本地 new 出来，先构建基础工具数组，再传入 `WorkflowEngine` 构造函数
    - 方式二：在 `WorkflowEngine` 的 `@Resource` 字段上加 `@Lazy`，延迟注入直到实际使用时才解析

16. **MongoDB 社区版不支持 Atlas Search**：`$vectorSearch` / `createSearchIndexes` 仅限 MongoDB Enterprise/Atlas（社区版 `--enableSearch` 报 `Unrecognized option`）。**项目已自研 `rag/MongoVectorStore.java`**（文档+向量存 MongoDB 集合 `vector_store`，应用层余弦检索 + filter 评估）。官方构件 `spring-ai-mongodb-atlas-store` 不要引入。`enableSearch`/`searchIndexManagement` 必须配置在 mongod.cfg **顶层**（与 `net:` 同级），放在 `net:` 子级会导致 mongod 启动失败

17. **DashScope embedding API 单次上限 25 条**：批量向量化（`embeddingModel.embed(docs, options, batchingStrategy)`）时默认 `TokenCountBatchingStrategy` 按 token 数分批（切片 token 少时单批可超 25 条），报 `The input texts limit 25`。修复：自定义按条数分批的 `BatchingStrategy`（每批 ≤20 条），见 `MongoVectorStoreConfig.DashScopeBatchingStrategy`

18. **Spring AI M6 的 Document 不可变**：没有 `setId()` 方法，需要 `Document.builder().id(x).text(x).metadata(x).build()` 重建。M6 的 `VectorStore` 接口需实现：`add(List<Document>)`、`delete(List<String>)`、`delete(Filter.Expression)`、`similaritySearch(SearchRequest)`；`Filter.Expression` 为 `(ExpressionType, Operand left, Operand right)` 结构，`Group` 操作数需解包

19. **向量库持久化的幂等增量加载**：为文档切片生成稳定 documentId（`文件名#内容MD5`），写入前查询集合已有 `_id` 过滤，避免重启重复调用 embedding API；`mongoTemplate.save` 为 upsert 语义（重复 id 覆盖）。DashScope 实际输出向量维度 **1536**（非配置项，索引/检索由 `EmbeddingModel.dimensions()` 决定）

20. **标题/metadata 不参与向量化（检索不准的头号原因）**：`MarkdownDocumentReader` 把标题放入 metadata 的 `title` 字段，而 embedding 仅基于正文（metadata 默认不参与）。**搜标题搜不到内容的修复：把标题拼入切片正文开头再入库**（见 `KnowledgeAppDocumentLoader.parseDocument`）。同理，`KeywordMetadataEnricher` 生成的关键词存 metadata 对检索无效，必须拼入正文或用于查询扩展

21. **MarkdownDocumentReader 按标题自动切片**：M6 的 reader 即使无 `---` 分割线也会按标题（##/###）切分，不要重复实现"补充分割线"逻辑；`---` 与标题都会产生切片边界。生成切片自带 `title`/`category` metadata（无需额外解析标题）

22. **status 等分类标签需语义化**：旧规则取文件名中间字符（`"1. JAVA.md"→"AV"`）无意义。改用文件主题（去序号前缀：`extractTopic` 正则 `^\d+\.\s*`），便于按分类过滤检索

23. **中文长文本余弦相似度普遍偏低**：`QuestionAnswerAdvisor` 的 `similarityThreshold` 经验值 0.5（0.6 会过滤掉本应命中的相关切片，与遗留 `KnowledgeAppRagCustomAdvisorFactory` 阈值一致）

24. **ChatClient.user(String) 会走 PromptTemplate 模板渲染**：prompt 中出现 `{` `}` 字面量（如 JSON 示例 `{"needsRetrieval": true}`）会报 `The template string is not valid`。**修复：prompt 中禁止出现 `{}` 字面量，用文字描述 JSON 格式**。注意：M6 的 `ChatClientRequestSpec` 只有 `user(String)/user(Resource)/user(Consumer<PromptUserSpec>)`，没有 `user(Message)` 重载

25. **LLM 不调用预设工具 + 任务型请求误走 RAG**：①工具 @Tool 描述需强引导（"多步骤任务必须使用本工具，不要分别调用其他工具"），工作流调用引导写在 `prompt.yml` 系统提示词的"工具使用"章节；②`QueryRewriter` 判定用"单一判据"（问题是否可能与用户笔记/收藏相关）而非场景清单（场景无限），任务型请求（搜索图片、生成PDF、下载等）判为不需要检索；③即使预判错误也有 `QuestionAnswerAdvisor.similarityThreshold` 兜底（检索不到就不注入上下文），预判错误代价仅是毫秒级向量检索；④改写查询必须保留任务步骤，不得删减（如"搜索图片并生成PDF"不能改写成只剩"搜索图片"）

26. **WorkflowEngine SPEL 细节**：SPEL 变量引用必须带 `#` 前缀（`{query}` 要解析为 `#query`，裸 `query` 报 EL1007E）；工具输出是纯文本字符串无法属性访问（`{result.urls[0]}` 无效），需注册 SPEL 函数提取（如 `toImages` 提取 URL 转 Markdown 图片、`extractUrl` 取首个 URL）；多参数工具调用需构建 JSON 对象 `{"参数名": "值"}` 传给 `tool.call(String)`

27. **tool.call() 返回值会被 JSON 序列化**：Spring AI 的 `DefaultToolCallResultConverter` 把工具返回值 `JsonParser.toJson(result)`——String 值变成**带首尾引号和转义的 JSON 字符串字面量**（真实换行变成字面量 `\n`、引号变 `\"`）。工作流步骤间传值时必须用 `objectMapper.readValue(output, String.class)` 还原（`normalizeToolOutput`），否则后续基于行结构/正则的处理全部失效

28. **图片下载必须走 ImageProxyService**：Bing 等 CDN 防盗链校验需要完整浏览器请求头（`sec-ch-ua`、`sec-fetch-*` 等），仅 UA+Referer 会被拒（返回 403/防盗链占位图）。`service/ImageProxyService.java` 统一提供下载（完整请求头 + 多策略 Referer），`ImageProxyController`（前端展示，单次尝试 `fetchOnce`）与 `PDFGenerationTool`（PDF 插图，多策略 `fetch`）共用，禁止各自实现下载逻辑

29. **防盗链素材站需在搜索结果源头过滤（含 CDN 域名，双层拦截）**：部分站点（如 `nipic.com` 昵图网、`51wendang.com`、`dfic.cn` 图虫、`veer.com`、`quanjing.com`、`vcg.com` 视觉中国、`58pic.com`、`zcool.com.cn`）的图片无法通过任何请求头组合正常下载（403 或返回防盗链占位图，如昵图网返回"昵图网防盗链"占位图）。**关键坑：Bing 返回的图片 URL 通常托管在站点的图片 CDN 域名上（如昵图网的 `ntimg.cn`），只收录主站域名（`nipic.com`）会漏过滤**。黑名单统一维护在 `constant/HotlinkImageConfig.java`（大小写不敏感 contains 匹配，需同时收录主站域名与 CDN 域名，遇到新的防盗链站点/其 CDN 域名追加即可），**双层防线**：①`ImageSearchTool` 搜索结果直接排除；②`ImageProxyService.tryDownload` 下载前拦截（历史消息/漏网 URL 也无法加载）。PDF 侧另有防御：下载的图片像素 <50x50 视为占位图跳过

30. **长文本禁止放 GET query 参数**：URL 编码后超过 Tomcat 默认 `max-http-header-size`（8KB）时，Tomcat 直接返回 400 HTML 错误页（请求根本到不了 Controller，表现为"长文本请求失败、短文本正常"）。**长文本一律走 POST + JSON body**（见 `SpeechController`：文本放 `SpeechRequest.text`）。同类问题排查时先确认 400 响应是 JSON（业务层）还是 HTML（Tomcat 层）

31. **Spring 6 的 MediaType 无 AUDIO_MPEG 常量**：`MediaType.AUDIO_MPEG` / `AUDIO_MPEG_VALUE` 都不存在（编译报"找不到符号"），音频类型用 `MediaType.parseMediaType("audio/mpeg")`

32. **DashScope 语音合成（TTS）接入要点**：`spring-ai-alibaba-starter` 自动注册 `DashScopeSpeechSynthesisModel`（Bean 方法名是**驼峰** `dashScopeSpeechSynthesisModel`，与 chat/embedding 的全小写 `dashscopeXxx` 不同），按类型注入 `SpeechSynthesisModel` 接口即可（当前仅一个实现）。调用链：`SpeechSynthesisPrompt(text, DashScopeSpeechSynthesisOptions(model, voice))` → `call()` → `getResult().getOutput().getAudio()`（ByteBuffer）。**CosyVoice 模型与音色必须匹配**（cosyvoice-v1 用 `longxiaochun`/`longcheng`，v2/v3 音色名带 `_v2`/`_v3` 后缀）；单次合成有长度上限（按 ≤900 字符分段，分段结果 MP3 字节直接拼接可连续播放）；**DashScope WS 合成偶发断连**（`SocketException: 你的主机中的软件中止了一个已建立的连接`），合成调用必须重试（见 `SpeechSynthesisService` 的 `MAX_RETRIES`）；音色/模型建议配置化（`ai.tts.*`）便于切换。**TTS 合成慢（短文本 1-2s，长文本可达 40s+），必须加缓存**：`SpeechSynthesisService` 按**清洗后文本的 MD5** 作 key，合成结果存 `tmp/tts/{md5}.mp3`（文件持久化，重启不丢，命中直接读文件返回，毫秒级）；相同 key 并发请求用 `ConcurrentHashMap` 锁防重复合成；⚠️ **DashScope 额度用尽时 WS 调用不报错而是挂起不响应**（前端表现为"播报无声音 + 120s 超时"），遇到此现象优先检查百炼控制台语音模型额度

34. **DashScope 语音识别（STT）接入要点**：百炼**录音文件识别（离线 paraformer-v2）仅支持公网 URL**（不支持本地文件/Base64），本地音频必须走**实时识别 WS**（`paraformer-realtime-v2`，`dashscope-sdk-java` 的 `com.alibaba.dashscope.audio.asr.recognition.Recognition`，注意 2.18.5 无 `realtimev2` 包）。半双工调用链：`RecognitionParam.builder().model().sampleRate(16000).format("pcm").apiKey()` → `recognition.call(param, callback)` → 逐帧 `sendAudioFrame(ByteBuffer)`（每帧 100ms 音频）→ `stop()`；回调 `isSentenceEnd()` 时取 `sentence.getText()` 拼接。**必须加超时保护**（`CountDownLatch.await(30s)`，WS 挂起教训同 TTS）+ 失败重试 3 次；WAV 需 16kHz 单声道 16bit PCM（前端 Web Audio API 录制 + 手动降采样 + 纯 JS 封装 44 字节 WAV 头，见 `SpeechRecognitionService` / KnowledgeChat.vue 的 `encodeWav`）。前端录音细节：**ScriptProcessor 不要 connect 到 destination（会扬声器回放产生回声）**；`AudioContext` 需 `await resume()`（自动化合成点击不被识别为用户手势时会 suspended 导致采集不触发）

37. **Knife4j 4.4.0 的 springdoc 2.3.0 与 Spring Boot 3.5（Spring 6.2）不兼容**：访问 `/api/v3/api-docs` / Swagger 页面报 `NoSuchMethodError: 'void org.springframework.web.method.ControllerAdviceBean.<init>(java.lang.Object)'`（Spring 6.2 移除了单参构造方法）。**修复：knife4j 升到 4.5.0，并在 pom.xml 显式声明 `springdoc-openapi-starter-webmvc-ui` 2.8.9**（knife4j 4.5.0 传递依赖仍是旧 springdoc 2.3.0，必须显式钉版本覆盖，勿误删该显式依赖）。该报错只影响 Swagger 文档接口，登录/业务接口不受影响——排查时先 curl 业务接口（如 `/api/auth/login`）区分是文档问题还是业务问题

40. **AI 会把 tmp/file 工作目录误当成"知识库"**：`FileOperationTool` 的 `listFiles`/`readFile` 根目录是 `{user.dir}/tmp/file/`（工作缓存目录，非知识库），已注册进 Agent 工具链。`prompt.yml` 的"用户引导"章节指示"输入模糊时主动查看用户知识领域"，DeepSeek 收到"？"等模糊输入后会**主动调用 `listFiles` 扫描 tmp/file**，把里面的缓存文件（如测试写入的 `编程导航.txt`、手动放置的 `shanghai_date_plan.txt`）当作"知识库"向用户汇报；文件删除后 AI 又会说"知识库是空的"——**此时向量数据库从未被查询**（`QueryRewriter` 把"？"判为不需要检索，走无 RAG 的 `doChatByStream`）。排查此类"AI 看到不该看的数据"问题时：先查 `chat_memory` 集合对应会话确认是工具调用还是 RAG 引用（`references` 字段），再直连 MongoDB 查 `vector_store` 集合 `metadata.filename` 确认向量库实际内容。**修复方案**：`prompt.yml` 明确"tmp/file 是文件工作缓存目录，不是知识库，仅在用户明确要求时调用文件工具，禁止主动 listFiles 扫描或把其中文件当作知识库介绍"；"用户引导"章节禁止输出"你的知识库是空的"之类表述，未命中检索时直接引导可问方向（联网搜索/资料整理/网页解读/文件管理等）

45. **历史会话排序必须用 `updatedAt`（修改时间）而非 `createdAt`**：`chat_memory` 文档含 `createdAt`/`updatedAt` 两个字段；`MongoChatMemory.add()` 每次追加消息都会刷新 `updatedAt`（含用户消息和 AI 回复），所以对话后修改时间天然会更新。**历史列表（`AiController.getKnowledgeAppHistory`）必须按 `updatedAt` DESC 排序**（最新对话置顶），旧数据 `updatedAt` 为 null 时回退 `createdAt` 且排最后（用 Java `Comparator` + `nullsLast`，勿在 MongoDB 层直接 `Sort.by(updatedAt)`，null 顺序不可控）。配套：`ChatHistoryDTO` 需暴露 `updatedAt`；前端历史时间显示用 `chat.updatedAt || chat.createdAt`；`KnowledgeApp` 两个流式聊天方法在 flux `.doOnComplete` 时调用 `touchUpdatedAt(chatId)` 兜底刷新修改时间（RAG 引用持久化时同步刷新）

46. **RAG 引用标注必须门控在"AI 实际引用了知识库"（引用门控）**：检索（`QuestionAnswerAdvisor` 相似度阈值 0.5）返回的切片**不等于**被 AI 使用——AI 可能明确回答"知识库中没有检索到数据"却仍附上 3 篇不相关切片引用，用户视角即为"没检索到还显示引用"。**修复（`KnowledgeApp.doChatByStreamWithRag`）**：用 `.doOnNext` 累积流式回复全文，流结束后仅当 `docs 非空 且 回复包含引用标注 [n]`（`containsCitation`，正则 `\[\d{1,2}\]`，限制 1-2 位数字避免误匹配年份如 [2026]）时才追加 `<!--RAG_REFS-->` 并持久化 references，否则 `Flux.empty()` 不下发（前端 `references.length > 0` 自然隐藏）。曾尝试过"传 `hasUsefulRefs` 标识"方案，但标识语义若仍是"docs 非空"就与 references 非空等价、解决不了问题，已还原；**判定"有用"的唯一可靠信号是 AI 回复中的 [n] 标注**（系统提示词已有引用规范引导 AI 引用时标注 [1]、[2]）。副作用：AI 用了知识库却不标 [n] 时引用会隐藏，属可接受权衡

56. **知识库文档目录写入限制（Web 端上传/删除）**：运行时上传的文档落在 `app.knowledge.document-dir`（默认 `src/main/resources/document`）。四个要点：①**打包成 jar 后该目录只读，上传与删除必定失败**，需以源码目录方式运行（`mvnw spring-boot:run`）或把配置指向可写目录；②dev 下加载器不能只用 `classpath:`——`classpath:document/*.md` 解析到 `target/classes`，运行时写进源码目录不会被加载，须做成「真实目录存在则扫 `file:` 该目录，否则回退 classpath」（`classpath:` 分支供 jar 运行用）；③`DocumentPreprocessor` 插入 `---` 分割线**必须幂等**（前一个非空行已是 `---` 就跳过），否则重新入库会在每个 `##` 前累积重复分割线；④「重新入库」要**先删该文档旧向量再重跑全流程**，只重跑不清理会留下孤儿切片；⑤ 重跑（与上传一样）会**原地覆盖磁盘上的 md**，所以对**受版本管理**的内置文档点一次「重新入库」，它就会在 git 工作区显示为已修改——实测一次重跑会在文件末尾多出 2 个空行（`processContent` 第 5 步 `stripLeading() + "\n"` 与原文 CRLF 尾部交互所致），但**不会无界累积**：第二次起稳定在 2 个空行；若不想留下该 diff，重跑后 `git checkout -- <文件>` 还原即可。另：切片 id 用「文件名#正文 MD5」（与启动加载同源），换标题拼接方式会导致同一文档重复向量化

57. **MongoDB 分页排序必须追加唯一二级键**：用户管理列表按注册时间排序时只给了 `Sort.by(desc("createdAt"))`，而 MongoDB 的排序是**不稳定**的——同值记录（同一秒注册、批量插入）的相对顺序在两次查询间可能不同，表现为翻页时**重复出现同一条或整条漏掉**。修复：每种排序都追加唯一二级键，例如 `Sort.by(Order.desc("createdAt"), Order.asc("username"))`，按用户名排序时用 `Order.asc("username"), Order.asc("userId")`（二级键不能与主键相同）。验证手法：`size=2` 逐页遍历，把各页用户名并集与一次全量请求比对，要求「不重不漏且分页拼接顺序与全量顺序一致」，三种排序各做一遍（本项目实测 union=6 / distinct=6 / 重复 0 / 缺失 0）

58. **超大页码会让分页偏移溢出（`total` 变垃圾值）**：`PageRequest.of(page - 1, size)` 内部按 `(page-1)*size` 算 offset，**这是 int 运算**。实测 `GET /ai/user/list?page=2147483647&size=2` 返回 `{"total":4294967294,"page":2147483647,"totalPages":2147483647,"items":[首屏前 2 条]}`——offset 溢成负数后被当作 0（skip 失效），count 与实际条数一起把 `total` 算成垃圾值。精确边界（size=2）：`page=1073741824`（offset 2147483646）仍正确返回空页，`page=1073741825`（offset 2147483648）即溢出。修复：**给 page 加上限**（`int pageIndex = Math.max(1, Math.min(page, MAX_PAGE))`，`MAX_PAGE = 1000000` 时 offset 最多 5×10⁷，留足安全余量）；修复后同一请求返回 `total:6 / totalPages:3 / items:[]`。注意 `size` 原本就有上限（clamp 到 50），page 却只做了下界，容易被漏掉

59. **「禁止改自己」不等于「至少留一个管理员」**：用户管理批量改角色最初只在写入**前**做前置校验 `countAdmins() - 待降级人数 >= 1`，并认为"操作者本人禁止被改，所以操作者必定留任管理员，不会清零"——这个推理只在单请求视角成立：两个管理员 A、B **同时互降**时，两个请求各自读到 `countAdmins()=2`、待降级数 1，都判定通过并各自写入，最终库里 **0 个管理员**（知识库管理与用户管理入口全部失效，只能改库恢复；`AdminAccountChecker` 只在启动时打一行 warn，不会自动恢复）。本项目 Mongo 无事务、写路径就是 `save`，所以采用**补偿式**修复：写入后复查 `countAdmins()`，为 0 则把本次变更的用户逐个回滚为 `ADMIN` 并抛 400「至少保留一个管理员账号」（降级场景下本次变更的目标必然原为管理员，回滚是精确的），同时删掉那个不可达的前置校验。⚠️ 该并发路径**未实测**（需两个管理员精确同时操作），是代码审查阶段推导出来的

61. **千问 Qwen3.7 Plus / 3.8 Flash 必须走 OpenAI 兼容模式端点**：
    - 现象：用 DashScope 原生文本端点调这两款 → 400 `InvalidParameter: url error`；经 SDK 聚合流式时因分片解析 NPE 变成 500
    - 原因：这两款是多模态型号，只在兼容模式（`/compatible-mode/v1`）与 multimodal 端点提供，原生 `text-generation/generation` 端点不支持
    - 修复：`MyChatClientConfig` 里用 `OpenAiChatModel` + `OpenAiApi.builder().apiKey(...).baseUrl("https://dashscope.aliyuncs.com/compatible-mode")`（复用 `DASHSCOPE_API_KEY`），Bean 名 `qwenChatModel`；原 `dashscopeChatModel` 保持给 Manus 等既有消费方不动

62. **Spring AI 1.0.0-M6 会把一个流式工具调用拆成两条**：
    - 现象：`qwen3.8-flash` + 注册工具时回复 `回复失败：toolInput cannot be null or empty`（后端 400）；改成"过滤空参数"后变成 500 `toolName is null`
    - 原因：M6 的流式合并把"任何带 id 的分片"当成新工具调用的开始，而 DashScope 兼容模式给 **3.8 系列**的续传分片带的是 `id:""`（3.7 系列是 `id:null`），同一个调用被劈成「有名字没参数」+「有参数没名字」两条——前者执行触发 `MethodToolCallback` 断言 → 400，后者 `toolName is null` → 500
    - 修复：`config/ToolCallRepairingManager`（实现 `ToolCallingManager`，包一层默认实现）执行前把相邻两半拼成一条、无名字的丢弃、参数为空补 `{}`；只挂在千问 ChatModel 上（DeepSeek 不分片）。**官方 PR #6381 已修，升级 Spring AI 后删掉该类即可**

66. **自写 Agent 执行循环必须复刻 run() 的消息初始化（否则模型全程空转）**：
    - 现象：任务执行正常（计划工具都调了）但模型反复回复"我没有收到任务描述"，直到步骤耗尽
    - 原因：绕过 `BaseAgent.run()` 自己写执行循环时，只设置了 systemPrompt/nextStepPrompt，**漏了把用户任务 `messageList.add(new UserMessage(任务))`**——run() 里本会做这一步，手写循环必须逐项核对与 run() 的差异
    - 修复：执行前补 `agent.getMessageList().add(new UserMessage(taskText))`（ManusTaskService.executeTask）。追问轮的初始消息由 `buildFollowUpContext` 摘要重建生成（含原任务+历次追问+历轮报告+当前计划+最近 10 条事件+本次追问）

67. **Agent 的最后一条消息 ≠ 面向用户的交付物（提示词补丁管不住，要结构性方案）**：
    - 现象：任务报告只显示"所有计划步骤都已完成，现在我将调用doTerminate工具结束任务"这类过程独白，用户拿不到答案；提示词两轮强化（"第一句直接给结论""禁止过程性表述"）后**仍偶发**
    - 原因：模型把"生成最终报告"当成一个**计划步骤**——答案写在中间某步的 think 里，而最后一条消息只剩调用 doTerminate 的独白；取"最后一条有文本的思考"当报告必然命中独白
    - 修复：**收尾专职报告生成**——任务 COMPLETED 时后端额外做一次无工具的 LLM 调用（`generateFinalReport`：输入=用户问题+本轮执行记录摘要，输出直接作 finalReport），失败才回退思考文本。实测对比类任务报告第一句即完整结论并带表格、零过程性表述。代价：每完成轮多一次 LLM 调用（无工具、短输出，可控）

68. **文件路径白名单校验用字符串 startsWith 会被同级目录绕过**：
    - 隐患：`"...\\ai-agent\\tmp2\\x".startsWith("...\\ai-agent\\tmp")` 为 true——`tmp2`、`tmp-backup` 等同级目录都能过校验；被校验的相对路径又来自模型输出/库内数据（可被篡改），等于白名单失效
    - 修复：`Path.startsWith(根目录)`（组件级比较）+ `根目录.relativize(path)` 推导相对路径；**登记侧与下载/预览侧都要校验**（后者防库内数据被篡改）；同时排除 `path.equals(根目录)` 自身

69. **spring-boot:run 的 jvmArguments 值含空格只生效第一段**：
    - 现象：`-Dspring-boot.run.jvmArguments="--enable-preview -Dfile.encoding=UTF-8 -Dmanus.agent.pool-size=1"` 启动正常但 `pool-size` 不生效（线程名出现 manus-task-2 证实）
    - 原因：Maven CLI 把带空格的 `-D` 值按空格拆分，只有第一段进 `jvmArguments`，其余被当成 Maven 自身 JVM 的属性，**不传给应用 JVM**
    - 修复：多参数传递改用环境变量——Spring 的 SystemEnvironmentPropertySource 会把 `MANUS_AGENT_POOL_SIZE` 映射给 `@Value("${manus.agent.pool-size}")`（点/横线转下划线+大写），实测生效

70. **对 MongoDB 库内字段/键名的假设必须直查验证**：
    - 现象：知识检索工具的来源标注全部显示"未命名笔记"（代码读 `metadata.get("title")`）
    - 根因：向量库切片的 metadata 实际键是 `['filename','category','lang','status']`——**没有 title**；凭上游加载器代码片段推断键名不可靠
    - 修复：取值做 title→filename 回退；教训是写取值逻辑前先用 pymongo/Compass 查一条真实文档（本机无 mongosh，pymongo 可用）

---

## 前端陷阱（Vue Web）

6. **localStorage 非响应式**：Vue `computed` 或 `watch` 中直接读取 `localStorage.getItem()` 不会追踪变化，必须通过 ref 代理。所有认证相关组件必须从 `auth.js` 导入响应式 ref 而非调用 `getUsername()`

7. **blur 与 click 竞态**：输入框聚焦时点击外部按钮，`@blur` 先于 `@click` 触发。如果 blur 处理了保存并清空状态，click 可能会错误地重新进入编辑模式。修复方案：操作按钮使用 `@mousedown.prevent.stop`，blur 回调加 `editingChatId` 守卫

8. **CSS position: absolute 容器塌陷**：绝对定位元素不占用父容器空间。若父容器无显式宽度，子元素宽度可能为 0（如胶囊 bar 使用 `position: absolute; left: 24px; right: 0` 但父容器仅 48px 宽时，bar 实际只有 24px）。优先使用 flex + 负边距方案替代绝对定位

33. **前端 Blob URL 必须成对 revoke**：`URL.createObjectURL(blob)` 创建的 URL 在播放结束（`onended`）、出错（`onerror`）、主动停止、播放失败（`play()` reject）四条路径都要 `URL.revokeObjectURL`，否则每次播报泄漏一个 Blob URL。停止/切换播报时统一在 `stopSpeech()` 里处理

38. **前端生产环境 BASE_URL 必须用相对路径（同源）**：`const BASE_URL = import.meta.env.DEV ? '' : 'http://localhost:8123'` 这类写法在生产构建后，外网用户浏览器里的 `localhost` 指向访问者自己的电脑，登录/聊天全部失败（只有部署机本机访问碰巧能用）。**正确写法：`const BASE_URL = ''`**，请求走同源 `/api/...`，由 nginx 反代到后端，任何设备可访问；流式接口用 `new URL(BASE_URL + '/api/...', window.location.origin)` 同样生效。**配套缓存坑**：nginx 对 js/css 设置 `expires 1y` 时，改完前端代码只重启 nginx 没用——浏览器缓存旧文件导致"还是旧页面"，必须 `npm run build` 后**硬刷新（Ctrl+F5）**或清缓存；根治方案：nginx 对 `index.html` 单独加 `Cache-Control: no-cache`（Vite 产物文件名带 hash，index.html 更新后自然拉到新 JS，静态资源仍可长缓存）

39. **语音播报按钮（已放出，三端 UI 统一）**：该按钮曾用 `v-if="false"` 长期隐藏（"脚本与样式原样保留"，所以逻辑一直是好的）；2026/9/29 按需求放出，并统一三端 UI —— **只留图标、去边框去底色**（原为 999px 胶囊 + 淡绿底 + 淡绿描边），点图标播报/停止，播报中换 `VolumeX` 并转红（用颜色区分状态、无需文字），纯图标按钮补 `aria-label`；移动端把点击区补到 44px。小程序端 `chat-bubble.vue` 同步该样式，播报中换 `volume-off.svg`（红色）。**恢复/回退方法**：若日后又需要隐藏，把 `v-if="false"` 加回按钮即可（TTS 逻辑与样式都在）

41. **移动端适配要点（前端）**：
    - **iOS Safari 聚焦输入框自动放大**：输入控件 font-size < 16px 时聚焦会自动 zoom。聊天页 `textarea` 原为 `0.95rem`（15.2px），必须在 `@media (max-width: 768px)` 下提升到 `1rem`；登录/改密输入框已是 1rem 不受影响。**不要用 `user-scalable=no` 禁缩放**（无障碍要求），靠 16px 字号根治
    - **移动端浏览器地址栏高度变化**：`height: 100%` 在 iOS Safari/Android Chrome 上可能高于可视区导致布局跳动。`.app` 需 `height: 100%; height: 100dvh;`（dvh 不支持的旧浏览器自动回退 100%），聊天页内部滚动才稳定
    - **KnowledgeChat 移动端侧边栏是抽屉（off-canvas）**：`@media (max-width: 768px)` 下 `transform: translateX(-100%)` 滑入，此时 `.toggle-sidebar-btn`（边缘切换按钮）是 `display: none`——**必须另加汉堡按钮**（`.sidebar-menu-btn`，桌面 `display:none`、移动端显示，放 header-left 最左）+ 遮罩层 `.sidebar-backdrop`（`position:absolute; inset:0; z-index:15`，低于 sidebar 的 20）点击关闭，否则手机上历史会话无法打开
    - **固定定位元素遮挡**：App.vue 的 `.user-dock`（左下角）在移动端聊天页会压住底部输入区、`.brand-logo`（右上角）会压住页面头部——移动端聊天页（`/knowledge`、`/manus`）两者都要隐藏（`showDock`/`hideBrand` computed 判断 `window.innerWidth <= 768` 且路由为聊天页），用户回首页仍可访问账号
    - **刘海屏/底部横条**：`viewport-fit=cover` + 固定定位元素用 `env(safe-area-inset-*)`（`.user-dock` 的 bottom、`.brand-logo` 的 top、聊天页 `.input-area` 的 `padding-bottom`）
    - **移动端发送按钮**：聊天页 send-btn 原 `padding: 0 28px` 带"发送"文字太宽，移动端改为 44×44 纯图标（模板文字包 `<span class="btn-text">`，移动端 `display:none`）
    - **Home 应用卡片**：`≤480px` 时改为横向布局（图标左 + 文字中 + 箭头右，`flex-direction: row`），比纵向堆叠更省空间；触控目标 ≥44px
    - 移动端检测统一用 `window.innerWidth <= 768` + `resize` 监听（App.vue / KnowledgeChat.vue 各维护 `isMobile` ref，组件卸载时移除监听），避免各组件硬编码 `window.innerWidth` 判断

42. **flex 子项默认 `min-width: auto` 会把内容挤出屏幕（聊天页"内容被裁切/挤出"头号原因）**：`KnowledgeChat` 的 `.chat-container { flex-grow: 1 }` 是 `.chat-layout`（row flex）的子项，未设 `min-width: 0` 时其 min-content 宽度由输入区决定（textarea 固有宽度 `cols` 约 190px + RAG 工具栏 + 麦克风 44 + 发送 44 + gap），在窄屏（如 375px）下容器被撑到 443px，外层 `.chat-layout { overflow: hidden }` 把右侧裁掉——**右对齐的用户气泡（`align-self: flex-end`）被切出屏幕**，表现为"用户提问和 AI 回复不在同一画面、内容被挤出"。桌面宽度充裕不触发，只在移动端暴露。**修复**：①`.chat-container` 加 `min-width: 0`；②移动端 `.input-area { flex-wrap: wrap }` + `.input-toolbar { flex: 1 1 100% }` 让 RAG 工具栏独占一行，避免和 textarea/按钮挤一行；③`.bubble-content` 加 `min-width: 0` + `overflow-wrap: anywhere`；④Markdown 表格原本无样式会撑破气泡，必须加 `.markdown-body table { display: block; overflow-x: auto }`（气泡内横向滚动）。排查手段：CDP `Emulation.setDeviceMetricsOverride` + `getBoundingClientRect()` 对比容器宽与视口宽（本机 Edge/Chrome headless 可用 `--remote-debugging-port` + Node 内置 WebSocket 驱动）。**后续迭代**：修复②的布局已在多模型需求中调整（四控件收进输入框、工具条与语音/发送同排），现见陷阱 64；本条核心教训（`min-width: 0`）不变

43. **带 /g 标志的正则 lastIndex 跨调用残留（前端工具函数隐蔽 bug）**：模块级 `const R = /.../g` 的 `.test()`/`.exec()` 会推进 `lastIndex`，**多次调用同一函数时上次的 lastIndex 会残留到下次**——若上次匹配位置超过本次字符串长度，`.test()` 直接返回 false，函数静默跳过处理（表现为"同样的输入，有时转换有时不转换"）。**修复：每次使用前必须 `R.lastIndex = 0` 重置，或在函数入口统一重置**。本项目 `frontend/src/utils/linkify.js` 即因此踩坑（连续转换多个消息时部分 URL 不链接化）。排查手段：CDP 页面内逐步执行正则观察 lastIndex

44. **聊天消息中 AI 返回的下载地址（`/api/files/pdf/xxx.pdf`）必须前端链接化**：`PDFGenerationTool` 返回根相对路径（无站点前缀），用户无法直接访问。**修复方案（前端 `src/utils/linkify.js` + `renderMarkdown`）**：在 `DOMPurify.sanitize` 之后对 HTML 文本节点做链接化——匹配 `https?://`、`www.`（补 `https://`）、`/api/` 根相对路径（点击时浏览器自动基于当前站点解析，生产环境经 nginx 同源反代同样生效），跳过 `pre`/`code`/已有 `a` 内的文本；`/api/` 前缀限制避免误转普通斜杠文本（如"1/2"）；中文文件名正常保留，但地址尾部粘连中文（如 `a.pdf。下载吧`）需按"扩展名/路径段后跟中文"启发式裁剪。不要尝试在后端拼绝对 URL（后端无法可靠知道公网域名/IP）。配套 `prompt.yml` 已改为引导 AI 输出"点击链接即可下载"，后端重启后生效

49. **语音输入（STT）前端必须为"录音过短/无语音/报错"给出可读提示**：原始 AXios 对 HTTP 400 的 `err.message` 是生硬的 `Request failed with status code 400`，且后端 STT 此前对多种情况返回**无 body 的 `badRequest().build()`**，前端拿不到任何描述。**修复（三层）**：①前端在 `stopRecording()` 用 PCM 采样数算时长（`totalLen / 16000`），`<0.6s` 直接拦截提示"录音时间过短，请按住麦克风说话至少 1 秒后再松开"，避免上传无效音频；②后端 `SpeechController.stt()` 所有 400 分支改为 `badRequest().body(Map.of("message", "…"))`（未接收到录音文件 / 过大 / 非 WAV / 解析失败），`GlobalExceptionHandler` 对 `IllegalArgumentException`（如"未识别到语音内容"）也返回带 message 的 JSON；③前端 `sttErrorMessage(err)` 错误映射顺序：`err.response.data.message` 优先（后端友好提示）→ 按状态码（400 音频无效 / 413 过大 / 5xx 服务异常）→ `ECONNABORTED` 超时 → 有 `err.request` 无 `response` 判网络异常 → 兜底。**经验**：后端异常响应一律带 `message` 字段，前端统一走 `response.data.message`，不要展示 Axios 原始 `err.message`

50. **侧边栏 `.history-list` 的滚动条与右侧"收起历史对话"按钮（`.toggle-sidebar-btn`）重叠**：`.history-list { overflow-y: scroll }` 的自定义 `::-webkit-scrollbar`（5px）固定在列表右边缘（x=255~260），而切换按钮 `position:absolute; left:260px; top:50%; translateX(-50%)` 中心压在侧边栏右边界（x=246~274），两者在垂直中部区域重叠，视觉上"滚动条被按钮切断/穿过"。**正确修复：让收起按钮整体移到侧边栏右缘外侧，而不是给列表让位**——`.toggle-sidebar-btn` 去掉 `translateX(-50%)`（`left: 260px` 即按钮左边缘贴右缘，占聊天区一侧 x=260~288），`.history-list` 保持无右侧 margin、滚动条吸附最右侧边框，两者互不重叠（该按钮定位上下文是 `.chat-layout`，不在 `.sidebar` 内，不受其 `overflow:hidden` 裁剪）。曾尝试 `.history-list { margin-right: 18px }` 让滚动条左移让位，虽避开了按钮但滚动条离右边框太远，视觉不佳，已还原。仅桌面端受影响（移动端 `.toggle-sidebar-btn` 已是 `display:none`）

51. **user-dock（左下角个人信息组件）与页面内容重叠**：`.user-dock` 是 `position:fixed; left:20px; bottom:20px; z-index:999`。三处重叠：`/knowledge` 页侧边栏展开时叠在历史列表底部，盖住历史项与标题无法点击；`/knowledge-documents` 页在视口宽约 769–850px 时叠在批量操作条上（dock `z-index:999` > 批量条 `100`），压住「取消」按钮文字并抢走点击；`/user-manage` 页同理——该页底部同样有固定批量操作条，dock 会压住左端的「取消」按钮。**临时修复：`App.vue` 的 `showDock` 对 `/knowledge`、`/knowledge-documents`、`/user-manage` 三个路由恒返回 `false`**（其余页面如首页仍显示）。⚠️ 副作用：这三页暂时无法从该组件进入"修改密码/退出登录"（知识库管理与用户管理页可经「返回」回首页使用），属**用户明示接受的临时方案**，后续需重新设计个人信息入口/布局（见 PROGRESS.md「后续优化」）

52. **flex 列容器的子项被压扁、内容被裁切（分块结果全部变成 1px）**：`.chunk-list` 是 `display:flex; flex-direction:column` 容器，子项 `.chunk-item` 自带 `overflow:hidden`。按 Flexbox 规范，子项 `overflow` 不是 `visible` 时其「自动最小尺寸」为 **0**，于是子项被压到几乎没有高度、内容被 `overflow:hidden` 裁掉——实测 76 个分块项每个 `offsetHeight:1px` 而 `scrollHeight:44px`（`flexShrink:1`），表现为"所有分块挤在一页、内容不显示、也看不到滚动条"；叠加内层 `max-height:320px` 的独立滚动，又导致"有的有滚动条、有的没有"。**修复：给子项显式 `flex-shrink: 0`**。这是陷阱 42（横向 `min-width:auto` 把内容挤出屏幕）的竖向版本；排查手段是量 `offsetHeight` 与 `scrollHeight` 是否相等

53. **自定义浮层（下拉/菜单）必须 Teleport + fixed**：`App.vue` 的 `html, body { overflow-x: hidden }` 按 CSS 规范会把 `overflow-y` 的计算值变成 `auto`，body 因此成为滚动容器，**绝对定位的浮层会被裁剪或引发多余滚动**，所以排序下拉这类浮层不能用 `position:absolute`。做法（照 `KnowledgeChat.vue` 三点菜单）：浮层 `<Teleport to="body">` + `position:fixed`，`nextTick` 后按触发元素的 `getBoundingClientRect()` 定位，面板最小宽度对齐触发元素，下方空间不足则向上翻转，左右各留 8px 边距；玻璃拟态用 `backdrop-filter: blur(20px)`，`z-index` 与既有 Teleport 菜单保持一致（3000）。交互要覆盖点击外部、`Esc`、`Tab`、页面滚动四种关闭路径；滚动关闭必须用 `window` 的 **capture** 监听，才能收到不冒泡的 `scroll` 事件

54. **详情类弹窗的请求时序竞态**：`openDetail` 是"先开弹窗再等接口"。若先点 A（响应慢）→ 关闭 → 点 B，A 的响应后到会**覆盖 B 的内容**（标题显示 B、正文却是 A）；若 A 请求失败，还会把刚为 B 打开的弹窗一起关掉。**修复：`openDetail` 用递增请求序号，响应回来先比对，不是最新一次就丢弃（`catch` 分支同理，避免误报错与误关弹窗）；`closeDetail` 也递增序号，使进行中的请求失效**。验证手法：给首个 `/content` 请求注入 2.5s 延迟制造乱序，再撤掉守卫做反证对比（撤掉时显示 A、恢复后显示 B）

55. **「重新入库」的显示条件与二次确认（不要收紧成"仅失败可见"）**：该按钮既是**失败恢复**手段，也是管理员日常的**主动重跑**入口（改完磁盘上的 md、想重建切片时用）。曾按"设计意图是失败恢复"把 `canReindex` 从 `!isRunning(doc)` 收紧为白名单 `['NOT_INDEXED','PREPROCESS_FAILED','VECTORIZE_FAILED']`，结果**已完成文档的重跑入口全消失**，用户随即反馈"按钮怎么没了"——判断可操作状态时，先确认该操作是否也是日常主动操作，别把常用入口一起收掉。**最终方案**：`canReindex` 回到 `!isRunning(doc)`（预处理中/向量化中不可点），并给**已完成**文档加二次确认弹窗——因为重跑会先删除已有切片，一旦重跑失败（如预处理报错），文档会从「已完成」掉到「失败」；未入库与失败状态本来就没有切片可丢，点了直接重跑、不弹确认。**教训**：收紧前端可见状态前，先问该操作是否属于日常流程；涉及"替换已有数据"的动作，用确认弹窗而不是隐藏入口来控风险

60. **后台标签页（`document.hidden`）会被浏览器节流，别把节流现象当应用缺陷**：用自动化浏览器验证页面时若标签页不在前台（`document.visibilityState === 'hidden'`），Chrome 会节流，产生三类**假象**：①**CSS 过渡不结束** → Vue `v-if` 的离场元素长期留在 DOM 里（实测排序面板 `aria-expanded` 已是 `false`、`opacity: 0`，但元素还在，看起来像"面板没关上"）；②**`setTimeout` 被大幅延迟** → toast 到点不消失（实测两三个请求过去了，上一条 toast 仍在 DOM 中）；③**自动化的可操作性检查超时** → 报 `Timeout waiting for locator ... Do not retry the same locator`，而同一元素 `document.elementFromPoint` 命中自身（`hitIsSelf: true`）、`disabled` 状态与尺寸（22×22）都正常、`count()` 也是 1，加 `{ force: true }` 仍超时。判别与绕行：先在页面里读 `document.hidden` / `visibilityState` 确认是否被节流（`browser.capabilities.visibility.set(true)` 只控制面板可见性，**窗口不在前台时页面仍是 hidden**）；确认节流后改用页面内断言（`locator.evaluate(el => el.click())` 触发 + `querySelector` 读状态）验证行为，并在报告里如实标注环境限制——同款排序面板在可见状态下已验证正常，属环境问题而非回归

63. **textarea 的上内边距属于可滚动区域，文字超长会把顶部留白"顶掉"**：
    - 现象：输入框里文字越写越多，顶部留白越来越小、最后文字贴住框顶（看着像留白被压扁），框顶到文字的间距从小变大又变小
    - 原因：textarea 自身的 `padding-top` 在**滚动区之内**，一旦内容超过高度上限（`max-height`）开始内部滚动，顶部内边距就随内容一起被滚出可视区
    - 修复：把顶部留白移出滚动区——桌面端放到外层容器 `.input-composer { padding-top }`，移动端（容器是 `display: contents` 没有盒子）改用 textarea 的 `margin-top`；同时把增长上限从固定 `120px` 放宽到 `30vh`，自适应 JS 改为读 CSS 的 `max-height`（避免两处数值脱钩）

64. **桌面/移动共用一份 DOM 做"控件全收进输入框"布局**：三个要点缺一不可——① 桌面端的内层容器在移动端 `display: contents`，让输入框与工具条直接成为外层输入区的排布项（否则工具条被"关"在容器里出不去）；② 工具条 `flex: 1 1 0`，在换行计算里不吃掉整行（否则麦克风/发送被挤到第三行，实测第一版就是这样）；③ 模型按钮 `width: 100%`，因为按钮是 fit-content 宽度、不跟随容器收缩，会溢出压到语音按钮上（实测与语音按钮重叠约 35px）

71. **SSE 客户端帧解析必须逐帧拆分（整段切分会在多帧同达时静默丢事件）**：
    - 现象：Manus 任务页多轮追问时，追问气泡/新轮过程/新报告全部不实时显示，**刷新页面才出现**；且后端数据完整，无任何报错——故障延迟爆发（丢的是"未来请求的起点"），极难排查
    - 原因：`parseFrames` 按 `buffer.lastIndexOf('\n\n')` 只切出"最后一个分隔符之前"的**整段**当一帧解析——服务端在一个 TCP 段里连续推送多条事件（同一把任务锁内相继 broadcast `plan_updated`+`tool_result` 时必然发生）时，两帧的 data 行被拼成一个字符串 `JSON.parse` 必然失败，`console.warn` 后**两条都丢**；丢事件使前端 `events.length` 小于后端真实数量，增量订阅 `after` 偏移，后续重订阅跳过 `user_message` → 轮次无法切分
    - 修复：非 flush 分支改为 `const complete = buffer.substring(0, idx); buffer = buffer.substring(idx + 2); frames = complete.split('\n\n')` 逐帧解析；JSON.parse 失败的 warn 日志带上原始 frame 便于发现。**排查工具**：页面注入 fetch 包装 tee 出第二流记录原始帧类型序列，一次就能看到 `user_message` 深陷在后续事件中间（正常应是重订阅首帧）

---

## 脚本工具陷阱（html-to-md / 文档处理）

35. **HTML 转 Markdown 工具（HtmlToMarkdownConverter）要点**：①**jsoup 1.19.1 的 `TextNode` 没有 `wholeText()` 方法**，保留原始空白（代码块缩进/换行）必须用 `getWholeText()`（编译报"找不到符号"时先 javap 查 jar 里的实际方法名）；②**递归转行内格式时禁止把元素自身传给下一层**（`appendWrapped(sb, el)` 内再调 `inlineText(el)` 会对 strong/a 等标签无限自环 → StackOverflowError），必须改为递归 `el.childNodes()`（子节点不会命中父级 case）；③**`## 前插 `---` 分割线必须跳过 ``` 代码围栏**，否则代码里的 `## 注释` 会被插入分割线破坏代码并产生错误切片边界，处理时按行扫描维护 `inFence` 状态；④**代码块原文保留**：`<pre><code>` 用 `getWholeText()` 取原始文本，`List<String>` 等泛型/小于号不会被清洗逻辑误删（该工具不执行 DocumentPreprocessor 的 HTML 标签剥离，jsoup 解析后标签不会泄漏进文本节点）；⑤**通用文件名重命名**：浏览器"另存为"的 `index.html` 等用页面 `<title>` 重命名输出（清洗非法字符），否则知识库 status 标签会变成无意义的 "index"；⑥**含第三方依赖的工具类不能裸 `java <文件>.java` 启动**（classpath 只有当前目录），必须走 `mvnw exec:java -Dexec.mainClass=...` 或 `script/` 目录下的 `html-to-md.bat/.sh` 脚本

36. **书签模式（html-to-md 解析浏览器收藏夹）要点**：①**Chrome/Edge 的 `Bookmarks` 文件是 JSON 且无扩展名**，书签导出有**三种格式**：JSON（精确名 `Bookmarks` 无扩展名）、**NETSCAPE-Bookmark-file-1 HTML**（按文件头含 `NETSCAPE-Bookmark` 识别）、**Chrome 导出的 `# Bookmarks` Markdown**（按 `.md` 扩展名识别，正则 `\[([^\[\]]*)\]\((https?://[^)\s]+)\)` 提取链接）；②**书签文件识别顺序不能靠"文件名含 bookmarks"**——导出 HTML/Markdown 文件名都含 bookmarks（如 `bookmarks.md`），`isBookmarksJson` 必须收紧为 `.json` 扩展名或**精确名 `Bookmarks`**，否则导出文件会被 Jackson 按 JSON 解析报 `Unexpected character`；③**`exec:java` 的 `-Dexec.args` 按空格分词，路径含空格（如 Chrome 的 `User Data\Default`）必须用引号包裹**（bat/sh 脚本已用 `'%INPUT_DIR%'` 单引号包裹，bat 中单引号是字面字符无转义问题），否则路径被截断报"输入文件或目录不存在"；④**下载细节**：HttpURLConnection 需完整浏览器 UA（部分站点校验 UA）+ 20s 超时 + 重试 2 次；下载存原始字节流，编码检测交给 `Jsoup.parse(file, null)`（自动读 meta）；下载失败只记日志不中断整体流程（**CSDN 等站点偶发/常发 HTTP 521 反爬拦截**——curl 带完整头也绕不过，属站点风控，失败清单打印后可稍后重跑或手动打开另存 HTML 走目录模式）；⑤**下载缓存 `tmp/bookmarks-html/`**（按书签标题命名，重名加序号，已存在的文件跳过下载，重复运行秒级完成）；⑥**并发下载用固定线程池**（Java 17 无虚拟线程，`newFixedThreadPool(4)` + Future.get 收集失败列表）；⑦**书签 JSON 结构**：`roots` 下 bookmark_bar/other/synced 三个根文件夹，节点 `type=url` 有 `url`/`name` 字段，`type=folder` 有 `children` 递归，按 URL 去重 + 过滤 chrome:// 等非 http 链接；⑧**无 alt 的 `<img>` 直接跳过**（点赞/收藏/分享等图标无 alt 只有 URL，输出 `![url](url)` 会成检索噪音），有 alt 的内容图才保留；CSDN 页面噪音类（`.article-info-box` 博主信息、`.toolbox-list` 点赞收藏、`.recommend-box` 推荐阅读等）已收录进 NOISE_SELECTOR；⑨**目录模式必须识别书签 HTML**：用户常把导出的书签 HTML 放进网页收藏目录一起处理，若按普通网页转换会输出一个"书签列表"md（内容只有链接标题，污染知识库）——`processDirectory` 遍历时对每个 `.html/.htm` 先查文件头 `NETSCAPE-Bookmark`，书签文件收集后统一走 `processBookmarksHtml` 书签管线，普通网页才直接转换；⑩**CSDN 等站点 HTTP 521 风控应对**：521 是基于 TLS/请求指纹 + 访问频率的服务端风控，完整 UA/Referer/头组合（curl 实测）均绕不过，**有效手段是携带浏览器 Cookie**（`--cookie "xxx=yyy; ..."` 直接传参或 `--cookie-file <文件>` 从文件读，取法：浏览器 F12 → Network → 右键 Copy as cURL 提取 Cookie 字段），`downloadUrl` 在 cookieHeader 非空时加 `Cookie` 请求头；Cookie 属敏感信息，禁止硬编码在代码里，只经命令行/临时文件传入（tmp 目录已在 .gitignore）；缓存目录 `tmp/bookmarks-html` 已存在的文件跳过下载，若 Cookie 过期导致下载到风控页，需删缓存重跑；**bat/sh 脚本必须透传 `--cookie`/`--cookie-file` 参数**（脚本用 shift 循环解析，`--cookie` 系列参数可出现在任意位置，位置参数 1/2 仍是输入/输出目录，值用单引号包裹避免空格截断）——若脚本只取 `%1` `%2` 两个位置参数，`--cookie-file` 会被当成输出目录，表现为"输出目录变成 --cookie-file"

---

## 移动端陷阱（安卓 APP + 微信小程序）

47. **安卓 WebView 壳工程（android/ 目录，分支 konwledge_app）要点**：
    - **独立解耦**：`android/` 是独立 Gradle 工程（settings 只含 `:app` 单模块），不引用 frontend 源码、不污染前端 package.json；**运行依赖仅 `androidx.core:core-ktx`**，其余纯系统 API（WebView / DownloadManager / FileProvider）；不引入 Material / AppCompat 库——Activity 用纯 `android.app.Activity` + 系统 `Theme.Material.Light.NoActionBar`（状态栏品牌色 #10B981），保持壳最精简
    - **加载模式是远程加载**：WebView 直接加载已部署 H5（隧道域名），nginx 反代 /api，前端零改动、H5 更新免重打包；因此壳层与 H5 之间无 JS 桥，只约定 URL 行为
    - **target="_blank" 链接必须拦截 onCreateWindow**：前端 linkify 生成的下载链接是 `<a target="_blank">`，WebView 中**不走 shouldOverrideUrlLoading**（那是普通导航），必须重写 `onCreateWindow` 用 WebViewTransport 取出目标 URL 分发后丢弃，否则 PDF 链接会在系统浏览器打开（AppWebView.kt）
    - **URL 分发规则**（`AppWebView.dispatchUrl`）：同 host 且路径 `/api/files` 前缀 → `DownloadHelper` 原生下载（DownloadManager 存系统 Downloads + 完成通知 + FileProvider 打开/分享）；同 host 其他 → 放行站内导航；其他域名 / scheme → 系统浏览器
    - **录音双层权限**：manifest 声明 `RECORD_AUDIO` + WebView `onPermissionRequest` 里申请 Android 运行时权限后再 `grant()`（`WebPermissionHandler`）；隧道 HTTPS 是安全上下文，H5 的 getUserMedia 无感可用
    - **录音必须 HTTPS 安全上下文**：手机端"浏览器不支持录音"的头号原因是非 HTTPS（`window.isSecureContext === false` 时 `navigator.mediaDevices` 直接 undefined）。前端 `toggleRecording` 已做**三档分诊提示**：①非安全上下文 →"当前是 HTTP 环境，请改用 https:// 地址"；②`mediaDevices?.getUserMedia` 缺失 →"浏览器/系统内核过旧，请升级"；③`NotAllowedError` →"请在系统设置中允许麦克风权限"。排查顺序：先确认地址是 https 隧道域名而非 http 内网 IP/微信内置浏览器
    - **WebView 下载有三条路径，缺一不可**（前端链接三种形态）：①普通导航（`shouldOverrideUrlLoading` 站内 `/api/files` 拦截）；②`target="_blank"`（`onCreateWindow` + WebViewTransport 取 URL 分发）；③`<a download>`（**必须 `setDownloadListener`**，不走前两者，壳层已补）。DownloadListener 回调的相对路径要拼当前 origin（**用 `Uri.authority` 保留端口**，`host` 会丢端口，`http://ip:8123` 直连调试会拼错）
    - **长按保存图片链路（download=1 约定）**：~~前端长按（600ms，touchmove 取消）→ 构造 `/api/image-proxy?url=...&download=1` 的 `<a download>` 点击~~ **⚠️ 该交互已取消**（移动端"长按后抬手触发 click"与浏览器手势冲突，双击会跳转下载页面；用户要求仅保留点击放大预览）。`download=1` 参数与壳层 `setDownloadListener`/`dispatchUrl` 规则仍保留（供 PDF 等下载用）。**图片预览必须用消息区 click 事件委托**（`.messages @click="handleMessagesClick"` + `closest('img.chat-image')` → `openPreview`）——**DOMPurify.sanitize 会剥离 img 的内联 `onclick`/`onerror` 属性**（默认白名单不含 on* 事件属性），这就是"点击图片不放大"的根因，任何内联事件属性方案都不可靠
    - **预览状态是共享单例**：`frontend/src/utils/previewImage.js` 导出共享 ref + `window.__previewImage` 全局注册（模块加载时一次）；两个聊天页不要再各自 onMounted 注册 / onUnmounted delete（会互相覆盖删除，曾踩坑）
    - **移动端 header 按钮统一高度 40px**：`.sidebar-menu-btn`（40×40 圆角 12）与 `.back-btn`（height:40px + 圆角 12，勿用胶囊 999px + padding 撑高——padding 行高在不同设备字号下高度不一致），标题文字由 header `align-items:center` 垂直居中
    - **批量按钮与个人信息按钮形状一致**：`.batch-bar-btn` 与 `.profile-action-btn` 同用**圆角 12px**（勿用胶囊 999px）；但批量按钮**配色保持原样**（白灰底 `#f8fafc`、黑字、删除红字、柔和阴影，无边框无绿 tint）——用户要求"只参考形状，配色恢复之前的"
      - **三点菜单必须 Teleport 到 body + fixed 定位（遮挡的根治方案）**：菜单渲染在历史列表内部时，`.history-list { overflow-y: scroll }` 会**裁剪**超出滚动容器可视区域的 absolute 菜单（向上/向下展开只要越界就被裁，视觉上像"被个人信息组件挡住"——z-index 再高也没用，因为不是层级遮挡而是容器裁剪）。**修复：菜单移到 `<Teleport to="body">`，`position: fixed`，位置在 `toggleMenu` 里用菜单项的 `getBoundingClientRect()`（视口坐标）+ `window.innerHeight` 计算（下方空间不足且上方够则向上展开），`left = itemRect.right - 8 - 160`，z-index 3000**。菜单项按钮必须 `@click.stop`（Teleport 到 body 后点击冒泡到 document 会立即关闭菜单）；"修改标题"需要 chat 对象时用 `computed` 按 menuChatId 从 historyList 反查
      - **批量模式切换布局零跳动**：批量模式隐藏"新对话按钮"和"个人信息卡片"会改变 sidebar-header（75px→64px）与底部区域（168px→69px）高度，history-list 高度随之变化 100px+，视觉上"画面上下拉伸"。**修复：`.sidebar-header` 固定 `height:75px`（box-sizing:border-box）；移动端 `.batch-bar` `min-height:168px`（与 sidebar-footer 同高，按钮垂直居中，桌面端保持紧凑）**——批量切换时 history-list 高度不变
    - **移动端侧边栏个人信息卡片**：KnowledgeChat.vue `.sidebar` 底部 `.sidebar-footer`（`v-if="isMobile && !batchMode"`，桌面不显示、桌面用 user-dock；进入批量管理时自动隐藏）；⚠️ **卡片背景必须 `transparent`（完全透明）**——半透明背景是叠加的：`rgba(255,255,255,0.55)` 放在同样是 0.55 的玻璃侧边栏上，合成后 ≈0.8，比底色更白、永远不协调（试过 0.75/0.55 均失败）。透明后底色 100% 跟随侧边栏，靠 1px 淡绿边框 `rgba(16,185,129,0.3)` + 阴影 + 圆角 20px 区分；移动端侧边栏统一灰色半透明底 `rgba(245,247,250,0.75)`（media query 内显式声明，桌面端保持白玻璃不动）；第一行头像**与主页 user-dock 头像视觉一致（淡绿底 #ECFDF5 圆 + 绿色渐变字母 `linear-gradient(135deg,#10B981,#34D399,#6EE7B7)` + background-clip:text，不带动效）** + 用户名（响应式 ref）；第二行修改密码（Lock）/ 退出登录（LogOut）两个 44px 按钮（复用 App.vue 的 logout 逻辑：POST /auth/logout + removeToken + 跳首页）
    - **修改密码页返回行为**：ChangePassword.vue 返回按钮文案"返回"（非"返回应用中心"），行为 `window.history.length > 1 ? router.back() : router.push('/')`——从历史会话个人信息卡片进入改密后返回，应回到历史会话界面且**侧边栏保持打开**。⚠️ **不能靠 `window.history.state.back` 判断来源**：浏览器回退后目标条目的 state 是**进入该页时 push 的旧 state**（back 是更早的路径，不是来源页），判断永不命中。**正确做法：进入改密页时 `sessionStorage.setItem('return_to_knowledge','1')` 打标，KnowledgeChat onMounted 读取后立即 `removeItem`**（防止改完密码自动登出/下次直接进入时残留误开侧边栏）
    - **历史对话侧边栏交互（三点菜单 + 批量管理）**：
      - 标题文案为"**历史对话**"（非"历史会话"，所有端统一，含 toggle/menu 按钮 title）
      - **三点菜单替换 hover 快捷按钮**：移动端无 hover，`history-item` 右侧原编辑/删除 hover 按钮必须改为常显竖三点（MoreVertical，36px 触控区），点击弹出悬浮菜单（白底圆角+阴影）：批量管理（ListChecks）/ 修改标题（Pencil）/ 删除对话（Trash2 红色）；`menuChatId` 控制打开状态，document click + 列表 @scroll 关闭（onMounted 注册/onUnmounted 移除，菜单项 @click.stop）
      - **修改标题是弹窗交互**（非行内编辑）：`editTitleVisible` + 预填当前标题 + 取消/确定（确定=绿色按钮 class `modal-btn confirm green`），确定调 `updateChatTitle` 后 fetchHistoryList + toast
      - **批量管理模式**（`batchMode` + `selectedChatIds` + `confirmBatchDelete`）：进入后**侧边栏重新排版**——隐藏新对话按钮、隐藏移动端个人信息卡片；历史项右侧变空心灰圆圈（选中→黑底白勾 Check 白色图标）；底部按钮条：取消（黑字）/ 删除 (N)（红字，N=选中数，N=0 禁用），背景 `rgba(245,247,250,0.75)` 与侧边栏一致
      - **⚠️ 桌面端 user-dock 与批量按钮条重叠**：user-dock（fixed，`z-index:999`）在左下角，会压住侧边栏底部的批量条「取消」按钮（点不到）→ 现按路由整页隐藏个人信息组件，**受影响的页面与历史方案见陷阱 51**；移动端 dock 本就隐藏，无此问题
      - **批量删除确认弹窗**（图三样式）：标题"删除对话记录"左对齐加粗、正文"删除后内容将无法恢复，确认删除选中记录？"、取消（浅灰底黑字）+ 删除（红底 #ef4444 白字）；确认后 `batchDeleteChats(ids)` → 退出批量模式 + 若删了当前会话 `createNewChat()` + fetchHistoryList + toast；**catch 分支必须也关闭弹窗并退出批量模式**（当前实现 catch 里没退，失败时用户会被困在批量界面——已知待优化点）
    - **STT 上传 1MB 限制（已修复）**：后端 `spring.servlet.multipart` 原为 Spring Boot 默认 1MB/文件，录音上限 60 秒（16kHz 16bit 单声道）WAV ≈1.9MB 会 400。**已修复**：`application.yml` 已加 `spring.servlet.multipart.max-file-size: 5MB` / `max-request-size: 6MB`
    - **首启地址配置**：`ServerConfig` 存 SharedPreferences；trycloudflare 等免费隧道域名重启会变，设置页改地址即可，无需重打包；`ServerConfig.normalize` 补全 https:// 前缀
    - **明文流量**：Android 9+ 默认禁 HTTP 明文，manifest 已配 `usesCleartextTraffic="true"` 兜底（自用内测可接受；上架前应移除并强制 HTTPS）
    - **构建**：`cd android && gradlew.bat assembleDebug`，产物 `app/build/outputs/apk/debug/app-debug.apk`；需要 JDK 17 + Android SDK（**本机已具备**：JDK 17 与 Android SDK 都在 `E:\IDE_Extesion_plugin_so_on\` 下，SDK 已装 android-34，`android/local.properties` 已指向它，已成功产出 debug APK）；Gradle Wrapper 8.9 + AGP 8.5.2 + Kotlin 2.0.20
    - **版本管理**：`versionCode`（整数递增）/ `versionName`（如 1.0.0）在 `app/build.gradle.kts`；release keystore 必须备份（丢失无法覆盖升级）

48. **微信小程序端（miniprogram/ 目录，uni-app CLI + Vite + Vue3，分支 knowledge-miniprogram）要点**：
    - **架构**：独立 uni-app 工程，与后端只通过 REST 契约交互；分层 pages（UI）/ components（复用组件）/ utils（config/request/api/auth/chat/markdown/tts/stt）；服务器地址两层解析（storage 优先 + config.js 默认值），上线改 config.js 一行即可
    - **真实 AppID 必须写进 manifest.json**：`mp-weixin.appid` 用游客占位 `touristappid` 时**真机调试点击无响应**（模拟器正常），必须用真实 AppID（本项目 wx85657988d800e96f）；开发者工具"详情→本地设置"需勾选"不校验合法域名"
    - **project.config.json 的 `es6` 必须为 false**（manifest.json `mp-weixin.setting.es6: false`）：置 true 时微信开发者工具用自带 SWC 把产物转 ES5，工具 SDK 缺 `_wrap_reg_exp` helper，启动即报 `module 'common/@swc/helpers/_/_wrap_reg_exp.js' is not defined`——这是工具缺陷不是项目 bug，模拟器/真机都崩
    - **预览/真机构建链语法限制（关键）**：工具预览/真机的语法解析器比模拟器旧，**不认识 ES2020 `??`、ES2018 Unicode 属性转义 `\p{L}` 等**——模拟器正常、预览报 `SyntaxError: Unexpected token ?` / `Invalid regular expression: /[\p{L}\p{N}]/u`。根因是 marked 18 产物含新语法，且 vite 默认不转译 node_modules、esbuild 无法转译正则字面量。**修复在 vite.config.js 的 `downgradeDeps` 插件**：对 marked 先做 `\p{X}` 属性转义→等价 BMP 字符范围替换（保留 /u 标志），再 esbuild 降级 es2015。产物自查：`grep -c "??" common/vendor.js` 与 `\p{` 必须为 0
    - **mp-html 两点**：① npm 版不含 markdown 插件（markdown 属性静默忽略）→ 用 marked 渲染 HTML 再交给 mp-html（easycom 注册）；② **懒加载必须 `:lazy-load="false"` 布尔绑定**（字符串 "false" 是 truthy 无效）——mp-html 懒加载监听页面 scroll，聊天页滚动在 scroll-view 内部永不触发，图片"刷新后才显示"；③ **流式期间勿更新 mp-html content**：其 node.vue watch 在子节点列表变短时往数组塞 `{}` 空对象，渲染报 `Cannot read properties of undefined (reading 'id')`——流式期间用纯文本（streaming-text），流结束内容固定后一次性渲染
    - **微信录音 encodeBitRate 范围 24000-96000**：PCM/WAV 无码率概念，**不要传该参数**（传 256000 直接报错）；录音 16000Hz 单声道；Android `format:'PCM'` 自封装 44 字节 WAV 头上传（对齐 Web 端 encodeWav），iOS `format:'wav'` 直出（采样率/位深待真机验证）；开发者工具模拟器不支持录音，必须真机
    - **聊天接口是 GET**：超长 message 命中 Tomcat 8KB 请求头上限返回 400 HTML，前端限制提问 ≤4000 字
    - **滚动到底部三连坑**：messages 更新后 DOM 未渲染 scroll-into-view 目标不存在 → nextTick + 延迟；目标 id 不变不触发 → 先清空再设置；流式每 chunk 触发高频 → 节流 150ms 合并
    - **页面跳转**：启动页 index（应用中心），未登录 onShow 守卫 reLaunch login；login onLoad 已登录 reLaunch index（同一 storage 守卫互斥，无死循环）；401 处理先 removeToken 再跳 login 同理安全
    - **SVG 图标**：小程序 wxml 不支持内联 SVG，用 `static/icons/*.svg` + image 组件（固定 stroke 色，不用 currentColor）；图标统一 24×24 viewBox / 1.5 描宽 / 圆角端点
    - **真机图片不显示的根治方案（关键）**：安卓真机微信新内核限制 `image` 组件加载 **http + IP 地址**图片（开发者工具/模拟器正常、真机空白），但 `wx.request`/`downloadFile` 网络栈不受限制（聊天流式能通即证明）。**修复（utils/image-loader.js）**：图片不交给 mp-html 内嵌 image 组件直接加载 http 代理地址，改为流结束后 `uni.downloadFile` 下载到本地临时文件（并发 ≤3），渲染时 img src 用**本地路径**；未下载完成显示**本地静态占位图** `static/img-placeholder.png`（⚠️ **不能用 base64 data URI 占位**：真机 image 组件对 data URI 渲染不可靠，且 mp-html parser 对 src 含 `data:` 的 img 强制 `ignore`，`imgtap` 事件不触发导致"点击重试"死代码——必须用打包本地图片）；下载完成 `renderVersion++` 触发重渲染换本地路径；点击占位图重试。**mp-html 必须传 `:preview-img="false"`**（否则其自动调 previewImage 与 onImgTap 双重预览）
    - **真机图片（续）**：`proxyImageUrl` 必须同时支持 `/api/` 根相对路径图片（prompt.yml 引导 AI 用 `![描述](/api/files/download/文件名)` 展示已保存图片）——拼 `getServerRoot()` 成完整 URL，中文文件名 `encodeURIComponent`；`preloadMessageImages` 正则要同时匹配 markdown 图片语法与原生 `<img src>` 两种形式
    - **真机验证清单**：SVG 图标渲染（部分机型本地 SVG 有白屏反馈）、iOS 录音 wav 直出格式、TTS 播报（短文本一次成功即止，模型贵）

65. **小程序 `<text>` 上的 `text-overflow: ellipsis` 不生效（省略号必须用 `<view>`）**：
    - 现象：聊天输入框底排的模型名很长时不肯截断，整行被撑宽，把右侧的语音/发送挤出输入框（真机截图表现为"右下角图标溢出"）
    - 原因：`<text>` 上不生效（项目里能正常省略的 `.tool-title`、历史项、登录输入框都用 `<view>`）
    - 修复：把该元素换成 `<view>`，并保证收缩链完整——名字 `min-width: 0` + 省略号三件套（`overflow:hidden; white-space:nowrap; text-overflow:ellipsis`）、外层控件组 `flex: 0 1 auto; min-width: 0`。经验：小程序里凡是"单行超长要省略"的文本，一律用 `<view>` 而不是 `<text>`
