package com.orgzly.android

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.orgzly.android.data.observers.DataChangedSignal
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SharingShortcutsDataObserver @Inject constructor(
    private val signal: DataChangedSignal,
    private val context: Application
) {
    private val manager by lazy { SharingShortcutsManager() }

    fun start() {
        ProcessLifecycleOwner.get().lifecycleScope.launch {
            signal.events.collect {
                manager.replaceDynamicShortcuts(context)
            }
        }
    }
}
