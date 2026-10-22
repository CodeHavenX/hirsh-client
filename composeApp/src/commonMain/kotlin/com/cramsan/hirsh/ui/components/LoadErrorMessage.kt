package com.cramsan.hirsh.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cramsan.hirsh.network.ApiError
import com.cramsan.hirsh.network.ApiException
import com.cramsan.hirsh.ui.theme.HissInk
import com.cramsan.hirsh.ui.theme.HissWarn

/**
 * Why a screen's data couldn't be read (HISS-663). Validation and conflict errors only come back
 * from writes, which each form reports itself; an expired session (401) is handled app-wide by
 * signing out; a missing record is each screen's own not-found state. What's left for a read is
 * these three.
 */
enum class LoadError(val message: String) {
    /** 403: the session's role can't read this. Retrying won't help. */
    FORBIDDEN("No tienes permiso para ver esta informacion."),

    /** No response at all -- the backend is down or the network is out. */
    UNREACHABLE("No se pudo conectar con el servidor. Revisa tu conexion e intenta de nuevo."),

    /** The backend answered with an error it didn't explain. */
    FAILED("No se pudo cargar la informacion. Intenta de nuevo."),
}

/**
 * Every HTTP error response reaches here as an [ApiException] (the shared client's validator), so
 * anything else means no response arrived -- a connection or timeout failure.
 */
fun Throwable.toLoadError(): LoadError = when {
    this !is ApiException -> LoadError.UNREACHABLE
    error is ApiError.Forbidden -> LoadError.FORBIDDEN
    else -> LoadError.FAILED
}

/** [error]'s message, with a retry button unless retrying can't help. */
@Composable
fun LoadErrorMessage(
    error: LoadError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.testTag("load_error"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(error.message, style = MaterialTheme.typography.bodyMedium, color = HissWarn)
        if (error != LoadError.FORBIDDEN) {
            OutlinedButton(
                onClick = onRetry,
                shape = fieldShape,
                border = BorderStroke(1.5.dp, HissInk),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                modifier = Modifier.testTag("load_error_retry_button"),
            ) {
                Text("Reintentar", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = HissInk)
            }
        }
    }
}
