package com.example.calcstudiopro.converter

enum class UnitCategory(val id: String, val label: String) {
    LENGTH("length", "Longueur"), MASS("mass", "Masse"), AREA("area", "Surface"),
    VOLUME("volume", "Volume"), TEMPERATURE("temperature", "Température")
}

/** Multiplicative factors are exact SI definitions where practical (inch, foot, yard, mile, pound, US gallon). */
data class UnitDefinition(
    val id: String, val category: UnitCategory, val name: String, val symbol: String,
    val factorToBase: Double = 1.0, val offsetToBase: Double = 0.0
) {
    fun toBase(value: Double): Double = value * factorToBase + offsetToBase
    fun fromBase(value: Double): Double = (value - offsetToBase) / factorToBase
}

object UnitCatalog {
    private fun u(id: String, c: UnitCategory, name: String, symbol: String, factor: Double = 1.0, offset: Double = 0.0) =
        UnitDefinition(id, c, name, symbol, factor, offset)
    val units = listOf(
        u("mm", UnitCategory.LENGTH, "Millimètre", "mm", .001), u("cm", UnitCategory.LENGTH, "Centimètre", "cm", .01),
        u("m", UnitCategory.LENGTH, "Mètre", "m"), u("km", UnitCategory.LENGTH, "Kilomètre", "km", 1000.0),
        u("in", UnitCategory.LENGTH, "Pouce international", "in", .0254), u("ft", UnitCategory.LENGTH, "Pied international", "ft", .3048),
        u("yd", UnitCategory.LENGTH, "Yard international", "yd", .9144), u("mi", UnitCategory.LENGTH, "Mile international", "mi", 1609.344),
        u("mg", UnitCategory.MASS, "Milligramme", "mg", 1e-6), u("g", UnitCategory.MASS, "Gramme", "g", .001),
        u("kg", UnitCategory.MASS, "Kilogramme", "kg"), u("metric_tonne", UnitCategory.MASS, "Tonne métrique", "t", 1000.0),
        u("oz", UnitCategory.MASS, "Once avoirdupois", "oz", .028349523125), u("lb", UnitCategory.MASS, "Livre avoirdupois", "lb", .45359237),
        u("mm2", UnitCategory.AREA, "Millimètre carré", "mm²", 1e-6), u("cm2", UnitCategory.AREA, "Centimètre carré", "cm²", 1e-4),
        u("m2", UnitCategory.AREA, "Mètre carré", "m²"), u("km2", UnitCategory.AREA, "Kilomètre carré", "km²", 1e6),
        u("hectare", UnitCategory.AREA, "Hectare", "ha", 10000.0), u("acre", UnitCategory.AREA, "Acre international", "ac", 4046.8564224),
        u("ml", UnitCategory.VOLUME, "Millilitre", "mL", 1e-6), u("cl", UnitCategory.VOLUME, "Centilitre", "cL", 1e-5),
        u("l", UnitCategory.VOLUME, "Litre", "L", .001), u("m3", UnitCategory.VOLUME, "Mètre cube", "m³"),
        u("us_gallon", UnitCategory.VOLUME, "Gallon américain", "gal US", .003785411784),
        u("celsius", UnitCategory.TEMPERATURE, "Degré Celsius", "°C", 1.0, 273.15),
        u("fahrenheit", UnitCategory.TEMPERATURE, "Degré Fahrenheit", "°F", 5.0 / 9.0, 255.3722222222222),
        u("kelvin", UnitCategory.TEMPERATURE, "Kelvin", "K")
    ).associateBy(UnitDefinition::id)

    fun forCategory(category: UnitCategory) = units.values.filter { it.category == category }
    fun get(id: String): UnitDefinition? = units[id]
    fun defaults(category: UnitCategory) = forCategory(category).take(2)
}
