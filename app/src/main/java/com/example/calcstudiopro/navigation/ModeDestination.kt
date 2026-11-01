package com.example.calcstudiopro.navigation

/** A mode that is currently available from the shared mode selector. */
data class ModeDestination(
    val route: String,
    val label: String
)

/** Keep this list limited to modes with a usable screen. */
val availableModes = listOf(
    ModeDestination(route = "calculator", label = "Calculatrice scientifique"),
    ModeDestination(route = "history", label = "Historique"),
    ModeDestination(route = "converter", label = "Convertisseur"),
    ModeDestination(route = "statistics", label = "Statistiques"),
    ModeDestination(route = "geometry", label = "Géométrie")
)
