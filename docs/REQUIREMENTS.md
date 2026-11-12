# 需求清单与页面清单

## 1. 当前范围

当前处于第二轮：**完善数据模型与页面原型设计**。只做设计与契约，不写业务逻辑、不写页面代码。

已交付：

- Room v2 表结构：食材品类、库存批次、菜谱、菜谱-食材关联。
- `IngredientType`、`Ingredient`、`Recipe`、`RecipeIngredient` 实体与索引/外键。
- `IngredientTypeDao`、`IngredientDao`、`RecipeDao` 与关系对象。
- `IngredientRepository`、`RecipeRepository` 接口草稿与表单 DTO（无实现）。
- 7 个页面的 UI 原型说明（布局、组件、交互、状态、跳转）。
- v1 → v2 迁移设计与迁移 SQL。

明确未实现：

- Repository / ViewModel 的具体实现。
- 页面接入真实数据；页面仍展示 `SampleData`。
- 新增/编辑/删除食材的保存逻辑。
- 菜谱匹配算法、到期通知、运行时权限申请。
- 主 App 只读 ContentProvider、看板读取真实数据、CSV 写入。

## 2. 业务实体

### 2.1 IngredientType（食材品类 / 食材字典）

表名：`ingredient_types`。代表“西红柿”这种稳定的食材身份，不随某次入库变化。

| 字段 | 类型 | 约束 / 含义 |
| --- | --- | --- |
| `id` | Long | 自增主键，0 表示尚未插入 |
| `name` | String | 展示名称，如“西红柿” |
| `normalized_name` | String | 规范化名称，唯一索引；匹配键 |
| `category` | String | 分类，索引；如蔬菜、肉蛋、乳制品 |
| `default_unit` | String | 默认单位，新增表单的默认值 |
| `created_at` | Long | 创建时间，Unix 毫秒 |

规范化规则（Repository 负责）：`trim`、转小写、全角转半角、连续空白折叠。唯一索引防止“鸡蛋 / 鸡蛋 ”重复建品类。

### 2.2 Ingredient（食材库存批次）

表名：`ingredients`。一条记录代表“一次入库”，同名食材允许多条记录。

| 字段 | 类型 | 约束 / 含义 |
| --- | --- | --- |
| `id` | Long | 自增主键，0 表示尚未插入 |
| `type_id` | Long | 外键 → `ingredient_types.id`，`ON DELETE RESTRICT`；匹配/统计以它为准 |
| `name` | String | 入库时的名称快照 |
| `category` | String | 入库时的分类快照 |
| `stored_at` | Long | 入库时间，Unix 毫秒时间戳（UTC） |
| `shelf_life_days` | Int | 保质期天数，必须 > 0 |
| `quantity` | Double | 当前数量，允许小数（如 0.5 kg） |
| `unit` | String | 单位，如个、克、毫升、袋 |

索引：`type_id`、`name`、`category`、`stored_at`。

到期时间不落库，由 `stored_at + shelf_life_days * 86_400_000` 派生，避免两份数据不一致。

### 2.3 Recipe（菜谱）

表名：`recipes`。

| 字段 | 类型 | 约束 / 含义 |
| --- | --- | --- |
| `id` | Long | 自增主键 |
| `name` | String | 菜名，索引 |
| `steps` | String | 做法，按换行分步保存；展示时拆分为步骤列表 |

### 2.4 RecipeIngredient（菜谱-食材关联）

表名：`recipe_ingredients`。`Recipe` ↔ `IngredientType` 的多对多中间表。

| 字段 | 类型 | 约束 / 含义 |
| --- | --- | --- |
| `recipe_id` | Long | 复合主键之一；外键 → `recipes.id`，级联删除 |
| `type_id` | Long | 复合主键之一；外键 → `ingredient_types.id`，RESTRICT |
| `quantity` | Double | 这道菜所需数量 |
| `unit` | String | 所需单位 |

索引：`recipe_id`、`type_id`。复合主键保证同一菜谱内同一品类只出现一次。

