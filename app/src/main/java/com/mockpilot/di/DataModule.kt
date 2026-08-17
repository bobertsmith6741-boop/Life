package com.mockpilot.di

import android.content.Context
import androidx.room.Room
import com.mockpilot.data.AppDatabase
import com.mockpilot.data.RouteDao
import com.mockpilot.data.SavedPlaceDao
import com.mockpilot.data.ScheduleDao
import com.mockpilot.data.geocode.NominatimApi
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "mockpilot.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideSavedPlaceDao(db: AppDatabase): SavedPlaceDao = db.savedPlaceDao()
    @Provides fun provideRouteDao(db: AppDatabase): RouteDao = db.routeDao()
    @Provides fun provideScheduleDao(db: AppDatabase): ScheduleDao = db.scheduleDao()
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val NOMINATIM_BASE = "https://nominatim.openstreetmap.org/"

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideOkHttp(): OkHttpClient {
        // Nominatim's usage policy requires an identifying User-Agent on every request.
        val userAgent = Interceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "MockPilot/1.0 (Android; osm geocoding)")
                .build()
            chain.proceed(request)
        }
        return OkHttpClient.Builder()
            .addInterceptor(userAgent)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideNominatim(client: OkHttpClient, json: Json): NominatimApi {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(NOMINATIM_BASE)
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(NominatimApi::class.java)
    }
}
