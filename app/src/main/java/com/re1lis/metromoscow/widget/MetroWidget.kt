package com.re1lis.metromoscow.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.re1lis.metromoscow.MainActivity
import com.re1lis.metromoscow.data.MetroRepository
import com.re1lis.metromoscow.data.MetroSeed
import com.re1lis.metromoscow.ui.theme.DarkColorScheme
import com.re1lis.metromoscow.ui.theme.LightColorScheme
import kotlin.math.roundToInt

object MetroWidgetColorScheme {
    val colors = ColorProviders(light = LightColorScheme, dark = DarkColorScheme)
}

class MetroWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = MetroRepository(context)
        val progress = repository.loadProgress()
        val total = MetroSeed.lines.sumOf { it.stations.size }
        val visited = progress.values.count { it.visited }
        val percent = if (total > 0) ((visited.toFloat() / total) * 100).roundToInt() else 0
        val fraction = if (total > 0) visited.toFloat() / total else 0f

        provideContent {
            GlanceTheme(colors = MetroWidgetColorScheme.colors) {
                MetroWidgetContent(
                    visited = visited,
                    total = total,
                    percent = percent,
                    fraction = fraction
                )
            }
        }
    }
}

@Composable
private fun MetroWidgetContent(
    visited: Int,
    total: Int,
    percent: Int,
    fraction: Float,
) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.surface)
            .clickable(actionStartActivity<MainActivity>())
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Text(
                text = "🚇 МЕТРО МОСКВЫ",
                style = TextStyle(
                    color = GlanceTheme.colors.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(GlanceModifier.defaultWeight())
            Text(
                text = "$percent%",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }

        Spacer(GlanceModifier.height(4.dp))

        Text(
            text = "$visited / $total",
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        )
        Text(
            text = "станций открыто",
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = 10.sp
            )
        )

        Spacer(GlanceModifier.height(8.dp))

        LinearProgressIndicator(
            progress = fraction,
            modifier = GlanceModifier.fillMaxWidth().height(8.dp),
            color = GlanceTheme.colors.primary,
            backgroundColor = GlanceTheme.colors.surfaceVariant
        )
    }
}

class MetroWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MetroWidget()
}
