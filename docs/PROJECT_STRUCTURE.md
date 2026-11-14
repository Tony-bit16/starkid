# 项目目录结构

```text
Fridge/
├─ README.md                          # 项目说明、构建方式、当前范围
├─ build.gradle.kts                   # 根构建脚本，只声明插件版本
├─ settings.gradle.kts                # 仓库配置 + 两个 application 模块
├─ gradle.properties                  # AndroidX / Gradle JVM 参数
├─ gradlew / gradlew.bat              # Gradle Wrapper
├─ gradle/
│  ├─ libs.versions.toml              # 版本目录（AGP/Kotlin/Compose/Room/MPAndroidChart）
│  └─ wrapper/
│     ├─ gradle-wrapper.jar
│     └─ gradle-wrapper.properties    # Gradle 8.13
├─ docs/
│  ├─ REQUIREMENTS.md                 # 需求清单、页面清单、权限与验收
│  ├─ ARCHITECTURE.md                 # MVVM、数据边界、Provider 契约、测试策略
│  ├─ DATA_MODEL.md                   # v2 表结构、ER 图、迁移与匹配口径
│  ├─ MVVM_LAYER_DESIGN.md            # Entity/Dao/Repository/ViewModel 契约
│  ├─ UI_PROTOTYPE.md                 # 10 个页面布局、交互、状态与跳转
│  └─ PROJECT_STRUCTURE.md            # 本文件
│
├─ refrigerator_main/                 # 主 App：冰箱管家
│  ├─ build.gradle.kts                # com.fridge.main，Compose + Room + KSP
│  ├─ proguard-rules.pro
│  ├─ schemas/                        # Room 导出 schema（v1、v2 均提交）
│  └─ src/main/
│     ├─ AndroidManifest.xml          # 声明 INTERNET、POST_NOTIFICATIONS、MainActivity
│     ├─ java/com/fridge/main/
│     │  ├─ FridgeApplication.kt      # 懒加载数据库 + 食材/菜谱/在线菜谱 Repository 单例
│     │  ├─ MainActivity.kt           # 唯一 Activity，edge-to-edge + Compose
│     │  ├─ domain/
│     │  │  └─ matching/
│     │  │     └─ RecipeMatcher.kt    # 纯 Kotlin 匹配算法：分级、排序、已有/缺少
│     │  ├─ data/
│     │  │  ├─ local/
│     │  │  │  ├─ FridgeDatabase.kt   # @Database version=2，MIGRATION_1_2
│     │  │  │  ├─ dao/
│     │  │  │  │  ├─ IngredientDao.kt # 食材批次增删改查、到期/品类查询
│     │  │  │  │  ├─ IngredientTypeDao.kt # 食材品类查询/写入
│     │  │  │  │  └─ RecipeDao.kt     # 菜谱与所需食材关联查询
│     │  │  │  ├─ entity/
│     │  │  │  │  ├─ IngredientType.kt # 食材品类（字典），normalized_name 唯一
│     │  │  │  │  ├─ Ingredient.kt    # 食材库存批次，type_id 外键
│     │  │  │  │  ├─ Recipe.kt        # 菜谱实体
│     │  │  │  │  └─ RecipeIngredient.kt # 菜谱-品类多对多关联
│     │  │  │  └─ relation/
│     │  │  │     ├─ IngredientWithType.kt
│     │  │  │     ├─ RecipeIngredientWithType.kt
│     │  │  │     └─ RecipeWithIngredients.kt
│     │  │  ├─ remote/
│     │  │  │  ├─ NetworkResult.kt    # 统一网络结果、BaseRemoteDataSource、异常文案
│     │  │  │  └─ recipe/
│     │  │  │     ├─ RecipeApi.kt     # TheMealDB search/lookup 接口
│     │  │  │     ├─ RecipeApiClient.kt # Retrofit/OkHttp/Gson 单例
│     │  │  │     ├─ MealDto.kt       # strIngredient1..20 / strMeasure1..20
│     │  │  │     ├─ MealMapper.kt    # DTO → OnlineRecipe → RecipeDraft
│     │  │  │     └─ OnlineRecipe.kt  # 在线菜谱领域模型
│     │  │  └─ repository/
│     │  │     ├─ IngredientRepository.kt # 契约 + IngredientDraft
│     │  │     ├─ DefaultIngredientRepository.kt # Room 事务、校验、异常包装
│     │  │     ├─ RecipeRepository.kt     # 契约 + RecipeDraft/匹配模型
│     │  │     ├─ DefaultRecipeRepository.kt # 菜谱事务、关联维护、校验、异常包装
│     │  │     ├─ OnlineRecipeRepository.kt # 在线搜索/详情/保存契约
│     │  │     └─ DefaultOnlineRecipeRepository.kt # 网络调用 + 保存到本地
│     │  └─ ui/
│     │     ├─ FridgeApp.kt           # Scaffold、顶栏、底部「食材库/菜谱库/在线搜索」导航
│     │     ├─ common/
│     │     │  ├─ UiState.kt          # Loading/Empty/Error/Success
│     │     │  └─ ScreenStateContent.kt # 四态分发，复用 components 下的通用布局
│     │     ├─ components/
│     │     │  ├─ StateLayouts.kt     # 通用加载/空/错误态布局
│     │     │  ├─ FridgeComponents.kt # 提示条、卡片、键值行、标签
│     │     │  └─ FreshnessPill.kt    # 新鲜/临期/过期统一配色
│     │     ├─ model/
│     │     │  ├─ IngredientUiModel.kt # 食材展示模型、到期/数量派生
│     │     │  ├─ RecipeUiModel.kt     # 菜谱/所需食材展示模型
│     │     │  └─ FreshnessStatus.kt   # 新鲜/临期/过期状态
│     │     ├─ navigation/
│     │     │  ├─ Routes.kt           # 路由常量与 id 构造函数
│     │     │  └─ FridgeNavHost.kt    # 8 个目的地；食材/菜谱/在线页注入 ViewModel
│     │     ├─ screens/
│     │     │  ├─ ingredients/
│     │     │  │  ├─ IngredientListScreen.kt
│     │     │  │  ├─ IngredientListViewModel.kt
│     │     │  │  ├─ IngredientDetailScreen.kt
│     │     │  │  ├─ IngredientDetailViewModel.kt
│     │     │  │  ├─ AddIngredientScreen.kt
│     │     │  │  ├─ AddIngredientViewModel.kt
│     │     │  │  ├─ IngredientForm.kt
│     │     │  │  ├─ IngredientFormState.kt
│     │     │  │  └─ IngredientError.kt
│     │     │  └─ recipes/
│     │     │     ├─ RecipeListScreen.kt
│     │     │     ├─ RecipeListViewModel.kt
│     │     │     ├─ RecipeDetailScreen.kt
│     │     │     ├─ RecipeDetailViewModel.kt
│     │     │     ├─ AddRecipeScreen.kt
│     │     │     ├─ AddRecipeViewModel.kt
│     │     │     ├─ RecipeForm.kt
│     │     │     ├─ RecipeFormState.kt
│     │     │     ├─ RecipeError.kt
│     │     │     ├─ RecipeMatchMapper.kt # RecipeWithIngredients → MatchableRecipe
│     │     │     ├─ RecipeMatchComponents.kt # 匹配等级徽标
│     │     │     ├─ OnlineRecipeListScreen.kt
│     │     │     ├─ OnlineRecipeListViewModel.kt
│     │     │     ├─ OnlineRecipeDetailScreen.kt
│     │     │     └─ OnlineRecipeDetailViewModel.kt
│     │     └─ theme/
│     │        └─ Theme.kt            # 绿/橙语义色，固定配色
│     └─ res/
│        ├─ drawable/ic_launcher.xml
│        └─ values/
│           ├─ strings.xml
│           └─ themes.xml
│
└─ dashboard_module/                  # 伴生看板：独立 APK
   ├─ build.gradle.kts                # com.fridge.dashboard，Compose + MPAndroidChart
   ├─ proguard-rules.pro
   └─ src/main/
      ├─ AndroidManifest.xml          # 独立 launcher；不申请存储权限
      ├─ java/com/fridge/dashboard/
      │  ├─ DashboardActivity.kt      # 唯一 Activity
      │  └─ ui/
      │     ├─ DashboardApp.kt        # 主页/导出页切换 + BackHandler
      │     ├─ charts/
      │     │  └─ DashboardCharts.kt  # MPAndroidChart 饼图/折线图 AndroidView 封装
      │     ├─ components/
      │     │  └─ DashboardComponents.kt # 指标卡、图表卡、四态组件
      │     ├─ model/
      │     │  └─ DashboardModels.kt  # 统计模型、UiState、静态示意数据
      │     ├─ screens/
      │     │  ├─ DashboardHomeScreen.kt
      │     │  └─ DashboardExportScreen.kt
      │     └─ theme/
      │        └─ Theme.kt
      └─ res/
         ├─ drawable/ic_launcher.xml
         └─ values/
            ├─ colors.xml             # 图表分类色
            ├─ strings.xml
            └─ themes.xml
```

