package com.sacredtxd.connectionchecker.ui.screen

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sacredtxd.connectionchecker.data.LatencyChartModel
import com.sacredtxd.connectionchecker.ui.theme.chartColors
import com.sacredtxd.connectionchecker.util.ChartImageSaver
import com.sacredtxd.connectionchecker.util.SaveResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

private val fileTimestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)

/**
 * The latency chart plus its save action. The chart is recorded into a graphics
 * layer as it draws, so saving exports exactly what is on screen rather than a
 * separately rendered copy that could drift from it.
 */
@Composable
fun ChartCard(
    model: LatencyChartModel,
    onSaved: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = chartColors
    val graphicsLayer = rememberGraphicsLayer()
    var saving by remember { mutableStateOf(false) }

    fun runSave() {
        saving = true
        scope.launch {
            val message = saveChart(context, graphicsLayer, model)
            saving = false
            onSaved(message)
        }
    }

    // Only pre-Android-10 devices reach the permission prompt; newer ones write
    // through MediaStore without one.
    val requestStoragePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            runSave()
        } else {
            onSaved("Saving needs permission to write to storage")
        }
    }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Round-trip latency",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            LatencyChart(
                model = model,
                modifier = Modifier
                    .background(colors.surface)
                    .drawWithContent {
                        graphicsLayer.record { this@drawWithContent.drawContent() }
                        drawLayer(graphicsLayer)
                    },
            )

            Text(
                text = caption(model),
                style = MaterialTheme.typography.bodySmall,
                color = colors.axisText,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    enabled = model.hasData && !saving,
                    onClick = {
                        val needsPermission =
                            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                ) != PackageManager.PERMISSION_GRANTED
                        if (needsPermission) {
                            requestStoragePermission.launch(
                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                            )
                        } else {
                            runSave()
                        }
                    },
                ) {
                    if (saving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Save graph")
                }
            }
        }
    }
}

private fun caption(model: LatencyChartModel): String {
    if (!model.hasData) return "Run a check to start plotting."
    val plotted = model.points.size
    val outages = model.outageCount
    val samples = if (plotted == 1) "1 sample" else "$plotted samples"
    return if (outages == 0) {
        "$samples, no offline samples. Axis to ${model.yMaxMs} ms."
    } else {
        val offline = if (outages == 1) "1 offline sample" else "$outages offline samples"
        "$samples, $offline marked on the baseline. Axis to ${model.yMaxMs} ms."
    }
}

private suspend fun saveChart(
    context: Context,
    graphicsLayer: androidx.compose.ui.graphics.layer.GraphicsLayer,
    model: LatencyChartModel,
): String {
    val name = "latency-${fileTimestamp.format(Date())}.png"
    val bitmap = runCatching { graphicsLayer.toImageBitmap().asAndroidBitmap() }
        .getOrElse { return "Could not capture the graph: ${it.message ?: "unknown error"}" }

    return when (val result = ChartImageSaver.save(context, bitmap, name)) {
        is SaveResult.Saved -> "Saved to ${result.displayPath}"
        is SaveResult.Failed -> "Could not save the graph: ${result.reason}"
    }
}
