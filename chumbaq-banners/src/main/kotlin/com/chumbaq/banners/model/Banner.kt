package com.chumbaq.banners.model

data class Banner(
    val id: String,
    val title: String,
    val imageUrl: String?,
    val clickUrl: String?,
    val ctaText: String?,
    val sortOrder: Int,
    val isActive: Boolean,
    val metadata: Map<String, Any>?,
)
