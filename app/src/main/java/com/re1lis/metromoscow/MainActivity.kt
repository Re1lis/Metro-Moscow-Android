package com.re1lis.metromoscow

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.app.DatePickerDialog
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.fontResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.re1lis.metromoscow.data.*
import com.re1lis.metromoscow.ui.theme.AccentColors
import com.re1lis.metromoscow.ui.theme.MetroRed
import com.re1lis.metromoscow.ui.theme.MetroTheme
import com.re1lis.metromoscow.worker.ReminderWorker
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private enum class MainTab(val title: String) { METRO("Метро"), HISTORY("История"), STATS("Статистика"), TRAINS("Поезда"), SETTINGS("Настройки") }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel: MetroViewModel = viewModel()
            val state by viewModel.state.collectAsStateWithLifecycle()

            // Request POST_NOTIFICATIONS on Android 13+
            val notifLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { /* result handled silently */ }
            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            // Schedule weekly reminder if enabled
            LaunchedEffect(state.settings.weeklyReminder) {
                if (state.settings.weeklyReminder) {
                    ReminderWorker.schedule(applicationContext)
                } else {
                    ReminderWorker.cancel(applicationContext)
                }
            }

            MetroTheme(state.settings) {
                MetroMoscowApp(state, viewModel)
            }
        }
    }
}

@Composable
private fun MetroMoscowApp(state: MetroUiState, vm: MetroViewModel) {
    var splash by remember { mutableStateOf(true) }
    var tab by remember { mutableStateOf(MainTab.METRO) }
    var activeLine by remember { mutableStateOf<MetroLine?>(null) }
    var activeStation by remember { mutableStateOf<Station?>(null) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { delay(3500); splash = false }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); vm.dismissMessage() }
    }

    if (splash) {
        SplashScreen()
        return
    }

    when {
        activeStation != null && activeLine != null -> StationScreen(
            line = activeLine!!,
            station = activeStation!!,
            progress = state.progress[activeStation!!.id] ?: StationProgress(),
            onBack = { activeStation = null },
            onToggleVisited = { vm.toggleVisited(activeStation!!) },
            onToggleFavorite = { vm.toggleFavorite(activeStation!!) },
            onSave = { date, note -> vm.updateStation(activeStation!!, date, note) }
        )
        activeLine != null -> LineScreen(
            line = activeLine!!,
            state = state,
            onBack = { activeLine = null },
            onStation = { activeStation = it },
            onToggleVisited = vm::toggleVisited
        )
        else -> Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = { MetroBottomBar(tab) { tab = it } },
            contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top)
        ) { padding ->
            Box(Modifier.padding(padding)) {
                when (tab) {
                    MainTab.METRO -> HomeScreen(
                        state = state,
                        onSearch = vm::setSearch,
                        onFilter = vm::setFilter,
                        onLine = { activeLine = it },
                        onStationSelect = { st, line -> activeLine = line; activeStation = st }
                    )
                    MainTab.HISTORY -> HistoryScreen(state) { st, line -> activeLine = line; activeStation = st }
                    MainTab.STATS -> StatisticsScreen(state) { line -> tab = MainTab.METRO; activeLine = line }
                    MainTab.TRAINS -> TrainsScreen()
                    MainTab.SETTINGS -> SettingsScreen(state, vm)
                }
            }
        }
    }
}

