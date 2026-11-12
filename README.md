# Fridge 冰箱管家

一个 Kotlin / Android 原生双 App 项目：

| 模块 | APK | 包名 | 作用 |
| --- | --- | --- | --- |
| `refrigerator_main` | 冰箱管家 | `com.fridge.main` | 食材与菜谱管理主 App |
| `dashboard_module` | 食材看板 | `com.fridge.dashboard` | 独立统计看板与 CSV 导出 |

两个模块都是 application，不共享代码与进程。看板后续通过主 App 的只读 ContentProvider 获取统计数据，不会直接打开主 App 私有数据库。

## 当前阶段

**本轮只完成项目初始化、实体设计与项目骨架，不写业务逻辑。**

已经落地：

- Gradle 双模块工程、版本目录、Gradle Wrapper。
- Room 实体 / Dao / Database 草稿：`Ingredient`、`Recipe`、`RecipeIngredient`。
- 主 App 5 个页面与列表 → 详情导航；看板 2 个页面。
- 加载 / 空 / 错误 / 成功四态 UI 基础组件。
- MPAndroidChart 饼图、折线图组件（静态示意数据）。
- 需求清单、页面清单、架构说明、目录结构文档。

明确尚未实现：

- ViewModel / Repository 业务层。
- 新增食材保存、编辑、删除。
- 菜谱匹配、到期通知、权限运行时申请。
- 主 App 只读 ContentProvider。
- 看板读取真实数据、CSV 实际写入。

页面上的橙色“骨架演示”条用于区分静态占位数据，接入真实数据后应删除对应提示。

## 技术栈

- Kotlin `2.2.10`
- Android Gradle Plugin `8.13.2` / Gradle `8.13`
- Jetpack Compose（Material 3）+ Navigation Compose
- Room `2.8.5` + KSP
- Kotlin Coroutines `1.10.2`
- MPAndroidChart `v3.1.0`（JitPack）
- minSdk 24 / compileSdk 36（minor 1）/ Java 17

## 目录入口

- [需求清单与页面清单](docs/REQUIREMENTS.md)
- [架构与关键决策](docs/ARCHITECTURE.md)
- [项目目录结构](docs/PROJECT_STRUCTURE.md)

## 构建

环境要求：

- JDK 17 或 21（本机使用 Android Studio 自带 JBR 21）
- Android SDK Platform 36.1（对应 AGP 的 `compileSdk = 36` + `compileSdkMinor = 1`）
- Android SDK Build Tools 36.0.0
- 首次构建需要网络下载 Gradle 8.13 与依赖

```powershell
$env:JAVA_HOME = "D:\Android Studio\jbr"
$env:ANDROID_HOME = "D:\androidstudio"
# 本机用户名含中文，Gradle Wrapper 解析默认缓存路径会乱码；使用纯 ASCII 路径规避。
$env:GRADLE_USER_HOME = "D:\Fridge\.gradle-user-home"

.\gradlew.bat :refrigerator_main:assembleDebug :dashboard_module:assembleDebug
```

产物：

```text
refrigerator_main/build/outputs/apk/debug/refrigerator_main-debug.apk
dashboard_module/build/outputs/apk/debug/dashboard_module-debug.apk
```

也可以直接用 Android Studio 打开仓库根目录 `D:\Fridge`，等待 Gradle Sync 后运行两个 application 配置。

## 下一阶段建议顺序

1. Repository + ViewModel + 主 App 食材 CRUD（含四态与表单校验）。
2. 菜谱数据与“能做 / 部分能做”匹配。
3. 到期提醒（通知渠道 + 权限申请 + 拒绝降级）。
4. 主 App 只读 ContentProvider + signature 权限。
5. 看板接入真实统计与 SAF CSV 导出。

## 本次提交

```text
feat: 项目初始化，完成实体设计与需求清单
```
