package dev.orgtd

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.orgtd.ui.theme.OrgtdTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OrgtdTheme {
                OrgtdApp()
            }
        }
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    Today("今天", Icons.Filled.DateRange),
    Inbox("收件箱", Icons.Filled.Email),
    Lists("清单", Icons.AutoMirrored.Filled.List),
    Search("搜索", Icons.Filled.Search),
}

private enum class Range(val label: String) {
    Today("今天"),
    Week("本周"),
    All("全部未完成"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrgtdApp() {
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    var rangeIndex by rememberSaveable { mutableIntStateOf(0) }
    val tab = Tab.entries[tabIndex]

    Scaffold(
        topBar = { TopAppBar(title = { Text(tab.label) }) },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = tabIndex == index,
                        onClick = { tabIndex = index },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(item.label) },
                    )
                }
            }
        },
        floatingActionButton = {
            if (tab != Tab.Search) {
                FloatingActionButton(onClick = { /* M4：快速添加 */ }) {
                    Icon(Icons.Filled.Add, contentDescription = "新建任务")
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (tab == Tab.Today) {
                RangeSelector(
                    selected = rangeIndex,
                    onSelect = { rangeIndex = it },
                )
            }
            EmptyState(tab)
        }
    }
}

@Composable
private fun RangeSelector(selected: Int, onSelect: (Int) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        androidx.compose.foundation.layout.Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Range.entries.forEachIndexed { index, range ->
                FilterChip(
                    selected = selected == index,
                    onClick = { onSelect(index) },
                    label = { Text(range.label) },
                )
            }
        }
    }
}

@Composable
private fun EmptyState(tab: Tab) {
    val (title, hint) = when (tab) {
        Tab.Today -> "还没有安排今天的任务" to "接入 org 文件后，这里显示 SCHEDULED 在今天的条目"
        Tab.Inbox -> "收件箱是空的" to "顶层 TODO 和「收件箱」清单里的条目会出现在这里"
        Tab.Lists -> "还没有清单" to "顶层标题就是清单，比如 * 工作"
        Tab.Search -> "搜索任务" to "按标题和正文全文匹配"
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Preview(showBackground = true, locale = "zh-rCN")
@Composable
private fun OrgtdAppPreview() {
    OrgtdTheme(dynamicColor = false) {
        OrgtdApp()
    }
}
