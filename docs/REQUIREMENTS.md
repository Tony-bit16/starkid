# 需求清单与页面清单

## 1. 当前范围

当前处于第十五轮：**看板过期趋势折线图、手动刷新与短时缓存**。看板新增未来 7 天到期趋势、手动刷新、60 秒进程级缓存，保留卡片/饼图/趋势的分区布局；前十四轮的食材/菜谱 CRUD、在线搜索、匹配推荐、到期提醒、看板统计/饼图/CSV、设置页全部保留。

已交付：

- Room v2 表结构：食材品类、库存批次、菜谱、菜谱-食材关联。
- `IngredientType`、`Ingredient`、`Recipe`、`RecipeIngredient` 实体与索引/外键。
- `IngredientTypeDao`、`IngredientDao`、`RecipeDao` 与关系对象。
- 食材模块 `IngredientRepository` 契约与 `DefaultIngredientRepository` 实现。
- 菜谱模块 `RecipeRepository` 契约与 `DefaultRecipeRepository` 实现。
- 在线菜谱 `OnlineRecipeRepository` 契约与 `DefaultOnlineRecipeRepository` 实现。
- Retrofit/OkHttp/Gson 网络层、统一 `NetworkResult`/`BaseRemoteDataSource`/异常文案。
- 食材 3 个 ViewModel、本地菜谱 3 个 ViewModel、在线搜索/详情 2 个 ViewModel。
- 食材列表/新增/详情、菜谱列表/新增/详情、在线搜索/详情共 8 个主 App 页面。
- 在线详情“保存到本地”复用本地菜谱事务，保存成功后本地列表自动刷新。
- 网络失败弹窗 + 可重试错误页；本地菜谱在无网时仍可正常使用。
- 纯算法 `RecipeMatcher` 与 JVM 单测：三级分类、稳定排序、空库存/空菜谱边界。
- 菜谱库「全部菜谱 / 推荐菜谱」筛选；推荐卡片展示等级、已备齐数量与完整缺少食材。
- 菜谱详情按库存显示“已有/缺少”高亮；无库存时给出提示。
- 匹配在 `Dispatchers.Default` 异步计算，加载/空/错误四态由统一状态布局承载。
- `ExpiryCalculator` 统一计算到期日与剩余天数；0..3 天为临期，负数天为已过期。
- WorkManager `2.10.1` 每日 09:00 唯一周期任务；通知渠道、合并通知、点击跳转食材库。
- Android 13+ 通知权限说明、申请、拒绝弹窗、永久拒绝“去设置”；未授权仍可查看全部食材状态。
- 食材列表三色标签：新鲜（绿）、临期（橙）、已过期（红），阈值与通知共用。
- 列表/详情加载、空、错误四态与重试；保存/删除失败的中文错误提示。
- 食材、菜谱、在线搜索三个列表的下拉刷新；刷新保留旧内容，失败只弹窗不丢列表。
- `AppErrorHandler` + `safeNavigate`/`safePopBackStack`：页面跳转异常进入根 Scaffold Snackbar，不崩溃。
- 新增食材/新增菜谱首次提交后即时校验；在线搜索为空或超过 40 字即时提示。
- 统一 `Shapes`（6/10/14/18/24dp），卡片、输入框、按钮视觉一致。
- 主 App 只读 `IngredientContentProvider`：`com.fridge.main.provider` + signature 读写权限；列白名单、只读拒绝写操作。
- 看板 `ContentResolver` 数据通道：`DashboardRepository`/`ProviderClient`/ViewModel 四态；展示库存条目、品种数与食材明细。
- 看板 `DashboardStatsCalculator` 统一统计口径：总条目、0..3 天临期、负数已过期、按分类统计条目数；JVM 单测覆盖边界与排序。
- 看板三张指标卡（总食材数/临期/已过期）+ MPAndroidChart 饼图；扇区标注“分类名称 + 条目数”，中心显示总数。
- 看板统计计算在 `Dispatchers.Default`；加载、空、错误、成功四态与重试完整，主 App 未安装/无食材时仍可独立展示。
- 看板 CSV 导出：名称、分类、数量、单位、入库时间、剩余保质期、过期状态；UTF-8 BOM + CRLF，字段按 RFC 4180 转义。
- Android 10+ 用 MediaStore 写 `Downloads/Fridge`，无需存储权限；Android 7-9 才申请 WRITE，拒绝/永久拒绝不阻断统计浏览。
- 导出前重新读取 Provider；写入在 `Dispatchers.IO`，页面显示“正在导出…”，成功/失败/无数据/无查看器均有弹窗。
- FileProvider（`${applicationId}.fileprovider`）只暴露 `cache/exports`，成功后可点击“打开文件”交给系统查看器；无查看器给出明确错误。
- `DashboardCsvFormatter` 5 个 JVM 单测覆盖 BOM/CRLF、转义、状态边界与数量格式。
- 看板「未来 7 天过期趋势」折线图：按到期日期聚合，空日期补 0，点旁标注每日条目数；无临期时显示空态而不是空坐标系。
- 看板顶栏「刷新」清空缓存并强制查询主 App；刷新时保留卡片/饼图/趋势，顶部进度与按钮“刷新中…”至少显示 450ms。
- 看板 Repository 60 秒进程级缓存：重复打开可在 TTL 内复用；主 App `notifyChange` 与手动刷新强制拉取最新数据。
- 趋势/饼图/指标卡在横竖屏下按可用宽度自适应，页面可滚动。
- 看板 `DashboardStatsCalculatorTest` 5 个、`DashboardRepositoryCacheTest` 2 个、CSV 6 个 JVM 单测。
- 食材库顶部“设置”入口：提前提醒天数（1–7，默认 3）、每日提醒时间、过期通知开关、通知权限状态与“去设置”、关于页。
- Preferences DataStore（`fridge_settings`）统一保存四项配置；`SettingsRepository` 抽象存储，读写走 IO 协程，保存后 Snackbar 提示。
- WorkManager 启动用 `KEEP` 保留任务，设置变更用 `REPLACE` 按新时间重排；关闭通知时取消唯一周期任务并清理旧通知。
- 设置页读取期间显示加载态，读取失败可重试；关于页展示版本、功能、数据边界与健康免责声明。
- 12 个页面（主 App 10 + 看板 2）的 UI 原型说明。

