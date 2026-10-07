# 安卓 APP 需求文档（WebView 壳）

> 本文档定义 ai-agent 项目安卓 APP 化的需求、架构与注意事项。
> 创建分支：`konwledge_app` ｜ 目录：`android/`

---

## 一、目标与范围

### 1.1 目标

将现有全栈 AI Agent 平台（Spring Boot 后端 + Vue3 前端）以 **安卓 APP** 形式提供给手机用户使用，本期为**自用 / 内测直装**，不走应用商店。

### 1.2 技术路线（已确认）

| 决策项 | 结论 | 说明 |
|--------|------|------|
| 形态 | **WebView 壳** | 自研轻量 Kotlin WebView 壳（约 600 行），不引入 Capacitor / RN / Flutter |
| 加载模式 | **远程加载** | WebView 直接加载已部署的 H5（隧道域名），nginx 反代 `/api`，**前端零改动**，H5 更新免重打包 APK |
| 后端访问 | **内网穿透** | 复用 `deploy/cloudflare`（Cloudflare Tunnel）或 `deploy/ngrok` 方案暴露后端 |
| 语音能力 | 录音转文字 + 语音播报 | 麦克风语音输入（STT）；AI 回复播报（TTS）已随 H5 放出：仅图标、无边框无底色，播报中转红表示可停止 |
| 分发 | **自用直装** | 签名 APK 直接安装，无需软著 / 备案 / 商店审核 |

### 1.3 明确不做（本期）

- 离线能力（无网络不可用，APP 强依赖隧道）
- 后台推送通知
- 应用市场上架
- 系统分享菜单接收文本（后续可做：分享网页给 AI 解读）

---

## 二、功能需求

### 2.1 现有页面复用（直接复用，零改动）

| 页面 | 路由 | 功能 |
|------|------|------|
| 首页应用中心 | `/` | 知识库问答 / Manus 两个应用入口 |
| 登录注册 | `/login` | JWT 登录、注册（图片验证码） |
| 个人知识助手 | `/knowledge` | RAG 知识库问答、流式对话、历史会话、引用标注、录音输入、**多模型切换**（输入框内左下角）、**语音播报**（仅图标） |
| 大模型详情 | `/models` | 可切换模型的参数 / 价格 / 能力展示（首页个人中心入口进入） |
| AI 超级智能体 | `/manus` | **任务制**：创建任务、执行过程时间线（计划进度）、任务报告、交付物预览/下载、多轮追问、停止任务、任务记录（改标题/删除/批量管理） |
| 修改密码 | `/change-password` | 修改密码后退出登录 |

前端已完成的移动端适配（375px 断点、抽屉侧边栏、safe-area、100dvh、16px 输入字号、头部历史对话开关）在 WebView 中直接生效。
**新增功能随 H5 生效**：多模型切换、大模型详情页、语音播报等只在 `frontend/` 实现，壳层零改动，部署新 H5 即可，**无需重打 APK**。

### 2.2 APP 特有功能（壳层实现）

| 功能 | 说明 |
|------|------|
| 应用图标 / 名称 | 应用名"知问答"；图标为主页右上角品牌地球的静态版（**彩色线框**，外圆 #818CF8、经线/纬线 #A78BFA、节点 #C4B5FD，与网页端一致）+ **淡绿渐变背景**（#ECFDF5 → #D1FAE5） |
| 图片点击放大 | 点击 AI 回复中的图片 → 全屏预览（背景虚化 blur，消息区事件委托实现）；~~长按保存已取消~~（移动端手势冲突，`download=1` 下载链路保留供 PDF 等使用） |
| 启动窗 | 主题 `windowBackground`（品牌色 #10B981 + 应用名），不额外做 Splash Activity |
| 服务器地址配置 | 首次启动（无已存地址）→ 设置页输入隧道域名；已配置 → 直接加载；加载失败 → 错误页提供"重新设置地址"入口。地址存 `SharedPreferences`，隧道域名变化无需重打包 |
| 文件下载 | 页面内 `/api/files/**` 链接（PDF 等）→ 原生 `DownloadManager` 下载到系统 Downloads 目录 + 完成通知，点击通知可打开 / 分享（FileProvider） |
| 录音权限 | manifest 声明 + Android 6+ 运行时授权 + WebView `onPermissionRequest` 放行，H5 录音代码无感复用 |
| 返回键 | 优先 WebView 历史回退；无历史时退到桌面（不销毁） |
| 外链处理 | 非本站域名链接交给系统浏览器打开 |

