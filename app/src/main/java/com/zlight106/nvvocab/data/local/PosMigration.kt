package com.zlight106.nvvocab.data.local

/** Shared by SQLiteOpenHelper and migration verification. All statements are additive. */
object PosMigration {
    val wordColumns = linkedMapOf(
        "parts_of_speech" to "TEXT NOT NULL DEFAULT ''",
        "pos_needs_review" to "INTEGER NOT NULL DEFAULT 0",
        "pos_explanation" to "TEXT NOT NULL DEFAULT ''",
    )
    val attemptColumns = linkedMapOf(
        "actual_pos" to "TEXT NOT NULL DEFAULT ''",
        "selected_pos" to "TEXT NOT NULL DEFAULT ''",
        "error_type" to "TEXT",
    )
    const val cacheSql = "CREATE TABLE IF NOT EXISTS pos_exercises (id TEXT PRIMARY KEY NOT NULL, payload TEXT NOT NULL)"
}
