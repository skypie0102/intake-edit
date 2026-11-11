package cloud.shadowmonarchbooks.intakeedit

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

private const val NOTICE_DURATION_MILLIS = 5_000L

/**
 * Show each notice for five seconds. When its message changes, the previous
 * dismissal coroutine is canceled so it cannot hide the new notice.
 */
@Composable
internal fun TransientNotice(message: String?, onTimeout: (() -> Unit)? = null) {
    var visibleMessage by remember { mutableStateOf(message) }

    LaunchedEffect(message) {
        visibleMessage = message
        if (message != null) {
            delay(NOTICE_DURATION_MILLIS)
            visibleMessage = null
            onTimeout?.invoke()
        }
    }

    visibleMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
}
