package com.hhvvg.ecm.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ClipboardReadInfo(
    val packageName: String,
    val timestamp: Long
) : Parcelable
