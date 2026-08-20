package com.example.loyaltyapp.data.repository

import com.example.loyaltyapp.data.local.dao.StationDao
import com.example.loyaltyapp.data.local.entity.StationEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StationRepository @Inject constructor(
    private val stationDao: StationDao
) {
    suspend fun getById(id: String): StationEntity? = stationDao.getById(id)
}
