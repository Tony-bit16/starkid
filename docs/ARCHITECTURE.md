# 架构与关键决策

## 1. 目标与边界

项目由两个完全独立的 APK 组成：

```text
┌─────────────────────────────┐        ┌──────────────────────────────┐
│ refrigerator_main           │        │ dashboard_module             │
│ com.fridge.main             │        │ com.fridge.dashboard         │
│                             │        │                              │
│ Compose UI                  │        │ Compose UI                   │
│ ViewModel                   │        │ ViewModel                   │
│ Repository                  │        │ DashboardRepository          │
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

目标结构（主 App 食材/菜谱模块、看板数据通道与统计饼图均已落地）：

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
- `ui/common/AppErrorHandler`：页面跳转等可恢复 UI 异常的错误总线；`safeNavigate`/`safePopBackStack` 捕获导航异常，根 Scaffold 用 Snackbar 统一提示。
- `ui/components/RefreshableContent`：统一的 Material3 下拉刷新容器，只包裹已有内容的列表；刷新状态由各列表 ViewModel 独立暴露。
- `domain/matching/`：纯 Kotlin 的 `RecipeMatcher`，只依赖 `MatchableRecipe` / 品类 id 集合，不依赖 Room、Compose 或 Android。
- `domain/expiry/`：`ExpiryCalculator` 统一到期日、剩余天数与可配置 warningDays 口径；UI 与 Worker 共用，避免两套边界。
- `notification/`：通知渠道、合并通知、待处理 Intent 与权限可用性检查；不持有 Activity/ViewModel。
- `work/`：`ExpiryCheckWorker` 负责后台查询与通知，`ExpiryReminderScheduler` 负责唯一周期任务；Worker 通过 `IngredientRepository` 查询，并通过 `SettingsRepository` 读取提前天数与通知开关。
- `data/local/`：Entity、Dao、Database、Relation；数据库版本 2，已加入食材品类主数据与菜谱-食材多对多关联。
- `data/remote/`：Retrofit API、DTO、Mapper、`NetworkResult` 与 `BaseRemoteDataSource`；只由在线菜谱 Repository 使用。
- `data/repository/`：`DefaultIngredientRepository`、`DefaultRecipeRepository`、`DefaultOnlineRecipeRepository`，在 Repository 层完成事务、校验、品类复用、网络异常包装。
- ViewModel：食材 3 个、本地菜谱 3 个、在线搜索/详情 2 个，通过 `StateFlow` 暴露四态或表单/操作状态；菜谱列表/详情额外观察库存 Flow 并调用匹配算法。
- 菜谱新增/编辑共用 `RecipeForm`，食材清单按行动态增删；菜谱详情通过 Room Flow 自动刷新。
- 在线菜谱详情保存时复用 `RecipeRepository.saveRecipe`，本地列表由同一个 Room Flow 自动刷新。
- 看板已接真实数据通道：`DashboardHomeViewModel` → `DashboardRepository` → `IngredientProviderClient` → `ContentResolver`；读取后在 `domain/stats/DashboardStatsCalculator` 聚合，主页用三张指标卡 + MPAndroidChart 饼图 + 未来 7 天趋势折线图展示；Repository 提供 60 秒进程级缓存与手动强制刷新。
- 看板 CSV 导出：`DashboardHomeViewModel.exportCsv()` 重新读取 Provider，交给 `DashboardCsvFormatter`（纯格式转换）与 `AndroidDashboardCsvExporter`（cache + MediaStore/旧版文件 + FileProvider URI）；导出状态独立于页面四态。
- `FridgeApplication` 懒加载数据库与 Repository，并暴露 `ingredientRepository`、`recipeRepository`、`onlineRecipeRepository`、`settingsRepository` 单例；`onCreate` 创建 `expiry_reminder` 渠道，读取 DataStore 首个配置后按用户时间 `ensureScheduled`。
- `data/settings/`：`AppSettings` 值对象 + `SettingsRepository` 契约 + `DefaultSettingsRepository`；Preferences DataStore 文件名 `fridge_settings`，页面与 Worker 都只依赖 Repository。
- `notification/` 与 `work/` 不依赖 ViewModel：`ExpiryCheckWorker` 通过 `IngredientRepository.getAllIngredients()` 读取库存，按 `ExpiryCalculator` 筛选后调用 `ExpiryNotifications`。

### 2.2 实现约束

- ViewModel 暴露 `StateFlow<UiState<T>>`，UI 用 `collectAsStateWithLifecycle()` 收集。
- 单次结果（保存成功 id、删除成功、保存成功提示）用状态位表达，由页面 `LaunchedEffect` 消费一次；后续若出现并发事件流，再改为 `Channel` / `SharedFlow`。
- Repository 是唯一允许拼装多个 DAO 结果、做业务校验的层。
- UI 不直接调用 Dao，不直接使用 `Cursor`，不持有 `Context` 做数据库操作。
- 主线程不做 IO：Room 的 `suspend` / `Flow` 查询由 Room 的执行器调度；网络统一经 `safeApiCall` 切到 `Dispatchers.IO`；文件、CSV、Provider 查询也显式使用 `Dispatchers.IO`。
- 匹配与看板统计是 CPU 计算，菜谱列表/详情在 `Dispatchers.Default` 执行 `RecipeMatcher`，看板在 `Dispatchers.Default` 执行 `DashboardStatsCalculator`；不阻塞主线程，也不放进 Repository。
- 匹配优先按 `type_id`，历史数据出现“同名不同 typeId”时再用规范化名称兜底；只把库存数量 > 0 的品类视为已有，不修改任何数据。
- 网络层不向上抛 Retrofit/OkHttp 异常：`BaseRemoteDataSource` 统一转换为 `NetworkResult.Failure(message)`，ViewModel 只消费中文文案。
- 网络失败不改变本地数据：在线搜索/详情失败只影响当前页，本地菜谱库仍可读取 Room。
- 到期检查在 `CoroutineWorker` 的 `Dispatchers.IO` 中执行；Worker 不持有 Activity，通知使用 application context。
- 通知权限被拒绝或渠道关闭时，Worker 直接成功返回，不重试、不抛异常；食材 CRUD 不受影响。
- 下拉刷新不重建 ViewModel：列表 VM 暴露独立 `isRefreshing`，通过递增 `retrySignal` 重新订阅 Flow；Room/网络发射或异常后复位，UI 不叠加第二个全屏加载态。
- 表单校验分两层：首次提交前不打扰用户；首次提交失败后，`onFormChange` 立即重新校验并更新字段错误。在线搜索空值/超长在点击搜索时即时提示。
- 页面跳转统一走 safe 扩展函数；只有可恢复的 `IllegalArgumentException`/`IllegalStateException` 被转成 Snackbar，不替换全局未捕获异常处理器。
- `FridgeApplication` 只懒加载数据库与 Repository，并创建通知渠道/入队每日任务；不做阻塞查询。

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

### 2.5 到期提醒数据流

```text
FridgeApplication.onCreate
        ├─ ExpiryNotifications.createChannel()
        └─ settingsRepository.settings.first()
                  └─ ExpiryReminderScheduler.ensureScheduled(settings)
                            └─ WorkManager 唯一周期任务（默认 09:00，可由设置调整）

