package com.orgzly.android.widgets

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.orgzly.android.data.observers.DataChangedSignal
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ListWidgetDataObserver @Inject constructor(
    private val signal: DataChangedSignal,
    private val context: Application
) {
    fun start() {
        ProcessLifecycleOwner.get().lifecycleScope.launch {
            signal.events.collect {
                ListWidgetProvider.notifyDataSetChanged(context)
            }
        }
    }
}