@Composable
private fun SplashScreen() {
    val scale = remember { androidx.compose.animation.core.Animatable(0.5f) }
    val alpha = remember { androidx.compose.animation.core.Animatable(0f) }

    LaunchedEffect(Unit) {
        scale.animateTo(
            targetValue = 1f,
            animationSpec = androidx.compose.animation.core.spring(
                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                stiffness = androidx.compose.animation.core.Spring.StiffnessLow
            )
        )
    }
    LaunchedEffect(Unit) {
        alpha.animateTo(
            targetValue = 1f,
            animationSpec = androidx.compose.animation.core.tween(durationMillis = 800)
        )
    }

    Box(
        Modifier.fillMaxSize().background(
            Brush.linearGradient(listOf(Color(0xFF870F14), MetroRed, Color(0xFFEF5350)))
        ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.graphicsLayer(alpha = alpha.value)
        ) {
            Box(
                Modifier
                    .size(126.dp)
                    .graphicsLayer(scaleX = scale.value, scaleY = scale.value)
                    .shadow(24.dp, CircleShape)
                    .background(Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("М", color = MetroRed, fontSize = 76.sp, fontWeight = FontWeight.Black)
            }
            Text("МЕТРО МОСКВЫ", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Text("Открывай город станция за станцией", color = Color.White.copy(alpha = .78f), fontSize = 15.sp)
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                modifier = Modifier.width(180.dp).clip(CircleShape),
                color = Color.White,
                trackColor = Color.White.copy(alpha = .25f)
            )
        }
    }
}

@Composable
private fun MetroBottomBar(selected: MainTab, onSelect: (MainTab) -> Unit) {
    NavigationBar(tonalElevation = 4.dp) {
        MainTab.entries.forEach { tab ->
            val icon = when (tab) {
                MainTab.METRO -> Icons.Default.Subway
                MainTab.HISTORY -> Icons.Default.History
                MainTab.STATS -> Icons.Default.BarChart
                MainTab.TRAINS -> Icons.Default.Train
                MainTab.SETTINGS -> Icons.Default.Settings
            }
            NavigationBarItem(
                selected = selected == tab,
                onClick = { onSelect(tab) },
                icon = { Icon(icon, null) },
                label = { Text(tab.title) }
            )
        }
    }
}

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    state: MetroUiState,
    onSearch: (String) -> Unit,
    onFilter: (StationFilter) -> Unit,
    onLine: (MetroLine) -> Unit,
    onStationSelect: (Station, MetroLine) -> Unit
) {
    val context = LocalContext.current
    var isSearching by remember { mutableStateOf(false) }
    var isFindingNearest by remember { mutableStateOf(false) }
    var nearestModalStation by remember { mutableStateOf<Pair<Station, MetroLine>?>(null) }
    var showNearestDialog by remember { mutableStateOf(false) }
    var nearestErrorMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.search) {
        if (state.search.isNotBlank()) {
            isSearching = true
            delay(400)
            isSearching = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            isFindingNearest = true
            nearestErrorMsg = null
            nearestModalStation = null

            scope.launch {
                val startTime = System.currentTimeMillis()
                var found: Pair<Station, MetroLine>? = null

                runCatching {
                    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
                    var loc: android.location.Location? = null
                    val providers = locationManager.getProviders(true)
                    for (provider in providers) {
                        val l = locationManager.getLastKnownLocation(provider)
                        if (l != null) {
                            loc = l
                            break
                        }
                    }

                    // Fallback to Moscow center (Kremlin) if location is null (e.g. indoors without GPS fix)
                    val latitude = loc?.latitude ?: 55.751244
                    val longitude = loc?.longitude ?: 37.618423

                    var closest: Station? = null
                    var closestLine: MetroLine? = null
                    var minDistance = Float.MAX_VALUE
                    val results = FloatArray(1)
                    for (line in state.lines) {
                        for (st in line.stations) {
                            android.location.Location.distanceBetween(latitude, longitude, st.lat, st.lon, results)
                            val dist = results[0]
                            if (dist < minDistance) {
                                minDistance = dist
                                closest = st
                                closestLine = line
                            }
                        }
                    }
                    if (closest != null && closestLine != null) {
                        found = closest to closestLine
                    }
                }

                // Ensure at least 5 seconds of search animation
                val elapsed = System.currentTimeMillis() - startTime
                if (elapsed < 5000) {
                    delay(5000 - elapsed)
                }

                isFindingNearest = false
                if (found != null) {
                    nearestModalStation = found
                    showNearestDialog = true
                } else {
                    nearestErrorMsg = "Не удалось определить ближайшую станцию"
                }
            }
        } else {
            nearestErrorMsg = "Нет разрешения на геолокацию"
        }
    }

    val filteredLines = state.lines.filter { line ->
        state.search.isBlank() || line.name.contains(state.search, true) || line.stations.any { it.name.contains(state.search, true) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { HomeHero(state) }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SearchBar(
                    query = state.search,
                    onQueryChange = onSearch,
                    onSearch = {},
                    active = false,
                    onActiveChange = {},
                    placeholder = { Text("Найти станцию или линию") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = if (state.search.isNotBlank()) {{ IconButton({ onSearch("") }) { Icon(Icons.Default.Close, null) } }} else null,
                    modifier = Modifier.weight(1f)
                ) {}

                FilledTonalIconButton(
                    onClick = { permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
                    modifier = Modifier.size(54.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.MyLocation, "Ближайшая станция", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        item { FilterRow(state.filter, onFilter) }

        if (isSearching) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp), color = MaterialTheme.colorScheme.primary)
                        Text("Ищем станции...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Линии метро", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                    Spacer(Modifier.weight(1f))
                    Text("${filteredLines.size}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
            items(filteredLines, key = { it.id }) { line ->
                LineCard(line, state.progress) { onLine(line) }
            }
            if (filteredLines.isEmpty()) item { EmptyState("Ничего не найдено", "Попробуй изменить запрос") }
        }
    }

    if (showNearestDialog && nearestModalStation != null) {
        val (st, line) = nearestModalStation!!
        AlertDialog(
            onDismissRequest = { showNearestDialog = false },
            icon = { Icon(Icons.Default.MyLocation, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Ближайшая станция") },
            text = { Text("К вам ближе всего станция «${st.name}» (${line.name}). Хотите открыть её?") },
            confirmButton = {
                TextButton({
                    showNearestDialog = false
                    onStationSelect(st, line)
                }) { Text("Открыть") }
            },
            dismissButton = {
                TextButton({ showNearestDialog = false }) { Text("Отмена") }
            }
        )
    }

    if (isFindingNearest) {
        AlertDialog(
            onDismissRequest = {},
            icon = { CircularProgressIndicator(modifier = Modifier.size(36.dp), color = MaterialTheme.colorScheme.primary) },
            title = { Text("Поиск ближайшей станции") },
            text = { Text("Ищем ближайшую станцию метро... Пожалуйста, подождите (минимум 5 секунд).") },
            confirmButton = {}
        )
    }

    if (nearestErrorMsg != null) {
        AlertDialog(
            onDismissRequest = { nearestErrorMsg = null },
            icon = { Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Станция не нашлась") },
            text = { Text(nearestErrorMsg!!) },
            confirmButton = {
                TextButton({ nearestErrorMsg = null }) { Text("OK") }
            }
        )
    }
}

@Composable
private fun HomeHero(state: MetroUiState) {
    val fraction = if (state.totalCount == 0) 0f else state.visitedCount.toFloat() / state.totalCount
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            Modifier.background(
                Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary.copy(alpha = .68f)))
            ).padding(24.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("МОСКОВСКОЕ МЕТРО", color = Color.White.copy(alpha = .78f), fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Text("Мой маршрут", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black)
                    }
                    Box(Modifier.size(62.dp).background(Color.White.copy(alpha = .16f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Subway, null, tint = Color.White, modifier = Modifier.size(34.dp))
                    }
                }
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth().height(9.dp).clip(CircleShape),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = .24f)
                )
                Row {
                    HeroMetric("${state.visitedCount}", "посещено")
                    HeroMetric("${state.totalCount - state.visitedCount}", "осталось")
                    HeroMetric("${(fraction * 100).roundToInt()}%", "пройдено")
                }
            }
        }
    }
}

