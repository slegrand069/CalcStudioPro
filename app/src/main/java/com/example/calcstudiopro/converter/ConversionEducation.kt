package com.example.calcstudiopro.converter

/** Explanations are derived from the same unit factors and offsets used by [ConversionEngine]. */
object ConversionEducation {
    fun explain(from: UnitDefinition, to: UnitDefinition): String {
        require(from.category == to.category)
        val title = when {
            from.id == "lb" || to.id == "lb" -> "La livre avoirdupois est une unité de masse."
            from.id == "speed_of_light" || to.id == "speed_of_light" -> "c désigne la vitesse exacte de la lumière dans le vide."
            else -> null
        }
        if (from.category == UnitCategory.TEMPERATURE) return temperatureRule(from, to)

        val ratio = from.factorToBase / to.factorToBase
        val conversion = if (ratio >= 1.0) {
            "Pour convertir des ${from.name.lowercase()} en ${to.name.lowercase()}, multiplier par ${ConversionFormatter.format(ratio)}."
        } else {
            val divisor = 1.0 / ratio
            "Pour convertir des ${from.name.lowercase()} en ${to.name.lowercase()}, diviser par ${ConversionFormatter.format(divisor)}."
        }
        val relation = if (ratio >= 1.0) "1 ${from.symbol} = ${ConversionFormatter.format(ratio)} ${to.symbol}."
            else "1 ${from.symbol} = ${ConversionFormatter.format(ratio)} ${to.symbol}."
        val example = "Exemple : 2 ${from.symbol} = ${ConversionFormatter.format(2.0 * ratio)} ${to.symbol}."
        return listOfNotNull(title, relation, conversion, example).joinToString(" ")
    }

    private fun temperatureRule(from: UnitDefinition, to: UnitDefinition): String {
        val formula = when (from.id to to.id) {
            "celsius" to "fahrenheit" -> "°F = °C × 9/5 + 32"
            "fahrenheit" to "celsius" -> "°C = (°F − 32) × 5/9"
            "celsius" to "kelvin" -> "K = °C + 273,15"
            "kelvin" to "celsius" -> "°C = K − 273,15"
            "fahrenheit" to "kelvin" -> "K = (°F + 459,67) × 5/9"
            "kelvin" to "fahrenheit" -> "°F = K × 9/5 − 459,67"
            else -> {
                val scale = from.factorToBase / to.factorToBase
                val offset = (from.offsetToBase - to.offsetToBase) / to.factorToBase
                "${to.symbol} = ${from.symbol} × ${ConversionFormatter.format(scale)} ${if (offset < 0) "−" else "+"} ${ConversionFormatter.format(kotlin.math.abs(offset))}"
            }
        }
        return "Conversion affine : $formula. La température de référence n’est pas zéro pour les deux échelles."
    }
}
