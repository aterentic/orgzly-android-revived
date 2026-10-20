package com.orgzly.android.ui.views.style

import android.text.style.RelativeSizeSpan
import android.text.style.TypefaceSpan

class TableSpan : TypefaceSpan("monospace")

class TableScaleSpan(scale: Float) : RelativeSizeSpan(scale)

const val MIN_TABLE_SCALE = 0.6f

/** The scale at which a table whose widest row is [widestRow] fits [available], never below [MIN_TABLE_SCALE]. */
fun tableScale(widestRow: Float, available: Float): Float =
    if (available <= 0 || widestRow <= available) 1f else maxOf(MIN_TABLE_SCALE, available / widestRow)