明确未实现：

- 按库存数量是否足够、临期食材优先级进行匹配；当前只判断品类是否有库存。
- 按日期范围导出、趋势区间切换；当前 CSV 导出全量库存、趋势固定未来 7 天。
- 在线菜谱图片、收藏、分页与中文 API 适配。

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

规范化规则（Repository 负责）：`trim`、连续空白折叠、转小写。全角/半角归一化与别名合并留到后续轮次。唯一索引防止“鸡蛋 / 鸡蛋 ”重复建品类。

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

到期时间不落库，按本地日历日派生：`expiryDate = stored_at 的本地日期 + shelf_life_days`，剩余天数用日历日差计算，避免跨时区/夏令时误差。

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

- `quantity > 0`（库存批次；当前不允许保存 0，避免误操作产生无意义记录）。
- `shelf_life_days` 为 1–36500。
- 名称、分类、单位去首尾空格后非空；名称 1–30 字，分类 1–20 字，单位 1–10 字。
- 入库时间不能晚于当前时间（表单只允许选择本地日历日）。
- 菜名 1–40 字；做法去空行后 1–30 步，总长度 ≤ 4000 字。
- 菜谱至少 1 种、最多 30 种食材；食材名 1–30 字，同菜谱内规范化名称不可重复。
- `RecipeIngredient.quantity > 0` 且 ≤ 1,000,000，单位非空且 ≤ 10 字。
- 删除菜谱时关联所需食材由外键级联清理。

## 3. 功能需求清单

状态说明：**已实现** = 已接 Room 并通过构建/真机验证；**设计完成** = 表/契约/原型已定，无实现；**骨架** = 页面已有静态 UI；**未开始** = 本轮未实现。

