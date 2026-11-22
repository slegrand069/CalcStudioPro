package com.example.calcstudiopro.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.calcstudiopro.history.domain.HistoryModes
import com.example.calcstudiopro.history.domain.HistoryRepository
import com.example.calcstudiopro.history.domain.NewHistoryEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale

enum class StatisticsPage { DASHBOARD, EDITOR, LIBRARY }

data class StatisticsState(
    val input: String = "",
    val result: StatisticsEngine.Result? = null,
    val error: String? = null,
    val saveError: String? = null,
    val dimension: StatisticsDimension = StatisticsDimension.ONE_D,
    val page: StatisticsPage = StatisticsPage.DASHBOARD,
    val oneDValues: List<Double> = emptyList(),
    val twoDPoints: List<Statistics2DEngine.PairValue> = emptyList(),
    val oneDText: String = "",
    val oneDGrouped: Boolean = false,
    val oneDFrequencyText: String = "",
    val twoDText: String = "",
    val oneDDirty: Boolean = false,
    val twoDDirty: Boolean = false,
    val sourceSeriesId: Long? = null,
    val oneDSourceSeriesId: Long? = null,
    val twoDSourceSeriesId: Long? = null,
    val library: List<StatisticsSeriesEntity> = emptyList(),
    val storageError: String? = null,
    val pasteUndo: StoredStatisticsData? = null,
    val percentileInput: String = "50",
    val model: Statistics2DEngine.Model = Statistics2DEngine.Model(Statistics2DEngine.Family.LINEAR),
    val predictionInput: String = "",
    val comparison: List<Statistics2DEngine.Comparison> = emptyList(),
    val comparisonData: List<Statistics2DEngine.PairValue>? = null,
    val recommendedModel: Statistics2DEngine.Model? = null,
    val comparisonLoading: Boolean = false,
    val selectedFit: Statistics2DEngine.FitResult = Statistics2DEngine.FitResult(unavailableReason = "Pas encore calculé."),
    val fitLoading: Boolean = false,
    val recommendationMessage: String = "Aucune recommandation fiable : lancez la comparaison des modèles.",
    val recommendationWarning: String? = null
) {
    val canSave: Boolean get() = result != null
    val activeDirty: Boolean get() = if (dimension == StatisticsDimension.ONE_D) oneDDirty else twoDDirty
    val twoDResult: Statistics2DEngine.Result? get() = Statistics2DEngine.analyze(twoDPoints)
}

