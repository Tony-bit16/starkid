# 架构与关键决策

## 1. 目标与边界

项目由两个完全独立的 APK 组成：

```text
┌─────────────────────────────┐        ┌──────────────────────────────┐
│ refrigerator_main           │        │ dashboard_module             │
│ com.fridge.main             │        │ com.fridge.dashboard         │
│                             │        │                              │
│ Compose UI                  │        │ Compose UI                   │
│ ViewModel（后续）            │        │ ViewModel（后续）             │
│ Repository（后续）           │        │ StatsRepository（后续）       │
│ Room DAO / fridge.db        │        │ ContentResolver              │
│        │                    │        │        │                     │
│        └── 只读 ContentProvider ◄─────┼────────┘                     │
│            signature 权限    │        │                              │
└─────────────────────────────┘        └──────────────────────────────┘
```

- 主 App 是唯一写入方，持有 Room 数据库。
- 看板不直接打开 `fridge.db`，不使用 `sharedUserId`，不复制数据库文件。
- 两个模块不共享 Gradle module / 源码；跨 App 边界只有 ContentProvider 契约。

## 2. 主 App 分层

目标结构（本轮只落地 UI 与 data/local 骨架）：

```text
UI（Compose Screen）
  │  收集 StateFlow<UiState<T>>
  ▼
ViewModel
  │  调用 suspend / Flow
  ▼
Repository
  │  业务校验、时间计算、匹配、错误映射
  ▼
DAO（Room）
  │  suspend / Flow，Room 自动在非主线程执行
  ▼
SQLite（fridge.db）
```

### 2.1 本轮现状

- `ui/`：Compose 页面、导航、四态组件、静态展示模型。
- `data/local/`：Entity、Dao、Database、Relation。
- 没有 ViewModel / Repository；页面直接引用 `SampleData`，所有列表均带“骨架演示”提示。

### 2.2 后续实现约束

- ViewModel 暴露 `StateFlow<UiState<T>>`，UI 用 `collectAsStateWithLifecycle()` 收集。
- 一次性事件（导航、Snackbar）使用 `Channel` / `SharedFlow`，不要放进 `UiState`。
- Repository 是唯一允许拼装多个 DAO 结果、做业务校验的层。
- UI 不直接调用 Dao，不直接使用 `Cursor`，不持有 `Context` 做数据库操作。
- 主线程不做 IO：Room 的 `suspend` / `Flow` 查询由 Room 的执行器调度；文件、CSV、Provider 查询显式使用 `Dispatchers.IO`。
- `FridgeApplication` 只懒加载数据库，不做阻塞初始化。

## 3. 数据模型与关系

```text
Recipe 1 ──── N RecipeIngredient
                     │
                     │ ingredient_name（逻辑匹配，不设外键）
                     ▼
                 Ingredient（库存，可多条同名）
```

### 3.1 为什么 RecipeIngredient 不外键到 Ingredient

菜谱是“做法模板”，可以先于库存存在。若 `recipe_ingredients.ingredient_name` 外键到 `ingredients.name`：

- 新增菜谱前必须先有对应库存，业务上不合理。
- 同名食材有多条库存记录，外键目标不唯一。
- 删除最后一条库存会破坏菜谱。

因此只在 `recipe_id` 上设外键（级联删除），食材名称在匹配时做规范化比较。匹配假设：

- 名称去首尾空格后精确匹配；暂不做别名、同义词。
- 数量只做 `> 0` 判断，暂不做单位换算。
- 全部所需食材都有库存 → 能做；部分有 → 部分能做，并列出缺失项。

### 3.2 到期时间

不落库，统一按：

```text
expiresAt = stored_at + shelf_life_days * 86_400_000L
```

查询使用同一表达式，避免“到期日”和“剩余天数”两份数据不一致。时间戳按 UTC 毫秒存储，展示时使用设备本地时区格式化。

### 3.3 数据库版本

- 当前 `version = 1`，`exportSchema = true`，schema 输出到 `refrigerator_main/schemas/`。
- 禁止 `fallbackToDestructiveMigration()`：食材是用户数据，升级必须写 `Migration`。
- 每次改表都提交生成的 schema JSON，并添加 Migration 测试。

## 4. 跨 App 数据契约（后续实现）

### 4.1 权限

主 App Manifest：

```xml
<permission
    android:name="com.fridge.main.permission.READ_STATS"
    android:protectionLevel="signature" />

<provider
    android:name=".provider.StatsProvider"
    android:authorities="com.fridge.main.provider"
    android:exported="true"
    android:readPermission="com.fridge.main.permission.READ_STATS" />
```

看板 Manifest：

```xml
<uses-permission android:name="com.fridge.main.permission.READ_STATS" />
```

