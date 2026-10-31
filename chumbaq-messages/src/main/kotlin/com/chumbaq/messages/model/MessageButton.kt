package com.chumbaq.messages.model

data class MessageButton(
    val id: String,
    val text: String,
    /** "dismiss" | "deep_link" | "url" | "custom_event" */
    val action: String,
    val actionUrl: String?,
    val actionEvent: String?,
    /** "primary" | "secondary" | "text" */
    val style: String,
)
