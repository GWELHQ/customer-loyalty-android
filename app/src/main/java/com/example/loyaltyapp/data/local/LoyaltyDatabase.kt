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
    // v4: licensePlateNumber (String?) -> licensePlateNumbers (List<String>); destructive migration wipes and re-syncs the cheap customer cache.
    // v5: customer_registrations gained attendantId — real, additive Migration(4, 5) in DatabaseModule (NOT destructive): unlike the
    // customer cache, sales/registrations queued offline are not re-syncable if wiped, so this one preserves existing rows.
    version = 5,
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
