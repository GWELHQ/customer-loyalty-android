package com.example.loyaltyapp.core.result

/** Simple success/failure wrapper with a plain-language message suitable for direct display. */
sealed class AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>()
    data class Failure(val message: String, val cause: Throwable? = null) : AppResult<Nothing>()
}

inline fun <T> AppResult<T>.onSuccess(block: (T) -> Unit): AppResult<T> {
    if (this is AppResult.Success) block(data)
    return this
}

inline fun <T> AppResult<T>.onFailure(block: (String) -> Unit): AppResult<T> {
    if (this is AppResult.Failure) block(message)
    return this
}
