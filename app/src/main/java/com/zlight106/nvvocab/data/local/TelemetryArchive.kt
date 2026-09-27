package com.zlight106.nvvocab.data.local

import android.content.Context
import com.zlight106.nvvocab.data.PracticeAttempt
import com.zlight106.nvvocab.domain.SessionTelemetryXmlWriter
import java.io.File
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.json.JSONObject

data class TelemetryArchiveEntry(
    val sessionId: String,
    val createdAt: Long,
    val category: String,
    val sourceName: String,
    val attemptCount: Int,
)

/** Private app storage. These files are intentionally outside SQLite and Supabase sync. */
class TelemetryArchive(context: Context) {
    private val directory = File(context.filesDir, "telemetry").apply { mkdirs() }

    fun list(): List<TelemetryArchiveEntry> = directory.listFiles { file -> file.extension == "json" }
        .orEmpty().mapNotNull { file ->
            runCatching {
                val json = JSONObject(file.readText())
                val id = json.getString("sessionId")
                if (!xmlFile(id).isFile) return@runCatching null
                TelemetryArchiveEntry(
                    id,
                    json.getLong("createdAt"),
                    json.getString("category"),
                    json.getString("sourceName"),
                    json.getInt("attemptCount"),
                )
            }.getOrNull()
        }.sortedByDescending(TelemetryArchiveEntry::createdAt)

    @Synchronized
    fun save(
        sessionId: String,
        attempts: List<PracticeAttempt>,
        includeTiming: Boolean,
        category: String,
        sourceName: String,
    ) {
        if (attempts.isEmpty()) return
        val xml = xmlFile(sessionId)
        val temporary = File(directory, "$sessionId.tmp")
        try {
            temporary.outputStream().use { SessionTelemetryXmlWriter.write(sessionId, attempts, it, includeTiming) }
            Files.move(temporary.toPath(), xml.toPath(), StandardCopyOption.REPLACE_EXISTING)
            File(directory, "$sessionId.json").writeText(
                JSONObject().put("sessionId", sessionId)
                    .put("createdAt", System.currentTimeMillis())
                    .put("category", category)
                    .put("sourceName", sourceName)
                    .put("attemptCount", attempts.size).toString(),
            )
        } finally {
            temporary.delete()
        }
    }

    fun exportOne(sessionId: String, output: OutputStream) {
        require(list().any { it.sessionId == sessionId }) { "遥测文件不存在" }
        xmlFile(sessionId).inputStream().use { it.copyTo(output) }
    }

    fun exportZip(sessionIds: Set<String>, output: OutputStream) {
        val entries = list().filter { it.sessionId in sessionIds }
        require(entries.isNotEmpty()) { "请选择遥测记录" }
        ZipOutputStream(output).use { zip ->
            entries.forEach { entry ->
                zip.putNextEntry(ZipEntry("nvvocab-telemetry-${entry.sessionId}.xml"))
                xmlFile(entry.sessionId).inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    private fun xmlFile(sessionId: String): File {
        require(sessionId.matches(Regex("[a-fA-F0-9-]{36}"))) { "无效的会话 ID" }
        return File(directory, "$sessionId.xml")
    }
}
