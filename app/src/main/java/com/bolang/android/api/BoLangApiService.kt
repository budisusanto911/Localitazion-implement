package com.bolang.android.api

import com.bolang.android.api.model.*
import com.google.gson.JsonObject
import retrofit2.http.*

interface BoLangApiService {

    // ===== HEALTH =====

    @GET("health")
    suspend fun health(): com.google.gson.JsonObject

    // ===== AUTH =====

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): AuthResponse

    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): AuthResponse

    @GET("auth/me")
    suspend fun getMe(@Header("Authorization") token: String): UserModel

    // ===== PROJECTS =====

    @GET("projects")
    suspend fun getProjects(@Header("Authorization") token: String): List<ProjectModel>

    @GET("projects/{id}")
    suspend fun getProject(
        @Header("Authorization") token: String,
        @Path("id") projectId: String
    ): ProjectModel

    @POST("projects/{id}/publish")
    suspend fun publishProject(
        @Header("Authorization") token: String,
        @Path("id") projectId: String
    ): PublishResponse

    // ===== TRANSLATIONS =====

    @GET("projects/{id}/translations")
    suspend fun getTranslations(
        @Header("Authorization") token: String,
        @Path("id") projectId: String,
        @Query("ns") namespace: String? = null,
        @Query("q") query: String? = null,
        @Query("missingOnly") missingOnly: Boolean? = null
    ): List<TranslationModel>

    @POST("projects/{id}/translations")
    suspend fun addTranslation(
        @Header("Authorization") token: String,
        @Path("id") projectId: String,
        @Body body: Map<String, Any>
    ): TranslationModel

    @PUT("projects/{id}/translations/{tid}")
    suspend fun updateTranslation(
        @Header("Authorization") token: String,
        @Path("id") projectId: String,
        @Path("tid") translationId: String,
        @Body body: Map<String, Any>
    ): TranslationModel

    @PATCH("projects/{id}/translations/{tid}/value")
    suspend fun patchValue(
        @Header("Authorization") token: String,
        @Path("id") projectId: String,
        @Path("tid") translationId: String,
        @Body body: Map<String, String>
    ): TranslationModel

    @DELETE("projects/{id}/translations/{tid}")
    suspend fun deleteTranslation(
        @Header("Authorization") token: String,
        @Path("id") projectId: String,
        @Path("tid") translationId: String
    ): JsonObject

    // ===== EXPORT =====
    // Returns: { "id": { "home.title": "Beranda", ... }, "en": { ... } }

    @GET("projects/{id}/translations/export/{format}")
    suspend fun exportTranslations(
        @Header("Authorization") token: String,
        @Path("id") projectId: String,
        @Path("format") format: String,
        @Query("lang") lang: String? = null
    ): JsonObject

    // Public endpoint — tanpa auth, hanya published keys
    // Cocok untuk load teks login screen sebelum user login
    @GET("projects/{id}/translations/public")
    suspend fun getPublicTranslations(
        @Path("id") projectId: String
    ): JsonObject
}