| 编号 | 功能 | 优先级 | 状态 | 验收要点 |
| --- | --- | --- | --- | --- |
| F-01 | 食材列表 | P0 | 已实现 | 从 Room 读取；展示名称、分类、数量、单位、剩余天数/新鲜度；四态与重试；列表 → 详情 |
| F-02 | 新增食材 | P0 | 已实现 | 表单字段完整；校验必填、数量/保质期；保存写入 Room 后回列表 |
| F-03 | 食材详情 | P0 | 已实现 | 展示入库时间、保质期、数量、到期状态；编辑保存；删除二次确认 |
| F-04 | 菜谱列表 | P0 | 已实现 | 从 Room 读取；展示菜名、食材摘要、步数；四态与重试；列表 → 详情 |
| F-05 | 菜谱详情 | P0 | 已实现 | 展示所需食材、分步做法；编辑保存；删除二次确认 |
| F-16 | 新增菜谱 | P0 | 已实现 | 菜名、动态食材清单、多行做法；校验通过后写入 Room 并返回列表 |
| F-17 | 在线菜谱搜索 | P0 | 已实现 | TheMealDB 关键词搜索；搜索框 + 结果列表；加载/空/错误/成功；网络失败弹窗 |
| F-18 | 在线菜谱详情与保存 | P0 | 已实现 | lookup 详情；食材/步骤完整展示；“保存到本地”写入 Room，本地列表自动刷新 |
| F-06 | 食材匹配推荐 | P0 | 已实现 | 纯算法按品类库存分级并排序；推荐筛选、等级/缺少食材展示、详情已有/缺少高亮；库存变化自动重算 |
| F-07 | 到期提醒 | P1 | 已实现 | 默认每日 09:00（可在设置调整）；按提前 1–7 天判定临期；多条合并通知；点击跳食材库；拒绝权限主流程可用 |
| F-08 | 看板统计、饼图与趋势 | P1 | 已实现 | 总条目/临期/已过期指标卡；分类库存占比饼图；未来 7 天过期趋势折线图（标注每日数值）；手动刷新与 60 秒缓存 |
| F-09 | CSV 导出 | P1 | 已实现 | 全量食材导出；UTF-8 BOM/CRLF；MediaStore 下载目录；FileProvider 打开；权限降级与失败提示 |
| F-10 | 跨 App 数据 | P1 | 已实现 | 主 App 只读 ContentProvider + signature 权限；看板 ContentResolver 查询并展示明细 |
| F-11 | 数据模型 | P0 | 设计完成 | 四张表、主外键、索引、迁移 SQL 完整 |
| F-12 | MVVM 契约 | P0 | 已实现 | 食材/菜谱/在线菜谱的 Entity、Dao、Repository、ViewModel 均已实现 |
| F-13 | 页面原型 | P0 | 设计完成 | 12 个页面的布局、交互、状态、跳转有文档 |
| F-14 | 状态与异常 | P0 | 已实现 | 食材/菜谱/在线搜索/详情四态、重试；数据库与网络异常转中文提示 |
| F-15 | 权限降级 | P0 | 已实现 | 通知权限拒绝/永久拒绝不崩溃；Android 7-9 存储权限拒绝仍有弹窗与“去设置”，统计浏览不中断 |

## 4. 页面清单

### 4.1 主 App（refrigerator_main）

| 页面 | 路由 | 入口 | 目标内容 |
| --- | --- | --- | --- |
| 首页食材列表 | `ingredients` | 启动页 / 底部“食材库” | 通知权限提示、库存计数、三色状态标签、食材卡片、新增入口 |
| 食材详情 | `ingredients/detail/{ingredientId}` | 点击食材卡片 | 名称/分类/数量/入库/到期、保质期进度、匹配提示、删除 |
| 新增食材 | `ingredients/new` | 首页右上角“新增” | 名称、分类、入库时间、保质期、数量、单位、到期预览、保存 |
| 菜谱列表 | `recipes` | 底部“菜谱库” | “全部/推荐”筛选、菜谱卡片、匹配等级、缺少食材摘要 |
| 菜谱详情 | `recipes/detail/{recipeId}` | 点击菜谱卡片 | 匹配徽标、所需食材及备齐状态、分步做法 |
| 新增菜谱 | `recipes/new` | 菜谱库右上角“新增” | 菜名、动态食材清单、多行做法、保存 |
| 在线搜索 | `recipes/online` | 底部“在线搜索” | 菜名搜索框、结果列表、加载/空/错误/成功、失败弹窗 |
| 在线菜谱详情 | `recipes/online/{mealId}` | 点击在线搜索结果 | 完整食材/步骤、来源提示、“保存到本地”、保存结果 |
| 设置 | `settings` | 食材库顶部“设置” | 通知权限状态/去设置、提前天数、提醒时间、通知开关、关于入口、加载/错误态、保存 Snackbar |
| 关于 | `about` | 设置页“关于冰箱管家” | 应用名、版本、功能说明、数据/隐私与健康免责声明 |

> 上表包含最终页面目标。第八轮已实现到期提醒、通知权限降级与三色状态标签；
> 本地菜谱关键词搜索、分类筛选、食材搜索/筛选等增强项仍未实现。

