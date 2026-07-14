package com.schwanitz.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.platform.LocalContext

@Composable
fun CollectSnackbarErrors(errorHolder: ErrorHolder, snackbarHostState: SnackbarHostState) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        errorHolder.errors.collect { error ->
            snackbarHostState.showSnackbar(
                message = error.toUserMessage(context),
                duration = SnackbarDuration.Short
            )
        }
    }
}
