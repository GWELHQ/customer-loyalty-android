package com.example.loyaltyapp.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.loyaltyapp.data.local.LoyaltyDatabase
import com.example.loyaltyapp.data.local.dao.CustomerDao
import com.example.loyaltyapp.data.local.dao.CustomerRegistrationDao
import com.example.loyaltyapp.data.local.dao.PriceDao
import com.example.loyaltyapp.data.local.dao.SaleDao
import com.example.loyaltyapp.data.local.dao.StationDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Additive — unlike every other version bump in this database, which relies on the destructive
 * fallback below, this one must NOT drop tables: `sales`/`customer_registrations` can hold
 * durable, not-yet-synced offline work at the moment an app update installs, and wiping them
 * would destroy exactly what the attendant refresh-token sync feature exists to protect.
 */
private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE customer_registrations ADD COLUMN attendantId TEXT NOT NULL DEFAULT ''")
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): LoyaltyDatabase =
        Room.databaseBuilder(context, LoyaltyDatabase::class.java, LoyaltyDatabase.DATABASE_NAME)
            .addMigrations(MIGRATION_4_5)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideStationDao(db: LoyaltyDatabase): StationDao = db.stationDao()

    @Provides
    fun provideCustomerDao(db: LoyaltyDatabase): CustomerDao = db.customerDao()

    @Provides
    fun providePriceDao(db: LoyaltyDatabase): PriceDao = db.priceDao()

    @Provides
    fun provideSaleDao(db: LoyaltyDatabase): SaleDao = db.saleDao()

    @Provides
    fun provideCustomerRegistrationDao(db: LoyaltyDatabase): CustomerRegistrationDao = db.customerRegistrationDao()
}