### 2.3 核心交互流程

```
首次启动：MainActivity → ServerConfig 无地址 → SettingsActivity 输入地址
        → 保存 → 加载 https://隧道域名/
已配置启动：MainActivity → 直接加载
聊天页点 PDF 链接（/api/files/pdf/xxx.pdf）→ 原生下载 + 通知
聊天页点外链（其他域名）→ 系统浏览器
聊天页点麦克风 → 原生授权（系统弹窗）→ H5 getUserMedia 录音 → STT
返回键：WebView 有历史 → 回退；否则退后台
```

---

## 三、架构设计

### 3.1 设计原则

- **独立工程**：`android/` 为独立 Gradle 工程，与 `frontend/`、后端平级；不引用前端源码、不污染前端 `package.json`，前端零改动
- **拒绝臃肿**：单模块 `app`；运行依赖仅 `androidx.core:core-ktx`（FileProvider 用），其余全部系统 API；不引入 MVVM 框架、协程库、网络库
- **解耦关键**：壳层与 H5 之间**无 JS 桥**，只约定 URL 行为（`/api/files` 下载拦截、外链走浏览器、录音权限放行），双方互不感知
- **职责单一**：包内按职责分包，`web/` 只管 WebView、`bridge/` 只管原生能力、`config/` 只管配置、`ui/` 只管设置页

### 3.2 目录结构

```
android/
├── settings.gradle.kts            # 工程声明（仅 :app 单模块）
├── build.gradle.kts               # 根构建（AGP + Kotlin 插件版本）
├── gradle.properties              # JVM 内存、AndroidX 开关
├── gradle/wrapper/                # Gradle Wrapper（8.9）
├── gradlew / gradlew.bat
└── app/
    ├── build.gradle.kts           # 模块构建：minSdk 26 / targetSdk 34 / 签名
    ├── proguard-rules.pro         # R8 混淆规则（默认空）
    └── src/main/
        ├── AndroidManifest.xml    # 权限、Application、MainActivity、SettingsActivity、FileProvider
        ├── java/com/example/aiagent/app/
        │   ├── MainActivity.kt          # 入口：WebView 容器、生命周期、返回键
        │   ├── web/AppWebView.kt        # WebView 封装：JS 配置、URL 拦截、新窗口拦截
        │   ├── web/WebPermissionHandler.kt  # 录音授权（onPermissionRequest + 运行时权限）
        │   ├── bridge/DownloadHelper.kt # 文件下载（DownloadManager）+ 通知 + 打开/分享
        │   ├── config/ServerConfig.kt   # 服务器地址（SharedPreferences）
        │   └── ui/SettingsActivity.kt   # 服务器地址设置页
        └── res/
            ├── drawable/ic_launcher_foreground.xml   # 前景图标（彩色静态版品牌地球：外圆 #818CF8、线框 #A78BFA、节点 #C4B5FD）
            ├── drawable/ic_launcher_background.xml   # 背景图标（淡绿渐变 #ECFDF5 → #D1FAE5）
            ├── mipmap-anydpi-v26/ic_launcher.xml     # 自适应图标
            ├── values/colors.xml / themes.xml / strings.xml
            └── xml/file_paths.xml        # FileProvider 路径配置
```

### 3.3 模块职责

