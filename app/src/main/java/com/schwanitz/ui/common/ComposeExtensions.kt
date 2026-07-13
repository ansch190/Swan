package com.schwanitz.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState

@Composable
fun CollectSnackbarErrors(errorHolder: ErrorHolder, snackbarHostState: SnackbarHostState) {
    LaunchedEffect(Unit) {
        errorHolder.errors.collect { error ->
            snackbarHostState.showSnackbar(
                message = error.toUserMessage(),
                duration = SnackbarDuration.Short
            )
        }
    }
}
