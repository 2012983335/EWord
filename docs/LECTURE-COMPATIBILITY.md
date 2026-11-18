# 讲义浏览兼容性检查与后续方案

## 0.4.14 当前状态

已内置完整离线兼容阅读引擎；两份旧 PPT 的图文、公式及返回流程经真机人工确认。新增 ODP/OTP/FODP 入口，Office 更多菜单可打开已保存版本的兼容阅读。资源开销、核验范围与限制见 [更新说明](RELEASE-0.4.14.md)。

下文的文字修复和候选引擎段落保留的是 0.4.13 阶段的检查历史，不代表当前状态。
## 当前问题和已验证结果

旧版 PPT 的正文不一定存放在 SlideListWithText，大量文本框存放于 SlideContainer 内的 OfficeArtClientTextbox。旧读取器漏读后会出现有页数却几乎全白的现象。

本次修复：通过 PersistDirectory 将大纲中的 persist id 映射到实际幻灯片，再读取其内部文本框；保持页序并限制解析预算。中文提示补齐，并限定为实际 Slides 类型，避免仅靠扩展名发出提示。

本机两份用户提供的讲义测试：19 页文件恢复 16 页可见文字，14 页文件恢复 11 页可见文字。已逐页渲染检查这些文字页包含可见像素。各有 3 页仍未提取到文字。图片、公式对象、原始图形和排版尚未恢复，不能宣称完整解决旧 PPT 兼容性。原文件未修改，用户文件不纳入源码仓库。

## 优先覆盖的格式族

这是候选覆盖清单，不是基于市场占有率的排名。

| 优先级 | 格式 | 当前状态/需要验证 |
| --- | --- | --- |
| P0 | PDF、扫描 PDF、带密码 PDF | 已有原生阅读；补充扫描页、大页、字体缺失、表单、密码等真实样本验证 |
| P0 | PPT/PPS/POT、PPTX/PPSX/POTX、PPTM | 现代格式已有阅读；旧 PPT 当前只是文字兼容预览，完整图文需复用完整 Office 引擎 |
| P0 | DOC/DOCX/DOT、RTF、ODT | 已有 Word 引擎；重点核对公式、浮动图、表格、分栏和嵌入字体 |
| P1 | XLS/XLSX、ODS、CSV、TSV | 已有表格引擎；优先讲义附表、公式结果及图表显示核验 |
| P1 | JPG/JPEG、PNG；TIFF、多页 TIFF、WebP | 前两者已有入口，其他格式需确认解码及多页导航；大图需缩采样 |
| P1 | EPUB、HTML/HTM、TXT/Markdown | TXT/Markdown 已有入口；EPUB 与安全的本地 HTML 阅读需新增专门阅读流程，保留目录和资源引用 |
| P2 | DjVu/DJV、CBZ/CBR、XPS/OXPS | 需要单独评估开源解码器、Android 移植及许可证，不可只添加扩展名 |
| P2 | WPS/DPS/ET、UOF/UOT/UOP、Pages/Keynote | 依赖完整兼容引擎及具体版本实测，不能等同于改后缀 |
| 特殊 | CAJ/NH/KDH/CEB、加密电子书、平台专有课件 | 逐项查文档、许可证和解码能力；受保护文件不承诺绕过限制 |
| 资料包 | ZIP/RAR/7z、视频、网页课程 | 压缩包属于容器，应解压后按内部格式浏览；视频与文档阅读分开考虑 |

## 完整 Office 阅读的候选

- 首选验证 Collabora Office / LibreOffice Android 引擎：官方明确列出 DOC/DOCX、XLS/XLSX、PPT/PPTX；Android 官方构建已存在。必须先用本次课件核对图文、公式与页序，然后确定源码集成、资源预算、许可与分发方式。尚未集成。
- ONLYOFFICE Android 也提供本地离线浏览，但其移动 WebView 集成文档指向商业版能力，不能将免费 Android 应用与可免费嵌入 SDK 画等号。需要进一步核对当前源码和集成许可。
- 完整引擎会涉及 Android 原生库、资源打包及生命周期管理，是较大的集成变更。保持现有签名、PDF 批注、扫描与翻译路径，兼容阅读器作为有边界的模块验证。

## 验收方法

按文件内容识别格式；每个格式至少覆盖正文、公式、图片、表格与多页样本。逐页与原软件/PDF 参考件对照，禁止以“成功返回一个空白位图”当作兼容通过。大文件离屏加载，限制解压大小和深度，首次启动断网测试。未知/不完整支持明确提示，不静默丢内容。

## 官方来源

- Microsoft MS-PPT TextHeaderAtom： https://learn.microsoft.com/en-us/openspecs/office_file_formats/ms-ppt/08d31a66-0750-4009-b416-49f2871cd178
- Collabora 格式与移动端说明： https://www.collaboraonline.com/faqs/
- Collabora Android 构建： https://www.collaboraoffice.com/downloads/Collabora-Office-Android-Snapshot/
- ONLYOFFICE Android 本地离线说明： https://helpcenter.onlyoffice.com/mobile/android/documents/getting-started/overview
- ONLYOFFICE 移动集成限制： https://api.onlyoffice.com/docs/docs-api/get-started/how-it-works/mobile-integration
- EPUB 3.3 规范： https://www.w3.org/TR/epub-33/

以上为调研与检查结果，表中的待实现/待验证项尚未完成。
