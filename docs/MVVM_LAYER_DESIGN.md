# MVVM 全链路分层设计

> 第二轮只定义结构与接口，不写具体业务实现。页面仍使用 `SampleData` 占位，
> 下一轮再把 ViewModel / Repository 接到真实 Room 数据。

## 1. 分层与依赖方向

```text
Compose Screen
   │  collectAsStateWithLifecycle()
   ▼
ViewModel
   │  StateFlow<UiState> + 一次性事件
   ▼
Repository（接口）
   │  业务校验 / 事务 / 时间计算 / 匹配 / 错误映射
   ▼
DAO（Room）
   │  Flow / suspend
   ▼
SQLite（fridge.db）
```

硬规则：

1. 依赖只能向下：UI → ViewModel → Repository → DAO。DAO 不引用 Repository，
   Repository 不引用 ViewModel，Entity 不引用 UI 模型。
2. Composable 不直接调用 DAO/Repository，不持有 `Context` 做数据库操作。
3. ViewModel 不持有 `Activity`/`Fragment`/`View`，只依赖 Repository 与
   `SavedStateHandle`。
4. 主线程不做 IO：Room 的 `suspend`/`Flow` 由 Room 执行器调度；CSV、文件、
   ContentResolver 查询显式 `withContext(Dispatchers.IO)`。
5. 一次性事件（导航、Snackbar）用 `Channel`/`SharedFlow`，不塞进 `UiState`，
   避免旋转屏幕后重复弹窗/重复跳转。

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
│  └─ repository/
│     ├─ IngredientRepository.kt      # 本轮：接口 + Draft 模型
│     └─ RecipeRepository.kt          # 本轮：接口 + Draft/Match 模型
├─ ui/
│  ├─ model/                          # UI 展示模型（后续从 Entity 映射）
│  ├─ viewmodel/                      # 后续：5 个主 App ViewModel
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

后续 `DefaultIngredientRepository` 的实现要点：

1. `addIngredient` / `updateIngredient` 校验：
   - 名称、分类、单位 `trim()` 后非空；
   - `shelfLifeDays > 0`；
   - `quantity >= 0`；
   - `storedAt <= now`（允许当天）。
2. `normalize(name)` → `typeDao.findByNormalizedName()`；不存在则插入
   `IngredientType`，唯一索引冲突时回查（并发安全）。
3. 在 `db.withTransaction { ... }` 中完成“查/建品类 + 写库存”。
4. `storedAt` 统一由调用方传入；新增页默认 `System.currentTimeMillis()`。
5. 错误映射成 `Result.failure(AppError.Validation(...))`，不把 `SQLiteException`
   直接抛给 UI。

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

后续实现要点：

1. `saveRecipe` 在事务中：
   - 校验菜名、步骤、每项食材名称/数量；
   - 解析/创建 `IngredientType`；
   - 新增时 `insertRecipe` + `insertRecipeIngredients`；
   - 编辑时 `updateRecipe` + `deleteIngredientsOfRecipe` + 重新插入；
   - 返回菜谱 id。
2. `observeMatches` 用 `DATA_MODEL.md` §6.2 的 SQL 聚合库存；
   单位不一致时只判断“有库存”，不做换算。
3. `RecipeMatch.missingIngredients` 用于详情页“还差：鸡蛋、葱”。

### 5.3 看板 StatsRepository（后续）

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

- 搜索按菜名过滤；匹配徽标来自 `observeMatches()`。
- 本轮没有“新增菜谱”页面，空态引导“先去添加食材，或等待菜谱导入”。

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
- 食材可用性三态：`ENOUGH / SHORT / MISSING`，由匹配结果映射。
- 没有编辑/删除入口时不要放禁用按钮，避免误导。

### 6.6 DashboardViewModel（看板）

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

统一错误类型（后续新增 `data/AppError.kt`）：

```kotlin
sealed class AppError : Exception() {
    data class Validation(val field: String, override val message: String) : AppError()
    data class Storage(override val message: String) : AppError()
    data class Permission(override val message: String) : AppError()
    data class NotFound(override val message: String) : AppError()
    data class Unknown(override val message: String) : AppError()
}
```

映射：

| 异常 | 用户文案 |
| --- | --- |
| `SQLiteException` | 本地数据读写失败，请重试 |
| `SecurityException` | 没有读取权限，请检查应用签名/权限 |
| `IOException` | 文件读写失败，请更换位置后重试 |
| 校验失败 | 具体字段提示（如“保质期必须大于 0”） |
| 记录不存在 | 该记录已被删除 |

原则：

- UI 只展示 `message`，不展示堆栈/类名。
- `Error` 状态必须提供“重试”；重试由 ViewModel 重新触发查询。
- 非致命错误用 Snackbar，不把整页切到 Error（例如删除失败但列表还在）。

## 9. 依赖组装（后续）

本轮不引入 Hilt，保持可读性。下一轮可在 `FridgeApplication` 中手工组装：

```kotlin
class FridgeApplication : Application() {
    val database by lazy { FridgeDatabase.getInstance(this) }

    val ingredientRepository by lazy {
        DefaultIngredientRepository(
            db = database,
            ingredientDao = database.ingredientDao(),
            typeDao = database.ingredientTypeDao(),
            ioDispatcher = Dispatchers.IO,
        )
    }

    val recipeRepository by lazy {
        DefaultRecipeRepository(
            db = database,
            recipeDao = database.recipeDao(),
            typeDao = database.ingredientTypeDao(),
            ioDispatcher = Dispatchers.IO,
        )
    }
}
```

ViewModel 通过 `viewModel(factory = ...)` 或自定义 `ViewModelProvider.Factory` 获取依赖；
不要在 Composable 中直接 `new` Repository。

## 10. 测试计划

| 层 | 测试 |
| --- | --- |
| DAO | 内存 Room：插入、到期区间、关联查询、级联删除、RESTRICT |
| Repository | fake DAO：名称规范化、品类复用、事务回滚、校验、错误映射 |
| ViewModel | `kotlinx-coroutines-test` + Turbine：四态顺序、防抖、事件只发一次 |
| Compose | Loading/Empty/Error/Success 快照；点击卡片路由；表单校验 |
| Migration | `MigrationTestHelper`：v1 数据 → v2，检查品类回填、外键、索引 |
| Dashboard | Provider 权限、Cursor 列名、CSV 转义/中文/BOM |

## 11. 本轮边界

已完成：

- 数据表 v2（品类主数据 + 库存 + 菜谱 + 多对多关联）。
- Repository 接口与 Draft/Match 模型。
- 本文档中的 ViewModel 契约与 UI 状态模型。

未完成（下一轮）：

- Repository 实现类、ViewModel 实现类、依赖注入。
- 页面从 `SampleData` 切换到真实 Flow。
- 通知、匹配算法、Provider、CSV 写文件。
