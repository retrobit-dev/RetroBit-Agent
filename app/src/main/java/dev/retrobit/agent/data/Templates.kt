package dev.retrobit.agent.data

import android.content.res.AssetManager

data class TemplateInfo(
    val id: String,
    val name: String,
    val emoji: String,
    val tagline: String,
    val description: String,
    val category: String,
)

object Templates {

    val ALL = listOf(
        TemplateInfo("saas", "Nimbus", "☁️", "Landing page SaaS",
            "Hero, fitur, harga, dan CTA yang siap dipakai untuk produk digital.", "Landing"),
        TemplateInfo("tasks", "Flow", "✅", "Aplikasi tugas harian",
            "Todo list dengan progres, filter, prioritas, dan penyimpanan lokal.", "Produktivitas"),
        TemplateInfo("commerce", "Lumen", "🛍️", "Toko online",
            "Katalog produk, filter kategori, keranjang, dan alur checkout.", "E-commerce"),
        TemplateInfo("fitness", "Pulse", "💪", "Dashboard kesehatan",
            "Ring aktivitas, grafik mingguan, dan kartu latihan yang interaktif.", "Kesehatan"),
        TemplateInfo("chat", "Relay", "💬", "Obrolan tim",
            "Antarmuka chat dengan bubble pesan, status mengetik, dan balasan.", "Sosial"),
        TemplateInfo("portfolio", "Atelier", "🎨", "Portofolio kreatif",
            "Layout editorial dengan tipografi besar, grid karya, dan kontak.", "Portofolio"),
        TemplateInfo("blank", "Kanvas kosong", "✦", "Mulai dari nol",
            "Halaman kosong berisi petunjuk — biarkan AI yang membangun semuanya.", "Kosong"),
    )

    fun byId(id: String): TemplateInfo = ALL.firstOrNull { it.id == id } ?: ALL.first()

    /** Cheap keyword router used by the dashboard composer before any AI call. */
    fun suggestId(prompt: String): String {
        val p = prompt.lowercase()
        fun has(vararg keys: String) = keys.any { p.contains(it) }
        return when {
            has("toko", "jual", "ecommerce", "e-commerce", "katalog", "produk", "belanja", "restoran", "kafe", "makanan") -> "commerce"
            has("tugas", "todo", "to-do", "task", "checklist", "produktivitas", "kebiasaan") -> "tasks"
            has("chat", "obrolan", "pesan", "messaging", "percakapan", "forum") -> "chat"
            has("sehat", "fitness", "olahraga", "workout", "kesehatan", "lari", "gym") -> "fitness"
            has("portofolio", "portfolio", "karya", "desainer", "fotografer", "studio") -> "portfolio"
            has("landing", "saas", "marketing", "startup", "bisnis", "promosi", "perusahaan") -> "saas"
            has("kosong", "blank", "dari nol", "kanvas") -> "blank"
            else -> "saas"
        }
    }

    fun designFor(templateId: String): DesignSystem {
        val presetId = when (templateId) {
            "commerce" -> "sunset"
            "tasks" -> "paper"
            "portfolio" -> "mono"
            "blank" -> "terminal"
            else -> "midnight"
        }
        return DesignSystem.PRESETS.firstOrNull { it.id == presetId }?.design ?: DesignSystem()
    }

    fun apply(assets: AssetManager, files: ProjectFiles, templateId: String, design: DesignSystem) {
        val folder = "templates/" + byId(templateId).id
        copyAssetFolder(assets, folder, files)
        installFonts(assets, files)
        files.ensureEntryPoint()
        applyDesign(files, design)
    }

    /** Copies the bundled Pixel Operator faces into the project so previews and exports share the font. */
    fun installFonts(assets: AssetManager, files: ProjectFiles) {
        for ((assetPath, targetPath) in listOf(
            "fonts/PixelOperator.ttf" to "assets/fonts/PixelOperator.ttf",
            "fonts/PixelOperator-Bold.ttf" to "assets/fonts/PixelOperator-Bold.ttf",
        )) {
            val target = files.resolve(targetPath)
            if (target.exists() && target.length() > 0) continue
            runCatching {
                assets.open(assetPath).use { input ->
                    target.parentFile?.mkdirs()
                    target.outputStream().use { input.copyTo(it) }
                }
            }
        }
    }

    private fun copyAssetFolder(assets: AssetManager, path: String, files: ProjectFiles, prefix: String = "") {
        val children = runCatching { assets.list(path) }.getOrNull() ?: return
        if (children.isEmpty()) {
            runCatching {
                assets.open(path).use { input ->
                    val relative = prefix.ifBlank { path.substringAfterLast('/') }
                    val target = files.resolve(relative)
                    target.parentFile?.mkdirs()
                    target.outputStream().use { input.copyTo(it) }
                }
            }
            return
        }
        for (child in children) {
            val childPath = "$path/$child"
            val childPrefix = if (prefix.isBlank()) child else "$prefix/$child"
            val sub = runCatching { assets.list(childPath) }.getOrNull()
            if (sub.isNullOrEmpty()) {
                runCatching {
                    assets.open(childPath).use { input ->
                        val target = files.resolve(childPrefix)
                        target.parentFile?.mkdirs()
                        target.outputStream().use { input.copyTo(it) }
                    }
                }
            } else {
                copyAssetFolder(assets, childPath, files, childPrefix)
            }
        }
    }

    /** Rewrites the RB_DESIGN block inside every HTML file so exports stay in sync. */
    fun applyDesign(files: ProjectFiles, design: DesignSystem) {
        val block = design.cssVariables()
        val regex = Regex("/\\*RB_DESIGN_START\\*/[\\s\\S]*?/\\*RB_DESIGN_END\\*/")
        for (file in files.allFiles()) {
            val rel = file.relativeTo(files.root).path.replace('\\', '/')
            if (!rel.endsWith(".html", ignoreCase = true)) continue
            val content = runCatching { file.readText() }.getOrNull() ?: continue
            if (!content.contains("RB_DESIGN_START")) continue
            val replacement = "/*RB_DESIGN_START*/\n$block\n/*RB_DESIGN_END*/"
            val updated = regex.replace(content, replacement)
            if (updated != content) runCatching { file.writeText(updated) }
        }
    }
}
