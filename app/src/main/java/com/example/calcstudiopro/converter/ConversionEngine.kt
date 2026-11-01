package com.example.calcstudiopro.converter

object ConversionEngine {
    fun parse(text: String): Double? {
        val normalized = text.trim().replace(',', '.')
        if (normalized.isEmpty() || normalized in setOf("-", "+", ".", "-.", "+.")) return null
        if (!normalized.matches(Regex("[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?"))) return null
        return normalized.toDoubleOrNull()?.takeIf(Double::isFinite)
    }

    fun convert(value: Double, from: UnitDefinition, to: UnitDefinition): Double {
        require(from.category == to.category) { "Unités incompatibles" }
        require(value.isFinite()) { "Valeur non finie" }
        val base = from.toBase(value)
        require(base.isFinite()) { "Débordement numérique" }
        if (from.category == UnitCategory.TEMPERATURE) require(base >= -1e-9) { "Température inférieure au zéro absolu" }
        val result = to.fromBase(base)
        require(result.isFinite()) { "Débordement numérique" }
        return result
    }
}