class StatisticsViewModel(
    private val history: HistoryRepository? = null,
    private val coroutineScopeOverride: CoroutineScope? = null,
    private val dao: StatisticsDao? = null
) : ViewModel() {
    private var fitRequest = 0
    private var comparisonRequest = 0
    private var comparisonRequested = false
    private val scope get() = coroutineScopeOverride ?: viewModelScope
    private val mutableState = MutableStateFlow(StatisticsState())
    val state = mutableState.asStateFlow()

    init {
        dao?.let { repository ->
            scope.launch(Dispatchers.IO) {
                runCatching { repository.sessions().first() }.getOrDefault(emptyList()).forEach(::restore)
                repository.series().collect { list -> mutableState.value = mutableState.value.copy(library = list) }
            }
        }
    }

    /** Legacy entry point retained for the existing history integration. */
    fun edit(text: String) {
        val analysis = StatisticsEngine.analyze(text)
        mutableState.value = mutableState.value.copy(
            input = text,
            result = (analysis as? StatisticsEngine.Analysis.Valid)?.result,
            error = (analysis as? StatisticsEngine.Analysis.Invalid)?.message?.takeIf { text.isNotBlank() },
            saveError = null
        )
    }

    fun selectDimension(dimension: StatisticsDimension) {
        val s = mutableState.value
        mutableState.value = s.copy(dimension = dimension, sourceSeriesId = if (dimension == StatisticsDimension.ONE_D) s.oneDSourceSeriesId else s.twoDSourceSeriesId, page = StatisticsPage.DASHBOARD)
    }

    fun openEditor() {
        val s = mutableState.value
        mutableState.value = if (s.dimension == StatisticsDimension.ONE_D) s.copy(page = StatisticsPage.EDITOR,
            oneDText = s.oneDValues.joinToString("\n") { it.toString() },
            oneDFrequencyText = if (s.oneDFrequencyText.isNotEmpty()) s.oneDFrequencyText else s.oneDValues.groupingBy { it }.eachCount().toSortedMap().entries.joinToString("\n") { "${it.key};${it.value}" })
        else s.copy(page = StatisticsPage.EDITOR, twoDText = s.twoDPoints.joinToString("\n") { "${it.x}\t${it.y}" })
    }

    fun navigate(page: StatisticsPage) { mutableState.value = mutableState.value.copy(page = page) }
    fun editOneD(text: String) { mutableState.value = mutableState.value.copy(oneDText = text, storageError = null) }
    fun editFrequencyText(text: String) { mutableState.value = mutableState.value.copy(oneDFrequencyText = text, storageError = null) }
    fun setOneDGrouped(grouped: Boolean) {
        val s = mutableState.value
        if (s.oneDGrouped == grouped) return
        if (grouped) {
            val parsed = StatisticsInputParser.oneD(s.oneDText)
            if (parsed !is StatisticsInputParser.Parsed.Valid) { mutableState.value = s.copy(storageError = (parsed as StatisticsInputParser.Parsed.Invalid).message); return }
            val frequencyText = parsed.value.groupingBy { it }.eachCount().toSortedMap().entries.joinToString("\n") { "${it.key};${it.value}" }
            mutableState.value = s.copy(oneDGrouped = true, oneDFrequencyText = frequencyText, storageError = null)
        } else {
            val parsed = StatisticsInputParser.frequencies(s.oneDFrequencyText)
            if (parsed !is StatisticsInputParser.Parsed.Valid) { mutableState.value = s.copy(storageError = (parsed as StatisticsInputParser.Parsed.Invalid).message); return }
            val expanded = parsed.value.flatMap { item -> List(item.count) { item.value } }
            mutableState.value = s.copy(oneDGrouped = false, oneDText = expanded.joinToString("\n"), storageError = null)
        }
    }
    fun editTwoD(text: String) { mutableState.value = mutableState.value.copy(twoDText = text, storageError = null) }

    /** Clears only the active dimension's observations and persists the current draft session. */
    fun clearCurrentData() {
        val current = mutableState.value
        when (current.dimension) {
            StatisticsDimension.ONE_D -> {
                val next = current.copy(oneDValues = emptyList(), oneDText = "", oneDFrequencyText = "", input = "", result = null,
                    error = null, oneDDirty = true, pasteUndo = null, storageError = null)
                mutableState.value = next
                persistSession(next, StatisticsDimension.ONE_D)
            }
            StatisticsDimension.TWO_D -> {
                val next = current.copy(twoDPoints = emptyList(), twoDText = "", twoDDirty = true, pasteUndo = null, storageError = null)
                mutableState.value = next
                persistSession(next, StatisticsDimension.TWO_D)
                computeSelectedFit(emptyList(), next.model)
                twoDPointsChanged(emptyList())
            }
        }
    }

    fun finishEditing() {
        val s = mutableState.value
        when (s.dimension) {
            StatisticsDimension.ONE_D -> {
                val parsedValues = if (s.oneDGrouped) when (val parsed = StatisticsInputParser.frequencies(s.oneDFrequencyText)) {
                    is StatisticsInputParser.Parsed.Invalid -> { mutableState.value = s.copy(storageError = parsed.message + (parsed.line?.let { " (ligne $it)" } ?: "")); null }
                    is StatisticsInputParser.Parsed.Valid -> parsed.value.flatMap { item -> List(item.count) { item.value } }
                } else when (val parsed = StatisticsInputParser.oneD(s.oneDText)) {
                    is StatisticsInputParser.Parsed.Invalid -> { mutableState.value = s.copy(storageError = parsed.message + (parsed.line?.let { " (ligne $it)" } ?: "")); null }
                    is StatisticsInputParser.Parsed.Valid -> parsed.value
                }
                if (parsedValues != null) {
                    val next = s.copy(oneDValues = parsedValues, input = parsedValues.joinToString(" "), result = StatisticsEngine.describe(parsedValues), error = null,
                        oneDDirty = true, page = StatisticsPage.DASHBOARD, storageError = null)
                    mutableState.value = next
                    persistSession(next, StatisticsDimension.ONE_D)
                }
            }
            StatisticsDimension.TWO_D -> when (val parsed = StatisticsInputParser.twoD(s.twoDText)) {
                is StatisticsInputParser.Parsed.Invalid -> mutableState.value = s.copy(storageError = parsed.message + (parsed.line?.let { " (ligne $it)" } ?: ""))
                is StatisticsInputParser.Parsed.Valid -> {
                    val next = s.copy(twoDPoints = parsed.value, twoDDirty = true, page = StatisticsPage.DASHBOARD, storageError = null)
                    mutableState.value = next
                    persistSession(next, StatisticsDimension.TWO_D)
                    computeSelectedFit(parsed.value, next.model)
                    twoDPointsChanged(parsed.value)
                }
            }
        }
    }

    fun setPercentile(value: String) { mutableState.value = mutableState.value.copy(percentileInput = value) }
    fun setModel(family: Statistics2DEngine.Family, degree: Int = mutableState.value.model.degree) {
        val model = Statistics2DEngine.Model(family, degree)
        mutableState.value = mutableState.value.copy(model = model)
        computeSelectedFit(mutableState.value.twoDPoints, model)
    }
    fun setPrediction(value: String) { mutableState.value = mutableState.value.copy(predictionInput = value) }
    fun compareModels() {
        comparisonRequested = true
        val points = mutableState.value.twoDPoints
        startComparison(points)
    }
    private fun startComparison(points: List<Statistics2DEngine.PairValue>) {
        val request = ++comparisonRequest
        mutableState.value = mutableState.value.copy(
            comparison = emptyList(), comparisonData = null, recommendedModel = null, comparisonLoading = true,
            recommendationMessage = "Comparaison en cours…", recommendationWarning = null
        )
        scope.launch(Dispatchers.Default) {
            val result = Statistics2DEngine.compare(points)
            val recommendation = Statistics2DEngine.recommend(result, points)
            if (request == comparisonRequest) mutableState.value = mutableState.value.copy(
                comparison = result,
                comparisonData = points,
                recommendedModel = recommendation.model,
                recommendationMessage = recommendation.message,
                recommendationWarning = recommendation.warning,
                comparisonLoading = false
            )
        }
    }
    private fun twoDPointsChanged(points: List<Statistics2DEngine.PairValue>) {
        comparisonRequest++ // Discard an in-flight result for the previous dataset.
        mutableState.value = mutableState.value.copy(
            comparison = emptyList(), comparisonData = null, recommendedModel = null, comparisonLoading = false,
            recommendationMessage = "Aucune recommandation fiable : les données ont changé.", recommendationWarning = null
        )
        if (comparisonRequested) startComparison(points)
    }
    private fun computeSelectedFit(points: List<Statistics2DEngine.PairValue>, model: Statistics2DEngine.Model) {
        val request = ++fitRequest
        mutableState.value = mutableState.value.copy(fitLoading = true)
        scope.launch(Dispatchers.Default) {
            val result = Statistics2DEngine.fit(points, model)
            if (request == fitRequest) mutableState.value = mutableState.value.copy(selectedFit = result, fitLoading = false)
        }
    }

    fun pasteOneD(text: String, replace: Boolean) {
        val s = mutableState.value
        val parsed = if (s.oneDGrouped) StatisticsInputParser.frequencies(text).let { p -> when (p) {
            is StatisticsInputParser.Parsed.Invalid -> p
            is StatisticsInputParser.Parsed.Valid -> StatisticsInputParser.Parsed.Valid(p.value.flatMap { item -> List(item.count) { item.value } })
        } } else StatisticsInputParser.oneD(text)
        if (parsed !is StatisticsInputParser.Parsed.Valid) { mutableState.value = s.copy(storageError = (parsed as StatisticsInputParser.Parsed.Invalid).message); return }
        val values = if (replace) parsed.value else s.oneDValues + parsed.value
        if (values.size > StatisticsEngine.MAX_VALUES) { mutableState.value = s.copy(storageError = "La série est limitée à ${StatisticsEngine.MAX_VALUES} observations."); return }
        val groupedText = values.groupingBy { it }.eachCount().toSortedMap().entries.joinToString("\n") { "${it.key};${it.value}" }
        mutableState.value = s.copy(pasteUndo = StoredStatisticsData.OneD(s.oneDValues), oneDValues = values, oneDText = values.joinToString("\n"),
            oneDFrequencyText = groupedText, oneDDirty = true, input = values.joinToString(" "))
    }

    fun pasteTwoD(text: String, replace: Boolean) {
        val s = mutableState.value
        val parsed = StatisticsInputParser.twoD(text)
        if (parsed !is StatisticsInputParser.Parsed.Valid) { mutableState.value = s.copy(storageError = (parsed as StatisticsInputParser.Parsed.Invalid).message + (parsed.line?.let { " (ligne $it)" } ?: "")); return }
        val points = if (replace) parsed.value else s.twoDPoints + parsed.value
        if (points.size > StatisticsEngine.MAX_VALUES) { mutableState.value = s.copy(storageError = "La série est limitée à ${StatisticsEngine.MAX_VALUES} couples."); return }
        mutableState.value = s.copy(pasteUndo = StoredStatisticsData.TwoD(s.twoDPoints), twoDPoints = points, twoDDirty = true,
            twoDText = points.joinToString("\n") { "${it.x}\t${it.y}" })
        twoDPointsChanged(points)
    }

    fun undoPaste() {
        val s = mutableState.value
        when (val undo = s.pasteUndo) {
            is StoredStatisticsData.OneD -> mutableState.value = s.copy(oneDValues = undo.values, oneDText = undo.values.joinToString("\n"),
                oneDFrequencyText = undo.values.groupingBy { it }.eachCount().toSortedMap().entries.joinToString("\n") { "${it.key};${it.value}" }, input = undo.values.joinToString(" "), pasteUndo = null)
            is StoredStatisticsData.TwoD -> {
                mutableState.value = s.copy(twoDPoints = undo.points, twoDText = undo.points.joinToString("\n") { "${it.x}\t${it.y}" }, pasteUndo = null)
                twoDPointsChanged(undo.points)
            }
            null -> Unit
        }
    }

    fun copyColumnToOneD(copyX: Boolean, confirm: Boolean) {
        val s = mutableState.value
        if (s.oneDDirty && !confirm) { mutableState.value = s.copy(storageError = "La session 1D non enregistrée sera remplacée. Confirmez le transfert."); return }
        val values = s.twoDPoints.map { if (copyX) it.x else it.y }
        val next = s.copy(dimension = StatisticsDimension.ONE_D, sourceSeriesId = s.oneDSourceSeriesId, oneDValues = values,
            oneDText = values.joinToString("\n"), oneDFrequencyText = values.groupingBy { it }.eachCount().toSortedMap().entries.joinToString("\n") { "${it.key};${it.value}" },
            input = values.joinToString(" "), result = StatisticsEngine.describe(values), oneDDirty = true, page = StatisticsPage.DASHBOARD, storageError = null)
        mutableState.value = next
        persistSession(next, StatisticsDimension.ONE_D)
    }

    fun saveLibrary(name: String, description: String, saveAs: Boolean = false) {
        val repository = dao ?: return
        val normalized = name.trim()
        if (normalized.isEmpty()) { mutableState.value = mutableState.value.copy(storageError = "Le nom de la série est obligatoire."); return }
        val s = mutableState.value
        val dimension = s.dimension
        val data = currentData(s, dimension)
        scope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            runCatching {
                val sourceId = if (dimension == StatisticsDimension.ONE_D) s.oneDSourceSeriesId else s.twoDSourceSeriesId
                if (!saveAs && sourceId != null) {
                    check(repository.updateSeries(sourceId, normalized, description.trim(), StatisticsDataCodec.encode(data), now) > 0)
                    sourceId
                } else repository.insertSeries(StatisticsSeriesEntity(name = normalized, description = description.trim(), dimension = dimension.name,
                    dataJson = StatisticsDataCodec.encode(data), createdAt = now, updatedAt = now))
            }.onSuccess { id ->
                val current = mutableState.value
                val stillSame = StatisticsDataCodec.encode(currentData(current, dimension)) == StatisticsDataCodec.encode(data)
                mutableState.value = current.copy(sourceSeriesId = if (current.dimension == dimension) id else current.sourceSeriesId,
                    oneDSourceSeriesId = if (dimension == StatisticsDimension.ONE_D) id else current.oneDSourceSeriesId,
                    twoDSourceSeriesId = if (dimension == StatisticsDimension.TWO_D) id else current.twoDSourceSeriesId,
                    oneDDirty = if (dimension == StatisticsDimension.ONE_D && stillSame) false else current.oneDDirty,
                    twoDDirty = if (dimension == StatisticsDimension.TWO_D && stillSame) false else current.twoDDirty, storageError = null)
            }
                .onFailure { error -> mutableState.value = mutableState.value.copy(storageError = "Enregistrement impossible : ${error.message ?: "erreur de stockage"}") }
        }
    }

    fun openSeries(id: Long, discardDirty: Boolean = false) {
        val repository = dao ?: return
        scope.launch(Dispatchers.IO) {
            val series = repository.series(id) ?: return@launch
            val s = mutableState.value
            val targetDimension = if (series.dimension == StatisticsDimension.TWO_D.name) StatisticsDimension.TWO_D else StatisticsDimension.ONE_D
            val targetDirty = if (targetDimension == StatisticsDimension.ONE_D) s.oneDDirty else s.twoDDirty
            if (targetDirty && !discardDirty) { mutableState.value = s.copy(storageError = "Cette session contient des modifications non enregistrées. Enregistrez-la ou confirmez son remplacement."); return@launch }
            val data = StatisticsDataCodec.decode(series.dataJson)
            if (data == null) { mutableState.value = mutableState.value.copy(storageError = "Série illisible ; les données de la session ont été conservées."); return@launch }
            val current = mutableState.value
            val next = when (data) {
                is StoredStatisticsData.OneD -> current.copy(dimension = StatisticsDimension.ONE_D, oneDValues = data.values, oneDGrouped = data.grouped,
                    oneDText = data.values.joinToString("\n"), oneDFrequencyText = data.values.groupingBy { it }.eachCount().toSortedMap().entries.joinToString("\n") { "${it.key};${it.value}" },
                    input = data.values.joinToString(" "), result = StatisticsEngine.describe(data.values), oneDDirty = false,
                    sourceSeriesId = id, oneDSourceSeriesId = id, page = StatisticsPage.DASHBOARD, storageError = null)
                is StoredStatisticsData.TwoD -> current.copy(dimension = StatisticsDimension.TWO_D, twoDPoints = data.points, twoDDirty = false,
                    sourceSeriesId = id, twoDSourceSeriesId = id, page = StatisticsPage.DASHBOARD, storageError = null)
            }
            mutableState.value = next
            persistSession(next, series.dimension.let { runCatching { StatisticsDimension.valueOf(it) }.getOrDefault(StatisticsDimension.ONE_D) })
            if (data is StoredStatisticsData.TwoD) {
                computeSelectedFit(data.points, next.model)
                twoDPointsChanged(data.points)
            }
        }
    }

    fun saveCurrentAndOpen(id: Long, nameIfNeeded: String) {
        val repository = dao ?: return
        val s = mutableState.value
        scope.launch(Dispatchers.IO) {
            val target = repository.series(id) ?: return@launch
            val dimension = if (target.dimension == StatisticsDimension.TWO_D.name) StatisticsDimension.TWO_D else StatisticsDimension.ONE_D
            val targetDirty = if (dimension == StatisticsDimension.ONE_D) s.oneDDirty else s.twoDDirty
            val sourceId = if (dimension == StatisticsDimension.ONE_D) s.oneDSourceSeriesId else s.twoDSourceSeriesId
            val data = currentData(s, dimension)
            val now = System.currentTimeMillis()
            val saved = runCatching {
                if (!targetDirty) sourceId
                else if (sourceId != null) {
                    val source = repository.series(sourceId) ?: error("Série source absente")
                    check(repository.updateSeries(sourceId, source.name, source.description, StatisticsDataCodec.encode(data), now) > 0)
                    sourceId
                } else {
                    check(nameIfNeeded.isNotBlank()) { "Un nom est requis pour enregistrer cette session." }
                    repository.insertSeries(StatisticsSeriesEntity(name = nameIfNeeded.trim(), description = "", dimension = dimension.name,
                        dataJson = StatisticsDataCodec.encode(data), createdAt = now, updatedAt = now))
                }
            }
            if (saved.isFailure) { mutableState.value = mutableState.value.copy(storageError = "Enregistrement impossible : ${saved.exceptionOrNull()?.message}"); return@launch }
            val refreshedTarget = repository.series(id) ?: return@launch
            val targetData = StatisticsDataCodec.decode(refreshedTarget.dataJson)
            if (targetData == null) { mutableState.value = mutableState.value.copy(storageError = "Série illisible ; la session actuelle est conservée."); return@launch }
            val current = mutableState.value
            val next = when (targetData) {
                is StoredStatisticsData.OneD -> current.copy(dimension = StatisticsDimension.ONE_D, oneDValues = targetData.values, oneDGrouped = targetData.grouped,
                    oneDText = targetData.values.joinToString("\n"), oneDFrequencyText = targetData.values.groupingBy { it }.eachCount().toSortedMap().entries.joinToString("\n") { "${it.key};${it.value}" },
                    input = targetData.values.joinToString(" "), result = StatisticsEngine.describe(targetData.values), oneDDirty = false,
                    twoDDirty = current.twoDDirty, sourceSeriesId = target.id, oneDSourceSeriesId = target.id, page = StatisticsPage.DASHBOARD, storageError = null)
                is StoredStatisticsData.TwoD -> current.copy(dimension = StatisticsDimension.TWO_D, twoDPoints = targetData.points, twoDDirty = false,
                    oneDDirty = current.oneDDirty, sourceSeriesId = target.id, twoDSourceSeriesId = target.id, page = StatisticsPage.DASHBOARD, storageError = null)
            }
            mutableState.value = next
            persistSession(next, if (target.dimension == StatisticsDimension.TWO_D.name) StatisticsDimension.TWO_D else StatisticsDimension.ONE_D)
            if (targetData is StoredStatisticsData.TwoD) {
                computeSelectedFit(targetData.points, next.model)
                twoDPointsChanged(targetData.points)
            }
        }
    }

    fun deleteSeries(id: Long) { dao?.let { repository -> scope.launch(Dispatchers.IO) {
        runCatching { repository.deleteSeries(id) }.onSuccess {
            val current = mutableState.value
            mutableState.value = current.copy(
                sourceSeriesId = if (current.sourceSeriesId == id) null else current.sourceSeriesId,
                oneDSourceSeriesId = if (current.oneDSourceSeriesId == id) null else current.oneDSourceSeriesId,
                twoDSourceSeriesId = if (current.twoDSourceSeriesId == id) null else current.twoDSourceSeriesId,
                storageError = null
            )
        }.onFailure { mutableState.value = mutableState.value.copy(storageError = "Suppression impossible.") }
    } } }

    fun renameSeries(id: Long, name: String, description: String) {
        val normalized = name.trim()
        if (normalized.isEmpty()) { mutableState.value = mutableState.value.copy(storageError = "Le nom est obligatoire."); return }
        dao?.let { repository -> scope.launch(Dispatchers.IO) {
            runCatching { val item = repository.series(id) ?: error("Série absente")
                check(repository.updateSeries(id, normalized, description.trim(), item.dataJson, System.currentTimeMillis()) > 0)
            }.onFailure { mutableState.value = mutableState.value.copy(storageError = "Renommage impossible : nom déjà utilisé ou erreur de stockage.") }
        } }
    }

    fun duplicateSeries(id: Long, name: String, description: String) {
        val normalized = name.trim()
        if (normalized.isEmpty()) { mutableState.value = mutableState.value.copy(storageError = "Le nom est obligatoire."); return }
        dao?.let { repository -> scope.launch(Dispatchers.IO) {
            runCatching { val item = repository.series(id) ?: error("Série absente"); val now = System.currentTimeMillis()
                repository.insertSeries(item.copy(id = 0, name = normalized, description = description.trim(), createdAt = now, updatedAt = now))
            }.onFailure { mutableState.value = mutableState.value.copy(storageError = "Duplication impossible : nom déjà utilisé ou erreur de stockage.") }
        } }
    }

    private fun persistSession(state: StatisticsState, dimension: StatisticsDimension) {
        dao?.let { repository -> scope.launch(Dispatchers.IO) {
            runCatching { repository.saveSession(StatisticsSessionEntity(dimension.name, StatisticsDataCodec.encode(currentData(state, dimension)), if (dimension == StatisticsDimension.ONE_D) state.oneDSourceSeriesId else state.twoDSourceSeriesId,
                if (dimension == StatisticsDimension.ONE_D) state.oneDDirty else state.twoDDirty, System.currentTimeMillis())) }
                .onFailure { mutableState.value = mutableState.value.copy(storageError = "Session conservée en mémoire ; persistance impossible.") }
        } }
    }

    private fun restore(session: StatisticsSessionEntity) {
        val data = StatisticsDataCodec.decode(session.dataJson) ?: return
        val s = mutableState.value
        mutableState.value = when (data) {
            is StoredStatisticsData.OneD -> s.copy(oneDValues = data.values, oneDGrouped = data.grouped, oneDText = data.values.joinToString("\n"),
                oneDFrequencyText = data.values.groupingBy { it }.eachCount().toSortedMap().entries.joinToString("\n") { "${it.key};${it.value}" },
                input = data.values.joinToString(" "), result = StatisticsEngine.describe(data.values),
                oneDDirty = session.dirty, oneDSourceSeriesId = session.sourceSeriesId,
                sourceSeriesId = if (s.dimension == StatisticsDimension.ONE_D) session.sourceSeriesId else s.sourceSeriesId)
            is StoredStatisticsData.TwoD -> s.copy(twoDPoints = data.points, twoDDirty = session.dirty, twoDSourceSeriesId = session.sourceSeriesId,
                sourceSeriesId = if (s.dimension == StatisticsDimension.TWO_D) session.sourceSeriesId else s.sourceSeriesId).also {
                computeSelectedFit(data.points, s.model)
                twoDPointsChanged(data.points)
            }
        }
    }

    private fun currentData(state: StatisticsState, dimension: StatisticsDimension): StoredStatisticsData = when (dimension) {
        StatisticsDimension.ONE_D -> StoredStatisticsData.OneD(state.oneDValues, state.oneDGrouped)
        StatisticsDimension.TWO_D -> StoredStatisticsData.TwoD(state.twoDPoints)
    }

    /** Original feature: keep the explicit history action available. */
    fun save() {
        val snapshot = mutableState.value
        val result = snapshot.result ?: return
        val details = """{"version":1,"values":"${escapeJson(snapshot.input)}","count":${result.count},"sum":"${result.sum}","mean":"${result.mean}","median":"${result.median}","minimum":"${result.minimum}","maximum":"${result.maximum}","populationStandardDeviation":"${result.populationStandardDeviation}"}"""
        val summary = listOf("Effectif : ${result.count}", "Somme : ${StatisticsEngine.format(result.sum)}", "Moyenne : ${StatisticsEngine.format(result.mean)}",
            "Médiane : ${StatisticsEngine.format(result.median)}", "Minimum : ${StatisticsEngine.format(result.minimum)}", "Maximum : ${StatisticsEngine.format(result.maximum)}",
            "Écart-type (population) : ${StatisticsEngine.format(result.populationStandardDeviation)}").joinToString("\n")
        history?.let { repository -> scope.launch { runCatching { repository.add(NewHistoryEntry(sourceMode = HistoryModes.STATISTICS,
            description = "Statistiques : ${snapshot.input.trim().replace(Regex("\\s*;\\s*"), " ; ")}", displayResult = summary, reusableValue = null, reusableValueType = null, detailsJson = details)) }
            .onFailure { error -> mutableState.value = mutableState.value.copy(saveError = error.message ?: "Enregistrement impossible") } } }
    }

    private fun escapeJson(value: String) = value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")

    class Factory(private val history: HistoryRepository, private val dao: StatisticsDao? = null) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(StatisticsViewModel::class.java))
            return StatisticsViewModel(history, dao = dao) as T
        }
    }
}
