# 需求清单与页面清单

## 1. 本轮范围

本轮（项目初始化）只交付：

- 可导入 Android Studio 的双模块工程骨架。
- 食材 / 菜谱数据实体、Dao、Database 草稿及字段注释。
- 主 App 5 个页面、看板 2 个页面的可运行 UI 骨架。
- 列表 → 详情导航。
- 加载态、空态、错误态 UI 组件。
- 需求、架构、目录结构文档。

不包含业务逻辑：没有 ViewModel / Repository，不写数据库，不申请运行时权限，不做通知、匹配、CSV 导出与跨 App 数据读取。

## 2. 业务实体

### 2.1 Ingredient（食材库存）

表名：`ingredients`。一条记录代表“一次入库”，不是食材字典；同名食材允许多条记录。

| 字段 | 类型 | 约束 / 含义 |
| --- | --- | --- |
| `id` | Long | 自增主键，0 表示尚未插入 |
| `name` | String | 食材名称，如“番茄” |
| `category` | String | 分类，如蔬菜、蛋奶、肉类、乳制品、调味品 |
| `stored_at` | Long | 入库时间，Unix 毫秒时间戳（UTC） |
| `shelf_life_days` | Int | 保质期天数，从入库时间起算，必须 > 0 |
| `quantity` | Double | 当前数量，允许小数（如 0.5 kg） |
| `unit` | String | 单位，如个、克、毫升、袋 |

索引：`name`、`category`、`stored_at`。

到期时间不落库，由 `stored_at + shelf_life_days * 86_400_000` 派生，避免两份数据不一致。

### 2.2 Recipe（菜谱）

表名：`recipes`。

| 字段 | 类型 | 约束 / 含义 |
| --- | --- | --- |
| `id` | Long | 自增主键 |
| `name` | String | 菜名 |
| `steps` | String | 做法，按换行分步保存；展示时拆分为步骤列表 |

### 2.3 RecipeIngredient（菜谱所需食材）

表名：`recipe_ingredients`。菜谱与“所需食材名称”的一对多关联。

| 字段 | 类型 | 约束 / 含义 |
| --- | --- | --- |
| `recipe_id` | Long | 复合主键之一；外键 → `recipes.id`，级联删除 |
| `ingredient_name` | String | 复合主键之一；所需食材名称 |
| `quantity` | Double | 所需数量 |
| `unit` | String | 所需单位 |

索引：`recipe_id`、`ingredient_name`。

**关键决策**：`ingredient_name` 不设到 `ingredients` 的外键。菜谱是模板，允许在冰箱没有该食材时也存在；匹配时按名称与库存做匹配，而不是要求先有库存记录。

关联查询使用 `RecipeWithIngredients`（`@Embedded Recipe` + `@Relation`）。

### 2.4 数据校验（后续 Repository 层）

数据库不负责全部业务校验，以下规则在 Repository / ViewModel 中补：

- `shelf_life_days > 0`。
- `quantity >= 0`。
- 名称、分类、单位去首尾空格后非空。
- 入库时间不能晚于当前时间（允许当天）。
- 删除菜谱时关联所需食材由外键级联清理。

## 3. 功能需求清单

状态说明：**骨架** = 本轮只有页面/组件，无真实业务；**未开始** = 本轮未实现。

| 编号 | 功能 | 优先级 | 本轮状态 | 验收要点 |
| --- | --- | --- | --- | --- |
| F-01 | 食材列表 | P0 | 骨架 | 展示名称、分类、数量、单位、剩余天数/新鲜度；支持列表 → 详情 |
| F-02 | 新增食材 | P0 | 骨架 | 表单字段完整；后续校验数量/保质期/必填项，保存后回列表 |
| F-03 | 食材详情 | P0 | 骨架 | 展示入库时间、保质期、数量、到期状态 |
| F-04 | 菜谱列表 | P0 | 骨架 | 展示菜名、所需食材摘要；列表 → 详情 |
| F-05 | 菜谱详情 | P0 | 骨架 | 展示所需食材与分步做法 |
| F-06 | 食材匹配 | P1 | 未开始 | 根据现有库存输出“能做 / 部分能做 / 缺少食材” |
| F-07 | 到期提醒 | P1 | 未开始 | 到期前按规则发通知；通知权限拒绝后主流程可用 |
| F-08 | 看板统计 | P1 | 骨架 | 分类数量、临期/过期数量；饼图 + 折线图 |
| F-09 | CSV 导出 | P1 | 骨架 | 统计 CSV；SAF 选择保存位置；取消不报错 |
| F-10 | 跨 App 数据 | P1 | 未开始 | 主 App 只读 ContentProvider + signature 权限 |
| F-11 | 状态与异常 | P0 | 基础组件 | 每个数据页面都有加载/空/错误态与重试 |
| F-12 | 权限降级 | P0 | 声明/文档 | 拒绝通知或取消文件选择不崩溃，有明确提示 |

