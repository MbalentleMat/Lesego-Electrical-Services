package com.lesego.electrical.di

import android.content.Context
import androidx.room.Room
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.lesego.electrical.data.db.AppDatabase
import com.lesego.electrical.data.db.dao.BookingDao
import com.lesego.electrical.data.db.dao.ServiceDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing application-level singleton dependencies.
 * Installed in the [SingletonComponent] so instances survive configuration changes.
 *
 * @author Lesego Electrical Team
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // ─── Firebase ────────────────────────────────────────────────────────────

    @Singleton
    @Provides
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Singleton
    @Provides
    fun provideFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

    // ─── Room Database ────────────────────────────────────────────────────────

    @Singleton
    @Provides
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        )
            .fallbackToDestructiveMigration()   // TODO: replace with proper Migrations
            .build()

    @Singleton
    @Provides
    fun provideServiceDao(db: AppDatabase): ServiceDao = db.serviceDao()

    @Singleton
    @Provides
    fun provideBookingDao(db: AppDatabase): BookingDao = db.bookingDao()
}