- `signature` 权限要求两个 APK 使用同一签名证书；debug 构建使用同一台机器的 debug keystore 即可，release 必须配置统一签名。
- Provider 只实现 `query`，`insert/update/delete` 抛 `UnsupportedOperationException`。
- 不建议 `android:grantUriPermissions="true"` 之外再暴露写权限。

### 4.2 URI 与字段（草案）

| URI | 内容 |
| --- | --- |
| `content://com.fridge.main.provider/stats/summary` | 食材品种数、临期数、过期数 |
| `content://com.fridge.main.provider/stats/categories` | 分类、品种数、库存总量、临期数 |
| `content://com.fridge.main.provider/stats/expiring` | 临期/过期食材的聚合行 |

约束：

- 固定 projection 白名单，拒绝任意列名拼接 SQL。
- `selection` 只允许固定参数（如 `days`），不把用户输入直接拼进 SQL。
- 返回 `MatrixCursor` 的列名作为稳定契约；改名视为 breaking change。
- Provider 的 `query()` 在 Binder 线程执行，内部通过 Repository 查询 Room；不要在主线程执行。
- 看板对 `SecurityException` 做降级：提示“请安装/更新冰箱管家并保持同一签名”，不崩溃。

### 4.3 为什么不用 sharedUserId

`sharedUserId` 已废弃，且会让两个 APK 共享 UID、私有目录与权限边界，安全性和升级兼容性都差。Provider + signature 权限是更明确的边界。

## 5. 状态与错误处理

统一使用四态：

```kotlin
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data object Empty : UiState<Nothing>
    data class Error(val message: String) : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
}
```

- `Empty` 与 `Success(emptyList())` 区分：空态需要引导文案，列表为空不等于错误。
- `Error.message` 必须是可直接展示的中文，不把异常堆栈抛给用户。
- 错误映射建议：
  - `SQLiteException` → “本地数据读取失败，请重试”。
  - `SecurityException` → “没有读取权限，请检查应用签名/权限”。
  - `IOException` → “文件读写失败，请更换位置后重试”。
- 重试由 ViewModel 重新触发查询，不在 Composable 中直接调用 Repository。

## 6. 权限与降级

### 6.1 通知

计划使用 WorkManager 每日检查到期食材，创建通知渠道；不引入精确闹钟权限。

- Android 13+：先申请 `POST_NOTIFICATIONS`。
- 拒绝：不崩溃、不影响食材 CRUD；首页/设置页展示“到期提醒已关闭”的提示。
- 通知渠道被用户关闭：同样按拒绝处理。
- 本轮只声明权限，未创建渠道、未申请、未调度。

### 6.2 文件导出

- 使用 SAF `CreateDocument("text/csv")`，由系统授予单次写权限。
- 不申请存储权限，不使用 `MANAGE_EXTERNAL_STORAGE`。
- 用户取消 → 静默返回；写入异常 → 可重试的错误提示。
- CSV 使用 UTF-8；建议带 BOM 以兼容 Excel 中文，具体在导出模块确定并测试。

## 7. 构建与依赖

| 项 | 版本 |
| --- | --- |
| AGP | 8.13.2 |
| Gradle | 8.13 |
| Kotlin | 2.2.10 |
| KSP | 2.2.10-2.0.2 |
| Compose BOM | 2026.02.01 |
| Room | 2.8.5 |
| Navigation Compose | 2.9.8 |
| MPAndroidChart | v3.1.0 |
| minSdk / compileSdk | 24 / 36（minor 1） |
| Build Tools | 36.0.0 |
| Java / JVM target | 17 |

依赖仓库：Aliyun 镜像优先，google/mavenCentral 兜底；JitPack 只对 `com.github.PhilJay` 开放，避免其他依赖误走 JitPack。

## 8. 测试策略（后续）

- DAO：内存 Room 数据库，覆盖插入、到期区间查询、关联查询、级联删除。
- Repository：用 fake DAO 测试校验、到期天数、匹配结果。
- ViewModel：`kotlinx-coroutines-test` + Turbine 验证四态顺序。
- Compose：对 Loading / Empty / Error / Success 做 UI 测试；列表点击触发正确路由。
- Provider：`ProviderTestRule` 验证权限、projection 白名单、Cursor 列名。
- CSV：字段转义、逗号/引号/换行、中文编码、空数据。
- Migration：`MigrationTestHelper` 验证 v1 → v2。

## 9. 已知风险 / 待确认

- 菜谱所需食材目前按名称精确匹配，后续可能需要别名表或“食材字典”。
- 数量单位不统一（个 / 克 / 毫升），匹配阶段暂不换算。
- 通知调度策略（每天几点、提前几天）尚未定；实现前需确认产品规则。
- 看板与主 App 的 release 签名必须统一，否则 signature 权限会拒绝。
- `stored_at + days * 86400000` 按固定 24 小时计算；若未来支持跨时区/夏令时，需要改为按本地日历日计算。