导航结构：

```text
ingredients (一级)
├─ ingredients/new
├─ ingredients/detail/{id}
└─ settings
   └─ about

recipes (一级)
├─ recipes/new
├─ recipes/detail/{id}
└─ recipes/online (一级)
   └─ recipes/online/{mealId}
```

- 一级页面显示底部导航（食材库 / 菜谱库 / 在线搜索）。
- 详情、新增页显示顶部返回按钮。
- 详情路由使用 `.../detail/{id}`，避免与 `ingredients/new` 的通配匹配冲突。
- 在线详情使用字符串 `mealId`，与本地 Long 型 `recipeId` 分开。
- 切换 tab 使用 `saveState` / `restoreState`，保留滚动位置。

### 4.2 伴生看板（dashboard_module）

| 页面 | 入口 | 目标内容 |
| --- | --- | --- |
| 统计看板主页 | 启动页 | 总食材数/临期/已过期指标卡、分类占比饼图、未来 7 天过期趋势、Provider URI、食材明细、加载/空/错误态与重试、顶栏刷新与“导出数据（CSV）” |
| 导出说明页 | 主页“查看导出字段说明” | 导出内容、CSV 字段、编码/换行、文件名、保存位置与权限说明；实际导出从主页触发 |

看板内部只有两个页面，使用状态切换，不引入 Navigation 组件；系统返回键从导出说明页回到主页。

### 4.3 UI 原型文档

逐页布局、组件、交互、状态矩阵、权限降级与可访问性检查见 [UI_PROTOTYPE.md](UI_PROTOTYPE.md)。

## 5. 状态与异常要求

所有数据页面必须显式处理：

1. **加载态**：进度指示 + “正在加载/统计”文案，不能白屏。
2. **空态**：说明为什么为空，并给出下一步动作（如“点击右上角新增”）。
3. **错误态**：可读的中文错误信息 + 重试按钮，不能只打 Log。
4. **成功态**：正常内容。

现有基础：

- 主 App：`UiState` + `ScreenStateContent`，四态布局由 `StateLayouts` 统一提供，列表/详情按四态渲染。
- 看板：`DashboardUiState` + `DashboardStateContent`。

页面状态矩阵：

| 页面 | Loading | Empty | Error | Success |
| --- | --- | --- | --- | --- |
| 食材列表 | 进度圈 + “正在加载…” | “冰箱还是空的” | 查询失败 + 重试 | 库存计数 + 列表 |
| 食材详情 | 进度圈 + “正在加载…” | 记录已删除 | 查询失败 + 重试 | 详情 + 编辑/删除 |
| 新增食材 | 无（表单） | 无 | 字段错误 / 保存失败横幅 | 保存成功返回并自动刷新 |
| 菜谱列表（全部） | 进度圈 + “正在加载…” | “还没有菜谱” | 查询失败 + 重试 | 菜谱计数 + 菜谱卡 |
| 菜谱列表（推荐） | 进度圈 + “正在加载…” | 无库存“冰箱里还没有食材”+去添加；无菜谱“还没有本地菜谱”+去在线搜索；无匹配“暂无推荐菜谱” | 查询失败 + 重试 | 推荐计数 + 等级/缺少食材卡片 |
| 菜谱详情 | 进度圈 + “正在加载…” | 菜谱已删除 | 查询失败 + 重试 | 匹配标签 + 已有/缺少食材 + 步骤 + 编辑/删除 |
| 新增菜谱 | 无（表单） | 无 | 字段错误 / 保存失败横幅 | 保存成功返回并自动刷新 |
| 在线搜索 | 进度圈 + “正在搜索…” | “没有找到相关菜谱” | “搜索失败” + 重试 + 弹窗 | 结果计数 + 在线菜谱卡 |
| 在线详情 | 进度圈 + “正在加载…” | 菜谱已下架 | “加载失败” + 重试 + 弹窗 | 食材 + 步骤 + 保存按钮 |
| 设置 | 进度圈 + “正在读取设置…” | 无（DataStore 有默认值） | 读取失败 + 重试 | 权限卡 + 天数/时间/开关 + 关于入口；保存时 Snackbar |
| 关于 | 无（静态） | 无 | 无 | 版本、功能、数据/隐私与免责声明 |
| 看板主页 | “正在读取食材并计算统计…” | “主 App 还没有食材” + 重新读取 | “读取失败” + 中文原因 + 重试 | 三张指标卡 + 分类饼图 + 趋势图/趋势空态 + Provider URI + 食材明细 + 导出按钮；手动刷新时保留内容并显示顶部进度 |
| 导出触发 | 按钮“正在导出…” + 进度圈 | 无数据弹窗“没有可导出的食材” | 权限/空间/写入失败弹窗 + 重试 | 成功弹窗显示路径 + 打开文件/关闭 |
| 导出说明页 | 无（静态） | 无 | 无 | 字段、编码、文件名与权限说明 |