@Composable
private fun RowScope.HeroMetric(value: String, label: String) {
    Column(Modifier.weight(1f)) {
        Text(value, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Black)
        Text(label, color = Color.White.copy(alpha = .72f), fontSize = 12.sp)
    }
}

@Composable
private fun FilterRow(selected: StationFilter, onSelect: (StationFilter) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(StationFilter.entries) { filter ->
            val title = when (filter) {
                StationFilter.ALL -> "Все"
                StationFilter.VISITED -> "Посещённые"
                StationFilter.UNVISITED -> "Не посещены"
                StationFilter.FAVORITES -> "Избранное"
            }
            FilterChip(selected = selected == filter, onClick = { onSelect(filter) }, label = { Text(title) },
                leadingIcon = if (filter == StationFilter.FAVORITES) {{ Icon(Icons.Default.Star, null, Modifier.size(16.dp)) }} else null)
        }
    }
}

@Composable
private fun LineCard(line: MetroLine, progress: Map<String, StationProgress>, onClick: () -> Unit) {
    val visited = line.stations.count { progress[it.id]?.visited == true }
    val fraction = visited.toFloat() / line.stations.size.coerceAtLeast(1)
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(54.dp).background(line.composeColor, CircleShape), contentAlignment = Alignment.Center) {
                Text(line.number, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.width(15.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(line.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("${line.stations.size} станций", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${line.lengthKm} км", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape), color = line.composeColor)
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text("$visited/${line.stations.size}", fontWeight = FontWeight.Black, color = line.composeColor)
                Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LineScreen(
    line: MetroLine,
    state: MetroUiState,
    onBack: () -> Unit,
    onStation: (Station) -> Unit,
    onToggleVisited: (Station) -> Unit
) {
    BackHandler(onBack = onBack)
    var pendingUnvisit by remember { mutableStateOf<Station?>(null) }
    val stations = line.stations.filter { station ->
        val p = state.progress[station.id] ?: StationProgress()
        val queryOk = state.search.isBlank() || station.name.contains(state.search, true)
        val filterOk = when (state.filter) {
            StationFilter.ALL -> true
            StationFilter.VISITED -> p.visited
            StationFilter.UNVISITED -> !p.visited
            StationFilter.FAVORITES -> p.favorite
        }
        queryOk && filterOk
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column { Text(line.name, fontWeight = FontWeight.Black); Text("Линия ${line.number}", style = MaterialTheme.typography.labelMedium, color = line.composeColor) } },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            item { LineSummary(line, state.progress) }
            items(stations, key = { it.id }) { station ->
                StationTimelineRow(
                    station = station,
                    lineColor = line.composeColor,
                    progress = state.progress[station.id] ?: StationProgress(),
                    first = station == stations.firstOrNull(),
                    last = station == stations.lastOrNull(),
                    onOpen = { onStation(station) },
                    onToggle = {
                        val isVisited = state.progress[station.id]?.visited == true
                        if (isVisited && state.settings.confirmUnvisit) pendingUnvisit = station else onToggleVisited(station)
                    }
                )
            }
            if (stations.isEmpty()) item { EmptyState("Список пуст", "Измени фильтр на главном экране") }
        }
    }
    pendingUnvisit?.let { station ->
        AlertDialog(
            onDismissRequest = { pendingUnvisit = null },
            icon = { Icon(Icons.Default.LocationOff, null) },
            title = { Text("Снять отметку?") },
            text = { Text("Станция «${station.name}» перестанет считаться посещённой.") },
            confirmButton = { TextButton({ onToggleVisited(station); pendingUnvisit = null }) { Text("Снять") } },
            dismissButton = { TextButton({ pendingUnvisit = null }) { Text("Отмена") } }
        )
    }
}

@Composable
private fun LineSummary(line: MetroLine, progress: Map<String, StationProgress>) {
    val visited = line.stations.count { progress[it.id]?.visited == true }
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = line.composeColor.copy(alpha = .13f))
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Route, null, tint = line.composeColor)
                Spacer(Modifier.width(10.dp))
                Text("${line.lengthKm} км · ${line.travelTime}", fontWeight = FontWeight.Bold)
            }
            Text("$visited из ${line.stations.size} станций", fontSize = 25.sp, fontWeight = FontWeight.Black)
            LinearProgressIndicator(progress = { visited.toFloat() / line.stations.size.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape), color = line.composeColor)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StationTimelineRow(
    station: Station,
    lineColor: Color,
    progress: StationProgress,
    first: Boolean,
    last: Boolean,
    onOpen: () -> Unit,
    onToggle: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { it * 0.35f }
    )

    // Trigger toggle when swiped in either direction, then reset
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
            onToggle()
            dismissState.snapTo(SwipeToDismissBoxValue.Settled)
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val isStart = direction == SwipeToDismissBoxValue.StartToEnd
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(vertical = 6.dp, horizontal = 4.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        if (progress.visited) MaterialTheme.colorScheme.errorContainer
                        else lineColor.copy(alpha = .2f)
                    ),
                contentAlignment = if (isStart) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val icon = if (progress.visited) Icons.Default.Close else Icons.Default.Check
                    val label = if (progress.visited) "Снять" else "Посещено"
                    val tint = if (progress.visited) MaterialTheme.colorScheme.onErrorContainer else lineColor
                    if (isStart) {
                        Icon(icon, null, tint = tint)
                        Text(label, fontWeight = FontWeight.Bold, color = tint)
                    } else {
                        Text(label, fontWeight = FontWeight.Bold, color = tint)
                        Icon(icon, null, tint = tint)
                    }
                }
            }
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 86.dp)) {
            Box(Modifier.width(50.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                if (!first) Box(Modifier.width(4.dp).fillMaxHeight(.5f).align(Alignment.TopCenter).background(lineColor.copy(alpha = .5f)))
                if (!last) Box(Modifier.width(4.dp).fillMaxHeight(.5f).align(Alignment.BottomCenter).background(lineColor.copy(alpha = .5f)))
                Box(
                    Modifier
                        .size(if (progress.visited) 34.dp else 24.dp)
                        .background(if (progress.visited) lineColor else MaterialTheme.colorScheme.surface, CircleShape)
                        .padding(2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (progress.visited) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(19.dp))
                    else Box(Modifier.size(12.dp).background(lineColor, CircleShape))
                }
            }
            Card(
                modifier = Modifier.weight(1f).padding(vertical = 6.dp),
                onClick = onOpen,
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Row(
                    Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                station.name,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (progress.favorite) {
                                Spacer(Modifier.width(6.dp))
                                Icon(Icons.Default.Star, null, tint = Color(0xFFFFB300), modifier = Modifier.size(17.dp))
                            }
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "Открыта в ${station.openingYear}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    IconButton(
                        onClick = onToggle,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            if (progress.visited) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            "Посещение",
                            tint = lineColor
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StationScreen(
    line: MetroLine,
    station: Station,
    progress: StationProgress,
    onBack: () -> Unit,
    onToggleVisited: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSave: (String?, String) -> Unit
) {
    BackHandler(onBack = onBack)
    var note by remember(station.id, progress.note) { mutableStateOf(progress.note) }
    var date by remember(station.id, progress.visitedAt) { mutableStateOf(progress.visitedAt) }
    val context = LocalContext.current
    val parsed = runCatching { LocalDate.parse(date ?: LocalDate.now().toString()) }.getOrDefault(LocalDate.now())
    val picker = remember(date) {
        DatePickerDialog(context, { _, y, m, d -> date = LocalDate.of(y, m + 1, d).toString() }, parsed.year, parsed.monthValue - 1, parsed.dayOfMonth)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Информация о станции", fontWeight = FontWeight.Black) },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
                actions = { IconButton(onToggleFavorite) { Icon(if (progress.favorite) Icons.Default.Star else Icons.Default.StarBorder, "Избранное", tint = if (progress.favorite) Color(0xFFFFB300) else LocalContentColor.current) } }
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Card(shape = RoundedCornerShape(30.dp), colors = CardDefaults.cardColors(containerColor = line.composeColor)) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(54.dp).background(Color.White.copy(alpha = .2f), CircleShape), contentAlignment = Alignment.Center) {
                            Text(line.number, color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)
                        }
                        Text(station.name, color = Color.White, fontWeight = FontWeight.Black, fontSize = 32.sp, lineHeight = 36.sp)
                        Text(line.name, color = Color.White.copy(alpha = .8f), fontSize = 16.sp)
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    InfoTile(Icons.Default.CalendarMonth, "Год открытия", station.openingYear.toString(), Modifier.weight(1f))
                    InfoTile(Icons.Default.ArrowDownward, "Глубина", station.depth?.let { "$it м" } ?: "Нет данных", Modifier.weight(1f))
                }
            }
            item {
                Button(
                    onClick = onToggleVisited,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (progress.visited) MaterialTheme.colorScheme.secondaryContainer else line.composeColor),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Icon(if (progress.visited) Icons.Default.CheckCircle else Icons.Default.AddLocationAlt, null)
                    Spacer(Modifier.width(10.dp))
                    Text(if (progress.visited) "Станция посещена" else "Отметить посещение", fontWeight = FontWeight.Bold)
                }
            }
            item {
                AnimatedVisibility(progress.visited) {
                    OutlinedButton(onClick = { picker.show() }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                        Icon(Icons.Default.Event, null)
                        Spacer(Modifier.width(8.dp))
                        Text(date?.let { formatDate(it) } ?: "Указать дату посещения")
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Заметка о станции") },
                    placeholder = { Text("Что запомнилось во время поездки?") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 130.dp),
                    shape = RoundedCornerShape(18.dp),
                    maxLines = 6
                )
            }
            item {
                FilledTonalButton(onClick = { onSave(date, note); onBack() }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.Default.Save, null); Spacer(Modifier.width(8.dp)); Text("Сохранить")
                }
            }
        }
    }
}