设置页修改
        └─ SettingsViewModel.update() → DataStore → ExpiryReminderScheduler.reschedule(settings)
                  └─ REPLACE 唯一周期任务；关闭总开关则 cancel

ExpiryCheckWorker.doWork（Dispatchers.IO）
        ├─ SettingsRepository.settings.first()
        ├─ 通知关闭 → cancel() 并 success()
        ├─ IngredientRepository.getAllIngredients()
        ├─ ExpiryCalculator.daysLeft / isExpiringSoon(warningDays)
        ├─ 无权限 / 渠道关闭 → success() 降级
        ├─ 无临期 → ExpiryNotifications.cancel()
        └─ 有临期 → show(items) 合并通知
                          └─ PendingIntent → MainActivity
                                  └─ EXTRA_OPEN_INGREDIENTS → FridgeApp 跳食材库
```

- 进入食材列表先展示通知说明，用户同意后才申请系统权限；拒绝/永久拒绝只影响通知。
- `NotificationPermissionBanner` 在 `ON_RESUME` 重新检查，设置页返回后自动消失。
- Worker 的查询与通知构造全部在后台线程；通知内容只包含名称与剩余天数，不包含数量/备注等敏感信息。

### 2.6 看板统计、趋势与刷新数据流

```text
DashboardHomeScreen（顶栏刷新 / 导出）
        │ 收集 DashboardUiState（Loading / Empty / Error / Success）
        ▼
