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
import com.github.mikephil.charting.formatter.ValueFormatter
import kotlin.math.roundToInt

/**
 * 食材分类占比饼图（MPAndroidChart）。
 *
 * 每个扇区直接标注“分类名称 + 库存条目数”，中心显示总条目数。
 * 分类统计口径与主页指标卡一致，均为库存条目数，而不是不同单位的数量之和。
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
    chart.setUsePercentValues(false)
    chart.setDrawEntryLabels(true)
    chart.setEntryLabelColor(textColor)
    chart.setEntryLabelTextSize(10f)
    chart.setHoleColor(Color.TRANSPARENT)
    chart.setHoleRadius(52f)
    chart.setTransparentCircleRadius(58f)
    chart.centerText = "总计\n${stats.sumOf { it.count }} 条"
    chart.setCenterTextSize(15f)
    chart.setCenterTextColor(textColor)
    // 扇区文字已带分类名与数量，关闭图例避免重复信息挤占空间。
    chart.legend.isEnabled = false
    chart.setTouchEnabled(false)
    chart.setRotationEnabled(false)
    chart.isHighlightPerTapEnabled = false
    chart.setExtraOffsets(6f, 6f, 6f, 6f)
    chart.setNoDataText("暂无分类数据")

    val colors = intArrayOf(
        ContextCompat.getColor(chart.context, R.color.chart_green),
        ContextCompat.getColor(chart.context, R.color.chart_orange),
        ContextCompat.getColor(chart.context, R.color.chart_blue),
        ContextCompat.getColor(chart.context, R.color.chart_red),
        ContextCompat.getColor(chart.context, R.color.chart_gray),
        ContextCompat.getColor(chart.context, R.color.chart_teal),
        ContextCompat.getColor(chart.context, R.color.chart_purple),
        ContextCompat.getColor(chart.context, R.color.chart_brown),
        ContextCompat.getColor(chart.context, R.color.chart_yellow),
    )
    val entries = stats.map { PieEntry(it.count.toFloat(), "${it.name} ${it.count}") }
    val dataSet = PieDataSet(entries, "").apply {
        setColors(*colors)
        sliceSpace = 2f
        selectionShift = 5f
        // 扇区标签已经包含数量，不再在扇区内部重复绘制数值。
        setDrawValues(false)
    }
    chart.data = PieData(dataSet)
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
    chart.setScaleEnabled(false)
    chart.setPinchZoom(false)
    chart.isDoubleTapToZoomEnabled = false
    chart.setNoDataText("暂无趋势数据")
    chart.setExtraOffsets(4f, 8f, 12f, 8f)

    val intFormatter = object : ValueFormatter() {
        override fun getFormattedValue(value: Float): String = value.roundToInt().toString()
    }

    chart.axisRight.isEnabled = false
    chart.axisLeft.axisMinimum = 0f
    chart.axisLeft.granularity = 1f
    chart.axisLeft.textColor = textColor
    chart.axisLeft.textSize = 10f
    chart.axisLeft.setDrawAxisLine(false)
    chart.axisLeft.gridColor = 0x22000000
    chart.axisLeft.valueFormatter = intFormatter

    chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
    chart.xAxis.setDrawGridLines(false)
    chart.xAxis.setDrawAxisLine(false)
    chart.xAxis.granularity = 1f
    chart.xAxis.labelCount = points.size
    chart.xAxis.textColor = textColor
    chart.xAxis.textSize = 10f
    chart.xAxis.setAvoidFirstLastClipping(true)
    chart.xAxis.valueFormatter = IndexAxisValueFormatter(points.map { it.label }.toTypedArray())

    val lineColor = ContextCompat.getColor(chart.context, R.color.chart_orange)
    val entries = points.mapIndexed { index, point ->
        Entry(index.toFloat(), point.count.toFloat())
    }
    val dataSet = LineDataSet(entries, "到期条目数").apply {
        color = lineColor
        setCircleColor(lineColor)
        lineWidth = 2.5f
        circleRadius = 4f
        setDrawCircleHole(true)
        circleHoleColor = Color.WHITE
        // 每个数据点直接显示具体数值，满足“标注每日具体数值”。
        setDrawValues(true)
        valueTextColor = textColor
        valueTextSize = 10f
        valueFormatter = intFormatter
        // 线性连接避免贝塞尔曲线在 0 附近产生负值视觉误导。
        mode = LineDataSet.Mode.LINEAR
        setDrawFilled(true)
        fillColor = lineColor
        fillAlpha = 36
    }
    chart.data = LineData(dataSet).apply {
        setValueTextColor(textColor)
        setValueTextSize(10f)
    }
    chart.invalidate()
}
