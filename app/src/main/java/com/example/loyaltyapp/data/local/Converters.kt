package com.example.loyaltyapp.data.local

import androidx.room.TypeConverter
import com.example.loyaltyapp.data.local.entity.Product
import com.example.loyaltyapp.data.local.entity.RegistrationSyncStatus
import com.example.loyaltyapp.data.local.entity.SmsStatus
import com.example.loyaltyapp.data.local.entity.SyncStatus
import java.math.BigDecimal

class Converters {

    @TypeConverter
    fun fromBigDecimal(value: BigDecimal?): String? = value?.toPlainString()

    @TypeConverter
    fun toBigDecimal(value: String?): BigDecimal? = value?.let { BigDecimal(it) }

    @TypeConverter
    fun fromProduct(value: Product): String = value.name

    @TypeConverter
    fun toProduct(value: String): Product = Product.valueOf(value)

    @TypeConverter
    fun fromSyncStatus(value: SyncStatus): String = value.name

    @TypeConverter
    fun toSyncStatus(value: String): SyncStatus = SyncStatus.valueOf(value)

    @TypeConverter
    fun fromSmsStatus(value: SmsStatus): String = value.name

    @TypeConverter
    fun toSmsStatus(value: String): SmsStatus = SmsStatus.valueOf(value)

    @TypeConverter
    fun fromRegistrationSyncStatus(value: RegistrationSyncStatus): String = value.name

    @TypeConverter
    fun toRegistrationSyncStatus(value: String): RegistrationSyncStatus = RegistrationSyncStatus.valueOf(value)

    // Plate numbers can't contain commas, so a plain join/split is safe — no need for JSON here.
    @TypeConverter
    fun fromStringList(value: List<String>): String = value.joinToString(",")

    @TypeConverter
    fun toStringList(value: String): List<String> = if (value.isEmpty()) emptyList() else value.split(",")
}