DashboardHomeViewModel
        ├─ 首次 / 重试 / 刷新：retrySignal.flatMapLatest
        │       └─ DashboardRepository.observeIngredients()
        │               ├─ 60s 内存缓存命中 → 直接发射
        │               ├─ DashboardIngredientSource（ContentResolver，Dispatchers.IO）
        │               └─ ContentObserver 变更 → forceRefresh
        └─ DashboardStatsCalculator.calculate()（Dispatchers.Default）
                 ├─ 总条目数、0..3 天临期、负数已过期
                 ├─ 分类条目数（按数量降序，空分类 → 未分类）
                 └─ 未来 7 天按到期日聚合（空日期补 0）
                         └─ DashboardHomeContent → 指标卡 + 饼图 + 趋势折线图
```

- 读取与统计分属 IO / Default 两条后台链路，主线程只消费结果。
- 分类占比按库存条目数统计；不同单位（kg、L、个）的 quantity 不相加，避免产生无意义的总量。
- 饼图使用 MPAndroidChart `PieChart`，每个 `PieEntry` 的 label 为“分类 + 条目数”，中心文字显示总条数；图例关闭，避免与扇区标签重复。
- 趋势图使用 `LineChart`，今天起连续 7 天，X 轴为 `M/d`，Y 轴整数；数据点开启 drawValues，无数据时由页面显示空态。
- 手动刷新保留旧内容：`isRefreshing` 单独驱动顶部进度条与“刷新中…”，不把 `Success` 切回 `Loading`；结果至少展示 450ms 后复位。
- 统计结果与原始明细一起进入 Success，避免页面或重组时重复计算。
- 缓存失败不写缓存，成功结果按 `System.nanoTime()` 记录 60 秒 TTL；主 App `notifyChange` 与手动刷新调用 `forceRefresh=true` 绕过缓存。

### 2.7 设置与配置数据流

```text
SettingsScreen
        │ collect SettingsUiState
        ▼
SettingsViewModel
        ├─ SettingsRepository.settings（DataStore Flow）
        └─ update(transform)
                ├─ IO：Preferences DataStore.edit（提醒天数/时间/通知开关）
                ├─ 通知开 → ExpiryReminderScheduler.reschedule(settings)
                └─ 通知关 → cancel() + ExpiryNotifications.cancel()
```

- `AppSettings.normalized()` 是唯一兜底：天数 1..7、小时 0..23、分钟 0..59；脏数据不会导致调度异常。
- DataStore 读取 IOException 时回退默认值，设置页仍可打开；其他异常进入 `UiState.Error` 并可重试。
- 页面只依赖 `SettingsRepository`，不直接接触 DataStore key；保存成功用状态位 + Snackbar 消费一次。
- 启动时用 `KEEP` 保留已有周期任务，只有用户修改设置才用 `REPLACE`，避免每次冷启动把下一次提醒往后推。

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

到期提醒统一使用本地日历日计算：

- `daysLeft = expiresAt 的本地日期 - 今天`；
- `0..warningDays` 天 → 临期（橙色）；`warningDays` 默认 3，可在设置页调为 1–7；
- `< 0` 天 → 已过期（红色），继续留在列表但不重复推送；
- `> warningDays` 天 → 新鲜（绿色）；
- 每日检查一次，时间与提前天数由 DataStore 配置；多条临期合并为一条通知，无临期时取消旧通知。

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

## 4. 跨 App 数据契约（已实现）

### 4.1 权限与 Provider

主 App Manifest 声明两个 signature 权限（读给看板，写保留给未来可信组件）：

```xml
<permission
    android:name="com.fridge.main.permission.READ_INGREDIENTS"
    android:protectionLevel="signature" />
<permission
    android:name="com.fridge.main.permission.WRITE_INGREDIENTS"
    android:protectionLevel="signature" />

<provider
    android:name=".provider.IngredientContentProvider"
    android:authorities="com.fridge.main.provider"
    android:exported="true"
    android:readPermission="com.fridge.main.permission.READ_INGREDIENTS"
    android:writePermission="com.fridge.main.permission.WRITE_INGREDIENTS" />
```

看板 Manifest：

```xml
<uses-permission android:name="com.fridge.main.permission.READ_INGREDIENTS" />
<queries>
    <provider android:authorities="com.fridge.main.provider" />
</queries>
```

- `signature` 权限要求两个 APK 使用同一签名证书；debug 构建使用同一台机器的 debug keystore 即可，release 必须配置统一签名。
- `IngredientContentProvider` 只实现 `query`；`insert/update/delete/bulkInsert` 返回 `null` 或 `0`，不在 Provider 内修改数据。
- `onCreate` 返回 `true`；`query` 显式校验调用方 UID 与 `READ_INGREDIENTS` 权限，再查询 Room。
- 查询通过 `runBlocking(Dispatchers.IO)` 调 `IngredientDao.findAll()`，避免阻塞 Binder 线程。

### 4.2 URI 与字段

| URI | 内容 |
| --- | --- |
| `content://com.fridge.main.provider/ingredients` | 完整食材库存行，只读 |

