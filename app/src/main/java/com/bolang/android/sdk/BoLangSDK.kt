package com.bolang.android.sdk

import android.content.Context
import android.content.SharedPreferences
import com.bolang.android.api.BoLangClient
import com.bolang.android.api.model.LoginRequest
import com.bolang.android.api.model.ProjectModel
import com.bolang.android.api.model.RegisterRequest
import com.bolang.android.api.model.TranslationModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import retrofit2.HttpException

/**
 * BoLangSDK — singleton untuk fetch & cache translation dari server BoLang.
 *
 * Cara pakai:
 *   1. BoLangSDK.init(context, "http://10.0.3.2:3000/api")
 *   2. BoLangSDK.login(email, password)
 *   3. BoLangSDK.loadTranslations(projectId)
 *   4. val title = BoLangSDK.getString("home.title", "Default Title")
 */
object BoLangSDK {

    private const val PREF_NAME        = "bolang_prefs"
    private const val KEY_TOKEN        = "token"
    private const val KEY_CACHE        = "translations_cache"
    private const val KEY_ACTIVE_LANG  = "active_lang"
    private const val KEY_BASE_URL     = "base_url"
    private const val DEFAULT_URL      = "http://10.0.3.2:3000/api"

    private lateinit var prefs: SharedPreferences
    private val gson = Gson()

    // translations[lang][key] = value
    private var translations: Map<String, Map<String, String>> = emptyMap()

    var activeLang: String = "id"
        private set

    // ===== INIT =====

    fun init(context: Context, baseUrl: String? = null) {
        prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val url = baseUrl ?: prefs.getString(KEY_BASE_URL, DEFAULT_URL) ?: DEFAULT_URL
        BoLangClient.init(url)
        // Jika belum ada preferensi tersimpan, pakai bahasa sistem HP
        val saved = prefs.getString(KEY_ACTIVE_LANG, null)
        activeLang = saved ?: java.util.Locale.getDefault().language
        loadCachedTranslations()
    }

    fun updateBaseUrl(url: String) {
        prefs.edit().putString(KEY_BASE_URL, url).apply()
        BoLangClient.init(url)
    }

    suspend fun ping(): Result<String> {
        return try {
            val res = BoLangClient.api().health()
            Result.success(res.get("status")?.asString ?: "ok")
        } catch (e: Exception) {
            Result.failure(parseError(e))
        }
    }

    /**
     * Load published translations TANPA login — untuk teks di login screen.
     * Endpoint public: GET /api/projects/{id}/translations/public
     */
    suspend fun loadPublicTranslations(projectId: String): Result<Int> {
        return try {
            val rawJson = BoLangClient.api().getPublicTranslations(projectId)
            val parsed = parseTranslationJson(rawJson)
            val merged = translations.toMutableMap()
            parsed.forEach { (lang, map) -> merged[lang] = (merged[lang] ?: emptyMap()) + map }
            translations = merged
            saveCache(merged)
            setLanguageFromLocale(merged)
            Result.success(parsed.values.sumOf { it.size })
        } catch (e: Exception) {
            Result.failure(parseError(e))
        }
    }

    // ===== TOKEN =====

    var token: String?
        get() = prefs.getString(KEY_TOKEN, null)
        private set(v) = prefs.edit().putString(KEY_TOKEN, v).apply()

    val bearerToken: String get() = "Bearer ${token.orEmpty()}"
    val isLoggedIn: Boolean get() = !token.isNullOrEmpty()

    // ===== LANGUAGE =====

    fun setLanguage(lang: String) {
        activeLang = lang
        prefs.edit().putString(KEY_ACTIVE_LANG, lang).apply()
    }

    /**
     * Pilih bahasa berdasarkan prioritas:
     * 1. Bahasa sistem HP (Locale.getDefault)
     * 2. Preferensi tersimpan sebelumnya
     * 3. Bahasa pertama yang tersedia di translations
     */
    private fun setLanguageFromLocale(available: Map<String, Map<String, String>>) {
        if (available.isEmpty()) return
        val deviceLang = java.util.Locale.getDefault().language
        val preferred = when {
            deviceLang in available  -> deviceLang   // HP pakai id? → pakai id
            activeLang in available  -> activeLang   // ada preferensi tersimpan?
            else -> available.keys.first()           // fallback bahasa pertama
        }
        setLanguage(preferred)
    }

    /** Reset preferensi bahasa agar ikut bahasa HP lagi */
    fun resetLanguageToDeviceLocale() {
        prefs.edit().remove(KEY_ACTIVE_LANG).apply()
        activeLang = java.util.Locale.getDefault().language
    }

    fun availableLanguages(): List<String> = translations.keys.toList()

