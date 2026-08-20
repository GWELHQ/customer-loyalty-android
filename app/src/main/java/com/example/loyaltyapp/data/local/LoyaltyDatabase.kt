package com.example.loyaltyapp.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.loyaltyapp.data.local.dao.CustomerDao
import com.example.loyaltyapp.data.local.dao.CustomerRegistrationDao
import com.example.loyaltyapp.data.local.dao.PriceDao
import com.example.loyaltyapp.data.local.dao.SaleDao
import com.example.loyaltyapp.data.local.dao.StationDao
import com.example.loyaltyapp.data.local.entity.CustomerEntity
import com.example.loyaltyapp.data.local.entity.CustomerRegistrationEntity
import com.example.loyaltyapp.data.local.entity.PriceEntity
import com.example.loyaltyapp.data.local.entity.SaleEntity
import com.example.loyaltyapp.data.local.entity.StationEntity

@Database(
    entities = [
        StationEntity::class,
        CustomerEntity::class,
        PriceEntity::class,
        SaleEntity::class,
        CustomerRegistrationEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class LoyaltyDatabase : RoomDatabase() {
    abstract fun stationDao(): StationDao
    abstract fun customerDao(): CustomerDao
    abstract fun priceDao(): PriceDao
    abstract fun saleDao(): SaleDao
    abstract fun customerRegistrationDao(): CustomerRegistrationDao

    companion object {
        const val DATABASE_NAME = "loyalty_app.db"
    }
}
