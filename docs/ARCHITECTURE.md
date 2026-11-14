# 架构与关键决策

## 1. 目标与边界

项目由两个完全独立的 APK 组成：

```text
┌─────────────────────────────┐        ┌──────────────────────────────┐
│ refrigerator_main           │        │ dashboard_module             │
│ com.fridge.main             │        │ com.fridge.dashboard         │
│                             │        │                              │
│ Compose UI                  │        │ Compose UI                   │
│ ViewModel                   │        │ ViewModel（后续）             │
│ Repository                  │        │ StatsRepository（后续）       │
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

目标结构（食材与菜谱模块均已按此落地；看板仍为后续）：

```text
UI（Compose Screen）
  │  收集 StateFlow<UiState<T>>
  ▼
ViewModel
  ├─ 调用 suspend / Flow ──► Repository
  │                             │  业务校验、时间计算、错误映射
  │                             ▼
  │                          DAO（Room）
  │                             │  suspend / Flow，Room 自动在非主线程执行
  │                             ▼
  │                          SQLite（fridge.db）
  │
  └─ 调用纯函数 ──────────► Domain（RecipeMatcher）
```

### 2.1 本轮现状

- `ui/`：Compose 页面、导航、四态组件；食材、本地菜谱、在线搜索/详情均已接 ViewModel。
- `domain/matching/`：纯 Kotlin 的 `RecipeMatcher`，只依赖 `MatchableRecipe` / 品类 id 集合，不依赖 Room、Compose 或 Android。
- `data/local/`：Entity、Dao、Database、Relation；数据库版本 2，已加入食材品类主数据与菜谱-食材多对多关联。
- `data/remote/`：Retrofit API、DTO、Mapper、`NetworkResult` 与 `BaseRemoteDataSource`；只由在线菜谱 Repository 使用。
- `data/repository/`：`DefaultIngredientRepository`、`DefaultRecipeRepository`、`DefaultOnlineRecipeRepository`，在 Repository 层完成事务、校验、品类复用、网络异常包装。
- ViewModel：食材 3 个、本地菜谱 3 个、在线搜索/详情 2 个，通过 `StateFlow` 暴露四态或表单/操作状态；菜谱列表/详情额外观察库存 Flow 并调用匹配算法。
- 菜谱新增/编辑共用 `RecipeForm`，食材清单按行动态增删；菜谱详情通过 Room Flow 自动刷新。
- 在线菜谱详情保存时复用 `RecipeRepository.saveRecipe`，本地列表由同一个 Room Flow 自动刷新。
- 看板仍为静态演示。
- `FridgeApplication` 懒加载数据库与 Repository，并暴露 `ingredientRepository`、`recipeRepository`、`onlineRecipeRepository` 单例。

### 2.2 实现约束

- ViewModel 暴露 `StateFlow<UiState<T>>`，UI 用 `collectAsStateWithLifecycle()` 收集。
- 单次结果（保存成功 id、删除成功、保存成功提示）用状态位表达，由页面 `LaunchedEffect` 消费一次；后续若出现并发事件流，再改为 `Channel` / `SharedFlow`。
- Repository 是唯一允许拼装多个 DAO 结果、做业务校验的层。
- UI 不直接调用 Dao，不直接使用 `Cursor`，不持有 `Context` 做数据库操作。
- 主线程不做 IO：Room 的 `suspend` / `Flow` 查询由 Room 的执行器调度；网络统一经 `safeApiCall` 切到 `Dispatchers.IO`；文件、CSV、Provider 查询也显式使用 `Dispatchers.IO`。
- 匹配是 CPU 计算，菜谱列表与详情在 `Dispatchers.Default` 执行 `RecipeMatcher`；不阻塞主线程，也不放进 Repository。
- 匹配只依赖 `type_id` 与库存数量 > 0，不修改任何数据；推荐列表与详情是同一算法的两种投影，避免两套口径。
- 网络层不向上抛 Retrofit/OkHttp 异常：`BaseRemoteDataSource` 统一转换为 `NetworkResult.Failure(message)`，ViewModel 只消费中文文案。
- 网络失败不改变本地数据：在线搜索/详情失败只影响当前页，本地菜谱库仍可读取 Room。
- `FridgeApplication` 只懒加载数据库与 Repository，不做阻塞初始化。

### 2.3 在线菜谱数据流

```text
OnlineRecipeListScreen / OnlineRecipeDetailScreen
        │ 收集 OnlineSearchUiState / OnlineRecipeDetailUiState
        ▼
OnlineRecipeListViewModel / OnlineRecipeDetailViewModel
        │ suspend 搜索、详情、保存
        ▼
DefaultOnlineRecipeRepository
        ├─ RecipeApi（TheMealDB search.php / lookup.php）
        │     └─ safeApiCall：超时/断网/HTTP/解析异常 → NetworkResult.Failure
        └─ RecipeRepository.saveRecipe（保存时复用本地事务）
              └─ Room recipes / recipe_ingredients
```

- 搜索与详情使用独立接口：搜索结果只携带列表所需字段，进入详情后重新 lookup，避免把临时列表当作唯一数据源。
- 在线详情“保存到本地”先由 `MealMapper` 转成 `RecipeDraft`，再走本地菜谱的校验与事务；因此保存后的数据与手动新增的菜谱完全同构。
- 保存成功后不手动刷新本地列表：`RecipeListViewModel` 仍订阅 `observeRecipes()`，Room Flow 会自动推送。
- 搜索请求会取消上一次协程，并在响应返回时比对当前关键词，防止慢响应覆盖新结果。

### 2.4 食材匹配数据流

```text
RecipeListViewModel / RecipeDetailViewModel
        │ combine(recipeRepository.observeRecipes(),
        │         ingredientRepository.observeIngredients())
        ▼
