# MVVM 全链路分层设计

> 第二轮定义结构与接口；第四轮实现食材模块，第五轮实现菜谱模块，第六轮实现在线菜谱搜索。
> 食材/本地菜谱各 3 个 ViewModel、在线菜谱 2 个 ViewModel 与 3 个 Repository 均已落地；
> 菜谱匹配与看板统计仍为后续。本文保留目标契约，实际实现以 `com.fridge.main` 源码为准。

## 1. 分层与依赖方向

```text
Compose Screen
   │  collectAsStateWithLifecycle()
   ▼
ViewModel
   │  StateFlow<UiState> + 一次性事件
   ▼
Repository（接口/实现）
   │  业务校验 / 事务 / 时间计算 / 匹配 / 错误映射
   ├─────────────────────────┐
   ▼                         ▼
DAO（Room）              Remote API（Retrofit/OkHttp）
   │  Flow / suspend         │  suspend + NetworkResult
   ▼                         ▼
SQLite（fridge.db）      TheMealDB（HTTPS）
```

硬规则：

1. 依赖只能向下：UI → ViewModel → Repository → DAO/Remote。DAO 不引用 Repository，
   Repository 不引用 ViewModel，Entity 不引用 UI 模型。
2. Composable 不直接调用 DAO/Repository，不持有 `Context` 做数据库/网络操作。
3. ViewModel 不持有 `Activity`/`Fragment`/`View`，只依赖 Repository 与
   `SavedStateHandle`。
4. 主线程不做 IO：Room 的 `suspend`/`Flow` 由 Room 执行器调度；Retrofit 调用统一经
   `safeApiCall` 切到 `Dispatchers.IO`；CSV、文件、ContentResolver 查询显式
   `withContext(Dispatchers.IO)`。
5. 网络异常不穿透到 UI：`BaseRemoteDataSource` 统一转换成 `NetworkResult.Failure`，
   ViewModel 只接收中文文案。
6. 一次性事件（导航、Snackbar）用 `Channel`/`SharedFlow`，不塞进 `UiState`，
   避免旋转屏幕后重复弹窗/重复跳转；在线页的“网络失败弹窗”由 `errorDialogMessage`
   状态位 + `dismissErrorDialog()` 消费，不做成一次性 Flow。

## 2. 目标目录

```text
com.fridge.main/
├─ data/
│  ├─ local/
│  │  ├─ FridgeDatabase.kt
│  │  ├─ dao/
│  │  │  ├─ IngredientDao.kt
│  │  │  ├─ IngredientTypeDao.kt
│  │  │  └─ RecipeDao.kt
│  │  ├─ entity/
│  │  │  ├─ Ingredient.kt
│  │  │  ├─ IngredientType.kt
│  │  │  ├─ Recipe.kt
│  │  │  └─ RecipeIngredient.kt
│  │  └─ relation/
│  │     ├─ IngredientWithType.kt
│  │     ├─ RecipeIngredientWithType.kt
│  │     └─ RecipeWithIngredients.kt
│  ├─ remote/
│  │  ├─ NetworkResult.kt              # Success/Failure、BaseRemoteDataSource
│  │  └─ recipe/
│  │     ├─ RecipeApi.kt
│  │     ├─ RecipeApiClient.kt
│  │     ├─ MealDto.kt
│  │     ├─ MealMapper.kt
│  │     └─ OnlineRecipe.kt
│  └─ repository/
│     ├─ IngredientRepository.kt      # 接口 + Draft 模型
│     ├─ DefaultIngredientRepository.kt
│     ├─ RecipeRepository.kt          # 接口 + Draft/Match 模型
│     ├─ DefaultRecipeRepository.kt
│     ├─ OnlineRecipeRepository.kt    # 在线搜索/详情/保存
│     └─ DefaultOnlineRecipeRepository.kt
├─ ui/
│  ├─ model/                          # UI 展示模型（Entity/Remote → UI 映射已落地）
│  ├─ viewmodel/                      # 8 个主 App ViewModel（食材/本地菜谱/在线菜谱）
│  ├─ screens/                        # Compose 页面
│  ├─ components/
│  ├─ navigation/
│  └─ theme/
└─ FridgeApplication.kt
```

看板模块：

```text
com.fridge.dashboard/
├─ data/
│  └─ StatsRepository.kt              # 后续：ContentResolver + CSV
└─ ui/
   ├─ model/DashboardModels.kt
   ├─ viewmodel/DashboardViewModel.kt # 后续
   └─ screens/...
```

