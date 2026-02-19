package com.ledge.splitbook.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledge.splitbook.data.entity.PlaceEntity
import com.ledge.splitbook.data.repo.PlaceRepository
import com.ledge.splitbook.data.repo.TripPlanRepository
import com.ledge.splitbook.data.repo.TripDayRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.time.LocalDate
import javax.inject.Inject

data class TripPlanUiState(
    val groupId: Long = 0L,
    val days: List<TripDayUi> = emptyList(),
    val isLoading: Boolean = false,
    val startDate: String? = null,
    val endDate: String? = null
)

data class TripDayUi(
    val id: Long,
    val dayNumber: Int,
    val date: String?,
    val places: List<PlaceEntity> = emptyList() // Load on demand
)

@HiltViewModel
class TripPlanViewModel @Inject constructor(
    private val tripDayRepo: TripDayRepository,
    private val placeRepo: PlaceRepository,
    private val tripPlanRepo: TripPlanRepository
) : ViewModel() {

    private val _ui = MutableStateFlow(TripPlanUiState())
    val ui: StateFlow<TripPlanUiState> = _ui.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun loadGroup(groupId: Long) {
        _ui.value = _ui.value.copy(groupId = groupId, isLoading = true)
        viewModelScope.launch {
            tripDayRepo.observeDays(groupId)
                .flatMapLatest { days ->
                    if (days.isEmpty()) {
                        flowOf(emptyList())
                    } else {
                        combine(
                            days.map { day ->
                                placeRepo.observePlaces(day.id)
                                    .map { places ->
                                        TripDayUi(
                                            id = day.id,
                                            dayNumber = day.dayNumber,
                                            date = day.date,
                                            places = places
                                        )
                                    }
                            }
                        ) { it.toList() }
                    }
                }
                .collect { dayUis ->
                    val dates = dayUis.mapNotNull { it.date }
                        .mapNotNull { raw -> runCatching { LocalDate.parse(raw) }.getOrNull() }

                    val (start, end) = if (dates.isEmpty()) {
                        null to null
                    } else {
                        dates.minOrNull()?.toString() to dates.maxOrNull()?.toString()
                    }

                    _ui.value = _ui.value.copy(
                        days = dayUis,
                        isLoading = false,
                        startDate = start,
                        endDate = end
                    )
                }
        }
    }

    fun addDay(date: String? = null) {
        val groupId = _ui.value.groupId
        if (groupId == 0L) return
        val nextDayNumber = (_ui.value.days.maxOfOrNull { it.dayNumber } ?: 0) + 1
        viewModelScope.launch {
            tripDayRepo.addDay(groupId, nextDayNumber, date)
        }
    }

    fun deleteDay(dayId: Long) {
        viewModelScope.launch {
            tripDayRepo.deleteDay(dayId)
        }
    }

    fun addPlace(dayId: Long, name: String) {
        val groupId = _ui.value.groupId
        if (groupId == 0L) return
        viewModelScope.launch {
            placeRepo.addPlace(groupId, dayId, name)
        }
    }

    fun deletePlace(placeId: Long) {
        viewModelScope.launch {
            placeRepo.deletePlace(placeId)
        }
    }

    fun setTripDates(startEpochMillis: Long?, endEpochMillis: Long?) {
        val groupId = _ui.value.groupId
        if (groupId == 0L) return
        val startMs = startEpochMillis ?: return
        val endMs = endEpochMillis ?: return

        val start = tripPlanRepo.epochMillisToLocalDate(startMs)
        val end = tripPlanRepo.epochMillisToLocalDate(endMs)
        if (end.isBefore(start)) return

        _ui.value = _ui.value.copy(startDate = start.toString(), endDate = end.toString())

        val dates = buildList {
            var d: LocalDate = start
            while (!d.isAfter(end)) {
                add(d)
                d = d.plusDays(1)
            }
        }

        viewModelScope.launch {
            tripPlanRepo.replaceTripDays(groupId, dates)
        }
    }
}
