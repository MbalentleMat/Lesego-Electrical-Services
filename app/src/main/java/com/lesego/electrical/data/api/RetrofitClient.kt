package com.lesego.electrical.data.api

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Retrofit client singleton.
 *
 * Provides an [ApiService] instance pre-configured with:
 * - Base URL for the Lesego Electrical REST API
 * - [AuthInterceptor] that attaches the Firebase JWT on every request
 * - HTTP logging in debug builds
 * - 30-second timeouts
 *
 * @author Lesego Electrical Team
 */
@Singleton
class RetrofitClient @Inject constructor(
    private val authInterceptor: AuthInterceptor
) {
    companion object {
        // Replace with your real deployed API URL
        private const val BASE_URL = "https://api.lesesoelectric.co.za/v1/"
    }

    private val loggingInterceptor = HttpLoggingInterceptor { message ->
        Timber.tag("OkHttp").d(message)
    }.apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val apiService: ApiService = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(ApiService::class.java)
}

/**
 * OkHttp interceptor that attaches the Firebase ID token as a
 * Bearer Authorization header on every outgoing request.
 *
 * The token is refreshed automatically when Firebase detects it has expired.
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val firebaseAuth: FirebaseAuth
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runBlocking {
            try {
                firebaseAuth.currentUser?.getIdToken(false)?.await()?.token
            } catch (e: Exception) {
                Timber.e(e, "Failed to get Firebase ID token")
                null
            }
        }

        val request = chain.request().newBuilder().apply {
            token?.let { header("Authorization", "Bearer $it") }
            header("Content-Type", "application/json")
            header("Accept", "application/json")
        }.build()

        return chain.proceed(request)
    }
}
