package com.example.loyaltyapp.di

import com.example.loyaltyapp.BuildConfig
import com.example.loyaltyapp.core.session.SessionManager
import com.example.loyaltyapp.data.remote.api.AuthApi
import com.example.loyaltyapp.data.remote.api.MobileApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.Retrofit
import javax.inject.Singleton

/** Login/nfc-login/refresh don't carry the current session's token, and a 401 on them never clears it. */
private val UNAUTHENTICATED_PATHS = setOf(
    "auth/attendant/login",
    "auth/attendant/nfc-login",
    "auth/attendant/refresh"
)

/**
 * Backend endpoints are configured here: base URL comes from [BuildConfig.API_BASE_URL]
 * (`app/build.gradle.kts`). Every mobile route requires `Authorization: Bearer <token>` except
 * login/nfc-login/refresh; a 401 anywhere else means the token is invalid/expired/deactivated —
 * clearing the session here sends the app back to the PIN login screen.
 *
 * A request that already carries an explicit `Authorization` header (e.g. `MobileApi.syncAs`,
 * used by [com.example.loyaltyapp.core.session.AttendantSyncAuthenticator] to sync a *different*,
 * logged-out attendant's queue in the background) is left untouched here — never overwritten with
 * the current foreground session's token, and never allowed to clear the foreground session on a
 * 401 that actually belongs to someone else's background sync attempt.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideAuthInterceptor(sessionManager: SessionManager): Interceptor = Interceptor { chain ->
        val request = chain.request()
        val isUnauthenticatedRoute = UNAUTHENTICATED_PATHS.any { request.url.encodedPath.endsWith(it) }
        val hasExplicitAuthHeader = request.header("Authorization") != null
        val token = sessionManager.currentAccessToken()
        val authed = if (!isUnauthenticatedRoute && !hasExplicitAuthHeader && !token.isNullOrBlank()) {
            request.newBuilder().header("Authorization", "Bearer $token").build()
        } else {
            request
        }
        val response: Response = chain.proceed(authed)
        if (response.code == 401 && !isUnauthenticatedRoute && !hasExplicitAuthHeader) {
            sessionManager.clear()
        }
        response
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(authInterceptor: Interceptor): OkHttpClient {
        val builder = OkHttpClient.Builder().addInterceptor(authInterceptor)
        if (BuildConfig.DEBUG) {
            builder.addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        }
        return builder.build()
    }

    @Provides
    @Singleton
    // coerceInputValues: a non-nullable field with a default (e.g. CustomerDto.totalCashbackEarned,
    // licensePlateNumbers) can come back as an explicit JSON `null` for a freshly created record
    // instead of the key being omitted — without this, that throws and gets swallowed by callers'
    // generic catch blocks, which was surfacing as "customer not found" for brand-new customers.
    fun provideJson(): Json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideAuthApi(retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)

    @Provides
    @Singleton
    fun provideMobileApi(retrofit: Retrofit): MobileApi = retrofit.create(MobileApi::class.java)
}