@Composable
private fun InfoTile(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun StatisticsScreen(state: MetroUiState, onLine: (MetroLine) -> Unit) {
    val fraction = state.visitedCount.toFloat() / state.totalCount.coerceAtLeast(1)
    val completedLines = state.lines.count { line -> line.stations.all { state.progress[it.id]?.visited == true } }
    var showShare by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 20.dp, 16.dp, 30.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    ScreenHeader("Статистика", "Твой личный дневник метро")
                }
                FilledTonalButton(
                    onClick = { showShare = true },
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Share, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Поделиться")
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(30.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)) {
                Row(Modifier.fillMaxWidth().padding(22.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(110.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxSize(), strokeWidth = 11.dp, color = Color.White, trackColor = Color.White.copy(alpha = .22f))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${(fraction * 100).roundToInt()}%", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Black)
                            Text("пройдено", color = Color.White.copy(alpha = .7f), fontSize = 11.sp)
                        }
                    }
                    Spacer(Modifier.width(22.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        StatText("${state.visitedCount}", "станций посещено")
                        StatText("${state.favoriteCount}", "в избранном")
                        StatText("$completedLines", "линий завершено")
                    }
                }
            }
        }
        item { SectionTitle("Достижения") }
        item {
            val achievements = listOf(
                Triple("Первая поездка", "Посети первую станцию", state.visitedCount >= 1),
                Triple("Исследователь", "Посети 25 станций", state.visitedCount >= 25),
                Triple("Знаток метро", "Посети 100 станций", state.visitedCount >= 100),
                Triple("Целая линия", "Заверши одну линию", completedLines >= 1),
                Triple("Коллекционер", "Добавь 10 станций в избранное", state.favoriteCount >= 10)
            )
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                achievements.forEach { (title, subtitle, unlocked) -> AchievementRow(title, subtitle, unlocked) }
            }
        }
        item { SectionTitle("Прогресс по линиям") }
        items(state.lines.sortedByDescending { line -> line.stations.count { state.progress[it.id]?.visited == true }.toFloat() / line.stations.size }, key = { it.id }) { line ->
            LineProgressRow(line, state.progress) { onLine(line) }
        }
    }
    if (showShare) {
        ShareProgressDialog(state) { showShare = false }
    }
}

