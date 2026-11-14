package com.fridge.main.ui.screens.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fridge.main.data.remote.NetworkResult
import com.fridge.main.data.remote.recipe.OnlineRecipe
import com.fridge.main.data.repository.OnlineRecipeRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 在线搜索页状态；Idle 与 Empty 分开，避免首次进入就显示“无结果”。 */
data class OnlineSearchUiState(
    val query: String = "",
    val queryError: String? = null,
    val result: OnlineSearchResult = OnlineSearchResult.Idle,
    val isRefreshing: Boolean = false,
    val errorDialogMessage: String? = null,
)

/** 在线搜索结果状态。 */
sealed interface OnlineSearchResult {
    data object Idle : OnlineSearchResult
    data object Loading : OnlineSearchResult
    data object Empty : OnlineSearchResult
    data class Error(val message: String) : OnlineSearchResult
    data class Success(val recipes: List<OnlineRecipe>) : OnlineSearchResult
}

/**
 * 在线菜谱搜索 ViewModel。
 *
 * - 所有网络请求在 [viewModelScope] 中执行，Repository 内部再切到 IO；
 * - 每次新搜索会取消上一次请求，避免慢响应覆盖新关键词的结果；
 * - 失败同时写入错误态与弹窗文案，用户关闭弹窗后仍可看到可重试的错误页。
 */
class OnlineRecipeListViewModel(
    private val repository: OnlineRecipeRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnlineSearchUiState())
    val uiState: StateFlow<OnlineSearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var requestId = 0

    fun onQueryChange(query: String) {
        // 关键词变化后旧请求的结果已无意义，取消并在下一次搜索时重新发起。
        searchJob?.cancel()
        requestId++
        _uiState.update {
            it.copy(
                query = query,
                queryError = null,
                // 关键词已变化，旧结果不再对应当前输入，回到 Idle 等待下一次搜索。
                result = OnlineSearchResult.Idle,
                isRefreshing = false,
                errorDialogMessage = null,
            )
        }
    }

    fun search() {
        startSearch(fullLoading = true)
    }

    /** 下拉刷新：保留当前结果，只在列表顶部显示刷新指示器。 */
    fun refresh() {
        if (_uiState.value.isRefreshing) return
        if (_uiState.value.result !is OnlineSearchResult.Success) return
        startSearch(fullLoading = false)
    }

    private fun startSearch(fullLoading: Boolean) {
        val query = _uiState.value.query.trim()
        when {
            query.isEmpty() -> {
                _uiState.update { it.copy(queryError = "请输入要搜索的菜名") }
                return
            }

            query.length > MAX_QUERY_LENGTH -> {
                _uiState.update {
                    it.copy(queryError = "搜索关键词不能超过 $MAX_QUERY_LENGTH 个字符")
                }
                return
            }
        }

        searchJob?.cancel()
        val currentRequest = ++requestId
        _uiState.update {
            it.copy(
                result = if (fullLoading) OnlineSearchResult.Loading else it.result,
                queryError = null,
                isRefreshing = !fullLoading,
                errorDialogMessage = null,
            )
        }

        searchJob = viewModelScope.launch {
            when (val response = repository.searchRecipes(query)) {
                is NetworkResult.Success -> {
                    // 用户可能在请求期间修改了关键词；旧请求结果直接丢弃。
                    if (requestId != currentRequest || _uiState.value.query.trim() != query) {
                        return@launch
                    }
                    _uiState.update {
                        it.copy(
                            result = if (response.data.isEmpty()) {
                                OnlineSearchResult.Empty
                            } else {
                                OnlineSearchResult.Success(response.data)
                            },
                            isRefreshing = false,
                        )
                    }
                }

                is NetworkResult.Failure -> {
                    if (requestId != currentRequest || _uiState.value.query.trim() != query) {
                        return@launch
                    }
                    _uiState.update {
                        it.copy(
                            result = if (fullLoading) {
                                OnlineSearchResult.Error(response.message)
                            } else {
                                // 刷新失败时保留已有结果，仅弹窗提示，避免用户丢失当前列表。
                                it.result
                            },
                            isRefreshing = false,
                            errorDialogMessage = response.message,
                        )
                    }
                }
            }
        }
    }

    fun retry() {
        search()
    }

    /** 只关闭弹窗，保留错误页和“重试”按钮，方便用户恢复网络后直接重试。 */
    fun dismissErrorDialog() {
        _uiState.update { it.copy(errorDialogMessage = null) }
    }

    companion object {
        private const val MAX_QUERY_LENGTH = 40

        fun factory(repository: OnlineRecipeRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { OnlineRecipeListViewModel(repository) }
        }
    }
}
