package com.zlight106.nvvocab.data.update

enum class EasyUpdatePhase { IDLE, CHECKING, DOWNLOADING, READY, NO_UPDATE, ERROR }

data class EasyUpdateRelease(
    val versionName: String,
    val versionCode: Long,
    val releaseNotes: String,
    val downloadUrl: String,
    val size: Long,
    val sha256: String,
    val mandatory: Boolean,
)

data class EasyUpdateState(
    val phase: EasyUpdatePhase = EasyUpdatePhase.IDLE,
    val message: String = "",
    val progress: Float? = null,
    val release: EasyUpdateRelease? = null,
    val apkPath: String? = null,
)