## 3. 各层职责

| 层 | 负责 | 不负责 |
| --- | --- | --- |
| Entity | 表结构、列名、主键、索引、外键 | 业务校验、单位换算、UI 文案 |
| DAO | 单表/关联查询、insert/update/delete、事务边界内的原子操作 | 多步业务编排、错误提示 |
| Relation | 用 `@Embedded` + `@Relation` 组装一对多/多对一结果 | 业务规则 |
| Repository | 组合多个 DAO、事务、校验、规范化、到期计算、匹配、异常映射 | 持有 UI 状态、导航 |
| ViewModel | 把 Repository 的 Flow/suspend 转成 `StateFlow<UiState>`；处理用户事件 | 直接拼 SQL、持有 Context |
| Screen | 渲染状态、发事件、导航 | 直接访问数据库、做业务计算 |

### 3.1 Entity ↔ UI 模型

UI 不直接使用 Room Entity，原因：

- Entity 字段是数据库口径，UI 需要格式化后的字符串/状态枚举。
- 避免数据库字段变更直接破坏 Compose 预览与测试。
- 匹配结果、剩余天数等是派生数据，不应塞回 Entity。

映射示例（后续实现）：

```kotlin
data class IngredientListItem(
    val id: Long,
    val name: String,
    val category: String,
    val quantityLabel: String,   // "2 个"
    val expiryLabel: String,     // "还剩 3 天"
    val status: FreshnessStatus,
)

fun Ingredient.toListItem(now: Long): IngredientListItem { ... }
```

## 4. DAO 契约

### 4.1 IngredientDao

```kotlin
@Dao
interface IngredientDao {
    fun observeAll(): Flow<List<Ingredient>>
    fun observeAllByExpiry(): Flow<List<Ingredient>>
    fun observeById(id: Long): Flow<Ingredient?>
    fun observeByName(keyword: String): Flow<List<Ingredient>>
    fun observeByType(typeId: Long): Flow<List<Ingredient>>
    fun observeExpiringBetween(from: Long, to: Long): Flow<List<Ingredient>>
    fun observeExpiringCount(from: Long, to: Long): Flow<Int>
    fun observeCount(): Flow<Int>

    suspend fun insert(ingredient: Ingredient): Long
    suspend fun update(ingredient: Ingredient)
    suspend fun delete(ingredient: Ingredient)
    suspend fun deleteById(id: Long)
}
```

约定：

- 查询返回 `Flow`，写入用 `suspend`；调用方不得在主线程 `collect` 之外做阻塞。
- 到期表达式统一 `stored_at + shelf_life_days * 86400000`。
- DAO 不做“名称规范化”，由 Repository 传入已规范化的 `typeId`。

### 4.2 IngredientTypeDao

```kotlin
@Dao
interface IngredientTypeDao {
    fun observeAll(): Flow<List<IngredientType>>
    suspend fun findByNormalizedName(normalizedName: String): IngredientType?
    suspend fun findById(typeId: Long): IngredientType?
    suspend fun insert(type: IngredientType): Long
    suspend fun update(type: IngredientType)
    suspend fun delete(type: IngredientType)
}
```

### 4.3 RecipeDao

```kotlin
@Dao
interface RecipeDao {
    @Transaction
    fun observeAllWithIngredients(): Flow<List<RecipeWithIngredients>>

    @Transaction
    fun observeByIdWithIngredients(id: Long): Flow<RecipeWithIngredients?>

    @Transaction
    fun observeByNameWithIngredients(keyword: String): Flow<List<RecipeWithIngredients>>

    suspend fun insertRecipe(recipe: Recipe): Long
    suspend fun updateRecipe(recipe: Recipe)
    suspend fun deleteRecipe(recipe: Recipe)
    suspend fun insertRecipeIngredient(link: RecipeIngredient)
    suspend fun insertRecipeIngredients(links: List<RecipeIngredient>)
    suspend fun deleteIngredientsOfRecipe(recipeId: Long)
}
```

`@Transaction` 必须加在返回 `@Relation` 的方法上，保证主表与明细来自同一快照。

## 5. Repository 契约

### 5.1 IngredientRepository

实际文件：`data/repository/IngredientRepository.kt`

```kotlin
interface IngredientRepository {
    fun observeIngredients(): Flow<List<Ingredient>>
    fun observeIngredient(id: Long): Flow<Ingredient?>
    fun observeExpiring(withinDays: Int): Flow<List<Ingredient>>
    suspend fun addIngredient(draft: IngredientDraft): Result<Long>
    suspend fun updateIngredient(id: Long, draft: IngredientDraft): Result<Unit>
    suspend fun deleteIngredient(id: Long): Result<Unit>
}
```