### 2.5 表间映射

```text
Recipe 1 ─── N RecipeIngredient N ─── 1 IngredientType
                                             │ 1
                                             │
                                             N
                                      Ingredient（库存批次）
```

- 一个菜谱需要多种食材；一种食材品类可出现在多个菜谱中 → 多对多。
- 一个品类对应多条库存批次（多次购买）→ 一对多。
- 删除菜谱：级联删除关联行，不影响品类与库存。
- 删除品类：被库存或菜谱引用时 RESTRICT，必须先解除引用。
- 菜谱可以先于库存存在：保存菜谱时若品类不存在，Repository 在同一事务内创建品类。

### 2.6 数据校验（Repository 层）

数据库只负责结构约束，以下业务规则在 Repository / ViewModel 中补：

- `quantity >= 0`（库存批次；0 表示已用完，保留历史记录）。
- `shelf_life_days` 为 1–3650。
- 名称、分类、单位去首尾空格后非空；名称 1–30 字。
- 入库时间不能晚于当前时间（允许 5 分钟时钟误差）。
- `RecipeIngredient.quantity > 0`，单位非空。
- 删除菜谱时关联所需食材由外键级联清理。

## 3. 功能需求清单

状态说明：**设计完成** = 表/契约/原型已定，无实现；**骨架** = 页面已有静态 UI；**未开始** = 本轮未实现。

| 编号 | 功能 | 优先级 | 状态 | 验收要点 |
| --- | --- | --- | --- | --- |
| F-01 | 食材列表 | P0 | 骨架 | 展示名称、分类、数量、单位、剩余天数/新鲜度；支持列表 → 详情 |
| F-02 | 新增食材 | P0 | 骨架 | 表单字段完整；校验数量/保质期/必填项；保存后回列表 |
| F-03 | 食材详情 | P0 | 骨架 | 展示入库时间、保质期、数量、到期状态；删除二次确认 |
| F-04 | 菜谱列表 | P0 | 骨架 | 展示菜名、所需食材摘要；列表 → 详情；支持匹配筛选 |
| F-05 | 菜谱详情 | P0 | 骨架 | 展示所需食材、缺失项与分步做法 |
| F-06 | 食材匹配 | P1 | 设计完成 | 按 `type_id` 聚合库存，输出“能做 / 部分能做 / 缺少食材” |
| F-07 | 到期提醒 | P1 | 未开始 | 到期前按规则发通知；通知权限拒绝后主流程可用 |
| F-08 | 看板统计 | P1 | 骨架 | 分类数量、临期/过期数量；饼图 + 折线图 |
| F-09 | CSV 导出 | P1 | 骨架 | 统计 CSV；SAF 选择保存位置；取消不报错 |
| F-10 | 跨 App 数据 | P1 | 未开始 | 主 App 只读 ContentProvider + signature 权限 |
| F-11 | 数据模型 | P0 | 设计完成 | 四张表、主外键、索引、迁移 SQL 完整 |
| F-12 | MVVM 契约 | P0 | 设计完成 | Entity/Dao/Repository/ViewModel 职责与接口明确 |
| F-13 | 页面原型 | P0 | 设计完成 | 7 个页面的布局、交互、状态、跳转有文档 |
| F-14 | 状态与异常 | P0 | 基础组件 | 每个数据页面都有加载/空/错误态与重试 |
| F-15 | 权限降级 | P0 | 声明/文档 | 拒绝通知或取消文件选择不崩溃，有明确提示 |

## 4. 页面清单

### 4.1 主 App（refrigerator_main）

