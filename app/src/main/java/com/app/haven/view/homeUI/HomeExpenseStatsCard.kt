//*AI-assisted code
package com.app.haven.view.homeUI

import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.app.haven.domain.expense.ExpenseCategory
import com.app.haven.view.expenseUI.money
import com.app.haven.viewmodel.home.HomeExpenseStatsState
import kotlinx.coroutines.delay

@Composable
fun HomeExpenseStatsCard(state: HomeExpenseStatsState) {
    var showChart by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val talkBackOn = remember {
        context.getSystemService(AccessibilityManager::class.java)?.isTouchExplorationEnabled == true
    }
    val hasData = state is HomeExpenseStatsState.Success && state.total > 0

    LaunchedEffect(hasData, talkBackOn, showChart) {
        if (!hasData || talkBackOn) return@LaunchedEffect
        delay(5_000)
        showChart = !showChart
    }

    Card(onClick = { if (hasData) showChart = !showChart }, modifier = Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .heightIn(min = 140.dp),
            contentAlignment = Alignment.Center
        ) {
            when (val s = state) {
                HomeExpenseStatsState.Loading -> CircularProgressIndicator()
                is HomeExpenseStatsState.Error -> Text(s.message, color = MaterialTheme.colorScheme.error)
                is HomeExpenseStatsState.Success -> AnimatedContent(
                    targetState = showChart && s.total > 0,
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "stats"
                ) { chart ->
                    if (chart) CategoryDonut(s.byCategory, s.total) else TotalPage(s.total)
                }
            }
        }
    }
}

@Composable
private fun TotalPage(total: Long) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Your spending this month", style = MaterialTheme.typography.labelMedium)
        Text(total.money(), style = MaterialTheme.typography.headlineMedium)
        Text(
            if (total == 0L) "No expenses yet this month" else "Tap to see by category",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun categoryColor(category: ExpenseCategory): Color = when (category) {
    ExpenseCategory.RENT -> MaterialTheme.colorScheme.primary
    ExpenseCategory.UTILITIES -> MaterialTheme.colorScheme.tertiary
    ExpenseCategory.GROCERIES -> MaterialTheme.colorScheme.secondary
    ExpenseCategory.OTHER -> MaterialTheme.colorScheme.outline
}

private fun ExpenseCategory.label() = name.lowercase().replaceFirstChar { it.uppercase() }

@Composable
private fun CategoryDonut(byCategory: Map<ExpenseCategory, Long>, total: Long) {
    val slices = byCategory.filterValues { it > 0 }
    val colors = slices.keys.associateWith { categoryColor(it) }
    val description = slices.entries.joinToString(", ") { (c, cents) -> "${c.label()} ${cents.money()}" }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Canvas(
            Modifier
                .size(110.dp)
                .semantics { contentDescription = "Spending by category: $description" }
        ) {
            val strokeWidth = 22.dp.toPx()
            val inset = strokeWidth / 2
            var start = -90f
            slices.forEach { (category, cents) ->
                //get the angle sweep
                val sweep = cents.toFloat() / total * 360f
                drawArc(
                    color = colors.getValue(category),
                    startAngle = start,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - strokeWidth, size.height - strokeWidth),
                    style = Stroke(width = strokeWidth)
                )
                //increment for the next sweep
                start += sweep
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            byCategory.forEach { (category, cents) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .background(categoryColor(category), CircleShape)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("${category.label()}  ${cents.money()}", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