## 文件职责补充

### 主 App

- `Routes.kt`
  - `ingredients`：食材列表（一级）。
  - `ingredients/new`：新增食材。
  - `ingredients/detail/{ingredientId}`：食材详情。
  - `recipes`：本地菜谱列表（一级）。
  - `recipes/new`：新增菜谱。
  - `recipes/detail/{recipeId}`：本地菜谱详情。
  - `recipes/online`：在线搜索（一级）。
  - `recipes/online/{mealId}`：在线菜谱详情。
- `FridgeApp.kt`：只负责 Scaffold 与导航外壳，不查询数据库；一级页 3 个底部 Tab，详情/新增显示返回。
- `ScreenStateContent.kt`：把 `UiState` 分发到通用加载/空/错误布局，成功态交给页面内容。
- `StateLayouts.kt`：`LoadingStateLayout`、`EmptyStateLayout`、`ErrorStateLayout`，所有数据页面复用。
- `FridgeNavHost.kt`：8 个目的地；食材/菜谱/在线页分别创建 ViewModel，并从 `FridgeApplication` 注入 Repository。
- `domain/matching/RecipeMatcher.kt`：纯 Kotlin、无 Android/Room 依赖；输入 `MatchableRecipe` 与库存 `typeId` 集合，输出匹配等级/已有/缺少；单测在 `src/test/java/.../RecipeMatcherTest.kt`。
- `data/local/`：不依赖 UI；Dao 返回 `Flow` 或 `suspend`，禁止在主线程调用。
- `data/remote/`：不依赖 UI；只由 `DefaultOnlineRecipeRepository` 调用；`NetworkResult` 统一异常，Retrofit 接口与 DTO 不做业务判断。
- `data/local/entity/IngredientType.kt`：食材品类主数据，`normalized_name` 唯一；菜谱与库存共同引用。
- `data/local/entity/Ingredient.kt`：库存批次；`type_id` 外键到品类，`name/category` 为展示快照。
- `data/local/entity/RecipeIngredient.kt`：复合主键 `(recipe_id, type_id)`，双外键；表达菜谱-品类多对多。
- `data/local/relation/`：`IngredientWithType`、`RecipeIngredientWithType`、`RecipeWithIngredients`，用于一次性取回关联数据。
- `data/repository/`：`IngredientRepository`、`RecipeRepository`、`OnlineRecipeRepository` 为契约；三个 `Default*Repository` 负责事务、校验、品类复用、网络异常与保存映射。
- `ui/model/IngredientUiModel.kt`：Entity → UI 的日期/数量/剩余天数转换，Composable 不直接做时间计算。
- `ui/model/RecipeUiModel.kt`：菜谱与所需食材的展示模型，拍平 `RecipeWithIngredients` 并按食材名排序。
- `ui/screens/ingredients/`：列表、详情、新增三套 Screen + ViewModel，共用 `IngredientForm`/`IngredientFormState`；`IngredientError` 做统一中文错误映射。
- `ui/screens/recipes/`：本地菜谱列表/详情/新增三套 Screen + ViewModel，列表提供“全部/推荐”筛选与匹配徽标，详情提供已有/缺少高亮；共用 `RecipeForm`/`RecipeFormState`；在线搜索/详情两套 Screen + ViewModel；`RecipeError` 做本地错误映射，`RecipeMatchMapper`/`RecipeMatchComponents` 负责匹配展示映射与徽标。

### 数据模型文档

- `DATA_MODEL.md`：v2 ER 图、四张表字段/索引/外键、迁移步骤、`RecipeMatcher` 匹配口径与边界。
- `MVVM_LAYER_DESIGN.md`：分层职责、DAO/Repository/ViewModel 契约、线程与错误映射。
- `UI_PROTOTYPE.md`：页面布局、组件、交互、状态矩阵、权限降级与可访问性。

### 看板

- `DashboardModels.kt`：统计快照与四态；`DashboardSampleData` 仅用于骨架演示。
- `DashboardCharts.kt`：只接收 `List<CategoryStat>` / `List<TrendPoint>`，不接触 Provider。
- `DashboardHomeScreen.kt`：指标卡 → 饼图 → 折线图 → 数据来源 → 导出入口。
- `DashboardExportScreen.kt`：导出内容/字段/文件命名说明；SAF 写入未实现。
- 看板没有 Room、没有 Entity；数据边界只有 ContentResolver。

## 构建产物位置

```text
refrigerator_main/build/outputs/apk/debug/refrigerator_main-debug.apk
dashboard_module/build/outputs/apk/debug/dashboard_module-debug.apk
```

`build/`、`.gradle/`、`local.properties` 均已在 `.gitignore` 中排除。