`DefaultIngredientRepository` 已按以下要点实现：

1. `addIngredient` / `updateIngredient` 校验：
   - 名称、分类、单位 `trim()` 后非空；名称≤30、分类≤20、单位≤10；
   - `shelfLifeDays` 1–36500；
   - `quantity > 0` 且 ≤ 1,000,000；
   - `storedAt > 0` 且 `<= now`。
2. `normalize(name)` → `typeDao.findByNormalizedName()`；不存在则插入
   `IngredientType`，唯一索引冲突时回查（并发安全）。
3. 在 `db.withTransaction { ... }` 中完成“查/建品类 + 写库存”。
4. `storedAt` 统一由调用方传入；新增页默认为本地当天 00:00。
5. 错误以 `Result.failure(Throwable)` 返回，由 `IngredientError.toIngredientMessage()`
   映射成中文；不把 `SQLiteException` 直接抛给 UI。

### 5.2 RecipeRepository

实际文件：`data/repository/RecipeRepository.kt`

```kotlin
interface RecipeRepository {
    fun observeRecipes(): Flow<List<RecipeWithIngredients>>
    fun observeRecipe(id: Long): Flow<RecipeWithIngredients?>
    fun observeMatches(): Flow<List<RecipeMatch>>
    suspend fun saveRecipe(draft: RecipeDraft): Result<Long>
    suspend fun deleteRecipe(id: Long): Result<Unit>
}
```

实现要点（`saveRecipe` / `deleteRecipe` 已落地；`observeMatches` 仅返回空列表）：

1. `saveRecipe` 在事务中：
   - 校验菜名、步骤、每项食材名称/数量；
   - 解析/创建 `IngredientType`；
   - 新增时 `insertRecipe` + `insertRecipeIngredients`；
   - 编辑时 `updateRecipe` + `deleteIngredientsOfRecipe` + 重新插入；
   - 返回菜谱 id。
2. `observeMatches` 本轮固定返回 `flowOf(emptyList())`；匹配算法留到下一轮。
3. 匹配落地后，`RecipeMatch.missingIngredients` 用于详情页“还差：鸡蛋、葱”。

### 5.3 OnlineRecipeRepository

实际文件：`data/repository/OnlineRecipeRepository.kt`

```kotlin
interface OnlineRecipeRepository {
    suspend fun searchRecipes(keyword: String): NetworkResult<List<OnlineRecipe>>
    suspend fun getRecipe(mealId: String): NetworkResult<OnlineRecipe?>
    suspend fun saveToLocal(recipe: OnlineRecipe): Result<Long>
}
```

`DefaultOnlineRecipeRepository`：

1. 继承 `BaseRemoteDataSource`，所有请求经 `execute()` → `safeApiCall()` 统一超时/断网/HTTP/解析异常。
2. 关键词 `trim()` 后为空直接返回 `Success(emptyList())`，不发请求。
3. `searchRecipes` 调 `RecipeApi.searchMeals`；`getRecipe` 调 `lookupMeal`，结果为空映射为 `Success(null)`，由 ViewModel 转 `Empty`。
4. `saveToLocal` 调 `MealMapper.toRecipeDraft()`，再委托 `RecipeRepository.saveRecipe()`；保存复用本地校验与事务，成功后本地列表由 Room Flow 自动刷新。
5. `CancellationException` 必须重新抛出，避免协程取消被 `Result.failure` 吞掉。

### 5.4 看板 StatsRepository（后续）

```kotlin
interface StatsRepository {
    fun observeStats(): Flow<DashboardStats>
    suspend fun exportCsv(uri: Uri, scope: ExportScope): Result<Unit>
}
```

- 数据通过 `ContentResolver.query()` 读取主 App 的只读 Provider；不打开 `fridge.db`。
- `query` 在 `Dispatchers.IO` 执行；`SecurityException` 映射为“请安装/更新冰箱管家并保持同一签名”。
- CSV 写入用 `contentResolver.openOutputStream(uri)`，UTF-8 + BOM。

## 6. ViewModel 契约

所有 ViewModel 暴露：

```kotlin
val uiState: StateFlow<UiState<T>>
val events: Flow<UiEvent>   // 或 Channel.receiveAsFlow()
```