@Composable
private fun StatText(value: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(value, color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp, modifier = Modifier.width(50.dp))
        Text(label, color = Color.White.copy(alpha = .78f), fontSize = 13.sp)
    }
}

@Composable
private fun AchievementRow(title: String, subtitle: String, unlocked: Boolean) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = if (unlocked) Color(0xFFFFB300).copy(alpha = .16f) else MaterialTheme.colorScheme.surfaceContainer)) {
        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).background(if (unlocked) Color(0xFFFFB300) else MaterialTheme.colorScheme.outline.copy(alpha = .25f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(if (unlocked) Icons.Default.EmojiEvents else Icons.Default.Lock, null, tint = if (unlocked) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (unlocked) Icon(Icons.Default.CheckCircle, null, tint = Color(0xFFFFB300))
        }
    }
}

@Composable
private fun LineProgressRow(line: MetroLine, progress: Map<String, StationProgress>, onClick: () -> Unit) {
    val visited = line.stations.count { progress[it.id]?.visited == true }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(39.dp).background(line.composeColor, CircleShape), contentAlignment = Alignment.Center) { Text(line.number, color = Color.White, fontWeight = FontWeight.Black, fontSize = 13.sp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(line.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(5.dp))
            LinearProgressIndicator(progress = { visited.toFloat() / line.stations.size }, modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape), color = line.composeColor)
        }
        Spacer(Modifier.width(12.dp)); Text("$visited/${line.stations.size}", fontWeight = FontWeight.Bold)
    }
}