## 4. 页面清单

### 4.1 主 App（refrigerator_main）

| 页面 | 路由 | 入口 | 本轮内容 |
| --- | --- | --- | --- |
| 首页食材列表 | `ingredients` | 启动页 / 底部“食材” | 4 条静态食材卡片、新鲜度标签、顶部“新增” |
| 食材详情 | `ingredients/detail/{ingredientId}` | 点击食材卡片 | 基本信息、保质期、状态；空态处理 |
| 新增食材 | `ingredients/new` | 首页右上角“新增” | 表单骨架，字段禁用，保存按钮禁用 |
| 菜谱列表 | `recipes` | 底部“菜谱” | 3 条静态菜谱卡片、所需食材摘要 |
| 菜谱详情 | `recipes/detail/{recipeId}` | 点击菜谱卡片 | 所需食材、编号步骤；空态处理 |

导航结构：

```text
ingredients (一级)
├─ ingredients/new
└─ ingredients/detail/{id}

recipes (一级)
└─ recipes/detail/{id}
```

- 一级页面显示底部导航（食材 / 菜谱）。
- 详情、新增页显示顶部返回按钮。
- 详情路由使用 `.../detail/{id}`，避免与 `ingredients/new` 的通配匹配冲突。

### 4.2 伴生看板（dashboard_module）

| 页面 | 入口 | 本轮内容 |
| --- | --- | --- |
| 统计看板主页 | 启动页 | 3 个指标卡、分类饼图、近 7 天临期折线图、数据来源说明、导出按钮 |
| 导出页面 | 主页右上角“导出” / 底部按钮 | 导出内容、CSV 字段、文件命名、SAF 说明；按钮禁用 |

看板内部只有两个页面，使用 `DashboardScreen` 状态切换，不引入 Navigation 组件；系统返回键从导出页回到主页。

## 5. 状态与异常要求

所有数据页面必须显式处理：

1. **加载态**：进度指示 + “正在加载/统计”文案，不能白屏。
2. **空态**：说明为什么为空，并给出下一步动作（如“点击右上角新增”）。
3. **错误态**：可读的中文错误信息 + 重试按钮，不能只打 Log。
4. **成功态**：正常内容。

本轮已提供：

- 主 App：`UiState` + `ScreenStateContent`，列表/详情按四态渲染。
- 看板：`DashboardUiState` + `DashboardStateContent`。

## 6. 权限与降级

### 6.1 通知权限

- Manifest 已声明 `android.permission.POST_NOTIFICATIONS`。
- Android 13（API 33）及以上需要运行时申请；本轮不申请。
- 后续拒绝策略：
  - 不崩溃、不阻塞食材管理。
  - 在提醒设置位置展示“通知已关闭，到期提醒不可用”的说明。
  - 提供跳转系统通知设置的入口（可选）。
- Android 12 及以下无需该运行时权限。

### 6.2 文件权限

- CSV 导出使用 Storage Access Framework（`ActivityResultContracts.CreateDocument("text/csv")`）。
- 不申请 `READ/WRITE_EXTERNAL_STORAGE`，不使用 `MANAGE_EXTERNAL_STORAGE`。
- 用户取消保存：不提示错误、不重试、不崩溃。
- 写入失败：提示“导出失败，请更换保存位置后重试”。

### 6.3 跨 App 数据权限

- 不使用 `sharedUserId`。
- 主 App 暴露只读 ContentProvider，使用 signature 级别自定义权限。
- 看板只持有读权限；主 App 是唯一写入方。

## 7. 本轮验收清单

- [x] 根工程可被 Android Studio 识别，两个 application 模块均存在。
- [x] `minSdk 24`、Kotlin、Compose、Room、KSP 配置完成。
- [x] `Ingredient`、`Recipe`、`RecipeIngredient` 实体及字段注释。
- [x] `IngredientDao`、`RecipeDao`、`FridgeDatabase` 草稿。
- [x] 主 App 5 个页面 + 列表到详情导航。
- [x] 看板 2 个页面 + MPAndroidChart 示意图。
- [x] 加载/空/错误/成功四态组件。
- [x] 权限声明与降级策略文档。
- [x] 需求清单、页面清单、目录结构。
- [x] 双模块 `assembleDebug` 编译通过（见构建记录）。

## 8. 明确不做（本轮）

- 不实现登录、云同步、多用户。
- 不实现食材编辑/删除/批量操作。
- 不实现菜谱新增/编辑。
- 不实现到期通知调度。
- 不实现 ContentProvider。
- 不实现 CSV 真正写文件。
- 不引入 Flutter、uni-app 等跨平台方案。