列契约：

```text
_id, type_id, name, category, stored_at, shelf_life_days, quantity, unit
```

约束：

- 固定 projection 白名单，未知列抛 `IllegalArgumentException`，拒绝任意列名。
- 返回 `MatrixCursor` 的列名作为稳定契约；改名视为 breaking change。
- `setNotificationUri` 指向同一 URI；主 App Provider 同时注册 Room `InvalidationTracker.Observer("ingredients")`，表失效时主动 `notifyChange`。
- 看板 `IngredientProviderClient` 先 `resolveContentProvider` 判断主 App 是否安装，再 `ContentResolver.query`；Cursor 映射为 `DashboardIngredient`。
- 看板通过 `callbackFlow + ContentObserver` 观察同一 URI，主 App 新增/修改/删除后自动重新查询；`conflate()` 合并高频写入，避免重复刷新。
- 看板对 `SecurityException` 提示权限不足；对 `IllegalArgumentException` 提示主 App 不可用；其他查询异常提示“读取失败”，均不崩溃。
- 主 App 未安装时错误态显示“未检测到冰箱管家”；主 App 无食材时显示空态与“重新读取”。

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
- 刷新失败时保留当前列表，只弹窗提示；不把已有内容替换成错误页。
- 页面跳转失败由 `AppErrorHandler` 发到根 Scaffold 的 Snackbar，文案为“页面打开失败/暂时无法打开，请返回后重试”；不替换 Android 默认崩溃处理。
- 重试由 ViewModel 重新订阅 Flow（`retrySignal`）或重新调用查询，不在 Composable 中直接调用 Repository。

## 6. 权限与降级

### 6.1 通知

使用 WorkManager `2.10.1` 每日检查，创建 `expiry_reminder` 通知渠道；不使用精确闹钟权限，允许系统按省电策略延迟。

- Android 13+：首次进入食材库先展示说明弹窗，用户同意后才请求 `POST_NOTIFICATIONS`。
- 拒绝：不崩溃、不影响食材 CRUD；弹窗说明并保留顶部提示条，可再次申请。
- 永久拒绝/通知总开关关闭：提示条显示“去设置”，跳转应用通知设置；`ON_RESUME` 返回后刷新。
- 通知渠道被用户关闭：`canPost()` 返回 false，Worker 直接成功返回，不反复重试。
- 未授权时列表仍按新鲜/临期/已过期展示，App 内可完整查看状态。

### 6.2 文件导出

- Android 10+（API 29+）：使用 `MediaStore.Downloads` 写入公共 `Downloads/Fridge`，`IS_PENDING` 防半成品可见，不需要存储权限。
- Android 7-9（API 24-28）：申请 `WRITE_EXTERNAL_STORAGE`（manifest `maxSdkVersion=28`），写公共 Downloads 后 `MediaScannerConnection` 扫描；拒绝/永久拒绝只影响导出，统计浏览不受影响。
- 每次导出先在 `cacheDir/exports` 写一份副本，FileProvider（authority `${applicationId}.fileprovider`）只暴露该目录，供“打开文件”授予临时读权限；公共目录副本才是用户可长期访问的文件。
- `DashboardCsvFormatter` 负责 UTF-8 BOM、CRLF、RFC 4180 转义、数量去尾零、状态文案；`AndroidDashboardCsvExporter` 负责存储与异常映射，二者都在 IO 协程执行。
- 失败分类：权限不足、存储空间不足、写入失败；ViewModel 转中文弹窗，保留看板统计。无 CSV 查看器时提示安装文件管理器。
- 文件命名 `fridge_inventory_yyyyMMdd_HHmmss.csv`；导出前重新读取 Provider，确保是最新库存。

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
| WorkManager | 2.10.1 |
| Preferences DataStore | 1.1.7 |
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
- Domain：`RecipeMatcherTest` 覆盖三级分类、稳定排序、已有/缺少名称、空库存、空菜谱；`ExpiryCalculatorTest` 覆盖到期日、默认/自定义 warningDays、夏令时边界；`DashboardStatsCalculatorTest` 覆盖总数/临期/过期/分类、空分类回退、跨月边界与 7 天趋势窗口；`DashboardRepositoryCacheTest` 覆盖 TTL 命中、强制刷新与失败清缓存。
- Settings：`AppSettingsTest` 覆盖默认值、normalized 边界、时间字段收敛。
- Work：`ExpiryReminderSchedulerTest` 覆盖默认/custom 09:00 前/后/准点初始延迟与越界收敛；Worker 通知合并与权限降级后续可用 `WorkManagerTestInitHelper` 补。
- ViewModel：`kotlinx-coroutines-test` + Turbine 验证四态顺序。
- Compose：对 Loading / Empty / Error / Success 做 UI 测试；列表点击触发正确路由。
- Provider：`ProviderTestRule` 验证权限、projection 白名单、Cursor 列名。
- CSV：`DashboardCsvFormatterTest` 已覆盖 BOM/CRLF、逗号/引号转义、状态边界、数量去尾零；空数据由 ViewModel 导出前拦截。`AndroidDashboardCsvExporter` 的 MediaStore/旧版权限路径需在 API 28/29 设备上补仪器测试。
- Migration：`MigrationTestHelper` 验证 v1 → v2。

