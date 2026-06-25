<div align="center">

<img src="ico.png" alt="LuminShade Icon" width="128" height="128" />

# LuminShade · 晦页

**一款专为刷题复盘打造的开源安卓全局悬浮遮罩工具**

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Version](https://img.shields.io/badge/Version-1.1.0-informational.svg)](#)
[![Platform](https://img.shields.io/badge/Platform-Android%209%2B-green.svg)](#)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.22-purple.svg)](#)
<!-- [![No Root](https://img.shields.io/badge/Root-Not%20Required-success.svg)](#) -->
<!-- [![APK Size](https://img.shields.io/badge/Release%20APK-2.65%20MB-lightgrey.svg)](#) -->

</div>

---

## 项目缘起

**晦页**取自书卷意境：「晦」代表遮蔽隐藏，「页」对应习题、试卷页面。

刷题时答案和题干往往同屏显示，靠手挡、靠便利贴遮答案的复盘体验都不够清爽。市面上同类工具要么绑定具体阅读器、要么塞满广告与冗余功能。LuminShade 的核心目标很明确——**在任意应用上层悬浮遮罩**，让你独立思考、按需揭晓答案；需要更精细处理时，也可以用颜色匹配模式只替换遮罩区域内的指定颜色。

## 设计原则

- **极致专一**：只做遮挡与局部颜色替换，不做笔记、搜题、图片管理
- **轻量**：Release APK 约 2.6 MB，依赖仅限 AndroidX / Material Components 等官方库，无任何第三方 SDK
- **极致克制**：基础遮罩仅需 `SYSTEM_ALERT_WINDOW`；颜色匹配模式开启时才请求系统屏幕捕获授权
- **完全离线**：无网络请求、无埋点、无广告、无推送、无统计
- **清冷调性**：低饱和深色 UI，无花哨动效，专注学习场景

## 核心功能

### 全局悬浮遮罩
跨应用悬浮黑色矩形，覆盖 PDF 阅读器、图片相册、网页浏览器、网课 App、电子试卷截图等**所有应用**的答案/解析区域。

### 一键显隐悬浮球
屏幕边缘常驻悬浮球，**单击瞬间切换所有遮罩可见性**——做题时遮挡、对答案时一键揭晓，再次单击恢复遮挡继续下一题。

### 编辑模式与普通模式分离
- **普通模式**：遮罩纯净无装饰，长按任意遮罩可临时透明窥视下方内容，松手即恢复
- **编辑模式**：遮罩出现淡蓝边框，可自由拖动位置、独立调节宽高、单击删除

### 预设布局保存
为不同试卷（数学卷、英语卷、专业课真题等）保存独立的遮罩布局预设，切换时无需重新拖拽。

### 透明度调节
全局滑块控制所有遮罩的不透明度，从纯黑完全遮挡到半透提示。

### 颜色匹配模式
开启后，遮罩在普通模式下保持透明，通过系统屏幕捕获读取遮罩区域下方画面，将匹配颜色替换为目标颜色。支持：

- 选择匹配颜色与替换颜色（`#RRGGBB` / `#AARRGGBB`）
- 进入屏幕取色模式，直接点击屏幕像素作为匹配色或替换色
- 调节匹配容差，适配抗锯齿、压缩、屏幕渲染导致的近似色
- 调节周边改色范围，把匹配像素外一圈指定半径内的像素一起改色
- 随预设保存每个遮罩的颜色匹配参数

> 说明：Android 不允许悬浮窗直接修改其他 App 的真实画面。颜色匹配模式的实现方式是读取屏幕画面后，在遮罩位置叠加一层透明背景的改色结果。

## 交互一览

| 手势 | 行为 |
|------|------|
| **单击悬浮球**（普通模式） | 切换所有遮罩显示/隐藏 |
| **单击悬浮球**（编辑模式） | 新增一个遮罩 |
| **长按悬浮球** | 进入 / 退出编辑模式（球变蓝紫，图标变 ＋） |
| **拖动悬浮球** | 移动至屏幕任意边缘（自动吸附） |
| **拖动遮罩**（编辑模式） | 移动位置 |
| **拖动遮罩右下角 ⇲**（编辑模式） | 独立调节宽高，不锁比例 |
| **点击右上角 ×**（编辑模式） | 删除该遮罩 |
| **长按遮罩**（普通模式） | 临时透明窥视下方答案，松手立即恢复 |

## 颜色匹配使用

1. 在主界面开启「颜色匹配模式」
2. 按系统弹窗授予「开始录制或投射」权限
3. 点击「匹配颜色」/「替换为」手动输入颜色，或点击「屏幕取匹配色」/「屏幕取替换色」
4. 使用屏幕取色时，应用会退到后台并显示一个可拖动的十字取色球
5. 取色期间屏幕其他区域可正常操作，你可以先切到要处理的软件页面，再拖动十字球压到目标像素上
6. 左上角会实时显示当前像素色号、色块和 9×9 放大区域
7. 在左上角预览面板点击「确认」完成取色，点击「取消」退出取色
8. 调整「匹配容差」与「周边改色范围」
9. 进入编辑模式调整遮罩覆盖范围，退出编辑模式后查看改色效果

颜色匹配模式下，遮罩本身在普通模式中是透明的，只显示被替换后的像素；编辑模式仍会显示淡色遮罩、边框、删除按钮和缩放手柄，方便定位。

## 截图

<table>
  <tr>
    <td align="center"><img src="docs/screenshots/main.png" width="240" alt="主界面"/></td>
    <td align="center"><img src="docs/screenshots/edit.png" width="240" alt="编辑模式"/></td>
    <td align="center"><img src="docs/screenshots/usage.png" width="240" alt="实战刷题"/></td>
  </tr>
  <tr>
    <td align="center"><b>主界面</b><br/><sub>权限引导、预设管理、透明度调节</sub></td>
    <td align="center"><b>编辑模式</b><br/><sub>遮罩淡蓝边框、× 删除按钮、悬浮球变 ＋</sub></td>
    <td align="center"><b>实战刷题</b><br/><sub>跨应用悬浮，遮挡答案选项与正确答案</sub></td>
  </tr>
</table>

## 安装

### 从 Releases 安装（推荐）
前往 [Releases](../../releases) 页面下载最新版后安装

### 首次使用配置
1. 打开晦页，按引导前往「**显示在其他应用上层**」设置授权
2. （可选）前往「**电池优化**」设置，将晦页设为「**不优化**」以防被系统进程杀掉
3. 返回主界面，点击「**+ 新建遮罩**」开始使用
4. （可选）开启「**颜色匹配模式**」时，按系统弹窗授予屏幕捕获权限

## 从源码构建

### 环境要求
- JDK 17 或更高（项目使用 Java 21 测试）
- Android SDK Platform 34 + Build Tools 34.0.0
- Gradle 8.4 ~ 8.7

### 构建命令
```bash
git clone https://github.com/buerka/LuminShade.git
cd LuminShade
./gradlew assembleDebug          # 调试版
./gradlew assembleRelease        # 正式版（含混淆压缩）
```

构建产物：
```
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/release/app-release-unsigned.apk
```

## 技术栈

| 项目 | 选型 |
|------|------|
| 语言 | Kotlin 1.9.22 |
| 构建工具 | Gradle 8.4 + Android Gradle Plugin 8.2.2 |
| 编译 SDK | compileSdk 34（targetSdk 34，minSdk 28） |
| JVM Target | Java 8 |
| UI 框架 | 原生 Android View + ViewBinding（无 Compose / 无 DataBinding） |
| 依赖库（共 4 项） | `androidx.core:core-ktx:1.12.0`<br>`androidx.appcompat:appcompat:1.6.1`<br>`com.google.android.material:material:1.11.0`<br>`androidx.constraintlayout:constraintlayout:2.1.4` |
| 持久化 | `SharedPreferences` + 系统自带 `org.json`（零序列化库依赖） |
| Release 打包 | R8 代码混淆 + 资源压缩（`minifyEnabled` + `shrinkResources`） |

**核心实现：**
- `WindowManager` + `TYPE_APPLICATION_OVERLAY` 实现全局悬浮层
- 自定义 `View.onDraw` / `onTouchEvent` 处理拖动、角点缩放、长按 peek
- `Service` + 前台通知（Android 14+ 使用 `FOREGROUND_SERVICE_TYPE_SPECIAL_USE`）保活
- `MediaProjection` + `ImageReader` 捕获屏幕帧，用于颜色匹配模式下的遮罩区域像素替换
- 全程无任何反射、无任何动态加载、无任何后台定时任务

### 项目结构

```
app/src/main/
├── AndroidManifest.xml         悬浮窗、前台服务与屏幕捕获服务声明
├── java/com/luminshade/
│   ├── MainActivity.kt         主界面：权限引导、预设管理、透明度与颜色匹配控制
│   ├── OverlayService.kt       前台服务：管理悬浮层、屏幕捕获与改色帧分发
│   ├── MaskView.kt             遮罩自定义视图：拖动 / 缩放 / 长按 peek / 颜色匹配绘制
│   ├── FloatingBallView.kt     悬浮控制球：边缘吸附 / 短按 / 长按 / 拖动
│   ├── PresetAdapter.kt        预设列表 RecyclerView 适配器
│   ├── PresetManager.kt        预设持久化（SharedPreferences + JSON）
│   └── data/
│       ├── MaskData.kt         单块遮罩数据模型（含颜色匹配参数）
│       └── PresetData.kt       预设数据模型
└── res/                        极简深色主题资源
```

## 隐私声明

晦页**不收集、不上传、不分析任何数据**：

- ❌ 无任何形式的网络请求
- ❌ 无埋点 / 统计 / 行为分析 SDK
- ❌ 无广告 / 推送 / 个性化推荐
- ❌ 不读取存储 / 相机 / 定位 / 通讯录 / 任何用户文件
- ✅ 颜色匹配模式会使用 Android 系统屏幕捕获授权，仅在本机内存中处理当前屏幕帧，不保存、不上传
- ✅ 仅本地保存预设布局（位于应用私有目录的 SharedPreferences）

## 开源协议

本项目基于 [MIT License](LICENSE) 开源。

```
MIT License — 你可以自由使用、修改、分发本软件，
仅需在副本中保留原始版权与许可声明。
```