| 文件 | 职责 | 关键点 |
|------|------|--------|
| `MainActivity` | WebView 容器、生命周期、返回键、地址加载 | `onBackPressed` 优先 `webView.goBack()`；断网/加载失败显示错误页 |
| `web/AppWebView` | WebView 统一配置与 URL 分发 | 启用 JS；`shouldOverrideUrlLoading`：本站 `/api/files` → 下载、本站其他 → 放行、外链 → 系统浏览器；`onCreateWindow` 拦截 `target="_blank"` 链接（前端 linkify 生成的 PDF 链接是 `target="_blank"`，**不能只靠 shouldOverrideUrlLoading**） |
| `web/WebPermissionHandler` | 录音授权 | `onPermissionRequest` 中 `RESOURCE_AUDIO_CAPTURE` → 申请运行时 `RECORD_AUDIO` → 授权后 `grant()` |
| `bridge/DownloadHelper` | 文件下载与分享 | `DownloadManager` 下载到 `Downloads/ai-agent/`；完成广播发通知；通知点击 `FileProvider` 打开/分享 |
| `config/ServerConfig` | 服务器地址 | `SharedPreferences` 持久化；`http/https` 前缀补全；默认值可在 `strings.xml` 配置 |
| `ui/SettingsActivity` | 设置页 | 地址输入 + 保存校验；保存后通知 MainActivity 重新加载 |

### 3.4 与现有系统的边界

```
┌──────────────────────────────────────────────┐
│ Android APP（android/，本期新增）             │
│  WebView ──加载──▶ 已部署 H5（隧道域名）      │
│  原生能力：下载 / 录音授权 / 地址配置 / 返回键 │
└──────────────┬───────────────────────────────┘
               │ https://隧道域名/api（nginx 反代，deploy/cloudflare 已有）
┌──────────────▼───────────────────────────────┐
│ 现有系统（本期零改动）                        │
│  nginx（静态 H5 + /api 反代）                │
│  Spring Boot :8123（/api 前缀、JWT、流式）   │
│  MongoDB（chat_memory / vector_store）       │
└──────────────────────────────────────────────┘
```

---

## 四、注意点清单（踩坑预防）

### 4.1 网络与安全

| 注意点 | 说明 |
|--------|------|
| **明文流量限制** | Android 9+ 默认禁止 HTTP 明文。隧道为 HTTPS 则无碍；直连内网 IP（如 `http://192.168.198.100:8123`）调试需 `usesCleartextTraffic="true"`（清单已配置） |
| **免鉴权接口暴露** | `/api/files/**`、`/api/speech/**`、`/api/image-proxy` 无需 JWT。经隧道暴露公网后：tmp 文件可被枚举下载、TTS 可被刷额度、image-proxy 可被当代理（SSRF 面）。自用可接受，长期暴露建议隧道侧加访问限制 |
| **JWT 有效期 7 天、后端重启即失效** | 无刷新机制，到期需重新登录，过期后 `/auth/me` 校验失败自动回登录页；后端签名密钥**每次启动随机生成**（见 `docs/known-pitfalls.md` 陷阱 2），后端一重启所有 token 立即失效 |
| **token 存储** | H5 存 WebView localStorage（WebView 持久化）。注意：清除 APP 数据会清掉登录态 |
| **401 硬跳转** | 前端 401 处理是 `window.location.href='/login'`，WebView 内经 nginx `try_files` 回退 `index.html`，Vue 路由正常显示登录页，无需改前端 |

### 4.2 语音（录音转文字）

