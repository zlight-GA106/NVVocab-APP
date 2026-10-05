# NVVocab Android

NVVocab 的原生 Android 版本。项目使用 Kotlin、Jetpack Compose Material 3、SQLite 与 WorkManager，支持 Android 12 至 Android 16。

## 已移植功能

- 原项目 `bwolf.png` 应用图标与 NVVocab 标题栏
- 仪表板每日复习进度、可设置的学习时长目标、月热力图与周热力图
- 词库批量文本解析、分类选择与本地导入
- 复习模式与练习模式的沉浸式拼写流程
- 单词用法复习：使用现有 AI 配置（默认 DeepSeek），按词库分类和搜索选定目标词，补全常见搭配、介词用法和语境例句，每词生成两道单选题；结果自动保存为题库，支持词组自动朗读及单词、例句重听
- 对照练习首次生成的题目支持英文题干与选项喇叭朗读，选项自动播放沿用沉浸复习的开关
- 设置中的“题库导出”可将所有题库打包为 ZIP，每个题库包含一个可重新导入的 XML，保留原题库名、题目、答案与解析
- 设置中的“EasyUpdate”支持自定义 HTTP/HTTPS 更新源，检查新版本后自动下载，校验大小、SHA-256、包名、版本号与签名，再打开 Android 系统安装器
- 有限队列、数量限制、分类筛选和熟练度双向排序
- SQLite 离线词库与复习日志
- Supabase 邮箱注册、登录、Token 刷新和 RLS 用户隔离
- WorkManager 联网自动同步与手动同步
- 桌面每日备忘微件、学习时长进度、次数达标删除线与每日零点重置
- WebView 国内在线发音、公开词典音频备用与系统本地 TTS 最终降级
- Android 系统原生 Material You 动态取色
- 霜潮、铜玄、黑猫、红狐、星空与薄荷六套持久化色彩预设
- 跟随系统、浅色和深色显示模式
- Android 13 及以上通知权限请求，支持单词匹配、复习默写与分组题目独立定时提醒
- 手机底部导航、紧凑屏缩放与 1920×1080（16:9）、1920×1200（16:10）HD 侧栏布局

## 环境要求

- Android Studio
- JDK 17
- Android SDK Platform 16，API 36.1
- Android SDK Build Tools 35 或更高版本

## 构建

用 Android Studio 打开本目录，等待 Gradle 同步后运行 `app` 配置即可。

命令行构建：

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat testDebugUnitTest assembleDebug
```

调试 APK 输出位置：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## EasyUpdate 更新

1. 在“设置 → EasyUpdate”填写并保存更新服务器地址，默认 `http://192.168.95.55:19910`，也可以粘贴该服务器的 `/admin/help` 链接。
2. 点击“检查更新”。有新版本时自动下载，校验完成后触发系统安装器。
3. 首次需要在 Android 系统页面允许本应用“安装未知应用”，返回后会继续安装；安装器仍需用户确认。取消后可点击“安装更新”重试。

服务器采用 [EasyUpdate](https://github.com/zlight-GA106/Easyupdate) 协议，按当前运行的包名和整数版本号查询 `/api/v1/apps/{package_name}/latest?version_code=...`。测试包为 `com.zlight106.nvvocab.debug`，正式包为 `com.zlight106.nvvocab`，应分别注册对应的包名并发布同签名 APK。手机需要能够访问所填服务器；局域网源应使用手机可达的地址。

## Supabase 连接

1. 打开应用的“设置”。
2. 展开“Supabase 节点”。
3. 填写 Project URL。
4. 填写 Supabase Publishable Key。旧项目也可使用兼容的 Anon Key，禁止填写 `service_role` 或 Secret Key。
5. 保存后进入“账户”注册或登录。
6. 登录后在“设置”或“账户”执行一次手动同步。

服务端应具备与 Web 版本一致的 `public.wordbase` 和 `public.review_logs` 表，并启用基于 `auth.uid() = user_id` 的 RLS 策略。

## 离线模型

SQLite 是移动端的本地数据源。导入和默写会先提交到本机数据库，不依赖即时网络响应。启用自动同步后，WorkManager 会先下载远端词条、复习日志与题单并合并到本机，再上传本地待同步修改，避免旧的本地快照覆盖云端新数据。

Android 版本不会使用 SM-2 直接决定完整调度，只把 `repetitions`、`interval`、`easiness` 与 `wrong_count` 用作熟练度参考。移动端复习结果采用稳定的本地复习节奏写入下一复习时间。
