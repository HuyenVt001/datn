package com.example.snapget.core.di

import com.example.snapget.BuildConfig
import com.example.snapget.core.network.api.CoopApi
import com.example.snapget.core.network.api.FrameApi
import com.example.snapget.core.network.api.FriendshipApi
import com.example.snapget.core.network.api.GachaApi
import com.example.snapget.core.network.api.MessageApi
import com.example.snapget.core.network.api.MomentApi
import com.example.snapget.core.network.api.QuestApi
import com.example.snapget.core.network.api.TopupApi
import com.example.snapget.core.network.api.UploadApi
import com.example.snapget.core.network.api.UserApi
import com.example.snapget.core.network.interceptor.AuthInterceptor
import com.example.snapget.core.network.interceptor.TokenAuthenticator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Tang network: OkHttp (gan Firebase token qua AuthInterceptor) + Retrofit tro
 * ve server NestJS (BuildConfig.SERVER_BASE_URL — doc tu local.properties).
 */
@Module
@InstallIn(SingletonComponent::class)
class NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        tokenAuthenticator: TokenAuthenticator,
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        // Gap 401 -> lam moi ID token va thu lai; van 401 -> dang xuat (2026-07-28)
        .authenticator(tokenAuthenticator)
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                // BODY chi o debug; che header Authorization de khong lo token ra log
                level = if (BuildConfig.DEBUG) {
                    HttpLoggingInterceptor.Level.BODY
                } else {
                    HttpLoggingInterceptor.Level.NONE
                }
                redactHeader("Authorization")
            },
        )
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.SERVER_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    @Provides
    @Singleton
    fun provideUserApi(retrofit: Retrofit): UserApi = retrofit.create(UserApi::class.java)

    @Provides
    @Singleton
    fun provideUploadApi(retrofit: Retrofit): UploadApi = retrofit.create(UploadApi::class.java)

    @Provides
    @Singleton
    fun provideMomentApi(retrofit: Retrofit): MomentApi = retrofit.create(MomentApi::class.java)

    @Provides
    @Singleton
    fun provideFriendshipApi(retrofit: Retrofit): FriendshipApi = retrofit.create(FriendshipApi::class.java)

    @Provides
    @Singleton
    fun provideMessageApi(retrofit: Retrofit): MessageApi = retrofit.create(MessageApi::class.java)

    @Provides
    @Singleton
    fun provideQuestApi(retrofit: Retrofit): QuestApi = retrofit.create(QuestApi::class.java)

    @Provides
    @Singleton
    fun provideFrameApi(retrofit: Retrofit): FrameApi = retrofit.create(FrameApi::class.java)

    @Provides
    @Singleton
    fun provideCoopApi(retrofit: Retrofit): CoopApi = retrofit.create(CoopApi::class.java)

    @Provides
    @Singleton
    fun provideGachaApi(retrofit: Retrofit): GachaApi = retrofit.create(GachaApi::class.java)

    @Provides
    @Singleton
    fun provideTopupApi(retrofit: Retrofit): TopupApi = retrofit.create(TopupApi::class.java)
}