## 6. 权限与降级

### 6.1 通知权限

- Manifest 已声明 `android.permission.POST_NOTIFICATIONS`。
- Android 13（API 33）及以上首次进入食材库先展示说明弹窗，用户同意后才触发系统权限请求；Android 12 及以下无需该权限。
- 拒绝策略：
  - 不崩溃、不阻塞食材管理；食材列表、详情、编辑删除全部可用。
  - 拒绝后弹窗说明“到期提醒无法推送；仍可查看食材状态”，可再次申请。
  - 永久拒绝时顶部提示条显示“去设置”，跳转应用通知设置；从设置返回后自动刷新授权状态。
  - 未授权时 Worker 直接 `success` 降级，不抛异常；通知权限恢复后下一次定时检查正常推送。
- 设置页展示当前通知权限状态；未开启时提供“去设置”，从系统设置返回后 `ON_RESUME` 自动刷新。
- 设置页的时间与提前天数不依赖通知权限，未授权也能保存；实际推送仍受系统权限与通知总开关约束。

### 6.2 文件权限

- Android 10+（API 29+）通过 MediaStore Downloads 写入 `Downloads/Fridge`，不申请存储权限；Android 7-9 才申请 `WRITE_EXTERNAL_STORAGE`（manifest 限定 `maxSdkVersion=28`）。
- 拒绝存储权限：弹窗说明“只影响导出，仍可查看看板统计”，可再次申请；永久拒绝时给“去设置”。
- 不使用 `MANAGE_EXTERNAL_STORAGE`；不申请 Android 10+ 的广泛文件访问权限。
- 导出写入 `cacheDir/exports` 副本 + 公共下载目录；FileProvider 只暴露缓存副本，authority 为 `${applicationId}.fileprovider`。
- 成功：弹窗显示 `下载/Fridge/文件名`，可“打开文件”；无 CSV 查看器时提示安装文件管理器。
- 失败：存储空间不足、写入失败、权限不足分别给出中文提示，保留看板统计可用。

### 6.3 跨 App 数据权限

- 不使用 `sharedUserId`。
- 主 App 已暴露只读 `IngredientContentProvider`（`com.fridge.main.provider`），使用 signature 级别读写权限。
- 看板只申请读权限；主 App 是唯一写入方，Provider 的 insert/update/delete 均拒绝。
- 无权限/未安装/签名不一致时，看板进入错误态并给出中文提示，不崩溃。
- 看板未安装主 App 时显示“未检测到冰箱管家”；主 App 无食材时显示空态与“重新读取”。

## 7. 验收清单

### 第二轮：数据模型与原型

- [x] v2 四张表：`ingredient_types`、`ingredients`、`recipes`、`recipe_ingredients`。
- [x] 主键、外键、唯一索引、查询索引完整。
- [x] 菜谱-食材多对多关联表与 `RecipeWithIngredients` 关系对象。
- [x] `IngredientRepository`、`RecipeRepository` 接口草稿与 DTO。
- [x] 8 个页面 UI 原型说明。
- [x] v1 → v2 迁移 SQL 与迁移测试计划。
- [x] 双模块 `assembleDebug` 编译通过（2026-11-12 构建验证）。
- [x] Room schema `2.json` 已生成并提交。

### 第三轮：主 App 导航骨架

- [x] Navigation Compose 导航图覆盖 5 个主 App 页面。
- [x] 底部导航切换「食材库 / 菜谱库」，保留各自返回栈与滚动状态。
- [x] 食材列表 → 食材详情、菜谱列表 → 菜谱详情的参数化跳转。
- [x] 通用加载态、空态、错误态布局封装并接入列表/详情。
- [x] 第三轮验收时未接入 Room、ViewModel 与业务逻辑，页面仍使用静态 `SampleData`（第四轮替换食材、第五轮替换菜谱）。

