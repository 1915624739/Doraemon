package com.cofbro.qian.mapsetting.model

data class PlaceSuggestion(
    val name: String,
    val address: String,
    val poiId: String,
    val latitude: Double?,
    val longitude: Double?,
    val city: String
)