| 页面 | 路由 | 入口 | 目标内容 |
| --- | --- | --- | --- |
| 首页食材列表 | `ingredients` | 启动页 / 底部“食材” | 搜索、状态筛选、本周概览、食材卡片、新增入口 |
| 食材详情 | `ingredients/detail/{ingredientId}` | 点击食材卡片 | 名称/分类/数量/入库/到期、保质期进度、匹配提示、删除 |
| 新增食材 | `ingredients/new` | 首页右上角“新增” | 名称、分类、入库时间、保质期、数量、单位、到期预览、保存 |
| 菜谱列表 | `recipes` | 底部“菜谱” | 搜索、匹配筛选、菜谱卡片、缺失食材摘要 |
| 菜谱详情 | `recipes/detail/{recipeId}` | 点击菜谱卡片 | 匹配徽标、所需食材及备齐状态、分步做法 |

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
- 切换 tab 使用 `saveState` / `restoreState`，保留滚动位置。

### 4.2 伴生看板（dashboard_module）

| 页面 | 入口 | 目标内容 |
| --- | --- | --- |
| 统计看板主页 | 启动页 | 数据来源状态、3 个指标卡、分类饼图、7 天临期折线图、即将过期列表、导出入口 |
| 导出页面 | 主页“导出” | 导出范围、字段预览、文件名、SAF 保存、成功/失败状态 |

看板内部只有两个页面，使用状态切换，不引入 Navigation 组件；系统返回键从导出页回到主页。

### 4.3 UI 原型文档

逐页布局、组件、交互、状态矩阵、权限降级与可访问性检查见 [UI_PROTOTYPE.md](UI_PROTOTYPE.md)。

## 5. 状态与异常要求

所有数据页面必须显式处理：

1. **加载态**：进度指示 + “正在加载/统计”文案，不能白屏。
2. **空态**：说明为什么为空，并给出下一步动作（如“点击右上角新增”）。
3. **错误态**：可读的中文错误信息 + 重试按钮，不能只打 Log。
4. **成功态**：正常内容。

现有基础：

- 主 App：`UiState` + `ScreenStateContent`，列表/详情按四态渲染。
- 看板：`DashboardUiState` + `DashboardStateContent`。

页面状态矩阵：

| 页面 | Loading | Empty | Error | Success |
| --- | --- | --- | --- | --- |
| 食材列表 | 骨架卡 | 冰箱空 | 查询失败 + 重试 | 概览 + 列表 |
| 食材详情 | 头部/信息骨架 | 记录已删除 | 查询失败 + 重试 | 详情 + 操作 |
| 新增食材 | 无（表单） | 无 | 字段错误 + Snackbar | 保存成功返回 |
| 菜谱列表 | 骨架卡 | 暂无菜谱/无筛选结果 | 查询失败 + 重试 | 菜谱卡 |
| 菜谱详情 | 头部/步骤骨架 | 菜谱已删除 | 查询失败 + 重试 | 食材 + 步骤 |
| 看板主页 | 指标/图表骨架 | 冰箱无食材 | Provider/权限错误 | 指标 + 图表 |
| 导出页 | 查询中禁用按钮 | 无可导出数据 | 写入失败 + 重试 | 成功路径 |

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
- 无权限/未安装/签名不一致时，看板进入错误态并给出中文提示，不崩溃。

## 7. 本轮验收清单

- [x] v2 四张表：`ingredient_types`、`ingredients`、`recipes`、`recipe_ingredients`。
- [x] 主键、外键、唯一索引、查询索引完整。
- [x] 菜谱-食材多对多关联表与 `RecipeWithIngredients` 关系对象。
- [x] `IngredientRepository`、`RecipeRepository` 接口草稿与 DTO。
- [x] 7 个页面 UI 原型说明。
- [x] v1 → v2 迁移 SQL 与迁移测试计划。
- [x] 双模块 `assembleDebug` 编译通过（2026-11-12 构建验证）。
- [x] Room schema `2.json` 已生成并提交。

## 8. 明确不做（本轮）

- 不实现 Repository / ViewModel 的具体业务逻辑。
- 不实现页面代码改动；现有页面保持静态骨架。
- 不实现食材编辑/删除的保存逻辑。
- 不实现菜谱新增/编辑/匹配算法。
- 不实现到期通知调度与运行时权限申请。
- 不实现 ContentProvider。
- 不实现 CSV 真正写文件。
- 不引入 Flutter、uni-app 等跨平台方案。
