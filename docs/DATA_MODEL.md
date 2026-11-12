# 数据模型设计（v2）

> 本文件是第二轮“完善数据模型”的设计说明。对应代码已落到
> `refrigerator_main/src/main/java/com/fridge/main/data/local/`，但 Repository / ViewModel
> 仍只有接口，没有业务实现。

## 1. 设计目标

1. 库存与菜谱解耦：菜谱是长期模板，不能因为某批食材被吃掉、删除而失效。
2. 支持“同一种食材多次入库”：西红柿可以有多条库存记录，各自有入库时间、保质期和数量。
3. 支持菜谱-食材多对多：一道菜需要多种食材，一种食材也会出现在多道菜中。
4. 让匹配、统计、看板都有稳定的关联键，而不是靠字符串模糊比较。
5. 保留展示快照，避免品类改名后历史库存难以辨认。
6. 所有时间用 Unix 毫秒时间戳（UTC）存储；到期时间派生，不落库。

## 2. 实体关系

```text
┌──────────────────┐
│  ingredient_types │  食材品类（主数据）
│  id (PK)          │
│  normalized_name  │  UNIQUE
└────────┬─────────┘
         │ 1
         │
         ├──────────────────────────────┐
         │ N                            │ N
┌────────▼─────────┐          ┌─────────▼──────────┐
│   ingredients     │          │ recipe_ingredients  │  菜谱-食材关联
│   库存批次         │          │ recipe_id (PK/FK)   │
│   type_id (FK)    │          │ type_id   (PK/FK)   │
└──────────────────┘          │ quantity / unit     │
                              └─────────┬───────────┘
                                        │ N
                              ┌─────────▼──────────┐
                              │      recipes        │
                              │      id (PK)        │
                              │      name/steps     │
                              └────────────────────┘
```

关系口径：

- `ingredient_types` 1 — N `ingredients`：一个品类对应多条库存批次。
- `ingredient_types` 1 — N `recipe_ingredients`：一个品类可被多道菜引用。
- `recipes` 1 — N `recipe_ingredients`：一道菜有多项食材需求。
- 因此 **Recipe N — N IngredientType**，中间表是 `recipe_ingredients`。
- `ingredients` 与 `recipe_ingredients` 不直接相连；匹配时通过共同的 `type_id` 关联。

> 注意：这里的“多对多”是“菜谱 ↔ 食材品类”，不是“菜谱 ↔ 库存批次”。
> 把菜谱外键到某条库存批次会导致食材被消耗后菜谱损坏，见 §4。

## 3. 表结构

### 3.1 ingredient_types（食材品类 / 主数据）

| 列 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | INTEGER | PK, AUTOINCREMENT, NOT NULL | 品类 id |
| `name` | TEXT | NOT NULL | 展示名，如“西红柿” |
| `normalized_name` | TEXT | NOT NULL, UNIQUE | 匹配键：trim + 小写 + 全角转半角 + 空白折叠 |
| `category` | TEXT | NOT NULL | 分类，如蔬菜、肉蛋、乳制品、调味品、其他 |
| `default_unit` | TEXT | NOT NULL | 默认单位，如个、克、毫升；仅作为新增表单默认值 |
| `created_at` | INTEGER | NOT NULL | 创建时间，Unix 毫秒 |

索引：

- `index_ingredient_types_normalized_name`：UNIQUE，保证同一品类不重复。
- `index_ingredient_types_category`：按分类筛选/统计。

规范化示例：

```text
" 西红柿 "  -> "西红柿"
"ＥＧＧ"    -> "egg"（全角转半角后小写）
"鸡  蛋"    -> "鸡 蛋"（连续空白折叠为一个空格）
```

### 3.2 ingredients（库存批次）

| 列 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | INTEGER | PK, AUTOINCREMENT, NOT NULL | 库存批次 id |
| `type_id` | INTEGER | NOT NULL, FK → `ingredient_types.id`, ON DELETE RESTRICT | 品类 |
| `name` | TEXT | NOT NULL | 入库时的名称快照 |
| `category` | TEXT | NOT NULL | 入库时的分类快照 |
| `stored_at` | INTEGER | NOT NULL | 入库时间，Unix 毫秒 |
| `shelf_life_days` | INTEGER | NOT NULL | 保质期天数，业务校验 > 0 |
| `quantity` | REAL | NOT NULL | 当前数量，业务校验 >= 0 |
| `unit` | TEXT | NOT NULL | 当前单位，如个、克、毫升 |