`UiState` 使用现有 `Loading / Empty / Error / Success` 四态；
`Success(emptyList())` 与 `Empty` 的区别：前者是“查询成功但没有数据”，
后者是“没有可展示内容，需要引导”。本项目的列表页统一把空列表映射为 `Empty`。

### 6.1 IngredientListViewModel

```kotlin
data class IngredientListUiState(
    val query: String = "",
    val filter: IngredientFilter = IngredientFilter.ALL,
    val totalCount: Int = 0,
    val expiringCount: Int = 0,
    val items: List<IngredientListItem> = emptyList(),
)

sealed interface IngredientListEvent {
    data class OpenDetail(val id: Long) : IngredientListEvent
    data object OpenAdd : IngredientListEvent
    data class ShowMessage(val message: String) : IngredientListEvent
}

class IngredientListViewModel(
    private val repository: IngredientRepository,
) : ViewModel() {
    val uiState: StateFlow<UiState<IngredientListUiState>>
    fun onQueryChange(query: String)
    fun onFilterChange(filter: IngredientFilter)
    fun onRetry()
    fun onDelete(id: Long)
}
```

实现方式：

- `query` + `filter` 用 `MutableStateFlow`，`flatMapLatest` 调 Repository。
- 多个 Flow（列表、总数、临期数）用 `combine`，统一映射四态。
- 删除成功后发 `ShowMessage("已删除")`；失败发错误文案。
- 搜索输入做 300ms 防抖（`debounce`），避免每次按键都查库。

### 6.2 IngredientDetailViewModel

```kotlin
data class IngredientDetailUiModel(
    val id: Long,
    val name: String,
    val category: String,
    val quantityLabel: String,
    val storedAtLabel: String,
    val expiryAtLabel: String,
    val daysLeftLabel: String,
    val status: FreshnessStatus,
)

sealed interface IngredientDetailEvent {
    data object Back : IngredientDetailEvent
    data class ShowMessage(val message: String) : IngredientDetailEvent
}

class IngredientDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: IngredientRepository,
) : ViewModel() {
    val uiState: StateFlow<UiState<IngredientDetailUiModel>>
    fun onDelete()
    fun onRetry()
}
```

- 从 `SavedStateHandle["ingredientId"]` 取 id，避免 UI 重复传参。
- Repository 发射 `null` → `Empty`，文案“该食材已被删除”，提供返回。
- 删除确认由 Screen 弹 `AlertDialog`，确认后调 `onDelete()`。

### 6.3 AddIngredientViewModel

```kotlin
data class AddIngredientFormState(
    val name: String = "",
    val category: String = "",
    val storedAt: Long = System.currentTimeMillis(),
    val shelfLifeDays: String = "",
    val quantity: String = "",
    val unit: String = "",
    val nameError: String? = null,
    val shelfLifeError: String? = null,
    val quantityError: String? = null,
    val isSaving: Boolean = false,
    val canSave: Boolean = false,
)

sealed interface AddIngredientEvent {
    data object Saved : AddIngredientEvent
    data class ShowMessage(val message: String) : AddIngredientEvent
}

class AddIngredientViewModel(
    private val repository: IngredientRepository,
) : ViewModel() {
    val formState: StateFlow<AddIngredientFormState>
    fun onNameChange(value: String)
    fun onCategoryChange(value: String)
    fun onStoredAtChange(value: Long)
    fun onShelfLifeChange(value: String)
    fun onQuantityChange(value: String)
    fun onUnitChange(value: String)
    fun onSave()
}
```

- 表单状态是 `StateFlow`，不是 `UiState`；保存时按钮显示进度。
- 校验在 ViewModel + Repository 双重执行：ViewModel 负责即时提示，Repository 负责最终兜底。
- 保存成功发 `Saved`，Screen 导航返回并显示 Snackbar。
- 保存中禁止重复点击。

### 6.4 RecipeListViewModel

```kotlin
data class RecipeListItem(
    val id: Long,
    val name: String,
    val ingredientSummary: String,
    val matchStatus: RecipeMatchStatus,
)

class RecipeListViewModel(
    private val repository: RecipeRepository,
) : ViewModel() {
    val uiState: StateFlow<UiState<List<RecipeListItem>>>
    fun onQueryChange(query: String)
    fun onRetry()
}
```

- 搜索、匹配徽标为后续能力；当前列表按菜名排序，卡片展示食材摘要与步数。
- 新增/编辑共用 `RecipeForm`；列表空态引导点击右上角“新增”。

### 6.5 RecipeDetailViewModel

