package com.artemchep.keyguard.android.util

import android.os.Bundle
import androidx.core.os.BundleCompat

inline fun <reified T> Bundle.getParcelableCompat(key: String?) =
    BundleCompat.getParcelable(this, key, T::class.java)