### 第四轮：食材 Room CRUD

- [x] 食材 Entity、Dao、Repository、ViewModel 全层级实现，数据库操作通过协程/Flow 异步执行。
- [x] 食材列表从 Room 读取，加载/空/错误/成功四态与重试生效；移除食材静态占位。
- [x] 新增食材表单校验通过后持久化，保存成功返回列表。
- [x] 食材详情支持编辑、删除（二次确认）；列表通过 Room Flow 自动刷新。
- [x] 数据库/写入异常转中文错误提示，App 不崩溃。
- [x] 模拟数据库不可读：列表进入“加载失败 + 重试”错误态，App 未崩溃；恢复权限后正常。
- [x] `force-stop` 后重新启动，食材数据仍在（模拟器真机验证）。
- [x] `:refrigerator_main:assembleDebug`、`:dashboard_module:assembleDebug` 构建通过。

### 第五轮：菜谱 Room CRUD

- [x] 菜谱 Entity、Dao、Repository、ViewModel 全层级实现，数据库操作通过协程/Flow 异步执行。
- [x] 菜谱列表从 Room 读取，加载/空/错误/成功四态与重试生效；移除菜谱 `SampleData`。
- [x] 新增菜谱支持菜名、动态食材清单、多行做法，校验通过后持久化。
- [x] 菜谱详情支持编辑、删除（二次确认）；列表通过 Room Flow 自动刷新。
- [x] 菜谱食材关联 `ingredient_types`，无库存时也可保存菜谱；重复食材名被拒绝。
- [x] 数据库/写入异常转中文错误提示，App 不崩溃。
- [x] 模拟数据库不可读：菜谱列表进入“加载失败 + 重试”错误态，App 未崩溃。
- [x] `force-stop` 后重新启动，菜谱数据仍在（模拟器验证）。
- [x] `:refrigerator_main:assembleDebug`、`:dashboard_module:assembleDebug` 构建通过。

### 第六轮：在线菜谱搜索与保存

- [x] 引入 Retrofit `2.11.0`、OkHttp `4.12.0`、Gson `2.11.0`，声明 `INTERNET` 权限。
- [x] 统一 `NetworkResult`、`BaseRemoteDataSource` 与全局异常中文映射，请求切到 `Dispatchers.IO`。
- [x] 接入 TheMealDB `search.php` / `lookup.php`，搜索 `Arrabiata` 返回 `Spicy Arrabiata Penne`。
- [x] 新增底部“在线搜索”入口、搜索框、结果列表与在线详情页。
- [x] 搜索与详情具备加载/空/错误/成功四态；错误页可重试。
- [x] 打开飞行模式后搜索：弹出“网络请求失败”，说明本地菜谱仍可用；关闭弹窗后可重试。
- [x] 在线详情展示 8 种食材与 3 个步骤，支持“保存到本地”。
- [x] 保存后本地菜谱列表出现 `Spicy Arrabiata Penne`，Room Flow 自动刷新。
- [x] 无网时切换到本地菜谱库，已保存菜谱仍可打开。
- [x] 保留菜谱库右上角“新增”和原有本地增删改查。
- [x] `:refrigerator_main:assembleDebug`、`:dashboard_module:assembleDebug` 构建通过。

### 第七轮：食材匹配推荐

- [x] 纯 Kotlin 算法 `RecipeMatcher`，不依赖 Room/Compose；单测覆盖三级分类、排序、空库存、空菜谱。
- [x] 菜谱列表新增“全部菜谱 / 推荐菜谱”筛选；推荐按等级降序，展示等级、已备齐数量与完整缺少食材。
- [x] 菜谱详情展示匹配标签；已有食材绿色“已有”，缺少食材红色“缺少”，不只靠颜色区分。
- [x] 匹配同时观察菜谱与库存 Room Flow，在 `Dispatchers.Default` 计算；库存变化自动刷新。
- [x] 无库存显示“冰箱里还没有食材 / 添加食材后查看推荐菜谱”，可跳转新增食材。
- [x] 无本地菜谱显示“还没有本地菜谱”，可跳转在线搜索。
- [x] 原有全部菜谱、在线搜索、手动新增与编辑删除保持可用。
- [x] `:refrigerator_main:testDebugUnitTest`、`:refrigerator_main:assembleDebug`、`:dashboard_module:assembleDebug` 通过。
- [x] 模拟器验证：无库存空态、加入 garlic 后显示“缺少多种食材 / 1/8 已备齐”、详情 garlic 绿色其余红色。

