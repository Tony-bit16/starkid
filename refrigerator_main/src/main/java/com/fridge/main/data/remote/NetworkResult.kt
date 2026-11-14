package com.fridge.main.data.remote

import com.google.gson.JsonParseException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * 网络层统一结果。
 *
 * UI/ViewModel 不直接接触 Retrofit 的异常类型，只根据 [Failure.message] 展示中文提示；
 * 这样即使以后替换接口实现，页面也无需跟着改错误处理。
 */
sealed interface NetworkResult<out T> {

    data class Success<T>(val data: T) : NetworkResult<T>

    data class Failure(
        val message: String,
        val cause: Throwable? = null,
    ) : NetworkResult<Nothing>
}

/** 保留成功/失败语义地转换数据，避免在 ViewModel 中反复写 when。 */
inline fun <T, R> NetworkResult<T>.map(transform: (T) -> R): NetworkResult<R> = when (this) {
    is NetworkResult.Success -> NetworkResult.Success(transform(data))
    is NetworkResult.Failure -> this
}

/**
 * 所有远程数据源的基类。
 *
 * 子类只声明接口调用，超时、HTTP 错误、断网、解析失败等统一由 [execute] 处理，
 * 保证“网络异常不崩溃、给用户可读提示”的约束只实现一次。
 */
abstract class BaseRemoteDataSource {

    protected suspend fun <T> execute(
        fallbackMessage: String = "网络请求失败，请稍后重试",
        block: suspend () -> T,
    ): NetworkResult<T> = safeApiCall(fallbackMessage, block)
}

/**
 * 在 IO 线程执行网络请求，并把异常映射为用户可读文案。
 *
 * Retrofit 的 suspend 方法本身会切到后台，但这里仍显式使用 [Dispatchers.IO]：
 * 调用方可能在未来加入本地缓存/文件处理，统一放在后台线程可避免主线程 IO。
 */
internal suspend fun <T> safeApiCall(
    fallbackMessage: String,
    block: suspend () -> T,
): NetworkResult<T> = withContext(Dispatchers.IO) {
    try {
        NetworkResult.Success(block())
    } catch (cancelled: CancellationException) {
        // 协程取消不是业务失败，必须继续向上抛，避免页面状态被错误覆盖。
        throw cancelled
    } catch (timeout: SocketTimeoutException) {
        NetworkResult.Failure("请求超时，请检查网络后重试", timeout)
    } catch (unknownHost: UnknownHostException) {
        NetworkResult.Failure("无法连接菜谱服务，请检查网络或稍后重试", unknownHost)
    } catch (connect: ConnectException) {
        NetworkResult.Failure("无法连接菜谱服务，请检查网络或稍后重试", connect)
    } catch (http: HttpException) {
        NetworkResult.Failure(http.toUserMessage(), http)
    } catch (parse: JsonParseException) {
        NetworkResult.Failure("菜谱数据解析失败，请稍后重试", parse)
    } catch (io: IOException) {
        NetworkResult.Failure("网络连接失败，请检查网络后重试", io)
    } catch (error: Throwable) {
        NetworkResult.Failure(fallbackMessage, error)
    }
}

private fun HttpException.toUserMessage(): String = when (code()) {
    400 -> "搜索条件有误，请换个菜名试试"
    401, 403 -> "菜谱服务访问受限，请稍后重试"
    404 -> "菜谱服务接口不存在"
    429 -> "请求过于频繁，请稍后再试"
    in 500..599 -> "菜谱服务暂时不可用，请稍后重试"
    else -> "请求失败（HTTP ${code()}），请稍后重试"
}