索引：

- `type_id`：按品类聚合库存，供匹配/统计。
- `name`：列表搜索。
- `category`：分类筛选。
- `stored_at`：默认排序与“最近入库”。

为什么保留 `name` / `category` 快照：

- 列表页不必每次 JOIN 品类表。
- 品类改名后，历史批次仍保留入库时的叫法，便于用户理解。
- 匹配与统计以 `type_id` 为准，快照不参与关联，避免两份数据“打架”。

### 3.3 recipes（菜谱）

| 列 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | INTEGER | PK, AUTOINCREMENT, NOT NULL | 菜谱 id |
| `name` | TEXT | NOT NULL | 菜名 |
| `steps` | TEXT | NOT NULL | 做法，按换行分步；UI 按 `\n` 拆分并过滤空行 |

索引：

- `name`：菜名搜索/排序。

### 3.4 recipe_ingredients（菜谱-食材关联）

| 列 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `recipe_id` | INTEGER | PK 之一, NOT NULL, FK → `recipes.id` ON DELETE CASCADE | 所属菜谱 |
| `type_id` | INTEGER | PK 之一, NOT NULL, FK → `ingredient_types.id` ON DELETE RESTRICT | 所需品类 |
| `quantity` | REAL | NOT NULL | 所需数量，业务校验 > 0 |
| `unit` | TEXT | NOT NULL | 用量单位 |

复合主键：`(recipe_id, type_id)`。

- 同一菜谱同一品类只允许一行；重复添加会在数据库层被拒绝。
- 删除菜谱：关联行级联删除，符合“菜谱不存在则需求无意义”。
- 删除品类：RESTRICT，必须先解除库存与菜谱引用，避免孤儿数据。

索引：

- `recipe_id`：按菜谱取明细。
- `type_id`：按食材反查菜谱，供匹配。

## 4. 关键取舍：为什么关联到品类而不是库存

如果 `recipe_ingredients` 直接外键到 `ingredients.id`：

1. 菜谱会绑定到“某一批西红柿”，这批吃完后菜谱就失去食材。
2. 新增菜谱前必须先有库存，业务顺序被颠倒。
3. 同一食材有多条库存，外键目标不唯一，无法表达“任意批次都行”。
4. 级联删除/限制删除都会造成不合理的数据行为。

因此采用：

```text
Recipe ──N:M── IngredientType ──1:N── Ingredient（库存批次）
```

菜谱只关心“需要哪种食材、多少”，库存只关心“当前有哪些批次”。
匹配时按 `type_id` 聚合，不依赖具体批次。

## 5. 到期时间与派生规则

不新增 `expires_at` 列，统一派生：

```text
expiresAt = stored_at + shelf_life_days * 86_400_000L
daysLeft  = ceil((expiresAt - now) / 86_400_000.0)
```

状态：

| 条件 | 状态 | UI 文案 |
| --- | --- | --- |
| `now < expiresAt - 3 天` | FRESH | 新鲜 |
| `expiresAt - 3 天 <= now < expiresAt` | EXPIRING | 临期 |
| `now >= expiresAt` | EXPIRED | 已过期 |

说明：

- 3 天是当前产品默认值，后续可做成设置项。
- 固定 24 小时计算，暂不处理夏令时/跨时区日历日。
- 列表排序与区间查询使用同一 SQL 表达式，避免 Java 与 SQL 两套算法不一致。

## 6. 索引与查询

### 6.1 已有索引覆盖

| 查询 | 使用索引 |
| --- | --- |
| 首页按入库时间倒序 | `index_ingredients_stored_at` |
| 按名称搜索库存 | `index_ingredients_name` |
| 按分类筛选库存 | `index_ingredients_category` |
| 按品类聚合库存 | `index_ingredients_type_id` |
| 菜谱按名称排序 | `index_recipes_name` |
| 取某菜谱明细 | `index_recipe_ingredients_recipe_id` / 复合主键前缀 |
| 按食材反查菜谱 | `index_recipe_ingredients_type_id` |
| 查找品类 | `index_ingredient_types_normalized_name`（UNIQUE） |
| 分类统计 | `index_ingredient_types_category` |

到期区间查询使用表达式 `stored_at + shelf_life_days * 86400000`，SQLite 无法直接使用
`stored_at` 单列索引做范围扫描。当前数据量（家庭冰箱）很小，全表扫描可接受；
如果未来数据量增大，可增加生成列 `expires_at` 并为其建索引，但需要新的 Migration。

