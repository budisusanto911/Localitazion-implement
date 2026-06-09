package com.bolang.android.api.model

data class ProjectModel(
    val id: String,
    val name: String,
    val slug: String,
    val desc: String,
    val langs: List<String>,
    val defaultLang: String,
    val keyCount: Int = 0
)
