package com.android.autopay.presentation.components

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.android.autopay.presentation.theme.BackgroundGrey
import com.android.autopay.presentation.theme.TextPrimary
import com.android.autopay.presentation.theme.TextSecondary
import com.android.autopay.utils.SourceFilter

private val ListHeight = 220.dp
private val AppIconSize = 40.dp

@Composable
fun SourceSelectionList(
    options: List<String>,
    selectedSources: Set<String>,
    emptyStateText: String,
    onToggleSource: (String) -> Unit
) {
    if (options.isEmpty()) {
        Text(
            text = emptyStateText,
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
        return
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(ListHeight)
            .verticalScroll(rememberScrollState())
    ) {
        options.forEach { option ->
            val isSelected: Boolean = selectedSources.any {
                SourceFilter.normalizeSource(it) == SourceFilter.normalizeSource(option)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSource(option) }
                )
                Text(
                    text = option,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
fun BankApplicationSelectionList(
    packageNames: List<String>,
    selectedSources: Set<String>,
    emptyStateText: String,
    onToggleSource: (String) -> Unit
) {
    if (packageNames.isEmpty()) {
        Text(
            text = emptyStateText,
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
        return
    }
    val context = LocalContext.current
    val packageManager: PackageManager = context.packageManager
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(ListHeight)
            .verticalScroll(rememberScrollState())
    ) {
        packageNames.forEach { packageName ->
            val isSelected: Boolean = selectedSources.any {
                SourceFilter.normalizeSource(it) == SourceFilter.normalizeSource(packageName)
            }
            val appInfo: AppDisplayInfo = remember(packageName) {
                resolveAppDisplayInfo(
                    packageManager = packageManager,
                    packageName = packageName
                )
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = BackgroundGrey),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggleSource(packageName) }
                    )
                    Image(
                        bitmap = appInfo.iconBitmap.asImageBitmap(),
                        contentDescription = appInfo.label,
                        modifier = Modifier.size(AppIconSize)
                    )
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp)
                    ) {
                        Text(
                            text = appInfo.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = packageName,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }
}

private data class AppDisplayInfo(
    val label: String,
    val iconBitmap: Bitmap
)

private fun resolveAppDisplayInfo(
    packageManager: PackageManager,
    packageName: String
): AppDisplayInfo {
    return try {
        val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
        val label: String = packageManager.getApplicationLabel(applicationInfo).toString().ifBlank { packageName }
        val icon: Drawable = packageManager.getApplicationIcon(applicationInfo)
        AppDisplayInfo(
            label = label,
            iconBitmap = drawableToBitmap(icon)
        )
    } catch (_: Exception) {
        AppDisplayInfo(
            label = packageName,
            iconBitmap = drawableToBitmap(packageManager.defaultActivityIcon)
        )
    }
}

private fun drawableToBitmap(drawable: Drawable): Bitmap {
    if (drawable is BitmapDrawable && drawable.bitmap != null) {
        return drawable.bitmap
    }
    val width: Int = drawable.intrinsicWidth.takeIf { it > 0 } ?: 96
    val height: Int = drawable.intrinsicHeight.takeIf { it > 0 } ?: 96
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}
