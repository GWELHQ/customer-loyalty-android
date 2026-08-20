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

/**
 * Backend endpoints are configured here: base URL comes from [BuildConfig.API_BASE_URL]
 * (`app/build.gradle.kts`). Every mobile route requires `Authorization: Bearer <token>` except
 * login; a 401 anywhere means the token is invalid/expired/deactivated and there's no refresh —
 * clearing the session here sends the app back to the PIN login screen.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideAuthInterceptor(sessionManager: SessionManager): Interceptor = Interceptor { chain ->
        val request = chain.request()
        val token = sessionManager.currentAccessToken()
        val authed = if (!token.isNullOrBlank() && !request.url.encodedPath.endsWith("auth/attendant/login")) {
            request.newBuilder().header("Authorization", "Bearer $token").build()
        } else {
            request
        }
        val response: Response = chain.proceed(authed)
        if (response.code == 401 && !request.url.encodedPath.endsWith("auth/attendant/login")) {
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
    fun provideJson(): Json = Json { ignoreUnknownKeys = true }

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
