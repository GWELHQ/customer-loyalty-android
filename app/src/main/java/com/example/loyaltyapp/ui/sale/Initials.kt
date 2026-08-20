package com.example.loyaltyapp.ui.sale

internal fun initialsOf(name: String): String =
    name.filter { it.isLetter() || it == ' ' }
        .trim()
        .split(Regex("\\s+"))
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")
        .ifEmpty { "GW" }
