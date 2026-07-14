package com.schwanitz.ui.common

import android.content.Context
import com.schwanitz.domain.error.AppError

fun AppError.toUserMessage(context: Context): String {
    val fallback = context.getString(fallbackStringRes())
    return message.ifBlank { fallback }
}
