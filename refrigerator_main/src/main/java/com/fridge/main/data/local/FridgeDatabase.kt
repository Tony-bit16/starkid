package com.fridge.main.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.fridge.main.data.local.dao.IngredientDao
import com.fridge.main.data.local.dao.IngredientTypeDao
import com.fridge.main.data.local.dao.RecipeDao
import com.fridge.main.data.local.entity.Ingredient
import com.fridge.main.data.local.entity.IngredientType
import com.fridge.main.data.local.entity.Recipe
import com.fridge.main.data.local.entity.RecipeIngredient

/**
 * 主 App 的本地 Room 数据库。
 *
 * 数据库文件位于主 App 的私有目录（fridge.db），其他 App 无法直接按路径打开。
 * 看板 App 通过主 App 暴露的只读 ContentProvider 读取统计所需数据，
 * 不能依赖 sharedUserId 或直接拷贝数据库文件（详见 docs/ARCHITECTURE.md）。
 *
 * 版本升级时必须新增 Migration，禁止用 destructive migration 抹掉用户食材数据。
 */
@Database(
    entities = [
        IngredientType::class,
        Ingredient::class,
        Recipe::class,
        RecipeIngredient::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class FridgeDatabase : RoomDatabase() {

    abstract fun ingredientTypeDao(): IngredientTypeDao

    abstract fun ingredientDao(): IngredientDao

    abstract fun recipeDao(): RecipeDao

    companion object {
        private const val DATABASE_NAME = "fridge.db"

        @Volatile
        private var instance: FridgeDatabase? = null

        /**
         * v1 → v2：把“菜谱所需食材名称”升级为稳定的食材品类外键。
         *
         * 迁移步骤：
         * 1. 建立 ingredient_types 主数据表；
         * 2. 从旧库存与旧菜谱明细中抽取不重复的品类；
         * 3. 给旧表补 type_id 并回填；
         * 4. 重建 ingredients / recipe_ingredients，加上 NOT NULL 与外键约束。
         *
         * 旧数据中同名但分类/单位不同的记录，按“先出现的记录”合并为一个品类；
         * 这是初始化阶段可接受的归一化策略，后续可在设置页提供品类合并/别名。
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `ingredient_types` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `normalized_name` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `default_unit` TEXT NOT NULL,
                        `created_at` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_ingredient_types_normalized_name` " +
                        "ON `ingredient_types` (`normalized_name`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_ingredient_types_category` " +
                        "ON `ingredient_types` (`category`)",
                )

                // 先从库存抽取品类，保留最早入库时的名称、分类与单位作为规范值。
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO `ingredient_types`
                        (`name`, `normalized_name`, `category`, `default_unit`, `created_at`)
                    SELECT `name`, lower(trim(`name`)), `category`, `unit`, `stored_at`
                    FROM `ingredients`
                    ORDER BY `stored_at` ASC
                    """.trimIndent(),
                )
                // 菜谱可能先于库存存在，把只在菜谱里出现的食材也补进品类表。
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO `ingredient_types`
                        (`name`, `normalized_name`, `category`, `default_unit`, `created_at`)
                    SELECT `ingredient_name`, lower(trim(`ingredient_name`)), '未分类', `unit`,
                           CAST(strftime('%s', 'now') AS INTEGER) * 1000
                    FROM `recipe_ingredients`
                    ORDER BY `recipe_id` ASC
                    """.trimIndent(),
                )

                // 先加可空列，回填后再重建表，避免 ALTER TABLE 无法补 NOT NULL 外键的问题。
                db.execSQL("ALTER TABLE `ingredients` ADD COLUMN `type_id` INTEGER")
                db.execSQL(
                    """
                    UPDATE `ingredients`
                    SET `type_id` = (
                        SELECT `id` FROM `ingredient_types`
                        WHERE `normalized_name` = lower(trim(`ingredients`.`name`))
                        LIMIT 1
                    )
                    """.trimIndent(),
                )
                db.execSQL("ALTER TABLE `recipe_ingredients` ADD COLUMN `type_id` INTEGER")
                db.execSQL(
                    """
                    UPDATE `recipe_ingredients`
                    SET `type_id` = (
                        SELECT `id` FROM `ingredient_types`
                        WHERE `normalized_name` = lower(trim(`recipe_ingredients`.`ingredient_name`))
                        LIMIT 1
                    )
                    """.trimIndent(),
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `ingredients_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `type_id` INTEGER NOT NULL,
                        `name` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `stored_at` INTEGER NOT NULL,
                        `shelf_life_days` INTEGER NOT NULL,
                        `quantity` REAL NOT NULL,
                        `unit` TEXT NOT NULL,
                        FOREIGN KEY(`type_id`) REFERENCES `ingredient_types`(`id`)
                            ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO `ingredients_new`
                        (`id`, `type_id`, `name`, `category`, `stored_at`, `shelf_life_days`, `quantity`, `unit`)
                    SELECT `id`, `type_id`, `name`, `category`, `stored_at`, `shelf_life_days`, `quantity`, `unit`
                    FROM `ingredients`
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE `ingredients`")
                db.execSQL("ALTER TABLE `ingredients_new` RENAME TO `ingredients`")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_ingredients_type_id` ON `ingredients` (`type_id`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_ingredients_name` ON `ingredients` (`name`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_ingredients_category` ON `ingredients` (`category`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_ingredients_stored_at` ON `ingredients` (`stored_at`)",
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `recipe_ingredients_new` (
                        `recipe_id` INTEGER NOT NULL,
                        `type_id` INTEGER NOT NULL,
                        `quantity` REAL NOT NULL,
                        `unit` TEXT NOT NULL,
                        PRIMARY KEY(`recipe_id`, `type_id`),
                        FOREIGN KEY(`recipe_id`) REFERENCES `recipes`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`type_id`) REFERENCES `ingredient_types`(`id`)
                            ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                    """.trimIndent(),
                )
                // 旧表以 ingredient_name 为主键，规范化后可能出现同菜谱同品类重复；
                // 迁移时按 rowid 保留最早一条，避免复合主键冲突。
                db.execSQL(
                    """
                    INSERT INTO `recipe_ingredients_new` (`recipe_id`, `type_id`, `quantity`, `unit`)
                    SELECT `recipe_id`, `type_id`, `quantity`, `unit`
                    FROM `recipe_ingredients`
                    WHERE `rowid` IN (
                        SELECT MIN(`rowid`) FROM `recipe_ingredients`
                        GROUP BY `recipe_id`, `type_id`
                    )
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE `recipe_ingredients`")
                db.execSQL("ALTER TABLE `recipe_ingredients_new` RENAME TO `recipe_ingredients`")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_recipe_ingredients_recipe_id` " +
                        "ON `recipe_ingredients` (`recipe_id`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_recipe_ingredients_type_id` " +
                        "ON `recipe_ingredients` (`type_id`)",
                )
            }
        }

        /**
         * 进程内单例。Room 的数据库实例创建成本较高，重复创建会导致连接与内存浪费。
         *
         * 注意：这里只创建实例，具体查询仍在 DAO 的 suspend / Flow 中执行。
         */
        fun getInstance(context: Context): FridgeDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    FridgeDatabase::class.java,
                    DATABASE_NAME,
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
    }
}
