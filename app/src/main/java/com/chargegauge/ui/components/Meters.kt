package com.chargegauge.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chargegauge.data.db.SampleEntity
import com.chargegauge.domain.ReadingSource
import com.chargegauge.domain.Readout

@Composable
fun MeterCard(
    label: String,
    value: String,
    source: ReadingSource? = null,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
            )
            if (source != null) {
                Text(
                    Readout.sourceLabel(source),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun Fact(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun SeriesChart(
    points: List<Pair<Long, Double>>,
    modifier: Modifier = Modifier,
) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier.height(180.dp).fillMaxWidth()) {
        if (points.size < 2) {
            if (points.size == 1) {
                drawCircle(color, radius = 5.dp.toPx(), center = center)
            }
            return@Canvas
        }
        val minT = points.first().first.toFloat()
        val maxT = points.last().first.toFloat().coerceAtLeast(minT + 1f)
        val minV = points.minOf { it.second }
        val maxV = points.maxOf { it.second }
        val span = (maxV - minV).takeIf { it > 0.01 } ?: 1.0
        val pad = 8.dp.toPx()
        val width = size.width - pad * 2
        val height = size.height - pad * 2
        val offsets = points.map { (time, value) ->
            val x = pad + ((time - minT) / (maxT - minT)) * width
            val y = pad + ((maxV - value) / span).toFloat() * height
            Offset(x, y)
        }
        for (index in 0 until offsets.lastIndex) {
            drawLine(
                color = color,
                start = offsets[index],
                end = offsets[index + 1],
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}

fun samplePoints(samples: List<SampleEntity>): List<Pair<Long, Double>> {
    return samples.mapNotNull { sample -> sample.watts?.let { sample.timestamp to it } }
}
