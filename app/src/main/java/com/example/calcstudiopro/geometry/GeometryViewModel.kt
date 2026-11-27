package com.example.calcstudiopro.geometry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.calcstudiopro.domain.AngleUnit
import com.example.calcstudiopro.history.domain.HistoryModes
import com.example.calcstudiopro.history.domain.NewHistoryEntry
import com.example.calcstudiopro.history.domain.HistoryRepository
import com.example.calcstudiopro.history.domain.HistoryDatum
import com.example.calcstudiopro.history.domain.HistoryStructuredData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

data class GeometryState(
    val figure: GeometryFigure = GeometryFigure.TRIANGLE,
    val variant: GeometryVariant = GeometryVariant.GENERAL,
    val fields: Map<String, String> = defaultFields(GeometryFigure.TRIANGLE, GeometryVariant.GENERAL),
    val solutions: List<GeometrySolution> = emptyList(),
    val selectedSolution: Int = 0,
    val error: String? = null,
    val status: GeometrySolveResult? = null,
    val radians: Boolean = false,
    val saved: Boolean = false,
    val saveError: String? = null
) {
    val result: TriangleEngine.Result? get() = solutions.getOrNull(selectedSolution)?.triangle
    val canSave: Boolean get() = solutions.getOrNull(selectedSolution)?.note == null && solutions.isNotEmpty()
    val aText get() = fields["a"].orEmpty()
    val bText get() = fields["b"].orEmpty()
    val cText get() = fields["c"].orEmpty()
}

private fun defaultFields(figure: GeometryFigure, variant: GeometryVariant): Map<String, String> = when (figure) {
    GeometryFigure.TRIANGLE -> when (variant) {
        GeometryVariant.RIGHT -> mapOf("a" to "5", "b" to "6", "c" to "", "C" to "90")
        GeometryVariant.ISOSCELES -> mapOf("a" to "5", "b" to "5", "c" to "7")
        GeometryVariant.EQUILATERAL -> mapOf("a" to "5", "b" to "", "c" to "", "A" to "60", "B" to "60", "C" to "60")
        else -> mapOf("a" to "5", "b" to "6", "c" to "7")
    }
    GeometryFigure.CIRCLE -> when (variant) {
        GeometryVariant.FULL_CIRCLE -> emptyMap()
        GeometryVariant.ARC_SECTOR -> emptyMap()
        else -> emptyMap()
    }
    GeometryFigure.PARALLELOGRAM -> when (variant) {
        GeometryVariant.RHOMBUS -> mapOf("a" to "16", "b" to "16", "angle" to "45", "diagonal1" to "", "diagonal2" to "")
        GeometryVariant.SQUARE -> mapOf("a" to "16", "b" to "16", "angle" to "90", "diagonal1" to "", "diagonal2" to "")
        else -> emptyMap()
    }
}

class GeometryViewModel(private val history: HistoryRepository? = null) : ViewModel() {
    private val mutableState = MutableStateFlow(GeometryState())
    val state = mutableState.asStateFlow()

    fun setAngleUnit(unit: AngleUnit) {
        val old=mutableState.value
        val radians=unit==AngleUnit.RADIANS
        if(old.radians==radians) return
        val angleIds=when(old.figure) {
            GeometryFigure.TRIANGLE -> setOf("A","B","C")
            GeometryFigure.CIRCLE,GeometryFigure.PARALLELOGRAM -> setOf("angle")
        }
        val factor=if(radians) Math.PI/180.0 else 180.0/Math.PI
        val converted=old.fields.mapValues { (key,text) ->
            if(key !in angleIds) text else text.trim().replace(',', '.').toDoubleOrNull()?.let { (it*factor).toString() } ?: text
        }
        mutableState.value=old.copy(fields=converted,radians=radians,solutions=emptyList(),selectedSolution=0,error=null,status=null)
    }
    fun selectFigure(figure: GeometryFigure) {
        val variant = when (figure) {
            GeometryFigure.TRIANGLE, GeometryFigure.PARALLELOGRAM -> GeometryVariant.GENERAL
            GeometryFigure.CIRCLE -> GeometryVariant.FULL_CIRCLE
        }
        mutableState.value = GeometryState(figure, variant, defaultFields(figure, variant), radians = mutableState.value.radians)
    }
    fun selectVariant(variant: GeometryVariant) {
        val old = mutableState.value
        mutableState.value = old.copy(variant = variant, fields = defaultFields(old.figure, variant), solutions = emptyList(), selectedSolution = 0, error = null, status = null)
    }
    fun edit(key: String, text: String) {
        val old = mutableState.value
        mutableState.value = old.copy(fields = old.fields + (key to text), solutions = emptyList(), selectedSolution = 0, error = null, status = null, saved = false, saveError = null)
    }
    fun editA(text: String) = edit("a", text)
    fun editB(text: String) = edit("b", text)
    fun editC(text: String) = edit("c", text)

