package com.example.loyaltyapp.data.remote

import com.example.loyaltyapp.data.remote.dto.ApiErrorDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException

/**
 * Turns a Retrofit [HttpException] into the plain-language message the app shows, using the
 * documented error envelope (`statusCode, error, message: string|string[]`). Falls back to a
 * generic message if the body isn't the expected shape.
 */
object NetworkErrors {

    private val json = Json { ignoreUnknownKeys = true }

    fun messageFor(exception: HttpException, fallback: String): String {
        val body = exception.response()?.errorBody()?.string()
        val parsed = body?.let { runCatching { json.decodeFromString(ApiErrorDto.serializer(), it) }.getOrNull() }
        val serverMessage = parsed?.message?.let { element ->
            when (element) {
                is JsonArray -> element.joinToString(" ") { it.jsonPrimitive.content }
                is JsonPrimitive -> element.content
                else -> null
            }
        }
        return when (exception.code()) {
            429 -> "Too many login attempts. Wait a minute and try again."
            401 -> serverMessage ?: "Invalid employee ID or PIN."
            403 -> serverMessage ?: "This account cannot do that. Contact your supervisor."
            404 -> serverMessage ?: fallback
            else -> serverMessage ?: fallback
        }
    }
}
