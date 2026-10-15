package com.chargegauge.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = {
                Text(title, style = MaterialTheme.typography.titleMedium)
                content()
            },
        )
    }
}

@Composable
fun SeriesChart(
    points: List<Pair<Long, Double>>,
    modifier: Modifier = Modifier,
) {
    val color = MaterialTheme.colorScheme.primary
    val baseline = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    Canvas(modifier.height(160.dp).fillMaxWidth()) {
        if (points.isEmpty()) return@Canvas
        val minT = points.first().first.toFloat()
        val maxT = points.last().first.toFloat().coerceAtLeast(minT + 1f)
        val lowest = minOf(0.0, points.minOf { it.second })
        val highest = maxOf(points.maxOf { it.second }, 0.0).coerceAtLeast(lowest + 0.5)
        val span = highest - lowest
        val pad = 4.dp.toPx()
        val width = size.width - pad * 2
        val height = size.height - pad * 2
        fun point(time: Long, value: Double): Offset {
            val x = pad + ((time - minT) / (maxT - minT)) * width
            val y = pad + ((highest - value) / span).toFloat() * height
            return Offset(x, y)
        }
        val zeroY = point(points.first().first, 0.0).y
        drawLine(
            color = baseline,
            start = Offset(pad, zeroY),
            end = Offset(size.width - pad, zeroY),
            strokeWidth = 1.dp.toPx(),
        )
        if (points.size == 1) {
            drawCircle(color, radius = 4.dp.toPx(), center = point(points.first().first, points.first().second))
            return@Canvas
        }
        val offsets = points.map { (time, value) -> point(time, value) }
        val fill = Path().apply {
            moveTo(offsets.first().x, zeroY)
            offsets.forEach { lineTo(it.x, it.y) }
            lineTo(offsets.last().x, zeroY)
            close()
        }
        drawPath(fill, color.copy(alpha = 0.18f))
        val stroke = Path().apply {
            moveTo(offsets.first().x, offsets.first().y)
            offsets.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(
            stroke,
            color,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

fun samplePoints(samples: List<SampleEntity>): List<Pair<Long, Double>> {
    return samples.mapNotNull { sample -> sample.watts?.let { sample.timestamp to it } }
}
