package com.dyplink.banners.model

data class BannerCategory(
    val id: String,
    val name: String,
    val layout: String,
    val aspectRatio: String?,
    val autoRotate: Boolean,
    val rotationInterval: Int,  // seconds
    val heading: String?,
    val backgroundColor: String,
    val padding: Int,
    val banners: List<Banner>,
)
