package com.example.loyaltyapp.core.sms

import com.example.loyaltyapp.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

sealed class SmsSendResult {
    data class Success(val providerMessageId: String) : SmsSendResult()
    data class Failure(val reason: String) : SmsSendResult()
}

@Serializable
private data class AfricasTalkingRecipientDto(
    val statusCode: Int? = null,
    val number: String? = null,
    val status: String? = null,
    val cost: String? = null,
    val messageId: String? = null
)

@Serializable
private data class AfricasTalkingSmsDataDto(
    @SerialName("Message") val message: String? = null,
    @SerialName("Recipients") val recipients: List<AfricasTalkingRecipientDto> = emptyList()
)

@Serializable
private data class AfricasTalkingSmsResponseDto(
    @SerialName("SMSMessageData") val smsMessageData: AfricasTalkingSmsDataDto? = null
)

/**
 * Sends the customer cashback-confirmation SMS directly from the device via Africa's Talking,
 * bypassing this app's own API entirely — the backend no longer sends this SMS for app-created
 * sales, so it must go out over the phone's own connectivity even if this app never regains a
 * connection back to the Green Wells API for this specific sale (see `SaleRepository`).
 *
 * Deliberately uses its own bare [OkHttpClient], not the shared Retrofit/OkHttp client from
 * [com.example.loyaltyapp.di.NetworkModule] — that client's auth interceptor unconditionally
 * attaches this app's own `Authorization: Bearer` attendant token to every request, which has no
 * meaning to Africa's Talking and must not be sent to a third party. Africa's Talking uses its
 * own `apiKey` header instead.
 */
@Singleton
class AfricasTalkingSmsSender @Inject constructor(
    private val json: Json
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /** Must match the backend's canonical template exactly — same text staff see in the web admin audit trail. */
    fun buildMessage(amountPaid: Double, cashbackEarned: Double, monthToDateCashback: Double): String =
        "Green Wells: You paid KES ${formatWhole(amountPaid)} and earned KES ${formatWhole(cashbackEarned)} cashback. Your total cashback this month is KES ${formatWhole(monthToDateCashback)}."

    /** Cashback figures are always whole shillings — no decimal places to show. */
    private fun formatWhole(value: Double): String = "%.0f".format(value)

    suspend fun send(phoneE164: String, message: String): SmsSendResult = withContext(Dispatchers.IO) {
        try {
            val baseUrl = BuildConfig.AFRICASTALKING_BASE_URL.trimEnd('/')
            val formBody = FormBody.Builder()
                .add("username", BuildConfig.AFRICASTALKING_USERNAME)
                .add("to", phoneE164)
                .add("message", message)
                .add("from", BuildConfig.AFRICASTALKING_SENDER_ID)
                .build()
            val request = Request.Builder()
                .url("$baseUrl/version1/messaging")
                .addHeader("apiKey", BuildConfig.AFRICASTALKING_API_KEY)
                .addHeader("Accept", "application/json")
                .post(formBody)
                .build()

            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext SmsSendResult.Failure("HTTP ${response.code}: ${bodyString.take(200)}")
                }
                val recipient = runCatching { json.decodeFromString<AfricasTalkingSmsResponseDto>(bodyString) }
                    .getOrNull()
                    ?.smsMessageData
                    ?.recipients
                    ?.firstOrNull()
                when {
                    recipient == null ->
                        SmsSendResult.Failure("Unparseable Africa's Talking response: ${bodyString.take(200)}")
                    recipient.status == "Success" ->
                        SmsSendResult.Success(recipient.messageId ?: "")
                    else ->
                        SmsSendResult.Failure("${recipient.status ?: "Unknown failure"} (code ${recipient.statusCode ?: "?"})")
                }
            }
        } catch (e: Exception) {
            SmsSendResult.Failure(e.message ?: e.javaClass.simpleName)
        }
    }
}