    // ===== TRANSLATE =====

    /**
     * Ambil nilai terjemahan berdasarkan key.
     * Fallback ke bahasa lain jika key tidak ada di [lang].
     */
    fun getString(key: String, fallback: String = key, lang: String = activeLang): String {
        return translations[lang]?.get(key)
            ?: translations.values.firstOrNull { it.containsKey(key) }?.get(key)
            ?: fallback
    }

    /** Ambil semua key-value untuk bahasa tertentu. */
    fun getAllStrings(lang: String = activeLang): Map<String, String> =
        translations[lang] ?: emptyMap()

    // ===== AUTH =====

    suspend fun login(email: String, password: String): Result<UserInfo> {
        return try {
            val res = BoLangClient.api().login(LoginRequest(email, password))
            token = res.token
            Result.success(UserInfo(res.user.id, res.user.name, res.user.email))
        } catch (e: Exception) {
            Result.failure(parseError(e))
        }
    }

    suspend fun register(name: String, email: String, password: String): Result<UserInfo> {
        return try {
            val res = BoLangClient.api().register(RegisterRequest(name, email, password))
            token = res.token
            Result.success(UserInfo(res.user.id, res.user.name, res.user.email))
        } catch (e: Exception) {
            Result.failure(parseError(e))
        }
    }

    fun logout() {
        prefs.edit().remove(KEY_TOKEN).remove(KEY_CACHE).apply()
        translations = emptyMap()
        token = null
    }

    // ===== PROJECTS =====

    suspend fun getProjects(): Result<List<ProjectModel>> {
        return try {
            Result.success(BoLangClient.api().getProjects(bearerToken))
        } catch (e: Exception) {
            Result.failure(parseError(e))
        }
    }

    suspend fun getProject(projectId: String): Result<ProjectModel> {
        return try {
            Result.success(BoLangClient.api().getProject(bearerToken, projectId))
        } catch (e: Exception) {
            Result.failure(parseError(e))
        }
    }

    // ===== TRANSLATIONS =====

    /**
     * Fetch & cache semua terjemahan dari project.
     * Setelah ini, [getString] sudah bisa dipakai.
     * @return jumlah key yang berhasil di-load
     */
    suspend fun loadTranslations(projectId: String): Result<Int> {
        return try {
            val rawJson = BoLangClient.api().exportTranslations(bearerToken, projectId, "json")
            val parsed = parseTranslationJson(rawJson)
            translations = parsed
            saveCache(parsed)
            setLanguageFromLocale(parsed)
            Result.success(parsed.values.sumOf { it.size })
        } catch (e: Exception) {
            Result.failure(parseError(e))
        }
    }

    private fun parseTranslationJson(json: com.google.gson.JsonObject): Map<String, Map<String, String>> {
        val result = mutableMapOf<String, Map<String, String>>()
        for ((lang, entry) in json.entrySet()) {
            if (entry.isJsonObject) {
                val map = mutableMapOf<String, String>()
                for ((k, v) in entry.asJsonObject.entrySet()) {
                    map[k] = if (v.isJsonNull) "" else v.asString
                }
                result[lang] = map
            }
        }
        return result
    }

    suspend fun getRawTranslations(
        projectId: String,
        namespace: String? = null,
        query: String? = null
    ): Result<List<TranslationModel>> {
        return try {
            Result.success(BoLangClient.api().getTranslations(bearerToken, projectId, namespace, query))
        } catch (e: Exception) {
            Result.failure(parseError(e))
        }
    }

    suspend fun updateValue(projectId: String, translationId: String, lang: String, value: String): Result<Unit> {
        return try {
            BoLangClient.api().patchValue(bearerToken, projectId, translationId, mapOf("lang" to lang, "value" to value))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(parseError(e))
        }
    }

    // ===== CACHE =====

    private fun saveCache(data: Map<String, Map<String, String>>) {
        prefs.edit().putString(KEY_CACHE, gson.toJson(data)).apply()
    }

    private fun loadCachedTranslations() {
        val json = prefs.getString(KEY_CACHE, null) ?: return
        try {
            val type = object : TypeToken<Map<String, Map<String, String>>>() {}.type
            translations = gson.fromJson(json, type) ?: emptyMap()
        } catch (_: Exception) {}
    }

    // ===== ERROR HELPER =====

    private fun parseError(e: Exception): Exception {
        if (e is HttpException) {
            val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
            val msg = runCatching {
                gson.fromJson(body, com.google.gson.JsonObject::class.java)
                    ?.get("error")?.asString
            }.getOrNull()
            return Exception(msg ?: "HTTP ${e.code()}: ${e.message()}")
        }
        return e
    }

    data class UserInfo(val id: String, val name: String, val email: String)
}
