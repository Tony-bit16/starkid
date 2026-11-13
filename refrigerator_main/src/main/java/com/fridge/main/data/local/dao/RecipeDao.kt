package com.fridge.main.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.fridge.main.data.local.entity.Recipe
import com.fridge.main.data.local.entity.RecipeIngredient
import com.fridge.main.data.local.relation.RecipeWithIngredients
import kotlinx.coroutines.flow.Flow

/**
 * 菜谱数据访问对象。
 *
 * 菜谱与所需食材分表存储，读取时通过 [RecipeWithIngredients] 一次性组装；
 * 写入时由 Repository 使用数据库事务保证“主表 + 明细”原子提交。
 */
@Dao
interface RecipeDao {

    /** 观察全部菜谱及其所需食材，按菜名排序。 */
    @Transaction
    @Query("SELECT * FROM recipes ORDER BY name COLLATE NOCASE ASC")
    fun observeAllWithIngredients(): Flow<List<RecipeWithIngredients>>

    /** 观察单个菜谱及其所需食材；不存在时返回 null。 */
    @Transaction
    @Query("SELECT * FROM recipes WHERE id = :recipeId")
    fun observeByIdWithIngredients(recipeId: Long): Flow<RecipeWithIngredients?>

    /** 按菜名模糊搜索菜谱。 */
    @Transaction
    @Query("SELECT * FROM recipes WHERE name LIKE '%' || :keyword || '%' ORDER BY name COLLATE NOCASE ASC")
    fun observeByNameWithIngredients(keyword: String): Flow<List<RecipeWithIngredients>>

    /** 按 id 查询菜谱主记录；更新前用于确认菜谱仍然存在。 */
    @Query("SELECT * FROM recipes WHERE id = :recipeId")
    suspend fun findRecipeById(recipeId: Long): Recipe?

    /** 新增菜谱主记录，返回自增主键。 */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRecipe(recipe: Recipe): Long

    /** 更新菜谱主记录。 */
    @Update
    suspend fun updateRecipe(recipe: Recipe)

    /** 删除菜谱主记录；recipe_ingredients 会按外键级联删除。 */
    @Delete
    suspend fun deleteRecipe(recipe: Recipe)

    /**
     * 按 id 删除菜谱主记录，返回受影响行数。
     *
     * 关联明细由 `recipe_ingredients.recipe_id` 的 CASCADE 外键自动清理，
     * 无需在 Repository 中再手动删一次。
     */
    @Query("DELETE FROM recipes WHERE id = :recipeId")
    suspend fun deleteRecipeById(recipeId: Long): Int

    /** 新增一条菜谱所需食材明细。 */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRecipeIngredient(recipeIngredient: RecipeIngredient)

    /** 批量新增菜谱所需食材明细。 */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRecipeIngredients(recipeIngredients: List<RecipeIngredient>)

    /** 清空某菜谱的所需食材明细，用于编辑菜谱时整体替换。 */
    @Query("DELETE FROM recipe_ingredients WHERE recipe_id = :recipeId")
    suspend fun deleteIngredientsOfRecipe(recipeId: Long)
}
