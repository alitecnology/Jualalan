package com.kelaslive.admin

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

class MainActivity : Activity() {
    // Ganti dengan URL Web App Apps Script setelah server berhasil di-deploy.
    private val apiUrl = "https://script.google.com/macros/s/PASTE_DEPLOYMENT_ID/exec"
    private val io = Executors.newSingleThreadExecutor()
    private lateinit var themeManager: ThemeManager
    private var activeTheme = AppTheme("dark", 0xFF12151C.toInt(), 0xFF8B9CFF.toInt(),
        0xFF536DFE.toInt(), 0xFFF3F4F8.toInt(), 0xFF202631.toInt(), 0xFF171C25.toInt())
    private var session = ""
    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private var pendingImageTarget = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        themeManager = ThemeManager(this)
        activeTheme = themeManager.load()
        showLogin()
    }

    private fun baseScreen(title: String) {
        window.statusBarColor = activeTheme.primary
        window.navigationBarColor = activeTheme.navigation
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = if (isLightColor(activeTheme.primary))
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR else 0
        }
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = if (isLightColor(activeTheme.navigation))
                window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            else window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
        }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 28, 24, 24)
            setBackgroundColor(activeTheme.background)
        }
        if (title == "Dashboard Admin" && activeTheme.wallpaperUri.isNotBlank()) {
            try {
                val uri = Uri.parse(activeTheme.wallpaperUri)
                contentResolver.openInputStream(uri)?.use { stream ->
                    val bitmap = BitmapFactory.decodeStream(stream)
                    if (bitmap != null) root.background = BitmapDrawable(resources, bitmap)
                }
            } catch (_: Exception) { root.setBackgroundColor(activeTheme.background) }
        }

        if (activeTheme.logoUri.isNotBlank()) {
            try {
                val image = ImageView(this)
                contentResolver.openInputStream(Uri.parse(activeTheme.logoUri))?.use { stream ->
                    image.setImageBitmap(BitmapFactory.decodeStream(stream))
                }
                image.adjustViewBounds = true
                image.scaleType = ImageView.ScaleType.FIT_CENTER
                root.addView(image, LinearLayout.LayoutParams(76, 76).apply { bottomMargin = 12 })
            } catch (_: Exception) { }
        }

        val heading = TextView(this).apply {
            text = title
            textSize = 25f * activeTheme.textScale
            setTextColor(activeTheme.primary)
            typeface = Typeface.DEFAULT_BOLD
        }
        root.addView(heading)
        root.addView(TextView(this).apply {
            text = "KELASLIVE  •  ADMIN"
            textSize = 11f * activeTheme.textScale
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(contrastText(activeTheme.navigation))
            setPadding(12, 9, 12, 9)
            background = roundedDrawable(activeTheme.navigation, activeTheme.cornerRadius)
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 8; bottomMargin = 6 })
        status = TextView(this).apply {
            textSize = 13f * activeTheme.textScale
            setTextColor(activeTheme.text)
            setPadding(12, 12, 12, 12)
            background = roundedDrawable(activeTheme.card, activeTheme.cornerRadius)
        }
        root.addView(status)
        if (session.isNotBlank()) addAdminNavigation(title)
        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            addView(root)
        })
    }


    /** Real in-app navigation; its background uses the selected theme's navigation color. */
    private fun addAdminNavigation(currentTitle: String) {
        val items = listOf(
            "Beranda" to { showDashboard() },
            "Harga" to { showPrices() },
            "Perangkat" to { showDevices() },
            "Transaksi" to { showOrders() },
            "Tema" to { showThemeSettings() }
        )
        val strip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(6, 6, 6, 6)
            background = roundedDrawable(activeTheme.navigation, activeTheme.cornerRadius)
        }
        items.forEach { (label, action) ->
            val selected = when (label) {
                "Beranda" -> currentTitle == "Dashboard Admin"
                "Harga" -> currentTitle == "Harga Paket"
                "Perangkat" -> currentTitle == "Perangkat Pelanggan"
                "Transaksi" -> currentTitle == "Riwayat Transaksi"
                "Tema" -> currentTitle.contains("Tema") || currentTitle.contains("Preview")
                else -> false
            }
            val item = TextView(this).apply {
                text = if (selected) "• $label" else label
                textSize = 12f * activeTheme.textScale
                typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setTextColor(contrastText(activeTheme.navigation))
                gravity = Gravity.CENTER
                setPadding(12, 12, 12, 12)
                minWidth = (64 * resources.displayMetrics.density).toInt()
                isClickable = true
                isFocusable = true
                contentDescription = "Buka menu $label"
                setOnClickListener { action() }
            }
            strip.addView(item, LinearLayout.LayoutParams(-2, -2))
        }
        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(strip)
        }
        root.addView(scroll, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = 8
            bottomMargin = 8
        })
    }

    private fun addField(hint: String, secret: Boolean = false): EditText {
        val field = EditText(this).apply {
            this.hint = hint
            textSize = 16f * activeTheme.textScale
            setTextColor(activeTheme.text)
            setHintTextColor(Color.argb(150, Color.red(activeTheme.text), Color.green(activeTheme.text), Color.blue(activeTheme.text)))
            if (secret) inputType = 129
            setSingleLine(true)
            background = roundedDrawable(activeTheme.card, activeTheme.cornerRadius)
            setPadding(14, 8, 14, 8)
        }
        root.addView(field, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 10 })
        return field
    }

    private fun addButton(label: String, action: () -> Unit) {
        val button = Button(this).apply {
            text = label
            textSize = 14f * activeTheme.textScale
            setTextColor(contrastText(activeTheme.button))
            background = roundedDrawable(activeTheme.button, activeTheme.cornerRadius)
            minHeight = (44 * resources.displayMetrics.density).toInt()
            isAllCaps = false
            setOnClickListener { action() }
        }
        root.addView(button, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 8 })
    }

    private fun addSectionLabel(label: String) {
        root.addView(TextView(this).apply {
            text = label
            textSize = 15f * activeTheme.textScale
            setTextColor(activeTheme.primary)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 14, 0, 6)
        })
    }

    private fun roundedDrawable(color: Int, radius: Float): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius * resources.displayMetrics.density
        }

    private fun contrastText(color: Int): Int {
        val r = Color.red(color); val g = Color.green(color); val b = Color.blue(color)
        return if ((r * 299 + g * 587 + b * 114) / 1000 >= 145) Color.BLACK else Color.WHITE
    }

    private fun isLightColor(color: Int): Boolean {
        val luminance = (Color.red(color) * 299 + Color.green(color) * 587 + Color.blue(color) * 114) / 1000
        return luminance >= 145
    }

    private fun showLogin() {
        baseScreen("KELASLIVE ADMIN")
        root.addView(TextView(this).apply {
            text = "Login khusus pemilik"
            textSize = 16f * activeTheme.textScale
            setTextColor(activeTheme.text)
        })
        val email = addField("Email pemilik")
        email.inputType = 33
        val password = addField("Kata sandi", true)
        addButton("MASUK") {
            val e = email.text.toString().trim()
            val p = password.text.toString()
            if (e.isBlank() || p.isBlank()) { status.text = "Isi email dan kata sandi."; return@addButton }
            request(JSONObject().put("a", "kla_owner_login").put("email", e).put("password", p)) { result ->
                if (result.optBoolean("ok")) {
                    session = result.optString("session")
                    showDashboard()
                } else status.text = result.optString("error", "Login gagal.")
            }
        }
        addButton("PENGATURAN TEMA") { showThemeSettings() }
        status.text = "Akun dibuat satu kali dari konfigurasi server. Tidak ada pendaftaran publik."
    }

    private fun showDashboard() {
        baseScreen("Dashboard Admin")
        addButton("MUAT RINGKASAN") {
            request(auth("kla_dashboard")) { r ->
                if (r.optBoolean("ok")) {
                    status.text = "Pelanggan aktif: ${r.optInt("active_customers")}\n" +
                        "Total perangkat: ${r.optInt("total_devices")}\n" +
                        "Transaksi berhasil: ${r.optInt("paid_orders")}\n" +
                        "Pendapatan kotor: Rp${r.optLong("revenue_idr")}"
                } else status.text = r.optString("error")
            }
        }
        addButton("PENGATURAN HARGA") { showPrices() }
        addButton("DAFTAR PELANGGAN / PERANGKAT") { showDevices() }
        addButton("RIWAYAT TRANSAKSI") { showOrders() }
        addButton("PENGATURAN TEMA") { showThemeSettings() }
        addButton("KELUAR") {
            request(auth("kla_logout")) { session = ""; showLogin() }
        }
        status.text = "Sesi pemilik aktif. Pilih menu untuk mengelola KelasLive."
    }

    private fun showThemeSettings() {
        baseScreen("Pengaturan Tema")
        addSectionLabel("Tema siap pakai")
        listOf(
            "Dark Mode" to "dark", "Light Mode" to "light",
            "Purple Mode" to "purple", "Premium Mode (Hitam & Emas)" to "premium"
        ).forEach { (label, id) ->
            addButton(label) {
                val selected = themeManager.builtInThemes().first { it.id == id }
                themeManager.save(selected)
                activeTheme = themeManager.load()
                showThemeSettings()
                status.text = "$label diterapkan dan disimpan."
            }
        }

        addSectionLabel("Custom Theme")
        root.addView(TextView(this).apply {
            text = "Warna navigasi/menu dipakai pada bilah menu KELASLIVE ADMIN dan bilah navigasi sistem."
            textSize = 12f * activeTheme.textScale
            setTextColor(activeTheme.text)
            setPadding(0, 0, 0, 8)
        })
        val custom = themeManager.loadCustom()
        val background = colorField("Background", custom.background)
        val primary = colorField("Warna utama", custom.primary)
        val button = colorField("Warna tombol", custom.button)
        val text = colorField("Warna teks", custom.text)
        val card = colorField("Warna kartu", custom.card)
        val navigation = colorField("Warna navigasi/menu", custom.navigation)

        val radiusLabel = TextView(this).apply {
            text = "Sudut kartu: ${custom.cornerRadius.toInt()} dp"
            textSize = 14f * activeTheme.textScale
            setTextColor(activeTheme.text)
            setPadding(0, 14, 0, 4)
        }
        root.addView(radiusLabel)
        val radius = SeekBar(this).apply {
            max = 32
            progress = custom.cornerRadius.toInt().coerceIn(0, 32)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    radiusLabel.text = "Sudut kartu: $progress dp"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        root.addView(radius, LinearLayout.LayoutParams(-1, -2))
        val textScaleLabel = TextView(this).apply {
            text = "Ukuran teks: ${(custom.textScale * 100).toInt()}%"
            textSize = 14f * activeTheme.textScale
            setTextColor(activeTheme.text)
            setPadding(0, 14, 0, 4)
        }
        root.addView(textScaleLabel)
        val textScale = SeekBar(this).apply {
            max = 80
            progress = ((custom.textScale - 0.8f) * 100).toInt().coerceIn(0, 80)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    textScaleLabel.text = "Ukuran teks: ${80 + progress}%"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        root.addView(textScale, LinearLayout.LayoutParams(-1, -2))

        addButton("PILIH WALLPAPER DASHBOARD") { chooseImage("wallpaper") }
        addButton("GANTI LOGO APLIKASI") { chooseImage("logo") }
        addButton("PREVIEW TEMA") {
            val candidate = buildCustomTheme(background, primary, button, text, card, navigation, radius, textScale)
            if (candidate == null) {
                status.text = "Format warna tidak valid. Gunakan format #RRGGBB atau #AARRGGBB."
            } else {
                activeTheme = candidate
                showThemePreview(candidate)
            }
        }
        addButton("SAVE THEME") {
            val candidate = buildCustomTheme(background, primary, button, text, card, navigation, radius, textScale)
            if (candidate == null) {
                status.text = "Format warna tidak valid. Gunakan format #RRGGBB atau #AARRGGBB."
            } else {
                themeManager.save(candidate)
                activeTheme = themeManager.load()
                showDashboardOrSettings()
            }
        }
        addButton("KEMBALI") { if (session.isBlank()) showLogin() else showDashboard() }
        status.text = "Perubahan Custom Theme belum disimpan sampai tombol SAVE THEME ditekan."
    }

    private fun colorField(label: String, color: Int): EditText {
        val field = addField("$label — #RRGGBB")
        field.setText(String.format("#%06X", 0xFFFFFF and color))
        return field
    }

    private fun buildCustomTheme(
        background: EditText, primary: EditText, button: EditText, text: EditText,
        card: EditText, navigation: EditText, radius: SeekBar, textScale: SeekBar
    ): AppTheme? {
        return try {
            val old = themeManager.load()
            AppTheme(
                "custom", parseColorInput(background.text.toString()),
                parseColorInput(primary.text.toString()), parseColorInput(button.text.toString()),
                parseColorInput(text.text.toString()), parseColorInput(card.text.toString()),
                parseColorInput(navigation.text.toString()), radius.progress.toFloat(),
                0.8f + textScale.progress / 100f, old.wallpaperUri, old.logoUri
            )
        } catch (_: Exception) { null }
    }

    private fun parseColorInput(value: String): Int {
        val normalized = value.trim().let { if (it.startsWith("#")) it else "#$it" }
        return Color.parseColor(normalized)
    }

    private fun showThemePreview(theme: AppTheme) {
        baseScreen("Preview Custom Theme")
        addSectionLabel("Contoh tampilan dashboard")
        val sample = TextView(this).apply {
            text = "KelasLive Admin\nPelanggan aktif: 120\nTransaksi berhasil: 85"
            textSize = 18f * theme.textScale
            setTextColor(theme.text)
            setPadding(18, 22, 18, 22)
            background = roundedDrawable(theme.card, theme.cornerRadius)
        }
        root.addView(sample, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 14 })
        addButton("Contoh tombol") { }
        addButton("SAVE THEME") {
            themeManager.save(theme)
            activeTheme = themeManager.load()
            showDashboardOrSettings()
        }
        addButton("KEMBALI EDIT") { showThemeSettings() }
        status.text = "Ini hanya preview. Tema belum disimpan."
    }

    private fun showDashboardOrSettings() {
        if (session.isBlank()) showLogin() else showDashboard()
    }

    private fun chooseImage(target: String) {
        pendingImageTarget = target
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, if (target == "wallpaper") 701 else 702)
    }

    @Deprecated("Deprecated by Android; retained for compatibility with the starter project")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data?.data == null) return
        val uri = data.data!!
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) { }
        if (requestCode == 701) themeManager.saveImageUris(wallpaperUri = uri.toString())
        else themeManager.saveImageUris(logoUri = uri.toString())
        activeTheme = themeManager.load()
        showThemeSettings()
        status.text = if (requestCode == 701) "Wallpaper dipilih." else "Logo dipilih."
    }

    private fun showPrices() {
        baseScreen("Harga Paket")
        request(auth("kla_prices")) { r ->
            if (!r.optBoolean("ok")) { status.text = r.optString("error"); return@request }
            val arr = r.optJSONArray("prices") ?: return@request
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                val id = item.getString("package_id")
                val label = item.getString("label")
                val input = addField("$label — harga rupiah")
                input.setText(item.getString("price_idr"))
                addButton("SIMPAN $label") {
                    val value = input.text.toString().toLongOrNull()
                    if (value == null) { status.text = "Harga tidak valid."; return@addButton }
                    request(auth("kla_set_price").put("package_id", id).put("price_idr", value)) { saved ->
                        status.text = if (saved.optBoolean("ok")) "$label berhasil diperbarui." else saved.optString("error")
                    }
                }
            }
        }
        addButton("KEMBALI") { showDashboard() }
    }

    private fun showDevices() {
        baseScreen("Perangkat Pelanggan")
        request(auth("kla_devices")) { r ->
            val arr = r.optJSONArray("devices")
            if (arr == null || arr.length() == 0) status.text = "Belum ada perangkat berlangganan."
            else {
                val lines = StringBuilder()
                for (i in 0 until arr.length()) {
                    val d = arr.getJSONObject(i)
                    lines.append("ID: ").append(d.optString("device_id")).append("\nStatus: ")
                        .append(d.optString("status")).append("\nPaket: ").append(d.optString("package_id"))
                        .append("\nBerakhir: ").append(d.optString("expires_at")).append("\n\n")
                }
                status.text = lines.toString()
            }
        }
        addButton("KEMBALI") { showDashboard() }
    }

    private fun showOrders() {
        baseScreen("Riwayat Transaksi")
        request(auth("kla_orders")) { r ->
            val arr = r.optJSONArray("orders")
            if (arr == null || arr.length() == 0) status.text = "Belum ada transaksi."
            else {
                val lines = StringBuilder()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    lines.append(o.optString("order_id")).append("\n")
                        .append(o.optString("status")).append(" — Rp").append(o.optString("amount_idr"))
                        .append("\nPerangkat: ").append(o.optString("device_id")).append("\n\n")
                }
                status.text = lines.toString()
            }
        }
        addButton("KEMBALI") { showDashboard() }
    }

    private fun auth(action: String) = JSONObject().put("a", action).put("session", session)

    private fun request(body: JSONObject, done: (JSONObject) -> Unit) {
        status.text = "Menghubungi server..."
        io.execute {
            val result = try {
                val conn = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 20000
                    readTimeout = 30000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Accept", "application/json")
                }
                conn.outputStream.use { it.write(body.toString().toByteArray(StandardCharsets.UTF_8)) }
                val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
                val text = stream.bufferedReader().use { it.readText() }
                conn.disconnect()
                JSONObject(text)
            } catch (e: Exception) {
                JSONObject().put("ok", false).put("error", "Tidak dapat menghubungi server: ${e.message ?: "koneksi gagal"}")
            }
            runOnUiThread { done(result) }
        }
    }

    override fun onDestroy() {
        io.shutdownNow()
        super.onDestroy()
    }
}