@Composable
private fun TrainsScreen() {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 20.dp, 16.dp, 30.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { ScreenHeader("Поезда метро", "От «Номерного» до «Москвы-2024»") }
        items(TrainSeed.trains, key = { it.id }) { train -> TrainCard(train) }
    }
}

@Composable
private fun TrainCard(train: Train) {
    Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(10.dp)) {
                items(train.images) { image ->
                    androidx.compose.foundation.Image(
                        painter = painterResource(image), contentDescription = train.name,
                        modifier = Modifier.width(285.dp).height(185.dp).clip(RoundedCornerShape(21.dp)), contentScale = ContentScale.Crop
                    )
                }
            }
            Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 21.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(train.name, fontSize = 26.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                    SuggestionChip(onClick = {}, label = { Text(train.year.toString()) })
                }
                Text(train.description, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 21.sp)
            }
        }
    }
}

@Composable
private fun SettingsScreen(state: MetroUiState, vm: MetroViewModel) {
    val context = LocalContext.current
    var showReset by remember { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { runCatching { context.contentResolver.openOutputStream(it)?.bufferedWriter()?.use { writer -> writer.write(vm.exportJson()) } } }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { runCatching { context.contentResolver.openInputStream(it)?.bufferedReader()?.use { reader -> reader.readText() } }.getOrNull()?.let(vm::importJson) }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 20.dp, 16.dp, 36.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { ScreenHeader("Настройки", "Сделай приложение своим") }
        item { SectionTitle("Оформление") }
        item {
            SettingsCard {
                Text("Тема", fontWeight = FontWeight.Bold)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ThemeMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = state.settings.themeMode == mode,
                            onClick = { vm.updateSettings { it.copy(themeMode = mode) } },
                            shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size)
                        ) { Text(when (mode) { ThemeMode.SYSTEM -> "Система"; ThemeMode.LIGHT -> "Светлая"; ThemeMode.DARK -> "Тёмная" }) }
                    }
                }
                Text("Акцентный цвет", fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    AccentColors.forEachIndexed { index, color ->
                        Box(
                            Modifier.size(42.dp).background(color, CircleShape).clickable { vm.updateSettings { it.copy(accentIndex = index) } },
                            contentAlignment = Alignment.Center
                        ) { if (state.settings.accentIndex == index) Icon(Icons.Default.Check, null, tint = Color.White) }
                    }
                }
                SwitchSetting("Уменьшить анимации", "Спокойные переходы между экранами", state.settings.reduceMotion) { value -> vm.updateSettings { it.copy(reduceMotion = value) } }
            }
        }
        item { SectionTitle("Посещения") }
        item {
            SettingsCard {
                SwitchSetting("Подтверждать снятие отметки", "Защищает от случайного нажатия", state.settings.confirmUnvisit) { value -> vm.updateSettings { it.copy(confirmUnvisit = value) } }
                HorizontalDivider()
                SwitchSetting("Еженедельное напоминание", "Push-уведомление раз в неделю о прогрессе", state.settings.weeklyReminder) { value -> vm.updateSettings { it.copy(weeklyReminder = value) } }
                HorizontalDivider()
                SettingsAction(Icons.Default.FileUpload, "Экспорт прогресса", "Сохранить резервную копию") { exportLauncher.launch("metro-moscow-backup.json") }
                SettingsAction(Icons.Default.FileDownload, "Импорт прогресса", "Восстановить резервную копию") { importLauncher.launch(arrayOf("application/json", "text/plain")) }
                SettingsAction(Icons.Default.DeleteForever, "Сбросить прогресс", "Удалить посещения, даты и заметки", danger = true) { showReset = true }
            }
        }
        item { SectionTitle("О приложении") }
        item {
            SettingsCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(54.dp).background(MetroRed, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) { Text("М", color = Color.White, fontSize = 31.sp, fontWeight = FontWeight.Black) }
                    Spacer(Modifier.width(14.dp))
                    Column { Text("Метро Москвы", fontWeight = FontWeight.Black, fontSize = 20.sp); Text("Версия ${BuildConfig.VERSION_NAME}", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                HorizontalDivider()
                Text("Приложение помогает отмечать посещённые станции, вести заметки и узнавать больше о поездах Московского метро.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text("Разрабатывается с помощью искусственного интеллекта", fontWeight = FontWeight.Medium)
                }
            }
        }
    }
    if (showReset) AlertDialog(
        onDismissRequest = { showReset = false },
        icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("Сбросить весь прогресс?") },
        text = { Text("Будут удалены отметки посещений, даты, избранное и заметки. Настройки оформления сохранятся.") },
        confirmButton = { TextButton({ vm.resetProgress(); showReset = false }) { Text("Сбросить", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton({ showReset = false }) { Text("Отмена") } }
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(15.dp), content = content)
    }
}