```kotlin
data class RecipeIngredientUiModel(
    val name: String,
    val quantityLabel: String,
    val availability: IngredientAvailability,
    val missingLabel: String?,
)

data class RecipeDetailUiModel(
    val id: Long,
    val name: String,
    val matchStatus: RecipeMatchStatus,
    val ingredients: List<RecipeIngredientUiModel>,
    val steps: List<String>,
)

class RecipeDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: RecipeRepository,
) : ViewModel() {
    val uiState: StateFlow<UiState<RecipeDetailUiModel>>
    fun onRetry()
}
```

- `steps` 按 `\n` 拆分，过滤空行；展示时重新编号，不依赖用户输入编号。
- 食材可用性三态：`ENOUGH / SHORT / MISSING`，待匹配算法落地后映射。
- 当前详情页已提供编辑、删除（二次确认）；编辑复用 `RecipeForm`。

### 6.6 OnlineRecipeListViewModel

```kotlin
sealed interface OnlineSearchResult {
    data object Idle : OnlineSearchResult
    data object Loading : OnlineSearchResult
    data object Empty : OnlineSearchResult
    data class Error(val message: String) : OnlineSearchResult
    data class Success(val recipes: List<OnlineRecipe>) : OnlineSearchResult
}

data class OnlineSearchUiState(
    val query: String = "",
    val queryError: String? = null,
    val result: OnlineSearchResult = OnlineSearchResult.Idle,
    val errorDialogMessage: String? = null,
)

class OnlineRecipeListViewModel(
    private val repository: OnlineRecipeRepository,
) : ViewModel() {
    val uiState: StateFlow<OnlineSearchUiState>
    fun onQueryChange(query: String)
    fun search()
    fun retry()
    fun dismissErrorDialog()
}
```

- `search()` 校验空关键词；取消上一次 Job，响应返回时比对当前关键词，丢弃过期结果。
- 失败同时写 `Error` 与 `errorDialogMessage`；关闭弹窗后错误页仍可重试。
- 网络在 `DefaultOnlineRecipeRepository` 内切 IO，ViewModel 不直接使用 OkHttp/Retrofit。

### 6.7 OnlineRecipeDetailViewModel

```kotlin
data class OnlineRecipeDetailUiState(
    val content: UiState<OnlineRecipe> = UiState.Loading,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val message: String? = null,
    val errorDialogMessage: String? = null,
)

class OnlineRecipeDetailViewModel(
    private val repository: OnlineRecipeRepository,
    private val mealId: String,
) : ViewModel() {
    val uiState: StateFlow<OnlineRecipeDetailUiState>
    fun retry()
    fun saveToLocal()
    fun clearMessage()
    fun dismissErrorDialog()
}
```

- `init` 调 `lookup`；返回 null → `UiState.Empty`，失败 → `Error` + 弹窗。
- `saveToLocal()` 防重复；成功 `saved = true`，由 `RecipeListViewModel` 的 Room Flow 自动刷新本地列表。
- 保存失败把 `Throwable` 映射为中文 `message`，并弹窗提示；页面保留在线内容。

### 6.8 DashboardViewModel（看板）

```kotlin
class DashboardViewModel(
    private val repository: StatsRepository,
) : ViewModel() {
    val uiState: StateFlow<UiState<DashboardStats>>
    val exportState: StateFlow<ExportState>
    fun onRefresh()
    fun onExport(uri: Uri, scope: ExportScope)
}
```

- 主页、导出页共用同一个 ViewModel（Activity 级）或各自一个；推荐共用，避免重复查询。
- `ExportState`：`Idle / Preparing / Writing / Success(path) / Error(message)`。
- 用户取消 SAF → 回到 `Idle`，不显示错误。
- Provider 不可用 → `Error("无法读取主 App 数据，请确认已安装冰箱管家")`。

## 7. 线程与生命周期

| 操作 | 调度 |
| --- | --- |
| Room `Flow` 查询 | Room 查询执行器，UI 在 `viewModelScope` 收集 |
| Room `suspend` 写入 | Room 事务执行器；Repository 可包 `withContext(io)` |
| Retrofit 网络请求 | `safeApiCall` 内 `withContext(Dispatchers.IO)` |
| 多 DAO 事务 | `RoomDatabase.withTransaction { ... }` |
| CSV 写文件 | `Dispatchers.IO` |
| ContentResolver 查询 | `Dispatchers.IO` |
| 日期/剩余天数计算 | 纯函数，可在主线程；数据量大时移到 Default |

UI 收集：

