# 功能清单

> 项目分为两个可独立安装的 App：主 App「冰箱管家」与伴生看板「食材看板」。
> 本文按模块梳理已交付能力，便于演示、答辩和后续迭代排期。

## 一、主 App（refrigerator_main）

### 1. 食材管理
- 新增食材：名称、分类、入库时间、保质期天数、数量、单位。
- 食材列表：Room Flow 实时刷新，展示新鲜 / 临期 / 已过期三色标签。
- 食材详情：查看完整信息，支持编辑与二次确认删除。
- 表单校验：必填、长度、保质期、数量、入库时间；首次提交后字段修正即时清错。
- 空态 / 加载态 / 错误态齐全；数据库不可读时展示可重试错误页，不崩溃。

### 2. 菜谱管理
- 菜谱列表：全部菜谱 / 推荐菜谱两种筛选。
- 新增/编辑菜谱：菜名、动态食材清单（名称/用量/单位）、多行做法步骤。
- 菜谱详情：食材用量、编号步骤、编辑与删除。
- 库存匹配：完全可制作 / 缺少 1 种 / 缺少多种；推荐列表展示已备齐数量与缺少食材，详情按已有/缺少着色。
- 匹配算法 `RecipeMatcher` 为纯 Kotlin 工具类，可独立单测；历史同名不同 `typeId` 时按规范化名称兜底。

### 3. 在线菜谱
- Retrofit + OkHttp + Gson 接入 TheMealDB，关键词搜索在线菜谱。
- 在线详情展示食材原始用量与做法步骤，支持“保存到本地”。
- 保存后经本地 `RecipeRepository` 写入 Room，菜谱列表随 Flow 自动刷新。
- 网络失败弹窗 + 错误页重试，本地菜谱、看板、手动新增均不受影响。

### 4. 保质期提醒
- WorkManager 每日 09:00 巡检；`ExpiryCalculator` 按本地日历日计算，0..3 天为临期。
- 多条临期食材合并为一条通知，点击跳转食材库；无临期时自动取消旧通知。
- Android 13+ 通知权限说明、系统申请、拒绝/永久拒绝降级与“去设置”入口。
- 无通知权限时仍可在列表查看三色状态。

### 5. 基础体验与数据共享
- 底部导航：食材库 / 菜谱库 / 在线搜索；列表 → 详情导航。
- 食材、菜谱、在线结果三个列表支持下拉刷新，失败保留旧内容。
- 统一 `AppErrorHandler` 与 safe 导航：页面跳转异常进入根 Snackbar，数据库/网络异常进入页面错误态。
- 只读 `IngredientContentProvider`（signature 权限）向伴生看板提供食材数据，不共享数据库文件。

## 二、伴生看板（dashboard_module）

- 独立 Launcher / APK，可脱离主 App 单独启动。
- 通过 `ContentResolver` 读取主 App Provider；主 App 未安装、无权限、读取失败均有中文错误态与重试。
- 主 App 写入后通过 Room `InvalidationTracker` → `notifyChange` → 看板 `ContentObserver` 自动刷新。
- 三项核心统计：总食材数、临期数、已过期数（按库存条目数统计，不直接相加不同单位数量）。
- MPAndroidChart 环形饼图：分类名称 + 条目数、中心总数、9 色区分、响应式高度。
- CSV 导出：名称、分类、数量、单位、入库时间、剩余保质期、过期状态；UTF-8 BOM + CRLF。
  - Android 10+ 写 MediaStore Downloads，无需存储权限。
  - Android 7–9 才申请 WRITE 权限；拒绝/永久拒绝不阻断统计浏览。
  - cache 副本经 FileProvider 打开；无查看器、无数据、无主 App、空间不足均有明确提示。

## 三、工程与质量

- Kotlin + Jetpack Compose + Navigation + ViewModel + Room + Retrofit/OkHttp + WorkManager + MPAndroidChart。
- MVVM 分层：Entity / Dao / Repository / ViewModel / UI，Domain 纯算法独立。
- 主线程不做数据库、网络、文件 IO；统计与匹配切到 `Dispatchers.Default` / `Dispatchers.IO`。
- 单元测试覆盖：食材匹配、保质期计算、调度时间、看板统计、CSV 格式化与编码。
- 双 App 共用同一套绿/橙语义色、6/10/14/18/24dp 圆角与卡片阴影规范。

## 四、当前边界

- 匹配只看“品类是否有库存”，不判断数量是否足够，也不优先消耗临期食材。
- 在线菜谱依赖 TheMealDB 英文接口，暂无中文菜谱源、图片、收藏与分页。
- 看板暂无趋势折线图、按日期范围导出。
- 通知为每日一次固定巡检，不是精确到分钟的到期闹钟。