availableTypeIds = inventory.filter(quantity > 0).map(typeId).toSet()
        │ flowOn(Dispatchers.Default)
        ▼
RecipeMatcher.matchAll / evaluate（纯函数）
        │  RecipeMatchLevel + available/missing 名称
        ▼
RecipeListUiState / RecipeDetailUiState
        │
        ├─ 列表：等级标签、已备齐 N/M、缺少食材
        └─ 详情：已有/缺少高亮、匹配徽标
```

- 菜谱与库存任一变化都会触发重算；保存/删除菜谱、增删食材后无需手动刷新页面。
- 列表“全部菜谱”不计算匹配；只有“推荐菜谱”调用 `matchAll`，避免无意义 CPU 开销。
- `RecipeMatcher` 用 `level.ordinal → missing.size → recipeName` 稳定排序；空食材菜谱按“缺少多种食材”处理。
- 匹配只判断品类是否有库存，不比较数量；数量/单位换算与临期优先级留到后续。

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

匹配假设与规则（由 `RecipeMatcher` 实现）：

- 以 `type_id` 匹配，不做名称/别名的运行时模糊匹配；
- 库存按品类判断是否存在数量 `> 0` 的批次，不汇总数量、不做单位换算；
- 全部所需食材都有库存 → `FULLY_COOKABLE`（完全可制作）；
- 只缺 1 种 → `MISSING_ONE`（缺少 1 种食材）；
- 缺 2 种及以上 → `MISSING_MANY`（缺少多种食材），并列出全部缺失名称；
- 空食材菜谱按 `MISSING_MANY` 处理，避免被误判为可制作；
- 排序：等级优先，同等级缺少越少越靠前，再按菜名稳定排序。

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
- 实际映射：
  - 本地：`IngredientError.kt` 与 `RecipeError.kt` 处理校验、记录不存在、SQLite 约束/异常；
  - 网络：`NetworkResult.kt` 处理超时、DNS、连接失败、HTTP 4xx/5xx、JSON 解析失败，统一输出中文。
- 在线搜索/详情失败时同时展示错误页与一次性弹窗：错误页保留“重试”，弹窗明确说明“本地菜谱仍可正常使用”。
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
| Retrofit / OkHttp / Gson | 2.11.0 / 4.12.0 / 2.11.0 |
| Navigation Compose | 2.9.8 |
| MPAndroidChart | v3.1.0 |
| minSdk / compileSdk | 24 / 36（minor 1） |
| Build Tools | 36.0.0 |
| Java / JVM target | 17 |
| Core library desugaring | 2.1.5（`java.time` 在 minSdk 24 可用） |

依赖仓库：Aliyun 镜像优先，google/mavenCentral 兜底；JitPack 只对 `com.github.PhilJay` 开放，避免其他依赖误走 JitPack。

## 8. 测试策略（后续）

- DAO：内存 Room 数据库，覆盖插入、到期区间查询、关联查询、级联删除。
- Repository：用 fake DAO 测试校验、到期天数、品类复用。
- Domain：`RecipeMatcherTest` 覆盖三级分类、稳定排序、已有/缺少名称、空库存、空菜谱。
- ViewModel：`kotlinx-coroutines-test` + Turbine 验证四态顺序。
- Compose：对 Loading / Empty / Error / Success 做 UI 测试；列表点击触发正确路由。
- Provider：`ProviderTestRule` 验证权限、projection 白名单、Cursor 列名。
- CSV：字段转义、逗号/引号/换行、中文编码、空数据。
- Migration：`MigrationTestHelper` 验证 v1 → v2。

## 9. 已知风险 / 待确认

- 在线菜谱依赖 TheMealDB 免费测试接口：无 SLA、英文菜名为主、字段可能变化；生产环境应换成有服务保障的 API 或加缓存/代理。
- TheMealDB 不提供单个食材分类，保存到本地时统一写“未分类”；在线用量是自由文本，解析失败时按“1 + 适量”保存。
- 菜谱所需食材已改用 `type_id` 关联品类，但品类规范化目前只做 `trim + lower`；别名、同义词与“番茄/西红柿”这类口语差异尚未处理。
- 数量单位不统一（个 / 克 / 毫升），匹配阶段暂不换算。
- 匹配只判断“品类有库存”，不判断数量是否够：例如菜谱要 3 个鸡蛋、冰箱只有 1 个，仍会显示“完全可制作”；这是当前明确的业务边界。
- 菜谱列表推荐需要加载全部菜谱与库存并在 `Dispatchers.Default` 计算；数据量很大时应改为分页/增量计算。
- 品类合并/改名会影响历史快照展示；改名时是否同步更新 `Ingredient.name/category` 需产品确认。
- 通知调度策略（每天几点、提前几天）尚未定；实现前需确认产品规则。
- 看板与主 App 的 release 签名必须统一，否则 signature 权限会拒绝。
- `stored_at + days * 86400000` 按固定 24 小时计算；若未来支持跨时区/夏令时，需要改为按本地日历日计算。
