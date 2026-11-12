# Fridge 冰箱管家

一个 Kotlin / Android 原生双 App 项目：

| 模块 | APK | 包名 | 作用 |
| --- | --- | --- | --- |
| `refrigerator_main` | 冰箱管家 | `com.fridge.main` | 食材与菜谱管理主 App |
| `dashboard_module` | 食材看板 | `com.fridge.dashboard` | 独立统计看板与 CSV 导出 |

两个模块都是 application，不共享代码与进程。看板后续通过主 App 的只读 ContentProvider 获取统计数据，不会直接打开主 App 私有数据库。

## 当前阶段

**第三轮：搭建主 App 导航骨架与基础页面 UI 框架。只做 UI 与页面跳转，不接 Room、不写业务逻辑。**

已经落地：

- Gradle 双模块工程、版本目录、Gradle Wrapper。
- Room v2：`ingredient_types`、`ingredients`、`recipes`、`recipe_ingredients` 四张表。
- 食材品类主数据与菜谱-食材多对多关联；`Ingredient` 保留名称/分类快照。
- `MIGRATION_1_2`：从 v1 的名称关联迁移到品类外键，含去重与索引重建。
- `IngredientRepository` / `RecipeRepository` 接口草稿与表单 DTO（无实现）。
- 主 App 底部导航「食材库 / 菜谱库」、5 个页面骨架与列表 → 详情跳转。
- 通用加载态、空态、错误态布局，统一由 `ScreenStateContent` 应用到数据页面。
- 主 App 5 个页面、看板 2 个页面的静态 UI 与四态组件。
- 7 个页面的 UI 原型说明（布局、交互、状态、跳转）。

明确尚未实现：

- Repository / ViewModel 的具体实现。
- 页面接入真实数据；页面仍展示 `SampleData`。
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
- [数据模型与迁移](docs/DATA_MODEL.md)
- [MVVM 分层契约](docs/MVVM_LAYER_DESIGN.md)
- [页面 UI 原型](docs/UI_PROTOTYPE.md)
- [项目目录结构](docs/PROJECT_STRUCTURE.md)

## 构建

环境要求：

- JDK 17 或 21（本机使用 Android Studio 自带 JBR 21）
- Android SDK Platform 36.1（对应 AGP 的 `compileSdk = 36` + `compileSdkMinor = 1`）
- Android SDK Build Tools 36.0.0
- 首次构建需要网络下载 Gradle 8.13 与依赖；Wrapper 已配置阿里云镜像，避免从 `services.gradle.org` 慢速下载

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

1. Repository 实现 + 主 App ViewModel + 食材 CRUD（含四态与表单校验）。
2. 菜谱数据与“能做 / 部分能做”匹配。
3. 到期提醒（通知渠道 + 权限申请 + 拒绝降级）。
4. 主 App 只读 ContentProvider + signature 权限。
5. 看板接入真实统计与 SAF CSV 导出。

## 本次提交

```text
feat: 搭建主App导航骨架，完成基础页面UI框架
```
