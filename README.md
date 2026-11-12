# Fridge 冰箱管家

一个 Kotlin / Android 原生双 App 项目：

| 模块 | APK | 包名 | 作用 |
| --- | --- | --- | --- |
| `refrigerator_main` | 冰箱管家 | `com.fridge.main` | 食材与菜谱管理主 App |
| `dashboard_module` | 食材看板 | `com.fridge.dashboard` | 独立统计看板与 CSV 导出 |

两个模块都是 application，不共享代码与进程。看板后续通过主 App 的只读 ContentProvider 获取统计数据，不会直接打开主 App 私有数据库。

## 当前阶段

**第四轮：食材模块接入 Room，完成本地增删改查持久化。**

已经落地：

- Gradle 双模块工程、版本目录、Gradle Wrapper。
- Room v2：`ingredient_types`、`ingredients`、`recipes`、`recipe_ingredients` 四张表。
- 食材品类主数据与菜谱-食材多对多关联；`Ingredient` 保留名称/分类快照。
- `MIGRATION_1_2`：从 v1 的名称关联迁移到品类外键，含去重与索引重建。
- 食材模块完整分层：`Entity` → `Dao` → `IngredientRepository`（含 `DefaultIngredientRepository`）→ `ViewModel` → Compose UI。
- 食材列表读取 Room Flow，具备加载/空/错误/成功四态与重试；已移除食材静态占位。
- 新增食材表单校验与持久化；食材详情支持编辑、删除，列表随 Room Flow 自动刷新。
- 数据库/写入异常统一转为中文提示；模拟数据库不可读时进入“加载失败 + 重试”错误态，App 不崩溃。
- 主 App 底部导航「食材库 / 菜谱库」、5 个页面与列表 → 详情跳转。
- 通用加载态、空态、错误态布局，统一由 `ScreenStateContent` 应用。
- 菜谱列表/详情仍使用静态 `SampleData`，页面保留“骨架演示”提示。
- 看板 2 个页面仍为静态演示 UI；7 个页面的 UI 原型文档保持有效。

明确尚未实现：

- 菜谱的 Room 读写、菜谱-食材维护、匹配算法。
- 到期通知、运行时通知权限申请与拒绝降级。
- 主 App 只读 ContentProvider。
- 看板读取真实数据、CSV 实际写入。
- 图片、搜索、筛选等增强交互。

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

1. 菜谱 Room 数据、列表/详情接真实数据与“能做 / 部分能做”匹配。
2. 到期提醒（通知渠道 + 权限申请 + 拒绝降级）。
3. 主 App 只读 ContentProvider + signature 权限。
4. 看板接入真实统计与 SAF CSV 导出。
5. 食材搜索、分类筛选、数量快捷调整等增强。

## 本次提交

```text
feat: 接入Room，实现食材本地增删改查持久化
```
