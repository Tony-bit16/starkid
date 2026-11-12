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

目标结构（食材模块已按此落地；菜谱与看板仍为后续）：

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

- `ui/`：Compose 页面、导航、四态组件；食材列表/详情/新增已接 ViewModel 与 Room。
- `data/local/`：Entity、Dao、Database、Relation；数据库版本 2，已加入食材品类主数据与菜谱-食材多对多关联。
- `data/repository/`：`IngredientRepository` 契约 + `DefaultIngredientRepository` 实现（事务、校验、品类复用、异常包装）；`RecipeRepository` 仍只有契约。
- ViewModel：`IngredientListViewModel`、`IngredientDetailViewModel`、`AddIngredientViewModel`，通过 `StateFlow` 暴露四态或表单/操作状态。
- 菜谱列表/详情仍直接引用 `SampleData`，带“骨架演示”提示；看板仍为静态演示。
- `FridgeApplication` 懒加载数据库，并暴露 `ingredientRepository` 单例。

### 2.2 实现约束

- ViewModel 暴露 `StateFlow<UiState<T>>`，UI 用 `collectAsStateWithLifecycle()` 收集。
- 单次结果（保存成功 id、删除成功、保存成功提示）用状态位表达，由页面 `LaunchedEffect` 消费一次；后续若出现并发事件流，再改为 `Channel` / `SharedFlow`。
- Repository 是唯一允许拼装多个 DAO 结果、做业务校验的层。
- UI 不直接调用 Dao，不直接使用 `Cursor`，不持有 `Context` 做数据库操作。
- 主线程不做 IO：Room 的 `suspend` / `Flow` 查询由 Room 的执行器调度；文件、CSV、Provider 查询显式使用 `Dispatchers.IO`。
- `FridgeApplication` 只懒加载数据库与 Repository，不做阻塞初始化。

## 3. 数据模型与关系

```text
IngredientType 1 ──── N Ingredient（库存批次）
       │
       │ 1
       │
       N
RecipeIngredient N ──── 1 Recipe
       │
       └─ 复合主键 (recipe_id, type_id)，表达 Recipe ↔ IngredientType 多对多
```

四张表：

| 表 | 角色 | 关键约束 |
| --- | --- | --- |
| `ingredient_types` | 食材品类主数据（食材字典） | `normalized_name` 唯一；`category` 索引 |
| `ingredients` | 库存批次（一次入库一条） | `type_id` 外键 → 品类（RESTRICT）；`name/category` 为展示快照 |
| `recipes` | 菜谱主表 | `name` 索引 |
| `recipe_ingredients` | 菜谱所需食材关联 | 复合主键 `(recipe_id, type_id)`；双外键；`quantity/unit` |

### 3.1 为什么引入 IngredientType

`Ingredient` 的字段包含入库时间、保质期、数量，这些是“批次”属性，不是食材本身的属性。同名食材可能分多次入库，因此 `ingredients` 天然会有多条同名记录。

菜谱需要的是一个稳定的“食材品类”引用：

- 菜谱可以先于库存存在，不能要求先买过才能建菜谱；
- 菜谱不能绑定到某一批会被吃完/删除的库存；
- 同一菜谱的“番茄”应匹配冰箱里所有番茄批次，而不是某一条。

因此把“食材品类”抽成 `ingredient_types`，`RecipeIngredient` 同时外键到 `recipes` 与 `ingredient_types`，形成真正的多对多；`Ingredient` 也外键到品类，库存与菜谱共享同一套品类身份。

`Ingredient.name/category` 保留为入库时的展示快照，原因：

- 品类改名后，历史入库记录仍能显示当时的名称；
- 列表查询不必每次 JOIN 品类表；
- 快照字段不参与匹配，匹配与统计以 `type_id` 为准。

### 3.2 匹配与到期规则

匹配假设：

- 以 `type_id` 匹配，不做名称/别名的运行时模糊匹配；
- 数量按品类汇总所有批次（`SUM(quantity)`），只做 `> 0` 判断，暂不做单位换算；
- 全部所需食材都有可用库存 → 能做；部分有 → 部分能做，并列出缺失项。

到期时间不落库，统一按：

```text
expiresAt = stored_at + shelf_life_days * 86_400_000L
```

查询使用同一表达式，避免“到期日”和“剩余天数”两份数据不一致。时间戳按 UTC 毫秒存储，展示时使用设备本地时区格式化。

### 3.3 数据库版本与迁移

- 当前 `version = 2`，`exportSchema = true`，schema 输出到 `refrigerator_main/schemas/`。
- v1 → v2 的 `MIGRATION_1_2` 会：
  1. 建 `ingredient_types` 与唯一/分类索引；
  2. 从旧 `ingredients` 与 `recipe_ingredients` 抽取品类（`lower(trim(name))` 规范化，菜谱独有品类记“未分类”）；
  3. 给旧表补可空 `type_id` 并回填；
  4. 重建 `ingredients`（`type_id NOT NULL` + 外键）与 `recipe_ingredients`（复合主键 + 双外键，按 `MIN(rowid)` 去重）；
  5. 重建索引。
- 禁止 `fallbackToDestructiveMigration()`：食材是用户数据，升级必须写 `Migration`。
- 每次改表都提交生成的 schema JSON，并添加 Migration 测试。
- 旧数据同名但分类/单位不同的记录，迁移时按“先出现者”合并；后续可在设置页提供品类合并/别名，不影响本轮表结构。

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
| `content://com.fridge.main.provider/stats/categories` | 分类、品类数、库存总量、临期数 |
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
- 食材模块的实际映射在 `IngredientError.kt`：
  - `IllegalArgumentException` / `NoSuchElementException` → 业务校验/记录不存在的中文；
  - `SQLiteConstraintException` → “数据冲突，请检查食材名称后重试”；
  - `SQLiteException` → “数据库操作失败，请稍后重试”；
  - 其它异常 → 页面兜底文案。
- 重试由 ViewModel 重新订阅 Flow（`retrySignal`）或重新调用查询，不在 Composable 中直接调用 Repository。

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
| Core library desugaring | 2.1.5（`java.time` 在 minSdk 24 可用） |

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

- 菜谱所需食材已改用 `type_id` 关联品类，但品类规范化目前只做 `trim + lower`；别名、同义词与“番茄/西红柿”这类口语差异尚未处理。
- 数量单位不统一（个 / 克 / 毫升），匹配阶段暂不换算。
- 品类合并/改名会影响历史快照展示；改名时是否同步更新 `Ingredient.name/category` 需产品确认。
- 通知调度策略（每天几点、提前几天）尚未定；实现前需确认产品规则。
- 看板与主 App 的 release 签名必须统一，否则 signature 权限会拒绝。
- `stored_at + days * 86400000` 按固定 24 小时计算；若未来支持跨时区/夏令时，需要改为按本地日历日计算。
