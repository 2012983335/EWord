# EWord 0.4.7 核验范围

## 已有记录

- Release 构建、签名验证及 APK 16 KiB ZIP 对齐检查通过。
- 七项中英模型的大小与 SHA-256 校验通过；APK 无 INTERNET 权限。
- 最近文件、长按手势门控、设置开关拖动门控共 13 项针对性 Android 测试通过。
- Rust mobile 引擎此前记录 45 项通过、1 项忽略；扫描算法此前记录 23 项通过。
- 合成中文 DOCX 样本在 Microsoft Word 重开核对过正文替换、表格、图片、页眉和页码。
- 已覆盖安装到开发用手机，版本 0.4.7 / 407992，未清除数据。

这些结论来自本地开发记录，不等于在所有 Android 设备上的完整验收。公开仓库不包含含个人路径、手机标识、最近文件名的原始日志或截图。

## 未完成或已知问题

- 飞行模式、清除数据后首次翻译、翻译速度和峰值内存仍需真机验收。
- 最终触感、最近列表长按/拖动、设置滚动手势及真实扫描质量由用户验收。
- Android 全量单元测试存在既有问题：ReadAloudLanguageTest 引用未定义的 languageIndex；UiTextTest 对部分动画调试标签误报。上面的 13 项结果使用本地编译排除脚本，不代表全量测试通过。
- PDF→DOCX 的表格结构、复杂 Office 分页和严重模糊照片不能保证完整恢复。

## 发布包

文件：EWord-0.4.7-offline-arm64.apk
SHA-256：ff7e9a5ed23fed6d44fb37538df23950b259b7d4f0a35a28bebf0fc104544052
应用 ID：cn.jiandoc.app
最低版本：Android 10（API 29）；架构：arm64-v8a
签名证书 SHA-256：1ca4cf49191457909f1cf1f7829cba603b64576355ce5836370bd12d8056ed2a
