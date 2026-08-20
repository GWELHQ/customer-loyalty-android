package com.example.loyaltyapp.di

import android.content.Context
import androidx.room.Room
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

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): LoyaltyDatabase =
        Room.databaseBuilder(context, LoyaltyDatabase::class.java, LoyaltyDatabase.DATABASE_NAME)
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