@Composable
private fun SwitchSetting(title: String, subtitle: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Switch(value, onChange)
    }
}

@Composable
private fun SettingsAction(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, danger: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold, color = if (danger) MaterialTheme.colorScheme.error else LocalContentColor.current); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ScreenHeader(title: String, subtitle: String) {
    Column(Modifier.padding(bottom = 6.dp)) {
        Text(title, fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.Black)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SectionTitle(title: String) = Text(title, fontSize = 20.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 6.dp))

@Composable
private fun EmptyState(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.SearchOff, null, modifier = Modifier.size(52.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp)); Text(title, fontWeight = FontWeight.Black, fontSize = 20.sp); Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatDate(raw: String): String = runCatching {
    LocalDate.parse(raw).format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
}.getOrDefault(raw)

@Composable
private fun HistoryScreen(state: MetroUiState, onStation: (Station, MetroLine) -> Unit) {
    val visitedStations = state.lines.flatMap { line ->
        line.stations.filter { station -> state.progress[station.id]?.visited == true }
            .map { station -> station to line }
    }.sortedByDescending { (station, _) ->
        state.progress[station.id]?.visitedAt ?: ""
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 20.dp, 16.dp, 36.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { ScreenHeader("История поездок", "Хронология посещённых станций") }
        items(visitedStations, key = { (station, _) -> station.id }) { (station, line) ->
            val p = state.progress[station.id] ?: StationProgress()
            Card(
                onClick = { onStation(station, line) },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Row(
                    Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(44.dp).background(line.composeColor, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(line.number, color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(station.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(Modifier.height(2.dp))
                        Text("Линия: ${line.name}", style = MaterialTheme.typography.bodySmall, color = line.composeColor)
                        if (!p.visitedAt.isNullOrBlank()) {
                            Text("Дата: ${formatDate(p.visitedAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (p.note.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text("«${p.note}»", style = MaterialTheme.typography.bodySmall, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (p.favorite) {
                        Icon(Icons.Default.Star, null, tint = Color(0xFFFFB300), modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
        if (visitedStations.isEmpty()) {
            item {
                EmptyState("История пуста", "Отмечай посещённые станции, и они появятся здесь")
            }
        }
    }
}

@Composable
private fun ShareProgressDialog(state: MetroUiState, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val visited = state.visitedCount
    val total = state.totalCount
    val percent = if (total > 0) ((visited.toFloat() / total) * 100).roundToInt() else 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Мой прогресс в метро") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(20.dp)).padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("🚇 Метро Москвы", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("$visited из $total станций", fontSize = 24.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("Пройдено: $percent%", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Text("Поделись своими достижениями с друзьями!", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Button(onClick = {
                val sendIntent = android.content.Intent().apply {
                    action = android.content.Intent.ACTION_SEND
                    putExtra(android.content.Intent.EXTRA_TEXT, "🚇 Мой прогресс в приложении «Метро Москвы»: посещено $visited из $total станций ($percent%). Открываю город станция за станцией!")
                    type = "text/plain"
                }
                context.startActivity(android.content.Intent.createChooser(sendIntent, "Поделиться прогрессом"))
                onDismiss()
            }) {
                Icon(Icons.Default.Share, null)
                Spacer(Modifier.width(8.dp))
                Text("Поделиться")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        }
    )
}


