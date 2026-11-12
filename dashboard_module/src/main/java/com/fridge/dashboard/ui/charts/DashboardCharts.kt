package com.fridge.dashboard.ui.charts

import android.graphics.Color
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.fridge.dashboard.R
import com.fridge.dashboard.ui.model.CategoryStat
import com.fridge.dashboard.ui.model.TrendPoint
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.PercentFormatter

/**
 * 食材分类占比饼图（MPAndroidChart）。
 *
 * 数据目前来自静态 [com.fridge.dashboard.ui.model.DashboardSampleData]，
 * 但图表组件只接收纯数据列表，后续接入 ContentProvider 时无需改动。
 */
@Composable
fun CategoryPieChart(
    stats: List<CategoryStat>,
    modifier: Modifier = Modifier.fillMaxWidth().height(240.dp),
) {
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    AndroidView(
        modifier = modifier,
        factory = { context ->
            PieChart(context).also { chart ->
                bindPieChart(chart, stats, textColor)
            }
        },
        update = { chart ->
            bindPieChart(chart, stats, textColor)
        },
    )
}

private fun bindPieChart(chart: PieChart, stats: List<CategoryStat>, textColor: Int) {
    chart.description.isEnabled = false
    chart.setUsePercentValues(true)
    chart.setDrawEntryLabels(false)
    chart.setHoleColor(Color.TRANSPARENT)
    chart.setHoleRadius(58f)
    chart.setTransparentCircleRadius(62f)
    chart.centerText = "分类\n占比"
    chart.setCenterTextSize(14f)
    chart.setCenterTextColor(textColor)
    chart.legend.isWordWrapEnabled = true
    chart.legend.textColor = textColor
    chart.legend.textSize = 11f
    chart.setTouchEnabled(false)
    chart.setRotationEnabled(false)
    chart.isHighlightPerTapEnabled = false
    chart.setNoDataText("暂无分类数据")

    val colors = intArrayOf(
        ContextCompat.getColor(chart.context, R.color.chart_green),
        ContextCompat.getColor(chart.context, R.color.chart_orange),
        ContextCompat.getColor(chart.context, R.color.chart_blue),
        ContextCompat.getColor(chart.context, R.color.chart_red),
        ContextCompat.getColor(chart.context, R.color.chart_gray),
    )
    val entries = stats.map { PieEntry(it.count.toFloat(), it.name) }
    val dataSet = PieDataSet(entries, "").apply {
        setColors(*colors)
        sliceSpace = 2f
        setDrawValues(true)
    }
    val data = PieData(dataSet).apply {
        setValueTextSize(12f)
        setValueTextColor(Color.WHITE)
        // 显示百分比而不是原始数量，避免“5 个分类”被误读成 5 件食材。
        setValueFormatter(PercentFormatter(chart))
    }
    chart.data = data
    chart.invalidate()
}

/**
 * 近 7 天临期食材数量折线图（MPAndroidChart）。
 *
 * 横轴标签由 [TrendPoint.label] 提供，纵轴从 0 开始，避免视觉上夸大波动。
 */
@Composable
fun ExpiringTrendChart(
    points: List<TrendPoint>,
    modifier: Modifier = Modifier.fillMaxWidth().height(220.dp),
) {
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    AndroidView(
        modifier = modifier,
        factory = { context ->
            LineChart(context).also { chart ->
                bindTrendChart(chart, points, textColor)
            }
        },
        update = { chart ->
            bindTrendChart(chart, points, textColor)
        },
    )
}

private fun bindTrendChart(chart: LineChart, points: List<TrendPoint>, textColor: Int) {
    chart.description.isEnabled = false
    chart.legend.isEnabled = false
    chart.setTouchEnabled(false)
    chart.setNoDataText("暂无趋势数据")
    chart.setExtraOffsets(4f, 8f, 12f, 4f)

    chart.axisRight.isEnabled = false
    chart.axisLeft.axisMinimum = 0f
    chart.axisLeft.granularity = 1f
    chart.axisLeft.textColor = textColor
    chart.axisLeft.textSize = 10f
    chart.axisLeft.setDrawAxisLine(false)
    chart.axisLeft.gridColor = 0x22000000

    chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
    chart.xAxis.setDrawGridLines(false)
    chart.xAxis.setDrawAxisLine(false)
    chart.xAxis.granularity = 1f
    chart.xAxis.textColor = textColor
    chart.xAxis.textSize = 10f
    chart.xAxis.valueFormatter = IndexAxisValueFormatter(points.map { it.label }.toTypedArray())

    val lineColor = ContextCompat.getColor(chart.context, R.color.chart_orange)
    val entries = points.mapIndexed { index, point ->
        Entry(index.toFloat(), point.count.toFloat())
    }
    val dataSet = LineDataSet(entries, "临期数量").apply {
        color = lineColor
        setCircleColor(lineColor)
        lineWidth = 2.5f
        circleRadius = 4f
        setDrawCircleHole(false)
        setDrawValues(false)
        mode = LineDataSet.Mode.CUBIC_BEZIER
        setDrawFilled(true)
        fillColor = lineColor
        fillAlpha = 40
    }
    chart.data = LineData(dataSet)
    chart.invalidate()
}