### 6.2 匹配 SQL 草案（后续 Repository 实现）

```sql
WITH stock AS (
    SELECT type_id, SUM(quantity) AS available
    FROM ingredients
    WHERE quantity > 0
    GROUP BY type_id
)
SELECT
    r.id,
    r.name,
    COUNT(ri.type_id) AS required_count,
    SUM(CASE WHEN COALESCE(s.available, 0) >= ri.quantity THEN 1 ELSE 0 END) AS satisfied_count
FROM recipes r
JOIN recipe_ingredients ri ON ri.recipe_id = r.id
LEFT JOIN stock s ON s.type_id = ri.type_id
GROUP BY r.id, r.name;
```

结果映射：

- `satisfied_count = required_count` → `CAN_COOK`（能做）。
- `0 < satisfied_count < required_count` → `PARTIAL`（部分能做）。
- `satisfied_count = 0` → `CANNOT_COOK`（不能做）。

限制：

- 不做单位换算：1 kg 与 500 g 目前不能自动相加；单位不一致时按“有库存”处理，
  数量比较只在单位相同时生效。
- 不处理别名/同义词；后续可加 `ingredient_aliases` 表。
- 匹配是只读查询，不应写回数据库。

## 7. v1 → v2 迁移

数据库版本从 `1` 升到 `2`，`FridgeDatabase` 注册 `MIGRATION_1_2`。

步骤：

1. 创建 `ingredient_types` 及唯一/分类索引。
2. 从 `ingredients` 抽取品类（按最早入库的记录），从 `recipe_ingredients` 补充
   只在菜谱中出现的食材（分类记为“未分类”）。
3. 给旧表加可空 `type_id`，按 `lower(trim(name))` 回填。
4. 重建 `ingredients`，把 `type_id` 改为 `NOT NULL` 并加外键。
5. 重建 `recipe_ingredients`，复合主键改为 `(recipe_id, type_id)`；
   旧表按规范名去重，保留最早一行。
6. 重建全部索引。

注意：

- 迁移不删除用户数据；旧表重建时保留原有 id。
- 规范化后同菜谱同品类重复的旧行会合并为一行（保留最早 rowid），这是初始化阶段的
  可接受策略；后续可在设置页提供“合并品类/别名”能力。
- 禁止 `fallbackToDestructiveMigration()`。
- 每次改表都要提交 `schemas/.../2.json`，并在后续补 `MigrationTestHelper` 测试。

## 8. 数据校验

数据库约束负责“结构正确”，Repository 负责“业务正确”。

| 规则 | 责任层 | 处理 |
| --- | --- | --- |
| `name/category/unit` 去空格后非空，名称≤30、分类≤20、单位≤10 | Repository | 返回字段错误 |
| `shelf_life_days` 1–36500 | Repository | 拒绝保存 |
| `quantity > 0`（库存） | Repository | 拒绝保存 |
| `quantity > 0`（菜谱需求） | Repository | 拒绝保存 |
| `stored_at` 不晚于当前时间 | Repository | 允许当天，未来时间拒绝 |
| `normalized_name` 唯一 | DB + Repository | 冲突时回查已有品类 |
| 删除被引用的品类 | DB | RESTRICT，Repository 转成友好提示 |
| 删除菜谱级联明细 | DB | CASCADE |

Room 不支持 `CHECK` 约束注解，因此数值范围不在建表 SQL 中写死；如果后续需要
数据库级兜底，可在 Migration 中手写 `CHECK`，但必须同步 Room 期望 schema。

## 9. 对看板 / Provider 的影响

看板需要的统计口径：

- 食材品种数：`COUNT(DISTINCT type_id)`。
- 库存批次/总数量：按 `ingredients` 聚合。
- 分类分布：`ingredient_types.category` + 库存聚合。
- 临期/过期：按 §5 的派生表达式过滤。
- CSV 字段：品类名称、分类、批次数量、单位、入库时间、到期时间、状态。

Provider 只读查询应基于同一套表与表达式，避免看板自己实现一套“到期”算法。
具体 URI 契约见 `docs/ARCHITECTURE.md` §4。

## 10. 待确认

- 单位换算规则（kg/g、L/mL）是否需要在匹配阶段支持。
- 品类合并/别名是否作为 P1 功能。
- 临期阈值 3 天是否可配置。
- 是否允许用户手动编辑品类名称/分类；若允许，库存快照是否同步更新。