| 注意点 | 说明 |
|--------|------|
| **安全上下文（HTTPS 必须）** | `getUserMedia` 要求安全上下文，非 HTTPS 时 `navigator.mediaDevices` 为 undefined，前端提示"当前是 HTTP 环境，请改用 https:// 地址"。**排查录音问题第一步：确认地址是 https 隧道域名而非 http 内网 IP**。隧道 HTTPS 天然满足；本地 assets 模式需 WebViewAssetLoader（本期远程加载，不涉及） |
| **双层权限** | ①manifest 声明 `RECORD_AUDIO`；②Android 6+ 运行时权限（`onPermissionRequest` 中申请）；③WebView `grant()` 放行。三层缺一不可 |
| **⚠️ STT 上传超限（已修复）** | 后端 `spring.servlet.multipart` 原为 Spring Boot 默认 **1MB/文件**，录音 60 秒 WAV ≈1.9MB 会 400（网页版同隐患）。**已修复**：`application.yml` 增加 `spring.servlet.multipart.max-file-size: 5MB` / `max-request-size: 6MB` |

### 4.3 流式聊天

| 注意点 | 说明 |
|--------|------|
| **GET 传参限制** | 聊天接口是 GET + query 传 message，URL 编码后超 Tomcat `max-http-header-size`（8KB）会直接 400（HTML 错误页）。长文本建议后续加 POST 版本（参考 `/speech/tts` 的做法） |
| **ReadableStream 依赖** | 前端流式用 `fetch + ReadableStream`，依赖较新 System WebView → `minSdk 26`（Android 8.0），老机型 System WebView 可自动升级 |
| **Manus 180s 超时** | 旧接口 `/api/ai/manus/chat` 用 `SseEmitter` 超时 180s（既有限制，已废弃）；任务制页走 `GET /task/{id}/stream` 无超时，仅受浏览器/隧道长连接限制 |
| **隧道长连接** | Cloudflare Tunnel / ngrok 对 SSE/流式长连接需实测；nginx 已配 `proxy_buffering off` |

### 4.4 下载与页面

| 注意点 | 说明 |
|--------|------|
| **target="_blank" 拦截** | 前端 linkify 把 PDF 链接生成 `<a target="_blank">`，WebView 中走 `onCreateWindow` 而非 `shouldOverrideUrlLoading`，两者都要处理 |
| **URL 判定** | 用 `Uri` host 与当前加载 host 比对判"本站"；路径前缀 `/api/files` 判下载；避免把站内普通导航误判为外链 |
| **H5 缓存** | 确认部署 nginx 对 `index.html` 设 `Cache-Control: no-cache`，否则 H5 更新后 WebView 显示旧页面（静态资源带 hash 可长缓存） |
| **键盘遮挡** | `windowSoftInputMode="adjustResize"`（默认）；聊天页输入框是否被键盘遮挡需真机验证，必要时引入键盘监听 |
| **后台切换** | WebView 在 Activity `onPause` 时 JS 定时器暂停，流式聊天切后台可能中断（可接受）；回前台自动恢复渲染 |

### 4.5 打包与发布

| 注意点 | 说明 |
|--------|------|
| **keystore 备份** | 生成 release keystore 并妥善备份（丢失后无法覆盖升级，只能换包名重装）。自用可先用 debug 签名 |
| **版本管理** | `versionCode`（整数递增）/ `versionName`（如 1.0.0） |
| **临时域名** | trycloudflare 免费域名每次启动隧道会变 → 地址配置页应对，无需重打包 |
| **图标** | 本期用自适应图标（前景 SVG + 背景色）；后续可换正式设计图 |

---

## 五、验收标准

- [ ] `gradlew assembleDebug` 编译通过（需本机安装 Android SDK，见第六节）
- [ ] APK 安装到手机，首启进入地址设置页
- [ ] 输入隧道地址 → 加载 H5 首页 → 登录（admin/admin）成功
- [ ] 知识库问答：流式输出正常、RAG 引用卡片可展开
- [ ] 录音：首次点击麦克风弹系统权限 → 录音 → 转文字填入输入框（HTTPS 隧道地址下）
- [ ] 点击 AI 回复中的图片 → 全屏放大预览（背景虚化）
- [ ] 移动端历史会话抽屉底部显示个人信息卡片（头像+用户名+修改密码/退出登录）
- [ ] PDF 下载：AI 生成 PDF → 点击链接 → 系统下载通知 → 点击打开/分享
- [ ] 外链点击 → 系统浏览器打开
- [ ] 返回键：聊天页先回退再退后台
- [ ] 断网/地址错误 → 显示错误页 → 可重新设置地址

