package com.bolang.android

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bolang.android.api.model.ProjectModel
import com.bolang.android.databinding.ActivityMainBinding
import com.bolang.android.sdk.BoLangSDK
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var projects: List<ProjectModel> = emptyList()
    private var selectedProject: ProjectModel? = null

    // Project ID sumber terjemahan (sesuaikan jika pakai project lain)
    private val SOURCE_PROJECT = "proj-1"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        BoLangSDK.init(this)
        setupListeners()

        // Selalu fetch public translations dulu sebelum tampilkan apapun
        lifecycleScope.launch {
            BoLangSDK.loadPublicTranslations(SOURCE_PROJECT)
            applyAllTranslations()           // apply ke semua view
            setLoading(false)

            if (BoLangSDK.isLoggedIn) {
                showProjectsView()
                fetchProjects()
            } else {
                showLoginView()
            }
        }
    }

    // =========================================================================
    // LISTENERS
    // =========================================================================

    private fun setupListeners() {
        binding.btnTestConn.setOnClickListener { testConnection() }

        binding.btnLogin.setOnClickListener {
            val url   = binding.etServerUrl.text.toString().trim()
            val email = binding.etEmail.text.toString().trim()
            val pass  = binding.etPassword.text.toString()
            if (url.isEmpty())   { snack("Isi Server URL dulu"); return@setOnClickListener }
            if (email.isEmpty() || pass.isEmpty()) { snack(BoLangSDK.getString("login.email", "Email") + " & " + BoLangSDK.getString("login.password", "Password") + " wajib diisi"); return@setOnClickListener }
            BoLangSDK.updateBaseUrl(url)
            doLogin(email, pass)
        }

        binding.btnLogout.setOnClickListener {
            BoLangSDK.logout()
            showLoginView()
        }

        binding.spinnerProjects.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>, v: View?, pos: Int, id: Long) {
                val project = projects.getOrNull(pos) ?: return
                if (project.id != selectedProject?.id) {
                    selectedProject = project
                    fetchTranslations(project)
                }
            }
            override fun onNothingSelected(p: AdapterView<*>) {}
        }

        binding.btnLoad.setOnClickListener {
            selectedProject?.let { fetchTranslations(it) }
        }

        binding.btnLangId.setOnClickListener { applyLanguage("id") }
        binding.btnLangEn.setOnClickListener { applyLanguage("en") }
        binding.btnLangJv.setOnClickListener { applyLanguage("jv") }
    }

    // =========================================================================
    // APPLY TRANSLATIONS — semua text dari BoLang, tidak ada hardcode
    // =========================================================================

    /**
     * Terapkan SEMUA terjemahan ke UI.
     * Dipanggil setiap kali bahasa berubah atau translations baru di-load.
     */
    private fun applyAllTranslations(lang: String = BoLangSDK.activeLang) {
        fun str(key: String, fallback: String = "") = BoLangSDK.getString(key, fallback, lang)

        // ── Header ─────────────────────────────────────────────────────────
        binding.tvAppTitle.text    = str("app.title")
        binding.tvAppSubtitle.text = str("app.subtitle")

        // ── Login screen ───────────────────────────────────────────────────
        binding.tvAuthTitle.text    = str("login.title")
        binding.tilEmail.hint       = str("login.email")
        binding.tilPassword.hint    = str("login.password")
        binding.btnLogin.text       = str("login.button")
        binding.btnTestConn.text    = str("login.test_conn")

        // ── Projects screen ────────────────────────────────────────────────
        binding.tvProjectSelectLabel.text = str("project.select")
        binding.btnLoad.text              = str("project.refresh")
        binding.btnLogout.text            = str("nav.logout")
        binding.tvPreviewLabel.text       = str("nav.preview_label")
        binding.tvDumpLabel.text          = str("nav.dump_label")

        // ── Live preview ───────────────────────────────────────────────────
        binding.tvLoginTitle.text    = str("login.title")
        binding.tvSubtitle.text      = str("home.subtitle")
        binding.btnLoginPreview.text = str("login.button")
        binding.tvNavHome.text       = "nav.home → " + str("nav.home")

        highlightActiveLangButton(lang)
    }

    private fun applyLanguage(lang: String) {
        BoLangSDK.setLanguage(lang)
        applyAllTranslations(lang)
        renderRawDump(lang)
    }

    private fun renderRawDump(lang: String) {
        val allKeys = BoLangSDK.getAllStrings(lang)
        val sb = StringBuilder()
        sb.appendLine("language: $lang\n")
        if (allKeys.isEmpty()) {
            sb.appendLine("(belum ada data)")
        } else {
            allKeys.entries.take(30).forEach { (key, value) ->
                sb.appendLine("$key")
                sb.appendLine("  ↳ $value")
            }
            if (allKeys.size > 30) sb.appendLine("… +${allKeys.size - 30} keys lainnya")
        }
        binding.tvOutput.text = sb.toString()
    }

    private fun highlightActiveLangButton(lang: String) {
        val active   = 0xFF4f8ef7.toInt()
        val inactive = 0xFF2d2d2d.toInt()
        binding.btnLangId.setBackgroundColor(if (lang == "id") active else inactive)
        binding.btnLangEn.setBackgroundColor(if (lang == "en") active else inactive)
        binding.btnLangJv.setBackgroundColor(if (lang == "jv") active else inactive)
    }

    // =========================================================================
    // NETWORK CALLS
    // =========================================================================

    private fun testConnection() {
        val url = binding.etServerUrl.text.toString().trim()
        if (url.isEmpty()) { snack("Isi Server URL dulu"); return }
        BoLangSDK.updateBaseUrl(url)
        setLoading(true)
        lifecycleScope.launch {
            BoLangSDK.ping()
                .onSuccess { snack("✅ Server OK: $it") }
                .onFailure { snack("❌ Gagal konek: ${it.message}") }
            setLoading(false)
        }
    }

    private fun doLogin(email: String, password: String) {
        setLoading(true)
        lifecycleScope.launch {
            BoLangSDK.login(email, password)
                .onSuccess { user ->
                    snack("✓ ${user.name}")
                    showProjectsView()
                    fetchProjects()
                }
                .onFailure { snack("❌ ${it.message}") }
            setLoading(false)
        }
    }

    private fun fetchProjects() {
        setLoading(true)
        lifecycleScope.launch {
            BoLangSDK.getProjects()
                .onSuccess { list ->
                    projects = list
                    val labels = list.map { "${it.name}  [${it.langs.joinToString(", ")}]" }
                    binding.spinnerProjects.adapter = ArrayAdapter(
                        this@MainActivity,
                        android.R.layout.simple_spinner_dropdown_item, labels
                    )
                    binding.btnLoad.isEnabled = list.isNotEmpty()
                    list.firstOrNull()?.let { first ->
                        selectedProject = first
                        fetchTranslations(first)
                    }
                }
                .onFailure { snack("❌ ${it.message}") }
            setLoading(false)
        }
    }

    private fun fetchTranslations(project: ProjectModel) {
        setLoading(true)
        lifecycleScope.launch {
            BoLangSDK.loadTranslations(project.id)
                .onSuccess { count ->
                    // SDK otomatis pilih bahasa sesuai locale HP
                    val lang = BoLangSDK.activeLang
                    applyAllTranslations(lang)
                    renderRawDump(lang)
                    snack("✓ $count keys · lang: $lang")
                    binding.btnLoad.isEnabled = true
                    binding.btnLangId.isEnabled = project.langs.contains("id")
                    binding.btnLangEn.isEnabled = project.langs.contains("en")
                    binding.btnLangJv.isEnabled = project.langs.contains("jv")
                }
                .onFailure { snack("❌ ${it.message}") }
            setLoading(false)
        }
    }

    // =========================================================================
    // VIEW HELPERS
    // =========================================================================

    private fun showLoginView() {
        binding.layoutLogin.visibility    = View.VISIBLE
        binding.layoutProjects.visibility = View.GONE
    }

    private fun showProjectsView() {
        binding.layoutLogin.visibility    = View.GONE
        binding.layoutProjects.visibility = View.VISIBLE
    }

    private fun setLoading(on: Boolean) {
        binding.progressBar.visibility = if (on) View.VISIBLE else View.GONE
    }

    private fun snack(msg: String) =
        Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG).show()
}