    fun calculate() {
        val s = mutableState.value
        val values = linkedMapOf<String, Double>()
        s.fields.forEach { (key, text) ->
            if (isGeometryImposed(s, key)) return@forEach
            if (text.isBlank()) return@forEach
            val parsed = text.trim().replace(',', '.').toDoubleOrNull()
            if (parsed == null || !parsed.isFinite()) {
                mutableState.value = s.copy(solutions = emptyList(), error = "Saisie invalide : ${fieldLabel(key)} doit être un nombre fini.", status = null)
                return
            }
            values[key] = parsed
        }
        when (val result = GeometrySolver.solve(GeometryProblem(s.figure, s.variant, values, s.radians))) {
            is GeometrySolveResult.Solved -> mutableState.value = s.copy(solutions = result.solutions, selectedSolution = 0, error = null, status = result)
            GeometrySolveResult.Insufficient -> mutableState.value = s.copy(solutions = emptyList(), error = "Données insuffisantes pour déterminer cette figure.", status = result)
            is GeometrySolveResult.Unsupported -> mutableState.value = s.copy(solutions = emptyList(), error = result.message, status = result)
            is GeometrySolveResult.Impossible -> mutableState.value = s.copy(solutions = emptyList(), error = result.message, status = result)
            is GeometrySolveResult.Indeterminate -> mutableState.value = s.copy(solutions = emptyList(), error = result.message, status = result)
        }
    }
    fun selectSolution(index: Int) {
        val old=mutableState.value
        if(index in old.solutions.indices) mutableState.value=old.copy(selectedSolution=index)
    }
    fun save() {
        val snapshot = mutableState.value
        val solution = snapshot.solutions.getOrNull(snapshot.selectedSolution)?.takeIf { it.note == null } ?: return
        val repo = history ?: return
        val unit = if (snapshot.radians) "rad" else "°"
        val inputs = snapshot.fields.mapNotNull { (key, text) ->
            text.trim().takeIf(String::isNotEmpty)?.let { raw ->
                val numeric = raw.replace(',', '.').toDoubleOrNull()?.takeIf(Double::isFinite)
                val inputUnit = if (key in setOf("A", "B", "C", "angle", "oppositeAngle")) " $unit" else ""
                HistoryDatum("${geometryLabel(key, snapshot.figure)} saisi", "$raw$inputUnit", numeric)
            }
        }
        val results = solution.values.map { (key, value) ->
            val suffix = if (key in setOf("A", "B", "C", "angle", "oppositeAngle")) " $unit" else ""
            HistoryDatum(geometryLabel(key, snapshot.figure), "${TriangleEngine.format(value)}$suffix", value)
        }
        val primaryKeys = when (snapshot.figure) {
            GeometryFigure.TRIANGLE -> setOf("a", "b", "c", "A", "B", "C", "area", "perimeter")
            GeometryFigure.CIRCLE -> setOf("radius", "diameter", "area", "circumference", "angle")
            GeometryFigure.PARALLELOGRAM -> setOf("a", "b", "angle", "area", "perimeter")
        }
        val primaryEntries = solution.values.filterKeys { it in primaryKeys }
        val primary = primaryEntries.map { (key, value) -> results.first { it.label == geometryLabel(key, snapshot.figure) && it.numericValue == value } }
        val primaryText = primary.take(8).joinToString(" · ") { "${it.label}: ${it.displayValue}" }
        val details = results.filterNot { it in primary } + solution.formulas.mapIndexed { index, formula ->
            HistoryDatum("Formule ${index + 1}", "${solution.formulaIds.getOrNull(index).orEmpty()} — $formula")
        } + listOfNotNull(solution.note?.let { HistoryDatum("Note", it) })
        val payload = HistoryStructuredData(
            operation = "${snapshot.figure.label} · ${snapshot.variant.label}",
            inputs = inputs,
            results = results,
            details = details
        )
        viewModelScope.launch {
            runCatching { repo.add(NewHistoryEntry(
                sourceMode = HistoryModes.GEOMETRY,
                description = "${snapshot.figure.label} — ${snapshot.variant.label}",
                displayResult = primaryText,
                reusableValue = null, reusableValueType = null, structuredData = payload
            )) }.onSuccess { mutableState.value = mutableState.value.copy(saved = true, saveError = null) }
                .onFailure { mutableState.value = mutableState.value.copy(saved = false, saveError = it.message ?: "Enregistrement impossible") }
        }
    }

    private fun fieldLabel(key: String) = when(key) { "a"->"a"; "b"->"b"; "c"->"c"; "A"->"A"; "B"->"B"; "C"->"C"; else->key }

    private fun geometryLabel(key: String, figure: GeometryFigure): String = when (key) {
        "a" -> "Côté a"
        "b" -> "Côté b"
        "c" -> "Côté c"
        "A", "B", "C" -> "Angle $key"
        "area" -> "Aire"
        "perimeter" -> "Périmètre"
        "radius" -> "Rayon"
        "diameter" -> "Diamètre"
        "circumference" -> "Circonférence"
        "angle", "oppositeAngle" -> "Angle"
        "arc" -> "Longueur d’arc"
        "sectorArea" -> "Aire du secteur"
        "chord" -> "Corde"
        "distance" -> "Distance au centre"
        "sagitta" -> "Flèche"
        "segmentArea" -> "Aire du segment"
        "diagonal1" -> "Diagonale 1"
        "diagonal2" -> "Diagonale 2"
        "heightA" -> "Hauteur relative à a"
        "heightB" -> "Hauteur relative à b"
        "heightC" -> "Hauteur relative à c"
        "inradius" -> "Rayon inscrit"
        "circumradius" -> "Rayon circonscrit"
        else -> key
    }

    private fun isGeometryImposed(state: GeometryState, key: String): Boolean = when (state.figure) {
        GeometryFigure.TRIANGLE -> when (state.variant) {
            GeometryVariant.RIGHT -> key == "C"
            GeometryVariant.ISOSCELES -> key == "b" || key == "B"
            GeometryVariant.EQUILATERAL -> key in setOf("b", "c", "A", "B", "C")
            else -> false
        }
        GeometryFigure.CIRCLE -> false
        GeometryFigure.PARALLELOGRAM -> when (state.variant) {
            GeometryVariant.RECTANGLE -> key == "angle"
            GeometryVariant.RHOMBUS -> key == "b"
            GeometryVariant.SQUARE -> key in setOf("b", "angle", "diagonal2")
            else -> false
        }
    }

    class Factory(private val history: HistoryRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(GeometryViewModel::class.java)); return GeometryViewModel(history) as T
        }
    }
}