## 9. 已知风险 / 待确认

- 在线菜谱依赖 TheMealDB 免费测试接口：无 SLA、英文菜名为主、字段可能变化；生产环境应换成有服务保障的 API 或加缓存/代理。
- TheMealDB 不提供单个食材分类，保存到本地时统一写“未分类”；在线用量是自由文本，解析失败时按“1 + 适量”保存。
- 菜谱所需食材已改用 `type_id` 关联品类，但品类规范化目前只做 `trim + lower`；别名、同义词与“番茄/西红柿”这类口语差异尚未处理。
- 数量单位不统一（个 / 克 / 毫升），匹配阶段暂不换算。
- 匹配只判断“品类有库存”，不判断数量是否够：例如菜谱要 3 个鸡蛋、冰箱只有 1 个，仍会显示“完全可制作”；这是当前明确的业务边界。
- 菜谱列表推荐需要加载全部菜谱与库存并在 `Dispatchers.Default` 计算；数据量很大时应改为分页/增量计算。
- CSV 导出在 API 36 模拟器上验证了 MediaStore 路径；API 24-28 的 WRITE 权限分支只能在旧系统设备上做仪器测试，当前以代码审查与构建覆盖。
- DataStore 首次读取只缓存进程内 Flow；App 与 Worker 各自读取的是同一文件，但极端并发下仍可能看到旧快照。当前以“设置保存后立即 reschedule”保证本轮体验。
- 导出文件只做 RFC 4180 转义，未对 Excel 公式注入（字段以 `= + - @` 开头）做额外前缀处理；若数据来源不可信，后续应加安全转义开关。
- 文件公开副本名为时间戳，多次导出不会覆盖；缓存副本暂不自动清理，长期使用需补保留策略。
- 品类合并/改名会影响历史快照展示；改名时是否同步更新 `Ingredient.name/category` 需产品确认。
- 提醒时间与提前天数已提供设置，但 WorkManager 周期任务允许系统延迟，不能保证秒级准点；设置页已说明这一限制。
- 通知只按“品类有库存 + 0..3 天”筛选；同一品类多条批次会各自触发一次，合并文案可能出现重复名称，后续可按品类聚合。
- Android 13 以下无运行时通知权限，但用户仍可在系统设置关闭通知；`canPost()` 已覆盖该场景。
- 看板与主 App 的 release 签名必须统一，否则 signature 权限会拒绝。
- 看板统计按“库存条目数”而非 quantity 求和：不同单位不能直接相加，因此页面明确标注口径；分类很多时饼图标签可能拥挤，后续可合并“其他”或增加交互图例。
- 看板缓存为进程内 60 秒：Activity 重建可复用，但进程被杀后首次进入仍会查询；主 App 变更通过 ContentObserver 强制刷新，手动刷新绕过 TTL。趋势固定 7 天，暂不支持区间切换。
- 看板独立实现了一份到期日公式；主 App 修改保质期口径时必须同步 `DashboardStatsCalculator`，否则两端统计会不一致。
- 到期日已改为按本地日历日计算，跨时区/夏令时切换时以设备当前时区为准；若用户频繁改时区，剩余天数可能跳变。
- 下拉刷新会重新订阅 Room Flow 或重发网络请求；同一页面快速连续刷新时，旧请求可能仍在进行，在线搜索已用 `requestId` 丢弃过期结果。
- `AppErrorHandler` 只捕获导航等可恢复异常，不替换 Android 默认崩溃处理；数据层异常仍需各自映射，避免 Snackbar 文案与页面错误态重复。
