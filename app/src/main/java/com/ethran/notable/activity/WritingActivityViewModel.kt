package com.ethran.notable.activity

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject

data class ActivityUi(
    /** False until the first count is in; the empty-state sentence waits for it. */
    val loaded: Boolean = false,
    val period: ActivityPeriod = ActivityPeriod.DEFAULT,
    /** Calendar years with data, newest first: one chip each. */
    val years: List<Int> = emptyList(),
    val view: ActivityView
)

/** Holds no Android context: the database, the snapshot and the preferences sit behind [WritingActivityRepository] and [ActivityPrefs]. */
@HiltViewModel
class WritingActivityViewModel internal constructor(
    private val repository: WritingActivityRepository,
    private val prefs: ActivityPrefs,
    private val compute: CoroutineDispatcher,
    private val today: () -> LocalDate
) : ViewModel() {
    @Inject constructor(repository: WritingActivityRepository, prefs: ActivityPrefs) :
        this(repository, prefs, Dispatchers.Default, { LocalDate.now() })

    private var counts: Map<LocalDate, Int> = emptyMap()
    private var period: ActivityPeriod = prefs.period() ?: ActivityPeriod.DEFAULT

    private val _ui = MutableStateFlow(
        // An empty grid until the first count arrives: the card does not jump when the data comes in.
        ActivityUi(period = period, view = WritingActivityStats.build(emptyMap(), period, today()))
    )
    val ui: StateFlow<ActivityUi> = _ui.asStateFlow()

    private var refreshJob: Job? = null
    private var publishJob: Job? = null

    /** Counts again if the strokes changed (the repository answers from memory otherwise). Call on resume of Home. */
    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            try {
                counts = repository.dayCounts()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "refresh failed: ${e.javaClass.simpleName}: ${e.message}")
                return@launch
            }
            publish(loaded = true)
        }
    }

    fun selectPeriod(choice: ActivityPeriod) {
        if (choice == period) return
        period = choice
        prefs.savePeriod(choice)
        publish(loaded = _ui.value.loaded)
    }

    private fun publish(loaded: Boolean) {
        val snapshot = counts
        val chosen = period
        val day = today()
        publishJob?.cancel()
        publishJob = viewModelScope.launch {
            val next = withContext(compute) {
                val years = WritingActivityStats.yearsWithData(snapshot, day)
                // A remembered year that has no data any more falls back to the default.
                val shown = if (chosen is ActivityPeriod.Year && loaded && chosen.year !in years) ActivityPeriod.DEFAULT else chosen
                ActivityUi(loaded, shown, years, WritingActivityStats.build(snapshot, shown, day))
            }
            _ui.value = next
        }
    }

    private companion object { const val TAG = "WritingActivityVM" }
}