### 第八轮：食材保质期提醒

- [x] WorkManager `2.10.1` 每日 09:00 唯一周期任务，`ExistingPeriodicWorkPolicy.KEEP`，查询在 `Dispatchers.IO`。
- [x] `ExpiryCalculator` 统一 0..3 天临期口径；列表显示新鲜/临期/已过期三色标签。
- [x] 临期食材合并为一条通知；多条时标题带数量，正文逐条列出剩余天数。
- [x] 点击通知通过 `MainActivity` 信号跳转食材库，并清理详情/新增返回栈。
- [x] 无临期时取消旧通知；无通知权限时跳过推送，App 不崩溃。
- [x] Android 13+ 首次说明、系统申请、拒绝弹窗、永久拒绝“去设置”与返回刷新均已验证。
- [x] 模拟器验证：milk（2 天）显示“临期”，yogurt（已过期 12 天）显示“已过期”，garlic 保持“新鲜”。
- [x] 强制运行 Worker 后出现“有 1 种食材即将过期 / milk：还剩 2 天”通知；点击打开食材库。
- [x] 新增 6 个 JVM 单测（到期日/边界/已过期、09:00 前后调度）；总计 11 个单测通过。
- [x] `:refrigerator_main:testDebugUnitTest`、`:refrigerator_main:assembleDebug`、`:dashboard_module:assembleDebug` 通过。

### 第九轮：全链路联调与交互统一

- [x] 食材、菜谱、在线搜索结果三个列表接入下拉刷新；刷新完成自动收起，失败保留旧列表并弹窗。
- [x] 页面跳转统一 `safeNavigate`/`safePopBackStack`，异常由根 Scaffold Snackbar 提示，不闪退。
- [x] 通知权限永久拒绝时仅显示轻量提示条与“去设置”，不强制跳转、不阻断食材管理。
- [x] 新增食材/新增菜谱空表单提交显示字段错误；修正字段后错误即时消失，无效数据不写库。
- [x] 在线搜索空输入与超过 40 字即时提示，搜索按钮不因空输入变灰。
- [x] 统一 Material3 `Shapes`，页面卡片、输入框、按钮圆角与间距一致。
- [x] 全链路手工验证：添加食材 → 在线搜菜谱 → 保存本地 → 推荐匹配 → 三色保质期状态。
- [x] `:refrigerator_main:testDebugUnitTest`、`:refrigerator_main:assembleDebug`、`:dashboard_module:assembleDebug` 通过。

### 第十轮：伴生看板数据通道

- [x] `dashboard_module` 为独立 application，可独立编译、安装、启动，不依赖主 App 源码。
- [x] 主 App `IngredientContentProvider` 使用 `com.fridge.main.provider`，读写权限为 signature 级；只实现 `query`，拒绝写操作。
- [x] 看板 Manifest 声明读权限与 `<queries>`；`ContentResolver.query` 读取列白名单数据。
- [x] 看板主页展示库存条目/品种数、Provider URI、食材名称/分类/入库日期/数量/保质期。
- [x] 主 App 有 1 条食材时，看板成功显示 `milk`、`Dairy`、`2026-11-15`、`1 L`、`7 天`。
- [x] 主 App 无食材时显示空态和“重新读取”；主 App 未安装时显示“未检测到冰箱管家”+ 重试，App 不崩溃。
- [x] 看板导出页可从主页进入，字段/文件名说明完整（第十轮为骨架，第十二轮起实际导出已接入）。
- [x] `:refrigerator_main:assembleDebug`、`:dashboard_module:assembleDebug` 构建通过。

### 第十一轮：看板库存统计与分类饼图

- [x] `DashboardStatsCalculator` 统一按本地日历日计算到期日与剩余天数；0..3 天临期，负数已过期。
- [x] 统计总条目数、临期数、已过期数与分类条目数；分类按数量降序，分类为空归入“未分类”。
- [x] 看板主页三张指标卡（总食材数 / 临期 / 已过期），颜色区分并适配窄屏。
- [x] MPAndroidChart 饼图展示分类占比，扇区标注“分类名称 + 条目数”，中心显示总条目数。
- [x] 统计计算在 `Dispatchers.Default` 执行，数据读取仍在 IO；页面只渲染结果。
- [x] 加载、空、错误、成功四态；主 App 未安装/无食材时看板可独立展示对应状态，不崩溃。
- [x] 分类占比按库存条目数统计，不把不同单位的数量直接相加，页面有口径说明。
- [x] 3 个 JVM 单测通过（总数/临期/过期/分类、空分类与排序、跨月边界）。
- [x] `:dashboard_module:testDebugUnitTest`、`:dashboard_module:assembleDebug` 通过。

