# 微信小程序端（miniprogram/）

个人测试用小程序，目标是把全栈链路（小程序 → 后端 → LLM/RAG → 小程序）跑通。技术栈：**uni-app CLI + Vite（Vue 3）**，编译到微信小程序。

- 创建分支：`knowledge-miniprogram`（从 `konwledge_app` 创建）
- 与 Web 端（frontend/）、安卓壳（android/）互不引用，只通过后端 REST 接口交互

## 一、环境准备

1. **安装微信开发者工具**（https://developers.weixin.qq.com/miniprogram/dev/devtools/download.html）；manifest.json 已写**真实 AppID**（`wx85657988d800e96f`），**不要用游客占位 `touristappid`**——模拟器能跑、真机调试点击无响应（见技术要点 9）
2. 后端启动（本机 8123 端口，见项目根 README/AGENTS.md 快速命令）
3. 编译并导入：
   ```bash
   cd miniprogram
   npm install
   npm run dev:mp-weixin   # 开发模式（改动热编译），或 build:mp-weixin 产物在 dist/build/mp-weixin
   ```
   打开微信开发者工具 → 导入项目 → 选择 `dist/dev/mp-weixin`（或 `dist/build/mp-weixin`）目录
4. **关键：开发者工具右上角"详情 → 本地设置"勾选"不校验合法域名"**（后端是局域网 HTTP，非 HTTPS 备案域名）

## 二、服务器地址配置（重要）

小程序无地址栏，每个请求都要完整 URL。后端地址不能写死（本机 localhost 手机访问不了、局域网 IP 会变、HTTPS 隧道域名重启会变），所以设计为：

- 登录页底部显示**当前服务器地址**一行小字，点击弹窗输入新地址（保存后自动补全 `/api` 后缀）
- 示例：`http://192.168.1.100:8123/api`（局域网，手机与电脑同一 Wi-Fi）或 `https://xxx.trycloudflare.com/api`（HTTPS 隧道，任意网络）
- 地址存本地 storage，`utils/request.js` 每次请求读取：`baseURL = storage(base_url) || config.DEFAULT_BASE_URL`
- **上线转正式服务器**：只需改 `src/utils/config.js` 的 `DEFAULT_BASE_URL` 为正式 HTTPS 域名，登录页移除设置入口（组件 `server-config.vue` 代码保留，随时可恢复）

## 三、功能清单

| 页面 | 功能 | 说明 |
|---|---|---|
| 应用中心首页 | 应用入口卡片（个人知识助手 / AI 超级智能体）、用户信息卡片（服务器设置 / 退出登录） | 启动页，未登录自动跳登录页 |
| 登录页 | 登录 / 注册（图形验证码）/ 服务器地址设置 | 预填 admin/admin；注册成功自动登录 |
| 知识聊天页 | 流式聊天（RAG 开关）、**大模型切换**（底部弹层，切换后上下文连续）、RAG 引用折叠展示、历史会话弹层（切换/改标题/删除/批量删除）、语音输入（STT）、语音播报（TTS，仅图标）、图片预览、PDF 下载打开、新建对话 | 对齐 Web 端 KnowledgeChat 交互 |
| Manus 页 | **任务制轮次对话**：创建任务 / 执行过程实时渲染（计划进度四态 + 事件列表，图标与 Web 一致）/ 任务报告卡片（铺满、可复制）/ 交付物下载打开（图片预览）/ 多轮追问 / 停止任务 / 任务记录弹层（新建任务、改标题、删除） | 对齐 Web 端 ManusChat 的任务制交互（任务数据按登录用户隔离） |

## 四、架构（松耦合分层）

```
src/
├── pages/        # 页面层：只做 UI 与交互编排
├── components/   # 组件层：chat-bubble（消息与任务报告，支持隐藏头像/紧凑排版/全宽模式）/ history-panel / confirm-dialog / server-config / model-picker（模型选择底部弹层）
└── utils/        # 逻辑层：config（常量）/ request（请求+流式）/ api（接口集中）/ auth / chat（会话+引用解析）/ markdown（预处理）/ tts / stt / model（模型清单缓存+当前选择，与 Web 端同构）
```

