# EWord

中文、离线、免登录的 Android 文档工具。当前发布版本 **0.4.10**，支持 Android 10 及以上的 **ARM64** 设备。

0.4.9 支持在 XLSX 单元格和 PPTX 文字段落的原位置直接输入，不再弹出编辑对话框；支持另存副本。使用方式、限制和核验结果见 [0.4.9 说明](docs/RELEASE-0.4.9.md)。

0.4.10 已补全设置中的 [GitHub 问题反馈入口](https://github.com/2012983335/EWord/issues)，见 [更新说明](docs/RELEASE-0.4.10.md)。

## 安装

从[本仓库最新发布](https://github.com/2012983335/EWord/releases/latest) 下载 `EWord-0.4.10-offline-arm64.apk`，允许系统安装该来源的应用。已安装旧版时可覆盖安装；不要先卸载，以免丢失应用私有数据。更新必须使用相同签名。

APK SHA-256：

```text
49c7a5c568fd0a0932134a312615d541dd4c745afcc1531c87c05823efd01aaf
```

## 功能

- Word：编辑 DOCX 正文和表格文字，签名图片放置。
- Excel / PowerPoint：XLSX 单元格文字、数字和公式原位编辑；PPTX 已有文字段落原位编辑，支持另存副本。
- PDF：阅读、搜索、页码跳转、阅读进度、笔迹批注、签名、水印、页码、图片合成 PDF、内容提取及永久擦除。
- 图片与扫描：裁剪、旋转、标记；自动找边、手动四角调整、透视校正、增强、多页排序和 PDF 导出。
- 文件管理：最近记录排序、多选、置顶、防误删锁定，分类、复制、移动、重命名、回收站、草稿及版本恢复。
- 选中文字中英双向翻译：Foxlet / Bergamot 引擎及模型随 APK 打包，本地处理，单次最多 2000 字。扫描件需有文字层或先 OCR。
- 个性化：玻璃界面、自选背景、语言和触感设置。

## 使用限制与核验范围

这是当前开发快照，不承诺对所有 Office 文档无损兼容。PDF 转 Word 的表格可能变为定位文字；严重失焦照片无法保证修复。永久擦除不能移除其他地方保存的原件或历史备份。

构建、签名、模型资产完整性及部分自动化测试已检查。真机首次启动离线翻译、扫描质量、最终手势和触感由使用者验收，尚未宣称完整通过。详见 [核验说明](docs/RELEASE-0.4.9.md)。处理重要文件前请保留原件。

## 源码与构建

源码保留在 `upstream/`，基于 [Artefact](https://github.com/Chethan616/Artefact/tree/9e117e16dc66f810f2f50301332d5f246a7de76a) 固定提交定制。Android 使用 Kotlin / Jetpack Compose，文档引擎使用 Rust / UniFFI。

准备 JDK 21、Android SDK Platform 37、NDK 29.0.14206865、Rust 1.99.0 和 cargo-ndk 4.1.2。配置 `ANDROID_HOME`、`ANDROID_NDK_HOME`，安装 Rust Android target，并确保 `cargo` 可用。Windows 建议使用英文路径。Gradle / Cargo 首次构建需要联网获取开发依赖，安装后的翻译不需要网络。

```powershell
cargo install cargo-ndk --version 4.1.2 --locked
rustup target add aarch64-linux-android
cd upstream/android
./gradlew.bat '-Partefact.abis=arm64-v8a' assembleRelease
```

正式签名通过不入库的 `upstream/key.properties` 配置 `storeFile`、`storePassword`、`keyAlias`、`keyPassword`。未配置时构建脚本使用调试签名，无法覆盖安装正式发布包。私钥不公开。

离线模型、Foxlet AAR 和相应许可保存在源码内；构建前校验哈希。引擎测试：

```powershell
cargo test --locked --manifest-path upstream/engine/Cargo.toml -p artefact-mobile
```

## 开源许可与致谢

本项目基于 Artefact，保留其 MIT / Apache-2.0 双许可及版权声明。完整文本见 [LICENSE-MIT](LICENSE-MIT)、[LICENSE-APACHE](LICENSE-APACHE)。第三方组件、模型及字体按各自许可分发，见 [第三方说明](docs/THIRD_PARTY.md) 及 `upstream/NOTICE`。

应用内“完全免费仅供个人使用，请勿付费或进行商业盈利”为作者的使用倡议，不替代或限制上游开源许可已授予的权利。图标及插画由项目维护者提供，其第三方权利保留，不承诺可按代码许可再授权。

维护者：乘醉听箫鼓；使用 Codex 辅助开发。反馈请使用仓库 Issues，或访问 [bilibili](https://space.bilibili.com/43259549)。请勿在公开反馈中上传签名、证件、私密文档或密钥。