```kotlin
val state by viewModel.uiState.collectAsStateWithLifecycle()
```

不要在 Composable 中 `LaunchedEffect` 里直接 `repository.observe...collect`；
不要在 `remember` 中创建 DAO。

## 8. 错误处理

本轮未抽统一的 `AppError` 类型；错误映射分散在三个边界：本地 `IngredientError`/`RecipeError`、网络 `NetworkResult.safeApiCall`，由 ViewModel 转成页面文案。

映射：

| 异常 | 用户文案 |
| --- | --- |
| `IllegalArgumentException` | 业务校验的具体中文（如“保质期需为 1–36500 天”） |
| `NoSuchElementException` | “食材不存在或已被删除” |
| `SQLiteConstraintException` | “数据冲突，请检查食材名称后重试” |
| `SQLiteException` | “数据库操作失败，请稍后重试” |
| 网络超时/DNS/连接失败 | “请求超时，请检查网络后重试” / “无法连接菜谱服务，请检查网络或稍后重试” |
| HTTP 4xx/5xx | “菜谱服务暂时不可用（HTTP xxx），请稍后重试” |
| JSON 解析失败 | “菜谱数据解析失败，请稍后重试” |
| 其它 `Throwable` | 页面兜底文案（如“食材加载失败，请重试”） |

原则：

- UI 只展示 `message`，不展示堆栈/类名。
- `Error` 状态必须提供“重试”；重试由 ViewModel 重新触发查询。
- 非致命错误用 Snackbar，不把整页切到 Error（例如删除失败但列表还在）。

## 9. 依赖组装

食材模块已手工组装，不引入 Hilt：

```kotlin
class FridgeApplication : Application() {
    val database by lazy { FridgeDatabase.getInstance(this) }

    val ingredientRepository: IngredientRepository by lazy {
        DefaultIngredientRepository(databaseProvider = { database })
    }

    val recipeRepository: RecipeRepository by lazy {
        DefaultRecipeRepository(databaseProvider = { database })
    }

    val onlineRecipeRepository: OnlineRecipeRepository by lazy {
        DefaultOnlineRecipeRepository(
            api = RecipeApiClient.api,
            recipeRepository = recipeRepository,
        )
    }
}
```

ViewModel 由 `FridgeNavHost` 用 `viewModel(factory = ...)` 创建，Repository 从
`applicationContext as FridgeApplication` 取得；食材/本地菜谱/在线菜谱共用同一组装模式。
不要在 Composable 中直接 `new` Repository。

## 10. 测试计划

| 层 | 测试 |
| --- | --- |
| DAO | 内存 Room：插入、到期区间、关联查询、级联删除、RESTRICT |
| Repository | fake DAO：名称规范化、品类复用、事务回滚、校验、错误映射 |
| ViewModel | `kotlinx-coroutines-test` + Turbine：四态顺序、防抖、事件只发一次 |
| Network | MockWebServer：搜索/详情 JSON、404/500、超时、断网、空 meals；断言 `NetworkResult` |
| Compose | Loading/Empty/Error/Success 快照；点击卡片路由；表单校验 |
| Migration | `MigrationTestHelper`：v1 数据 → v2，检查品类回填、外键、索引 |
| Dashboard | Provider 权限、Cursor 列名、CSV 转义/中文/BOM |

## 11. 当前实现边界

已完成：

- 数据表 v2（品类主数据 + 库存 + 菜谱 + 多对多关联）。
- Repository 接口与 Draft/Match 模型。
- `DefaultIngredientRepository`：事务、校验、品类复用、异常包装。
- `DefaultRecipeRepository`：事务、菜谱/食材关联维护、校验、异常包装。
- `DefaultOnlineRecipeRepository` + `NetworkResult`：TheMealDB 搜索/详情、异常文案、保存到本地。
- 食材 3 个 ViewModel、本地菜谱 3 个 ViewModel、在线菜谱 2 个 ViewModel。
- 食材/菜谱列表、详情、新增/编辑页面接入 Room 与四态 UI；在线搜索/详情接入网络与三 Tab 导航。
- 菜谱新增/编辑共用 `RecipeForm`，支持动态食材清单与多行做法；在线菜谱保存复用同一事务。

未完成（下一轮）：

- 现有食材匹配菜谱的“能做 / 部分能做”算法与 `observeMatches` 实现。
- 本地菜谱关键词搜索、匹配徽标、食材可用性提示。
- 看板真实统计数据与 CSV 导出。
- 通知、Provider、CSV 写文件。
