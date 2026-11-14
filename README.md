# Fridge 冰箱管家

一个 Kotlin / Android 原生双 App 项目：

| 模块 | APK | 包名 | 作用 |
| --- | --- | --- | --- |
| `refrigerator_main` | 冰箱管家 | `com.fridge.main` | 食材与菜谱管理主 App |
| `dashboard_module` | 食材看板 | `com.fridge.dashboard` | 独立统计看板与 CSV 导出 |

两个模块都是 application，不共享代码与进程。看板后续通过主 App 的只读 ContentProvider 获取统计数据，不会直接打开主 App 私有数据库。

## 当前阶段

**第九轮：主 App 全链路联调，统一交互规范与异常处理。**

已经落地：

- Gradle 双模块工程、版本目录、Gradle Wrapper。
- Room v2：`ingredient_types`、`ingredients`、`recipes`、`recipe_ingredients` 四张表。
- 食材品类主数据与菜谱-食材多对多关联；`Ingredient` 保留名称/分类快照。
- `MIGRATION_1_2`：从 v1 的名称关联迁移到品类外键，含去重与索引重建。
- 食材模块完整分层：`Entity` → `Dao` → `IngredientRepository`（含 `DefaultIngredientRepository`）→ `ViewModel` → Compose UI。
- 食材列表读取 Room Flow，具备加载/空/错误/成功四态与重试；已移除食材静态占位。
- 新增食材表单校验与持久化；食材详情支持编辑、删除，列表随 Room Flow 自动刷新。
- 菜谱模块完整分层：`DefaultRecipeRepository` + 3 个 ViewModel，列表/详情/新增全部接 Room。
- 新增菜谱支持菜名、动态食材清单与多行做法；菜谱详情支持编辑、删除，列表随 Room Flow 自动刷新。
- 菜谱所需食材关联到 `ingredient_types`，允许“菜谱先于库存存在”；重复食材名会被拒绝。
- 新增在线菜谱：Retrofit + OkHttp + Gson，接入 TheMealDB 搜索/详情接口。
- 在线搜索/详情具备加载、空、错误与重试；网络失败弹出提示，本地菜谱仍可正常使用。
- 在线详情支持“保存到本地”，转换食材与步骤后写入 Room，本地菜谱列表自动刷新。
- 数据库/网络异常统一转为中文提示；模拟数据库不可读时进入“加载失败 + 重试”错误态，App 不崩溃。
- 纯算法类 `RecipeMatcher`：按「完全可制作 / 缺少 1 种 / 缺少多种」分级并降序排序，配套 JVM 单测。
- 菜谱库「全部菜谱 / 推荐菜谱」筛选；推荐卡片展示等级、已备齐数量与完整缺少食材名称。
- 菜谱详情按库存标绿“已有”、标红“缺少”；库存变化后匹配结果随 Room Flow 自动刷新。
- 匹配计算在 `Dispatchers.Default` 执行；无库存、无本地菜谱、无匹配结果均有对应空态与下一步入口。
- WorkManager 每日 09:00 检查库存，`ExpiryCalculator` 统一按 0..3 天判定临期；过期不进入提醒。
- 临期食材合并为一条通知，点击通过 `MainActivity` 信号跳转食材库；无临期时自动取消旧通知。
- Android 13+ 通知权限说明、系统申请、拒绝/永久拒绝降级与“去设置”入口；未授权仍可查看食材状态。
- 食材列表按「新鲜 / 临期 / 已过期」显示绿 / 橙 / 红三色标签，阈值与通知共用同一计算口径。
- 主 App 底部导航「食材库 / 菜谱库 / 在线搜索」、8 个主 App 页面与列表 → 详情跳转。
- 通用加载态、空态、错误态布局，统一由 `ScreenStateContent` 应用。
- 食材、菜谱、在线搜索结果三个列表接入 Material3 下拉刷新；刷新保留旧内容，完成后自动收起。
- 页面跳转统一走 `safeNavigate`/`safePopBackStack`，异常进入根 Scaffold 的 Snackbar；数据库/网络异常继续由页面四态兜底。
- 新增/新增菜谱首次提交后开启即时校验，字段修正后错误提示立即消失；在线搜索限制 40 字并即时提示空输入。
- 主题补充统一 `Shapes`（6/10/14/18/24dp），卡片、输入框、按钮圆角与间距一致。
- 看板 2 个页面仍为静态演示 UI；10 个页面的 UI 原型文档保持有效。

明确尚未实现：

- 按库存数量是否足够、临期食材优先级进行匹配；当前只判断食材品类是否有库存。
- 主 App 只读 ContentProvider。
- 看板读取真实数据、CSV 实际写入。
- 图片、本地菜谱搜索、分类筛选等增强交互。

## 技术栈

- Kotlin `2.2.10`
- Android Gradle Plugin `8.13.2` / Gradle `8.13`
- Jetpack Compose（Material 3）+ Navigation Compose
- Room `2.8.5` + KSP
- Retrofit `2.11.0` + OkHttp `4.12.0` + Gson `2.11.0`（在线菜谱）
- WorkManager `2.10.1`（保质期定时检查）
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

1. 主 App 只读 ContentProvider + signature 权限。
2. 看板接入真实统计与 SAF CSV 导出。
3. 匹配增强：库存数量是否足够、临期食材优先。
4. 本地菜谱搜索、分类筛选、图片与数量快捷调整等增强。

## 本次提交

```text
feat: 主App全流程联调，完善异常处理与交互体验
```
