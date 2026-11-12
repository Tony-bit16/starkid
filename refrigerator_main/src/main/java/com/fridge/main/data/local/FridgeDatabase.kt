package com.fridge.main.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.fridge.main.data.local.dao.IngredientDao
import com.fridge.main.data.local.dao.RecipeDao
import com.fridge.main.data.local.entity.Ingredient
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
        Ingredient::class,
        Recipe::class,
        RecipeIngredient::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class FridgeDatabase : RoomDatabase() {

    abstract fun ingredientDao(): IngredientDao

    abstract fun recipeDao(): RecipeDao

    companion object {
        private const val DATABASE_NAME = "fridge.db"

        @Volatile
        private var instance: FridgeDatabase? = null

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
                ).build().also { instance = it }
            }
    }
}