---

## 六、安卓开发环境安装指南（换机 / 重装时参考）

> 本机环境（2026/9/29 复核）：**已装** JDK 17（`E:\IDE_Extesion_plugin_so_on\JAVA_JDK17`）与 Android SDK（`E:\IDE_Extesion_plugin_so_on\Android\Sdk`，已装 android-34），`android/local.properties` 已指向该 SDK，**已成功产出 debug APK**（`app-debug.apk`）。以下步骤保留备查。

### 6.1 安装 JDK 17

后端与安卓壳都要求 Java 17（本机 JDK 17 在 `E:\IDE_Extesion_plugin_so_on\JAVA_JDK17`，构建时把 `JAVA_HOME` 指向它即可）。

- 下载：Oracle JDK 17（https://www.oracle.com/java/technologies/downloads/#java17 ）或 **Eclipse Temurin 17**（https://adoptium.net ，免费，推荐）
- 安装后设置环境变量：
  ```
  JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.x
  Path 追加 %JAVA_HOME%\bin
  ```
- 验证：`java -version` 显示 17.x

### 6.2 安装 Android Studio（含 SDK）

1. 下载：https://developer.android.com/studio （Windows 版，约 1GB）
2. 安装时勾选 **Android SDK** 组件；SDK 默认装到 `C:\Users\<你>\AppData\Local\Android\Sdk`
3. 首次启动 → SDK Manager（右上角 SDK Manager 图标）→ SDK Platforms 勾选 **Android 14（API 34）**，SDK Tools 勾选 **Android SDK Build-Tools**、**Platform-Tools**（含 adb）
4. 系统环境变量：
   ```
   ANDROID_HOME=C:\Users\<你>\AppData\Local\Android\Sdk
   Path 追加 %ANDROID_HOME%\platform-tools
   ```

### 6.3 编译 APK（命令行）

在 `android/` 目录执行（Gradle Wrapper 自动下载 Gradle，无需单独装）：

```bash
# 首次构建（自动下载 Gradle 8.9 + AGP 依赖，需联网，约几分钟）
gradlew.bat assembleDebug
```

产物：`android/app/build/outputs/apk/debug/app-debug.apk`

### 6.4 安装到手机

```bash
# 方式一：USB 调试（手机开启开发者模式 + USB 调试）
adb install app-debug.apk

# 方式二：直接拷贝 APK 到手机，点击安装（需允许"安装未知来源应用"）
```

### 6.5 真机验证清单

1. 手机与服务器网络可达（隧道域名手机浏览器能打开）
2. APP 首启输入隧道域名 → 加载成功
3. 登录 → 知识库问答 → 录音 → PDF 下载，逐项对照第五节验收标准
4. 看 logcat 有无 WebView 报错：
   ```bash
   adb logcat -s chromium:V AndroidRuntime:E
   ```

### 6.6 发布签名（可选，自用可跳过）

```bash
keytool -genkey -v -keystore release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias ai-agent
# 将 release.jks 放到 android/app/ 下，在 app/build.gradle.kts 配置 signingConfigs 后执行：
gradlew.bat assembleRelease
```

---

## 七、后续迭代方向（本期不做）

| 方向 | 说明 |
|------|------|
| 本地打包模式 | H5 打包进 APK（离线启动快），需 WebViewAssetLoader 解决安全上下文 + BASE_URL 改造 |
| 分享接收 | 系统分享文本 → 打开 APP 聊天页预填 |
| 原生登录页 | 登录态与 Web 分离，token 存 Keystore |
| 上架准备 | 软著、隐私政策、备案、安全检测 |
