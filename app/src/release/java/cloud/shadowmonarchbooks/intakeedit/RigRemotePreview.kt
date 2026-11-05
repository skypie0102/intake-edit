package cloud.shadowmonarchbooks.intakeedit

import android.content.Context
import android.widget.RemoteViews
import androidx.compose.runtime.Composable

@Composable internal fun RigRemotePreview() { }
internal object RigRemoteFrames {
    fun apply(context: Context, mood: PetMood, views: RemoteViews, frameViews: List<Int>): Boolean = false
}
