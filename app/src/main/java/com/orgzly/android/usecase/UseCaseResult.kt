package com.orgzly.android.usecase

data class UseCaseResult constructor(
        val modifiesListWidget: Boolean = false,
        val triggersSync: Int = UseCase.SYNC_NOT_REQUIRED,
        val userData: Any? = null) {
    constructor(userData: Any?) : this(false, UseCase.SYNC_NOT_REQUIRED, userData)
}
