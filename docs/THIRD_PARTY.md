# 第三方组件与素材

EWord 基于 Artefact 固定提交 `9e117e16dc66f810f2f50301332d5f246a7de76a`。原版权、许可证及源码归属保存在 upstream/LICENSE-*、NOTICE、ATTRIBUTION.* 和各 vendor 目录。

主要复用组件包括 PhotoEditor (MIT)、Android Image Cropper (Apache-2.0)、Reorderable (Apache-2.0)、AndroidX Compose 及 Artefact 的 Rust 文档引擎。完整清单以保留的 NOTICE 和各组件许可为准。

翻译使用未修改的 Foxlet Translate v0.5.0 AAR（MIT 包装层、Bergamot MPL-2.0、Marian MIT，其他组件分别许可）。对应源码：
https://github.com/yinvoke/foxlet-translate/tree/v0.5.0

Mozilla en→zh-Hans 2.2 和 zh-Hans→en 2.1 模型采用 MPL-2.0，总计 106,491,667 字节。发布方 URL、大小及 SHA-256 在 `upstream/android/app/src/main/assets/translation/registry.json`；许可与对应源码位置在 `upstream/android/app/src/main/assets/licences/foxlet/`、`MPL-translation-models.txt`。

APK 包含上述许可和通知文件。模型与 AAR 保留在源码内，安装后不从网络下载。应用图标及插画为维护者提供的素材；涉及第三方角色与图形的权利仍归原权利人，不将其声明为维护者原创或按代码许可证授权。
