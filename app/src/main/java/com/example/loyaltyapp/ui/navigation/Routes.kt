package com.example.loyaltyapp.ui.navigation

object Routes {
    const val LOGIN = "login"
    const val NEW_SALE = "new_sale"
    const val TODAY = "today"
    const val SYNC_QUEUE = "sync_queue"
    const val PROFILE = "profile"
    const val DAILY_SUMMARY = "daily_summary"
    const val TODAY_DETAIL = "today_detail/{type}/{id}"

    fun todayDetail(type: TodayDetailType, id: String) = "today_detail/${type.name}/$id"
}

/** Which repository a Today's-sales row's detail page should load from. */
enum class TodayDetailType { SALE, REGISTRATION }
