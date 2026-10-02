package com.zlight106.nvvocab.domain

import com.zlight106.nvvocab.data.QuizBank
import com.zlight106.nvvocab.data.QuizQuestion
import java.io.OutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object QuizBankZipWriter {
    fun write(banks: List<QuizBank>, output: OutputStream, loadQuestions: (String) -> List<QuizQuestion>) {
        require(banks.isNotEmpty()) { "没有可导出的题库。" }
        val usedNames = mutableSetOf<String>()
        ZipOutputStream(output).use { zip ->
            banks.forEach { bank ->
                val base = bank.name.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
                    .trim(' ', '.').take(80).ifBlank { "题库" }
                var fileName = "$base.xml"
                var suffix = 2
                while (!usedNames.add(fileName.lowercase(Locale.ROOT))) fileName = "$base (${suffix++}).xml"
                zip.putNextEntry(ZipEntry(fileName))
                QuizXmlWriter.write(loadQuestions(bank.id), zip, bank.name)
                zip.closeEntry()
            }
        }
    }
}
