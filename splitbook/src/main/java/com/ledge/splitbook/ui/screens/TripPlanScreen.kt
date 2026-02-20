package com.ledge.splitbook.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ledge.splitbook.ui.vm.TripPlanViewModel
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar

@Composable
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
fun TripPlanScreen(
    groupId: Long,
    groupName: String,
    onBack: () -> Unit,
    viewModel: TripPlanViewModel = hiltViewModel()
) {
    LaunchedEffect(groupId) {
        viewModel.loadGroup(groupId)
    }

    var addPlaceForDayId by remember { mutableStateOf<Long?>(null) }
    var placeName by remember { mutableStateOf("") }
    var dayMenuForDayId by remember { mutableStateOf<Long?>(null) }
    var placeMenuForPlaceId by remember { mutableStateOf<Long?>(null) }
    var startDateMs by remember { mutableStateOf<Long?>(null) }

    val context = LocalContext.current

    val ui by viewModel.ui.collectAsState()

    val dateFmt = remember { DateTimeFormatter.ofPattern("d MMM yyyy") }
    val rangeFmt = remember { DateTimeFormatter.ofPattern("d MMM") }

    if (addPlaceForDayId != null) {
        AlertDialog(
            onDismissRequest = {
                addPlaceForDayId = null
                placeName = ""
            },
            title = { Text(stringResource(id = com.ledge.splitbook.R.string.add_place)) },
            text = {
                OutlinedTextField(
                    value = placeName,
                    onValueChange = { placeName = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val dayId = addPlaceForDayId
                        val name = placeName.trim()
                        if (dayId != null && name.isNotEmpty()) {
                            viewModel.addPlace(dayId, name)
                        }
                        addPlaceForDayId = null
                        placeName = ""
                    }
                ) {
                    Text(stringResource(id = com.ledge.splitbook.R.string.add))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        addPlaceForDayId = null
                        placeName = ""
                    }
                ) {
                    Text(stringResource(id = com.ledge.splitbook.R.string.cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val days = ui.days.size
                    val places = ui.days.sumOf { it.places.size }
                    val summary = if (days == 0) {
                        stringResource(id = com.ledge.splitbook.R.string.no_plan_added)
                    } else {
                        stringResource(id = com.ledge.splitbook.R.string.days_places, days, places)
                    }
                    val range = if (ui.startDate != null && ui.endDate != null) {
                        val startLabel = runCatching { LocalDate.parse(ui.startDate).format(rangeFmt) }.getOrNull() ?: ui.startDate
                        val endLabel = runCatching { LocalDate.parse(ui.endDate).format(rangeFmt) }.getOrNull() ?: ui.endDate
                        "$startLabel - $endLabel"
                    } else {
                        null
                    }

                    Column {
                        Text(
                            text = "$groupName - ${stringResource(id = com.ledge.splitbook.R.string.trip_plan)}",
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            if (range != null) "$summary • $range" else summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = stringResource(id = com.ledge.splitbook.R.string.back)) }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val cal = Calendar.getInstance()
                            DatePickerDialog(
                                context,
                                { _, yy, mm, dd ->
                                    val start = LocalDate.of(yy, mm + 1, dd)
                                    startDateMs = start.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

                                    // After picking start, pick end
                                    val cal2 = Calendar.getInstance()
                                    cal2.set(yy, mm, dd)
                                    val endMinMs = cal2.timeInMillis
                                    DatePickerDialog(
                                        context,
                                        { _, ey, em, ed ->
                                            val end = LocalDate.of(ey, em + 1, ed)
                                            val endMs = end.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                                            viewModel.setTripDates(startDateMs, endMs)
                                        },
                                        yy,
                                        mm,
                                        dd
                                    ).apply {
                                        datePicker.minDate = endMinMs
                                    }.show()
                                },
                                cal.get(Calendar.YEAR),
                                cal.get(Calendar.MONTH),
                                cal.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        }
                    ) {
                        Icon(Icons.Default.DateRange, contentDescription = stringResource(id = com.ledge.splitbook.R.string.set_trip_dates))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.addDay() }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(id = com.ledge.splitbook.R.string.add_day))
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 12.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (ui.days.isEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    stringResource(id = com.ledge.splitbook.R.string.trip_plan),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    stringResource(id = com.ledge.splitbook.R.string.trip_plan_help),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                ui.days.forEach { d ->
                    item(key = d.id) {
                        Card(
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            stringResource(id = com.ledge.splitbook.R.string.trip_day_title, d.dayNumber),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        d.date?.let { raw ->
                                            val formatted = runCatching {
                                                LocalDate.parse(raw).format(dateFmt)
                                            }.getOrNull() ?: raw
                                            Text(
                                                formatted,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                            )
                                        }
                                    }
                                    Box {
                                        IconButton(onClick = { dayMenuForDayId = d.id }) {
                                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(id = com.ledge.splitbook.R.string.more))
                                        }
                                        DropdownMenu(
                                            expanded = dayMenuForDayId == d.id,
                                            onDismissRequest = { dayMenuForDayId = null }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text(stringResource(id = com.ledge.splitbook.R.string.delete)) },
                                                onClick = {
                                                    dayMenuForDayId = null
                                                    viewModel.deleteDay(d.id)
                                                }
                                            )
                                        }
                                    }
                                }

                                if (d.places.isEmpty()) {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            stringResource(id = com.ledge.splitbook.R.string.no_places),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        TextButton(
                                            onClick = {
                                                addPlaceForDayId = d.id
                                                placeName = ""
                                            },
                                            modifier = Modifier
                                                .align(Alignment.Start),
                                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Text("+ ${stringResource(id = com.ledge.splitbook.R.string.add_place)}")
                                        }
                                    }
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        d.places.forEach { place ->
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .combinedClickable(
                                                        onClick = { },
                                                        onLongClick = { placeMenuForPlaceId = place.id }
                                                    )
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        Icons.Default.Place,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    Text(
                                                        place.name,
                                                        style = MaterialTheme.typography.bodyLarge,
                                                        fontWeight = FontWeight.Normal,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                }
                                                DropdownMenu(
                                                    expanded = placeMenuForPlaceId == place.id,
                                                    onDismissRequest = { placeMenuForPlaceId = null }
                                                ) {
                                                    DropdownMenuItem(
                                                        text = { Text(stringResource(id = com.ledge.splitbook.R.string.delete)) },
                                                        onClick = {
                                                            placeMenuForPlaceId = null
                                                            viewModel.deletePlace(place.id)
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                if (d.places.isNotEmpty()) {
                                    TextButton(
                                        onClick = {
                                            addPlaceForDayId = d.id
                                            placeName = ""
                                        },
                                        modifier = Modifier
                                            .align(Alignment.Start),
                                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("+ ${stringResource(id = com.ledge.splitbook.R.string.add_place)}")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
