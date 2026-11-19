package com.example.calcstudiopro.converter

enum class UnitCategory(val id: String, val label: String) {
    LENGTH("length", "Longueur"), MASS("mass", "Masse"), AREA("area", "Surface"),
    VOLUME("volume", "Volume"), TEMPERATURE("temperature", "Température"),
    SPEED("speed", "Vitesse"), PRESSURE("pressure", "Pression"),
    POWER("power", "Puissance"), ENERGY("energy", "Énergie")
}

/** Multiplicative factors convert to the category's SI base unit. */
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
        u("kelvin", UnitCategory.TEMPERATURE, "Kelvin", "K"),
        u("m_per_s", UnitCategory.SPEED, "Mètre par seconde", "m/s"),
        u("km_per_h", UnitCategory.SPEED, "Kilomètre par heure", "km/h", 1.0 / 3.6),
        u("mph", UnitCategory.SPEED, "Mile par heure", "mph", 0.44704),
        u("kn", UnitCategory.SPEED, "Nœud", "kn", 1852.0 / 3600.0),
        u("ft_per_s", UnitCategory.SPEED, "Pied par seconde", "ft/s", 0.3048),
        u("pa", UnitCategory.PRESSURE, "Pascal", "Pa"),
        u("hpa", UnitCategory.PRESSURE, "Hectopascal", "hPa", 100.0),
        u("kpa", UnitCategory.PRESSURE, "Kilopascal", "kPa", 1000.0),
        u("bar", UnitCategory.PRESSURE, "Bar", "bar", 100000.0),
        u("mbar", UnitCategory.PRESSURE, "Millibar", "mbar", 100.0),
        u("atm", UnitCategory.PRESSURE, "Atmosphère standard", "atm", 101325.0),
        u("psi", UnitCategory.PRESSURE, "Livre-force par pouce carré", "psi", 6894.757293168),
        u("mmhg", UnitCategory.PRESSURE, "Millimètre de mercure conventionnel", "mmHg", 133.322387415),
        u("w", UnitCategory.POWER, "Watt", "W"),
        u("kw", UnitCategory.POWER, "Kilowatt", "kW", 1000.0),
        u("mw", UnitCategory.POWER, "Mégawatt", "MW", 1e6),
        u("ch", UnitCategory.POWER, "Cheval-vapeur métrique", "ch", 735.49875),
        u("hp", UnitCategory.POWER, "Horsepower mécanique", "hp", 745.69987158227022),
        u("j", UnitCategory.ENERGY, "Joule", "J"),
        u("kj", UnitCategory.ENERGY, "Kilojoule", "kJ", 1000.0),
        u("mj", UnitCategory.ENERGY, "Mégajoule", "MJ", 1e6),
        u("cal", UnitCategory.ENERGY, "Calorie thermochimique", "cal", 4.184),
        u("kcal", UnitCategory.ENERGY, "Kilocalorie thermochimique", "kcal", 4184.0),
        u("wh", UnitCategory.ENERGY, "Wattheure", "Wh", 3600.0),
        u("kwh", UnitCategory.ENERGY, "Kilowattheure", "kWh", 3.6e6),
        u("ev", UnitCategory.ENERGY, "Électronvolt", "eV", 1.602176634e-19)
    ).associateBy(UnitDefinition::id)

    fun forCategory(category: UnitCategory) = units.values.filter { it.category == category }
    fun get(id: String): UnitDefinition? = units[id]
    fun defaults(category: UnitCategory) = forCategory(category).take(2)
}
