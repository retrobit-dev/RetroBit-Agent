package dev.retrobit.agent.data

/** Conservative SQL lexical gate, not a SQL parser: one project-local statement, no file I/O. */
internal object ProjectSqlPolicy {
    fun validate(sql: String) {
        require(sql.length in 1..20_000) { "SQL kosong atau terlalu panjang." }
        val visible = StringBuilder()
        var i = 0
        while (i < sql.length) {
            val c = sql[i]
            when {
                sql.startsWith("--", i) -> { i = sql.indexOf('\n', i).takeIf { it >= 0 } ?: sql.length; visible.append(' ') }
                sql.startsWith("/*", i) -> {
                    val end = sql.indexOf("*/", i + 2)
                    require(end >= 0) { "Komentar SQL tidak lengkap." }
                    i = end + 2; visible.append(' ')
                }
                c == '\'' || c == '"' || c == '`' || c == '[' -> {
                    val start = i
                    val close = if (c == '[') ']' else c
                    i++
                    var ended = false
                    while (i < sql.length) {
                        if (sql[i++] == close) {
                            if (c != '[' && i < sql.length && sql[i] == close) { i++; continue }
                            ended = true; break
                        }
                    }
                    require(ended) { "Literal SQL tidak lengkap." }
                    visible.append(if (c == '\'') " literal " else " " + sql.substring(start, i).uppercase() + " ")
                }
                else -> { visible.append(c.uppercaseChar()); i++ }
            }
        }
        val code = visible.toString().trim().removeSuffix(";").trim()
        require(';' !in code) { "Tool SQL menerima satu perintah per langkah." }
        val words = Regex("[A-Z_]+").findAll(code).map { it.value }.toList()
        require(words.firstOrNull() in setOf("SELECT", "WITH", "EXPLAIN", "INSERT", "UPDATE", "DELETE", "CREATE", "ALTER", "DROP", "REPLACE")) { "Jenis SQL ini tidak diizinkan." }
        require(words.none { it in setOf("ATTACH", "DETACH", "PRAGMA", "VACUUM", "LOAD_EXTENSION", "READFILE", "WRITEFILE", "RECURSIVE") }) { "SQL akses berkas/schema internal atau rekursif tidak diizinkan." }
    }
    fun defaultLiteral(raw: String): Boolean = raw.uppercase() in setOf("NULL", "CURRENT_TIMESTAMP", "CURRENT_DATE", "CURRENT_TIME") ||
        raw.matches(Regex("[+-]?[0-9]+(\\.[0-9]+)?")) || raw.matches(Regex("'(?:[^']|'')*'"))
}
