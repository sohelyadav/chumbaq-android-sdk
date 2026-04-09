package com.dyplink.messages.model

data class MessageTheme(
    val backgroundColor: String?,
    val textColor: String?,
    val titleColor: String?,
    val buttonPrimaryColor: String?,
    val buttonSecondaryColor: String?,
    val overlayColor: String?,
    val borderRadius: Int?,
    /** "fade" | "slide_up" | "slide_down" | "scale" | "none" */
    val animation: String?,
)
