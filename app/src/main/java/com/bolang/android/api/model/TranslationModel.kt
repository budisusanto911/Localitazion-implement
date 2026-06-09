package com.bolang.android.api.model

data class TranslationModel(
    val id: String,
    val projectId: String,
    val key: String,
    val namespace: String,
    val values: Map<String, String>,
    val status: String
)

data class PublishResponse(
    val message: String,
    val publishedCount: Int = 0
)