### 第十二轮：看板 CSV 导出

- [x] 主页顶栏“导出”和主页按钮共用同一导出入口；导出期间禁用按钮并显示“正在导出…”。
- [x] 导出前重新读取 Provider，不在主线程做查询或写文件。
- [x] CSV 字段：食材名称、分类、数量、单位、入库时间、剩余保质期(天)、过期状态；UTF-8 BOM + CRLF。
- [x] Android 10+ 写入 `Downloads/Fridge`，实测生成 `fridge_inventory_20261115_024539.csv`。
- [x] FileProvider 注册 `com.fridge.dashboard.fileprovider`，只暴露 `cache/exports`；成功后可打开文件。
- [x] Android 7-9 存储权限被拒/永久拒绝有弹窗与“去设置”；权限申请不影响统计浏览。
- [x] 成功、无数据、主 App 未安装、无 CSV 查看器、写入/空间失败均有明确弹窗，不崩溃。
- [x] `DashboardCsvFormatter` 5 个单测通过；看板共 8 个单测通过。
- [x] `:dashboard_module:testDebugUnitTest`、`:dashboard_module:assembleDebug` 通过。

### 第十四轮：设置页与 DataStore

- [x] 食材库顶部“设置”入口；设置页包含通知权限状态/去设置、提前提醒天数、每日提醒时间、过期通知开关与关于入口。
- [x] 提前天数 1–7（默认 3），时间选择器可设置任意本地时间；保存后 Snackbar“设置已保存”。
- [x] Preferences DataStore `fridge_settings` 持久化；`force-stop` 重启后仍为 4 天 / 10:30。
- [x] 设置变更后 `ExpiryReminderScheduler.reschedule` 用 `REPLACE` 重排；关闭通知时取消唯一周期任务。
- [x] Worker 读取 DataStore 的提前天数与通知开关；关闭时取消通知并成功返回。
- [x] 通知权限未开启时设置页显示“去设置”；从系统设置返回后自动刷新状态。
- [x] 关于页展示应用名、版本、功能说明、数据/隐私与健康免责声明。
- [x] 设置读取加载态、读取错误重试；保存失败有错误提示。
- [x] `AppSettingsTest`、`ExpiryCalculatorTest`、`ExpiryReminderSchedulerTest` 共 19 个主 App 单测通过；双模块构建通过。

### 第十五轮：看板趋势图与刷新缓存

- [x] `DashboardStatsCalculator` 生成今天起连续 7 天趋势，按到期日期聚合、空日期补 0，含今天与第 6 天，排除第 7 天。
- [x] 折线图 X 轴显示 7 个日期、Y 轴整数，数据点标注每日条目数；无临期时显示空态。
- [x] 看板三张指标卡 + 饼图 + 趋势图纵向分区；横屏下滚动与图表高度正常。
- [x] 顶栏“刷新”强制绕过缓存并重新查询 Provider，刷新时保留旧内容、按钮显示“刷新中…”，至少 450ms。
- [x] Repository 60 秒缓存；重复读取命中缓存，手动刷新/主 App 变更通知强制查询。
- [x] `DashboardStatsCalculatorTest` 5 个、`DashboardRepositoryCacheTest` 2 个、CSV 6 个，共 13 个看板单测通过。
- [x] `:dashboard_module:testDebugUnitTest`、`:dashboard_module:assembleDebug` 通过。

## 8. 明确不做（本轮）

- 不实现库存数量是否足够、临期食材优先级的匹配增强；本轮只判断品类是否有库存。
- 不实现通知分组折叠、免打扰时段、多个提醒时间；本轮支持单次每日提醒时间与 1–7 天提前量。
- 不实现按日期范围导出、趋势区间切换、云端上传；本轮 CSV 导出全量库存，趋势固定未来 7 天。
- 不实现在线菜谱图片加载、收藏、分页、账号体系与中文 API 适配。
- 不实现食材图片、本地菜谱搜索、分类筛选、数量快捷调整。
- 不引入 Flutter、uni-app 等跨平台方案。