- 页面不直接拼 URL，全部走 `utils/api.js`
- 后端接口契约见各文件 Javadoc 注释对应的 Controller（AiController / AuthController / SpeechController / ImageProxyController）

## 五、技术要点与踩坑记录

1. **流式聊天**：后端 knowledge 两个接口是**纯文本 chunk 流**（非 SSE 帧），Manus 任务页订阅 `GET /ai/manus/task/{id}/stream?after=` 的 **SSE 事件流**（每帧 `data:` 为一个事件 JSON）。`utils/request.js` 的 `streamRequest` 用 `uni.request({ enableChunked: true })` + `onChunkReceived` 累积，传 `{ sse: true }` 自动剥离帧前缀；事件 JSON 解析失败只丢该帧（与 Web 端策略一致）。**跨 chunk 的 UTF-8 多字节字符**已做残留缓冲拼接，中文不乱码
2. **Markdown 渲染**：mp-html npm 版**不含 markdown 插件**（markdown 属性会被静默忽略），改用 **marked 渲染为 HTML**（gfm 自动链接裸 URL）再交给 mp-html 展示与清洗。图片 URL 在 marked renderer 里重写为 `/api/image-proxy?url=` 代理（防盗链）；`/api/files/` 裸下载路径在预处理里包成链接
3. **代码块保护**：markdown 预处理先提取 ``` 围栏和行内代码为占位符，URL 改写后再还原，避免污染代码内容
4. **图片预览**：mp-html `imgtap` → `uni.previewImage`；**链接**：`/api/files/` 前缀 → `uni.downloadFile` + `uni.openDocument`（PDF 等），外链 → 复制剪贴板
5. **语音播报 TTS**：POST `/speech/tts` 拿 arraybuffer → 写 USER_DATA_PATH 临时 mp3 → `uni.createInnerAudioContext` 播放，播完删临时文件。⚠️ **TTS 模型费用较高：测试用短文本、成功即止**
6. **语音输入 STT**：录音 16000Hz 单声道 → 上传后端识别。
   - Android：`format: 'PCM'` 拿原始帧**自封装 44 字节 WAV 头**（16kHz/16bit/单声道）上传
   - iOS：`format: 'wav'` 直出上传（**采样率/位深需真机验证**，不满足时降级同 PCM 方案）
   - ⚠️ **微信开发者工具模拟器不支持录音**，语音输入必须在真机上测（真机调试扫码，手机与电脑同一 Wi-Fi）
7. **401 处理**：后端 JWT 签名密钥每次启动随机生成，**服务器重启后所有 token 失效**（根因见 `docs/known-pitfalls.md` 陷阱 2）——请求层 401 自动清登录态跳登录页
8. **聊天接口是 GET**：长文本会超 Tomcat 8KB 请求头上限（400），前端已限制提问 ≤4000 字
9. **真机调试/预览要求**：必须使用**真实 AppID**（manifest.json 已配置 wx85657988d800e96f），游客模式（touristappid）真机调试会无响应；开发者工具"详情 → 本地设置"需勾选"不校验合法域名"
10. **预览/真机构建链语法限制**：工具预览构建的语法解析器较旧——不支持 ES2020 `??`、ES2018 Unicode 属性转义 `\p{L}` 等。vite.config.js 的 `downgradeDeps` 插件对 marked 等依赖做正则替换 + es2015 降级。**产物自查**：`grep -c "??" dist/dev/mp-weixin/common/vendor.js` 应为 0
11. **es6 转译必须关闭**：manifest.json `mp-weixin.setting.es6` 必须为 false，否则工具自带 SWC 转译产物报 `_wrap_reg_exp is not defined`（工具 SDK 缺陷）
12. **mp-html 懒加载失效**：mp-html 默认懒加载监听页面 scroll，聊天页滚动在 scroll-view 内部不触发 → 图片永不加载。已传 `:lazy-load="false"`（**必须布尔绑定**，字符串 "false" 是 truthy 无效）
13. **录音参数坑**：微信 `encodeBitRate` 范围 24000-96000，且 PCM/WAV 无码率概念——**不传该参数**；录音必须 16000Hz 单声道，Android PCM 自封装 WAV 头、iOS wav 直出（采样率待真机验证）
14. **真机图片不显示（安卓微信新内核）**：开发工具正常、真机空白的经典问题——image 组件加载 http+IP 图片受限（wx.request/downloadFile 网络栈不受限）。**已根治**：图片先 `uni.downloadFile` 下载到本地临时文件（并发 ≤3、缓存复用、占位图点击重试），渲染用本地路径；占位图用本地静态 `static/img-placeholder.png`（**勿用 data URI base64**：真机渲染不可靠 + mp-html 对 data: src 强制 ignore 导致 imgtap 失效）；mp-html 需传 `:preview-img="false"`（避免与 onImgTap 双重预览）
15. **滚动到底**：scroll-into-view 目标若为最后一条消息，长消息时只滚到消息顶部（最新内容仍在屏下）。**已修复**：列表末尾固定 `bottom-anchor` 锚点元素，scroll-into-view 始终滚向锚点（先清空再设置触发），节流 150ms
16. **页面尺寸**：聊天页 `.page` 用 `height: 100vh + 100dvh`（dvh 新内核生效、旧内核回退）+ `overflow: hidden`，避免真机整体可滚动；空状态 `v-else` 独立容器 flex 垂直水平居中（勿用 margin-top 固定值）
17. **大模型切换**：`utils/model.js`（清单缓存 + 当前选择持久化到 storage `ai_agent_model`，与 Web 端同键名）+ `components/model-picker.vue` 底部弹层（模型名 + 厂商 + 当前打勾，未配置密钥的置灰不可选）；发送时 `api.js` 把 `model` 拼进流式接口 query，**空值不下发该参数**（否则会拼出 `model=undefined` 打到后端 400）；清单只请求一次（失败清缓存可重试）
18. **输入区布局（与 Web 端移动端一致）**：四个控件全在输入框内——输入框独占上半部分，底部一排左下角 RAG 与模型切换**并列**、右下角语音/发送；语音/发送**无边框、底色与输入框一致**（录音/终止才用实色底 + 白图标，便于分辨状态）
19. **单行超长文本省略号必须用 `<view>`**：`<text>` 上 `text-overflow: ellipsis` 不生效，名字不截断会把整行撑宽、把右侧按钮挤出容器（详见 `docs/known-pitfalls.md` 65）

## 六、已知限制（待真机验证）

- [ ] STT 录音：Android PCM 封装需真机验证；iOS wav 直出采样率是否 16kHz 需真机验证
- [ ] TTS 播报：真机播放体验（开发者工具可播但建议真机确认）
- [ ] 长按/滚动等手势在小程序端的实际体验
- [ ] Manus 任务制页真机回归：任务创建 → 事件流实时渲染 → 报告卡片 → 交付物下载打开 → 追问 → 任务记录（新建/改标题/删除）；长任务事件流可能触发请求超时（`STREAM_TIMEOUT` 5 分钟）需真机确认

## 七、测试清单（个人测试流程）

1. 开发者工具模拟器：登录/注册（验证码）、知识聊天流式（RAG 开/关）、引用标注展示、历史弹层全操作（切换/改标题/删除/批量删除）、Manus 任务制（创建任务/执行过程/报告/交付物/追问/任务记录管理）、图片预览、PDF 下载打开
2. 真机（扫码预览，同一 Wi-Fi）：语音输入（Android 必测，iOS 有则测）、TTS 播报（短内容一次）、局域网/隧道地址切换
3. 后端日志确认无异常、测试数据清理
