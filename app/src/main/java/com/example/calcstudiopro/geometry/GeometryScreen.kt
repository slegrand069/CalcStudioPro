package com.example.calcstudiopro.geometry

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import com.example.calcstudiopro.ui.components.bringFocusedFieldIntoView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.calcstudiopro.domain.AngleUnit
import kotlin.math.*

@Composable
fun GeometryRoute(viewModel: GeometryViewModel, onOpenModeSelector: () -> Unit) {
    val state by viewModel.state.collectAsState()
    GeometryScreen(
        state, onOpenModeSelector, viewModel::editA, viewModel::editB, viewModel::editC,
        viewModel::save, viewModel::selectFigure, viewModel::selectVariant, viewModel::edit,
        viewModel::calculate, viewModel::selectSolution, viewModel::setAngleUnit
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeometryScreen(
    state: GeometryState,
    onOpenModeSelector: () -> Unit,
    onEditA: (String) -> Unit,
    onEditB: (String) -> Unit,
    onEditC: (String) -> Unit,
    onSave: () -> Unit,
    onSelectFigure: (GeometryFigure) -> Unit = {},
    onSelectVariant: (GeometryVariant) -> Unit = {},
    onEditField: (String, String) -> Unit = { _, _ -> },
    onCalculate: () -> Unit = {},
    onSelectSolution: (Int) -> Unit = {},
    onAngleUnitChange: (AngleUnit) -> Unit = {}
) {
    var allFormulasExpanded by remember { mutableStateOf(false) }
    val fields = geometryFields(state.figure, state.variant)
    Scaffold(topBar = { TopAppBar(title = { Text("Géométrie") }, actions = {
        TextButton(onClick = onOpenModeSelector, modifier = Modifier.testTag("geometry-mode")) { Text("MODE") }
    }) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).imePadding().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            item {
                SelectorRow("Figure", state.figure.label, GeometryFigure.entries.map { it.label }, { label ->
                    GeometryFigure.entries.firstOrNull { it.label == label }?.let(onSelectFigure)
                }, "geometry-figure")
            }
            item {
                val options = variantsFor(state.figure)
                SelectorRow("Type", state.variant.label, options.map { it.label }, { label ->
                    options.firstOrNull { it.label == label }?.let(onSelectVariant)
                }, "geometry-variant")
            }
            item {
                val help = when (state.figure to state.variant) {
                    GeometryFigure.TRIANGLE to GeometryVariant.RIGHT -> "Convention : l’angle C vaut 90° (ou π/2 rad) et c = AB est l’hypoténuse."
                    GeometryFigure.TRIANGLE to GeometryVariant.ISOSCELES -> "Convention : a = b sont les côtés égaux ; c est la base. Les angles A et B sont égaux."
                    GeometryFigure.TRIANGLE to GeometryVariant.EQUILATERAL -> "Convention : a = b = c et A = B = C = 60° (π/3 rad)."
                    GeometryFigure.CIRCLE to GeometryVariant.ARC_SECTOR -> "θ est l’angle parcouru : 180° correspond à un demi-cercle et 360° au cercle complet."
                    GeometryFigure.CIRCLE to GeometryVariant.MINOR_SEGMENT -> "Le segment mineur utilise un angle au centre inférieur ou égal à 180°."
                    GeometryFigure.CIRCLE to GeometryVariant.MAJOR_SEGMENT -> "Le segment majeur utilise un angle au centre supérieur à 180°."
                    GeometryFigure.PARALLELOGRAM to GeometryVariant.GENERAL -> "a et b sont les côtés adjacents ; l’angle est compris entre eux. d₁ correspond au signe + de la formule."
                    else -> null
                }
                if (help != null) Text(help, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(AngleUnit.DEGREES, AngleUnit.RADIANS).forEach { unit ->
                        ChoiceButton(
                            label = if (unit == AngleUnit.DEGREES) "DEG" else "RAD",
                            selected = state.radians == (unit == AngleUnit.RADIANS),
                            modifier = Modifier.testTag("geometry-angle-${unit.name.lowercase()}")
                        ) { onAngleUnitChange(unit) }
                    }
                }
            }
            item { Text("Données connues", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            item { GeometryFieldGrid(state, fields.filterNot { it.advanced }, onEditField) }
            if (fields.any { it.advanced }) {
                item {
                    var expanded by remember(state.figure, state.variant) { mutableStateOf(false) }
                    TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth().testTag("geometry-advanced-toggle")) {
                        Text(if (expanded) "Masquer les données complémentaires  ⌃" else "Afficher les données complémentaires  ⌄")
                    }
                    if (expanded) GeometryFieldGrid(state, fields.filter { it.advanced }, onEditField)
                }
            }
            item {
                Button(onClick = onCalculate, modifier = Modifier.fillMaxWidth().testTag("geometry-calculate")) { Text("Calculer") }
            }
            if (state.solutions.isNotEmpty()) {
                item { Text("Résultats", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                if (state.solutions.size > 1) item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.solutions.indices.forEach { index ->
                            ChoiceButton("Solution ${index + 1}", index == state.selectedSolution,
                                Modifier.testTag("geometry-solution-$index")) { onSelectSolution(index) }
                        }
                    }
                }
                val solution = state.solutions[state.selectedSolution]
                if (solution.note == null) item { GeometryFigureCanvas(state.figure, state.variant, solution, state.radians,
                    Modifier.fillMaxWidth().height(when {
                        state.figure == GeometryFigure.TRIANGLE -> 315.dp
                        state.figure == GeometryFigure.CIRCLE && state.variant in setOf(GeometryVariant.MINOR_SEGMENT, GeometryVariant.MAJOR_SEGMENT) -> 315.dp
                        state.figure == GeometryFigure.PARALLELOGRAM -> 252.dp
                        else -> 210.dp
                    }).testTag("geometry-diagram")) }
                val resultRows = compactResultRows(state.figure, state.variant, solution.values.keys)
                itemsIndexed(resultRows, key = { index, row -> "result-row-$index-${row.joinToString()}" }) { _, keys ->
                    CompactResultRow(state.figure, keys, solution.values, state.radians)
                }
                solution.note?.let { note -> item { Text(note, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium) } }
                item { Text("Formules utilisées", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
                itemsIndexed(solution.formulas, key = { index, _ -> "formula-$index" }) { _, formula ->
                    val displayed = formulaForDisplay(formula, state.figure, state.variant)
                    if (state.figure != GeometryFigure.CIRCLE) MathText("•  ${if (state.figure == GeometryFigure.TRIANGLE) triangleHeightNotation(displayed) else displayed}", MaterialTheme.typography.bodyMedium)
                    else Text("•  $displayed", style = MaterialTheme.typography.bodyMedium)
                }
                item {
                    val variables = formulaVariables(state.figure, state.variant)
                    if (state.figure != GeometryFigure.CIRCLE) MathText(if (state.figure == GeometryFigure.TRIANGLE) triangleHeightNotation(variables) else variables, MaterialTheme.typography.bodySmall, MaterialTheme.colorScheme.onSurfaceVariant)
                    else Text(variables, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item {
                    TextButton(onClick = { allFormulasExpanded = !allFormulasExpanded }, modifier = Modifier.testTag("geometry-all-formulas-toggle")) {
                        Text(if (allFormulasExpanded) "Masquer toutes les formules" else "Toutes les formules")
                    }
                    if (allFormulasExpanded) {
                        val usedText = solution.formulas.joinToString(";") { formulaForDisplay(it, state.figure, state.variant) }.replace(" ", "")
                        allFormulas(state.figure, state.variant)
                            .filterNot { formula -> formula.replace(" ", "").length > 7 && formula.replace(" ", "") in usedText }
                            .forEach {
                                if (state.figure != GeometryFigure.CIRCLE) MathText("•  ${if (state.figure == GeometryFigure.TRIANGLE) triangleHeightNotation(it) else it}", MaterialTheme.typography.bodySmall)
                                else Text("•  $it", style = MaterialTheme.typography.bodySmall)
                            }
                    }
                }
                item {
                    Button(onClick = onSave, enabled = state.canSave, modifier = Modifier.fillMaxWidth().testTag("geometry-save")) { Text("Enregistrer dans l’historique") }
                    if (state.saved) Text("Résultat enregistré dans l’historique.", color = MaterialTheme.colorScheme.primary)
                    state.saveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
            state.error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("geometry-error")) } }
        }
    }
}

private data class InputField(val id: String, val label: String, val advanced: Boolean = false, val editable: Boolean = true)
private fun geometryFields(figure: GeometryFigure, variant: GeometryVariant): List<InputField> = when (figure) {
    GeometryFigure.TRIANGLE -> listOf(
        InputField("a", "a (BC)"),
        InputField("b", "b (AC)", editable = variant != GeometryVariant.ISOSCELES && variant != GeometryVariant.EQUILATERAL),
        InputField("c", "c (AB)", editable = variant != GeometryVariant.EQUILATERAL),
        InputField("A", "A", editable = variant != GeometryVariant.EQUILATERAL),
        InputField("B", "B", editable = variant != GeometryVariant.ISOSCELES && variant != GeometryVariant.EQUILATERAL),
        InputField("C", "C", editable = variant != GeometryVariant.RIGHT && variant != GeometryVariant.EQUILATERAL),
        InputField("area", "Aire", true), InputField("perimeter", "Périmètre", true),
        InputField("hA", "Hauteur h_a", true), InputField("hB", "Hauteur h_b", true), InputField("hC", "Hauteur h_c", true)
    )
    GeometryFigure.CIRCLE -> when (variant) {
        GeometryVariant.FULL_CIRCLE -> listOf(InputField("radius", "Rayon r"), InputField("diameter", "Diamètre d"), InputField("circumference", "Circonférence C"), InputField("area", "Aire S"))
        GeometryVariant.ARC_SECTOR -> listOf(InputField("radius", "Rayon r"), InputField("angle", "Angle θ"), InputField("arc", "Arc s"), InputField("sectorArea", "Secteur S"))
        else -> listOf(InputField("radius", "Rayon r"), InputField("angle", "Angle θ"), InputField("chord", "Corde c"), InputField("distance", "Distance d"), InputField("sagitta", "Flèche h"), InputField("segmentArea", "Segment S"))
    }
    GeometryFigure.PARALLELOGRAM -> listOf(
        InputField("a", "Côté a"), InputField("b", "Côté b", editable = variant != GeometryVariant.RHOMBUS && variant != GeometryVariant.SQUARE), InputField("angle", "Angle θ", editable = variant != GeometryVariant.RECTANGLE && variant != GeometryVariant.SQUARE),
        InputField("diagonal1", "Diagonale d₁"), InputField("diagonal2", "Diagonale d₂"),
        InputField("area", "Aire", true), InputField("perimeter", "Périmètre", true)
    )
}

private fun fieldRows(state: GeometryState, fields: List<InputField>): List<List<InputField>> = when (state.figure) {
    GeometryFigure.TRIANGLE -> fields.chunked(3)
    GeometryFigure.CIRCLE -> if (state.variant == GeometryVariant.MINOR_SEGMENT || state.variant == GeometryVariant.MAJOR_SEGMENT) fields.chunked(3) else fields.chunked(2)
    GeometryFigure.PARALLELOGRAM -> fields.chunked(3)
}

@Composable
private fun GeometryFieldGrid(state: GeometryState, fields: List<InputField>, onEdit: (String, String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        fieldRows(state, fields).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { field ->
                    val value = displayedFieldValue(state, field)
                    GeometryInput(field, value, state.radians, state.figure == GeometryFigure.TRIANGLE, Modifier.weight(1f)) { onEdit(field.id, it) }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private fun displayedFieldValue(state: GeometryState, field: InputField): String {
    val typed = state.fields[field.id].orEmpty()
    val fixedRadians = when {
        state.figure == GeometryFigure.TRIANGLE && state.variant == GeometryVariant.EQUILATERAL && field.id in setOf("A", "B", "C") -> Math.PI / 3
        state.figure == GeometryFigure.TRIANGLE && state.variant == GeometryVariant.RIGHT && field.id == "C" -> Math.PI / 2
        state.figure == GeometryFigure.PARALLELOGRAM && state.variant in setOf(GeometryVariant.RECTANGLE, GeometryVariant.SQUARE) && field.id == "angle" -> Math.PI / 2
        else -> null
    }
    if (fixedRadians != null) return if (state.radians) if (fixedRadians == Math.PI / 3) "π/3" else "π/2" else if (fixedRadians == Math.PI / 3) "60" else "90"
    val reference = when {
        state.figure == GeometryFigure.TRIANGLE && state.variant in setOf(GeometryVariant.ISOSCELES, GeometryVariant.EQUILATERAL) && field.id == "b" -> state.fields["a"]
        state.figure == GeometryFigure.TRIANGLE && state.variant == GeometryVariant.EQUILATERAL && field.id == "c" -> state.fields["a"]
        state.figure == GeometryFigure.TRIANGLE && state.variant == GeometryVariant.ISOSCELES && field.id == "B" -> state.fields["A"]
        state.figure == GeometryFigure.PARALLELOGRAM && state.variant in setOf(GeometryVariant.RHOMBUS, GeometryVariant.SQUARE) && field.id == "b" -> state.fields["a"]
        state.figure == GeometryFigure.PARALLELOGRAM && state.variant == GeometryVariant.SQUARE && field.id == "diagonal2" -> state.fields["diagonal1"]
        else -> null
    }
    return if (reference != null) reference else typed
}

private fun compactResultRows(figure: GeometryFigure, variant: GeometryVariant, available: Set<String>): List<List<String>> {
    val groups = when (figure) {
        GeometryFigure.TRIANGLE -> listOf(listOf("a", "b", "c"), listOf("A", "B", "C"), listOf("perimeter", "area"), listOf("hA", "hB", "hC"), listOf("inradius", "circumradius"))
        GeometryFigure.CIRCLE -> when (variant) {
            GeometryVariant.FULL_CIRCLE -> listOf(listOf("radius", "circumference", "area"))
            GeometryVariant.ARC_SECTOR -> listOf(listOf("radius", "circumference", "area"), listOf("angle", "arc", "sectorArea"))
            else -> listOf(listOf("radius", "circumference", "area"), listOf("angle", "segmentArea"), listOf("chord", "distance", "sagitta"))
        }
        GeometryFigure.PARALLELOGRAM -> listOf(listOf("a", "b"), listOf("angle", "oppositeAngle"), listOf("diagonal1", "diagonal2"), listOf("heightA", "heightB"), listOf("perimeter", "area"))
    }
    return groups.map { group -> group.filter { it in available } }.filter { it.isNotEmpty() }
}

@Composable
private fun CompactResultRow(figure: GeometryFigure, keys: List<String>, values: Map<String, Double>, radians: Boolean) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            keys.forEach { key ->
                val value = values[key] ?: return@forEach
                Column(Modifier.weight(1f).testTag("geometry-result-$key"), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (figure != GeometryFigure.CIRCLE) MathText(resultLabel(key), MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 2)
                    else Text(resultLabel(key), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 2)
                    Text(TriangleEngine.format(value) + if (key in setOf("A", "B", "C", "angle", "oppositeAngle")) if (radians) " rad" else "°" else "",
                        fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                }
            }
            repeat(3 - keys.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

private fun variantsFor(figure: GeometryFigure) = when (figure) {
    GeometryFigure.TRIANGLE -> listOf(GeometryVariant.GENERAL, GeometryVariant.RIGHT, GeometryVariant.ISOSCELES, GeometryVariant.EQUILATERAL)
    GeometryFigure.CIRCLE -> listOf(GeometryVariant.FULL_CIRCLE, GeometryVariant.ARC_SECTOR, GeometryVariant.MINOR_SEGMENT, GeometryVariant.MAJOR_SEGMENT)
    GeometryFigure.PARALLELOGRAM -> listOf(GeometryVariant.GENERAL, GeometryVariant.RECTANGLE, GeometryVariant.RHOMBUS, GeometryVariant.SQUARE)
}

@Composable
private fun SelectorRow(label: String, selected: String, options: List<String>, onSelected: (String) -> Unit, tag: String) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().testTag(tag)) {
                Text(selected, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                Text("⌄")
            }
            DropdownMenu(expanded, { expanded = false }) {
                options.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { expanded = false; onSelected(option) }) }
            }
        }
    }
}

@Composable
private fun GeometryInput(field: InputField, value: String, radians: Boolean, triangle: Boolean, modifier: Modifier = Modifier, onValueChange: (String) -> Unit) {
    val suffix = if (field.id in setOf("A", "B", "C", "angle") && value !in setOf("π/2", "π/3")) if (radians) " (rad)" else " (°)" else ""
    OutlinedTextField(value, onValueChange, modifier = modifier.bringFocusedFieldIntoView().testTag("geometry-input-${field.id}"),
        label = {
            val label = field.label + suffix + if (field.editable) "" else " · imposé"
            if (triangle) MathText(label, MaterialTheme.typography.labelSmall, maxLines = 1) else Text(label, maxLines = 1, style = MaterialTheme.typography.labelSmall)
        }, singleLine = true,
        readOnly = !field.editable, placeholder = { Text("Inconnu") }, textStyle = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun ChoiceButton(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = if (selected) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
    else ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface)
    Button(onClick = onClick, modifier = modifier, colors = colors, shape = RoundedCornerShape(20.dp)) { Text(label, maxLines = 1) }
}

private fun resultLabel(key: String) = when (key) {
    "a" -> "Côté a"; "b" -> "Côté b"; "c" -> "Côté c"; "A" -> "Angle A"; "B" -> "Angle B"; "C" -> "Angle C"
    "perimeter" -> "P"; "area" -> "S"; "hA" -> "h_a"; "hB" -> "h_b"; "hC" -> "h_c"
    "inradius" -> "r inscrit"; "circumradius" -> "R circonscrit"; "radius" -> "r"; "diameter" -> "d"
    "circumference" -> "P"; "angle" -> "θ"; "arc" -> "s"; "sectorArea" -> "S secteur"
    "chord" -> "c"; "distance" -> "d"; "sagitta" -> "h"; "segmentArea" -> "S segment"
    "oppositeAngle" -> "Angle adjacent"; "diagonal1" -> "d₁"; "diagonal2" -> "d₂"
    "heightA" -> "hₐ"; "heightB" -> "hᵦ"; else -> key
}

private fun triangleHeightNotation(text: String): String = text.replace("hₐ", "h_a").replace("h꜀", "h_c")

@Composable
private fun MathText(
    text: String,
    style: TextStyle,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE
) {
    val subscriptStyle = SpanStyle(fontSize = style.fontSize * .72f, baselineShift = BaselineShift(-.25f))
    val annotated = buildAnnotatedString {
        var index = 0
        while (index < text.length) {
            if (text.startsWith("h_", index) && index + 2 < text.length && text[index + 2] in "abc") {
                append('h')
                withStyle(subscriptStyle) { append(text[index + 2]) }
                index += 3
            } else {
                append(text[index])
                index++
            }
        }
    }
    Text(annotated, style = style, color = color, textAlign = textAlign, maxLines = maxLines)
}

private fun allFormulas(figure: GeometryFigure, variant: GeometryVariant): List<String> = when (figure) {
    GeometryFigure.TRIANGLE -> listOf("A + B + C = 180° (π rad)", "S = ab sin(C)/2", "s = (a+b+c)/2", "S = √[s(s−a)(s−b)(s−c)]", "P = a+b+c", "hₐ = 2S/a", "h_b = 2S/b", "h꜀ = 2S/c", "r = S/s", "R = abc/(4S)")
    GeometryFigure.CIRCLE -> when (variant) {
        GeometryVariant.FULL_CIRCLE -> listOf("d = 2r", "P = 2πr", "S = πr²")
        GeometryVariant.ARC_SECTOR -> listOf("P = 2πr", "S = πr²", "s = r|θ|", "S secteur = r²|θ|/2")
        GeometryVariant.MAJOR_SEGMENT -> listOf("P = 2πr", "S = πr²", "c = 2r sin(θₘ/2)", "d = r|cos(θₘ/2)|", "h = r(1−cos(|θ|/2))", "S mineur = r²(θₘ−sin θₘ)/2", "S segment = πr²−S mineur")
        else -> listOf("P = 2πr", "S = πr²", "c = 2r sin(θ/2)", "d = r cos(θ/2)", "h = r(1−cos(θ/2))", "S segment = r²(θ−sin θ)/2")
    }
    GeometryFigure.PARALLELOGRAM -> listOf("A = C", "B = D", "A + B = 180° (π rad)", "S = ab sin(θ)", "P = 2(a+b)", "d₁²=a²+b²+2ab cos(θ)", "d₂²=a²+b²−2ab cos(θ)", "hₐ = S/a", "hᵦ = S/b")
}

private fun formulaForDisplay(formula: String, figure: GeometryFigure, variant: GeometryVariant): String = when (formula) {
    "Loi des cosinus" -> "a² = b² + c² − 2bc cos(A) ; b² = a² + c² − 2ac cos(B) ; c² = a² + b² − 2ab cos(C)"
    "Formule de Héron" -> "s = (a+b+c)/2 ; S = √[s(s−a)(s−b)(s−c)]"
    "Loi des sinus" -> "a/sin(A) = b/sin(B) = c/sin(C) = 2R"
    "Somme des angles" -> "A + B + C = 180° (π rad)"
    "h꜀ = 2S/c" -> "h꜀ = 2S/c (hauteur relative au côté c)"
    "S = ab sin(C)/2" -> "S = (a × b × sin(C))/2"
    "sin(A) = a/c" -> "sin(A) = a/c (a opposé à A, c hypoténuse)"
    "sin(B) = b/c" -> "sin(B) = b/c (b opposé à B, c hypoténuse)"
    "cos(A) = b/c" -> "cos(A) = b/c (b adjacent à A, c hypoténuse)"
    "cos(B) = a/c" -> "cos(B) = a/c (a adjacent à B, c hypoténuse)"
    "s = rθ" -> "s = r|θ| (longueur d’arc positive, θ orienté)"
    "A = r²θ/2" -> "S secteur = r²|θ|/2 (θ en radians, aire positive)"
    "S secteur = r²|θ|/2 (θ en radians, aire positive)" -> "S secteur = r²|θ|/2 (θ en radians, aire positive)"
    "A = r²(θ − sin θ)/2" -> if (variant == GeometryVariant.MAJOR_SEGMENT) "S segment = πr² − r²(θₘ − sin θₘ)/2 ; θₘ = 2π−|θ| (radians)" else "S segment = r²(|θ| − sin |θ|)/2 ; θ en radians"
    "S segment = r²(θ − sin θ)/2" -> "S segment = r²(|θ| − sin |θ|)/2 ; θ en radians"
    "S segment = S cercle − S mineur" -> "S segment = πr² − r²(θₘ − sin θₘ)/2 ; θₘ = 2π−|θ| (radians)"
    "c = 2r sin(θ/2)" -> if (variant == GeometryVariant.MAJOR_SEGMENT) "c = 2r sin(θₘ/2) ; θₘ = 2π−|θ|, en radians" else "c = 2r sin(|θ|/2) ; θ en radians"
    "d = r|cos(θ/2)|" -> "d = r|cos(|θ|/2)| ; θ en radians"
    "h = r(1 − cos(θ/2))" -> "h = r(1 − cos(|θ|/2)) ; θ en radians"
    else -> formula
}

private fun formulaVariables(figure: GeometryFigure, variant: GeometryVariant) = when (figure) {
    GeometryFigure.TRIANGLE -> "a, b, c : côtés opposés à A, B, C ; S : aire ; P : périmètre ; s : demi-périmètre ; hₐ, h_b, h꜀ : hauteurs ; r, R : rayons inscrit et circonscrit."
    GeometryFigure.CIRCLE -> when (variant) {
        GeometryVariant.FULL_CIRCLE -> "r : rayon ; d : diamètre ; P : circonférence ; S : aire."
        GeometryVariant.ARC_SECTOR -> "r : rayon ; θ : angle orienté au centre (formules trigonométriques en radians) ; s : longueur d’arc positive ; P : circonférence ; S : aire du cercle ; S secteur : aire du secteur."
        GeometryVariant.MAJOR_SEGMENT -> "r : rayon ; θ : angle orienté en radians ; c : corde ; d : distance du centre à la corde ; h : flèche ; P : circonférence ; S : aire du cercle ; S segment : aire du segment majeur (π < |θ| ≤ 2π)."
        else -> "r : rayon ; θ : angle orienté en radians ; c : corde ; d : distance du centre à la corde ; h : flèche ; P : circonférence ; S : aire du cercle ; S segment : aire du segment mineur (0 < |θ| ≤ π)."
    }
    GeometryFigure.PARALLELOGRAM -> "a, b : côtés adjacents ; θ : angle compris ; d₁, d₂ : diagonales ; h_a, h_b : hauteurs ; P : périmètre ; S : aire."
}

internal fun parallelogramAngleRadians(displayAngle: Double, radians: Boolean): Double =
    if (radians) displayAngle else Math.toRadians(displayAngle)

@Composable
private fun GeometryFigureCanvas(figure: GeometryFigure, variant: GeometryVariant, solution: GeometrySolution, radians: Boolean, modifier: Modifier = Modifier) {
    val shapeColor = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.tertiary
    val surface = MaterialTheme.colorScheme.surfaceVariant
    if (figure == GeometryFigure.TRIANGLE && solution.triangle != null) {
        TriangleCanvas(solution.triangle, modifier, radians)
        return
    }
    Canvas(modifier) {
        val cx=size.width/2; val cy=size.height/2
        if (figure == GeometryFigure.CIRCLE) {
            val r=min(size.width,size.height)*.35f
            val angle=solution.values["angle"]?.let { if(radians) it else Math.toRadians(it) }
            if(angle!=null) {
                val start=-PI/2;val end=start+angle
                val p1=Offset(cx+r*cos(start).toFloat(),cy+r*sin(start).toFloat())
                val p2=Offset(cx+r*cos(end).toFloat(),cy+r*sin(end).toFloat())
                if(solution.values["sectorArea"]!=null) {
                    drawArc(secondary.copy(alpha=.16f),startAngle=-90f,sweepAngle=Math.toDegrees(angle).toFloat(),useCenter=true,
                        topLeft=Offset(cx-r,cy-r),size=androidx.compose.ui.geometry.Size(2*r,2*r),style=androidx.compose.ui.graphics.drawscope.Fill)
                } else if(solution.values["chord"]!=null) {
                    val segment=Path().apply {
                        moveTo(p1.x,p1.y)
                        for(step in 1..96) {
                            val t=step/96.0; val a=start+angle*t
                            lineTo(cx+r*cos(a).toFloat(),cy+r*sin(a).toFloat())
                        }
                        close()
                    }
                    drawPath(segment,secondary.copy(alpha=if(variant==GeometryVariant.MAJOR_SEGMENT).12f else .2f))
                }
                drawArc(secondary,startAngle=-90f,sweepAngle=Math.toDegrees(angle).toFloat(),useCenter=solution.values["sectorArea"]!=null,
                    topLeft=Offset(cx-r,cy-r),size=androidx.compose.ui.geometry.Size(2*r,2*r),style=Stroke(4.dp.toPx(),cap=StrokeCap.Round))
                if(solution.values["sectorArea"]!=null) {
                    drawLine(shapeColor,Offset(cx,cy),p1,2.dp.toPx());drawLine(shapeColor,Offset(cx,cy),p2,2.dp.toPx())
                } else if(solution.values["chord"]!=null) {
                    drawLine(secondary,p1,p2,3.dp.toPx(),cap=StrokeCap.Round)
                    val chordMid=Offset((p1.x+p2.x)/2,(p1.y+p2.y)/2)
                    val arcMid=Offset(cx+r*cos(start+angle/2).toFloat(),cy+r*sin(start+angle/2).toFloat())
                    drawLine(surface,Offset(cx,cy),chordMid,1.dp.toPx())
                    drawLine(surface,chordMid,arcMid,1.dp.toPx())
                    drawLine(shapeColor,Offset(cx,cy),p2,2.dp.toPx())
                    val labelPaint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color=shapeColor.toArgb();textSize=13.sp.toPx();textAlign=android.graphics.Paint.Align.CENTER }
                    val canvas=drawContext.canvas.nativeCanvas
                    canvas.drawText("c",chordMid.x,chordMid.y+15.dp.toPx(),labelPaint)
                    canvas.drawText("h",(chordMid.x+arcMid.x)/2+8.dp.toPx(),(chordMid.y+arcMid.y)/2,labelPaint)
                }
                val markerRadius=r*.22f
                drawArc(shapeColor,startAngle=-90f,sweepAngle=Math.toDegrees(angle).toFloat(),useCenter=false,
                    topLeft=Offset(cx-markerRadius,cy-markerRadius),size=androidx.compose.ui.geometry.Size(2*markerRadius,2*markerRadius),style=Stroke(1.5.dp.toPx()))
                drawLine(shapeColor,Offset(cx,cy),p1,2.dp.toPx())
                if(solution.values["sectorArea"]!=null) drawLine(shapeColor,Offset(cx,cy),p2,2.dp.toPx())
                drawLine(shapeColor,Offset(cx,cy),Offset(cx,cy-r),2.dp.toPx())
                drawContext.canvas.nativeCanvas.drawText("θ",cx+markerRadius*.8f,cy-markerRadius*.8f,android.graphics.Paint(3).apply { color=shapeColor.toArgb();textSize=13.sp.toPx() })
            } else {
                drawLine(shapeColor,Offset(cx,cy),Offset(cx,cy-r),2.dp.toPx(),cap=StrokeCap.Round)
            }
            drawCircle(shapeColor,r,Offset(cx,cy),style=Stroke(3.dp.toPx()))
            drawCircle(shapeColor,3.dp.toPx(),Offset(cx,cy))
            drawContext.canvas.nativeCanvas.drawText("r",cx-12.dp.toPx(),cy-r/2,android.graphics.Paint(3).apply { color=shapeColor.toArgb(); textSize=14.sp.toPx() })
        } else {
            val a=solution.values["a"]?:1.0; val b=solution.values["b"]?:1.0
            val theta=parallelogramAngleRadians(solution.values["angle"]?:if (radians) Math.PI/2 else 90.0,radians)
            val lengthScale=max(a,b)
            val normalizedA=a/lengthScale;val normalizedB=b/lengthScale
            val spanX=normalizedA+abs(normalizedB*cos(theta)); val spanY=normalizedB*sin(theta)
            val s=min(size.width*.65f/spanX.toFloat(),size.height*.65f/spanY.toFloat())
            val minX=cx-spanX.toFloat()*s/2; val minY=cy-spanY.toFloat()*s/2
            val ax=minX+max(0.0,-normalizedB*cos(theta)).toFloat()*s; val ay=minY+spanY.toFloat()*s
            val p1=Offset(ax,ay);val p2=Offset(ax+normalizedA.toFloat()*s,ay)
            val p3=Offset(p2.x+(normalizedB*cos(theta)).toFloat()*s,p2.y-(normalizedB*sin(theta)).toFloat()*s)
            val p4=Offset(p1.x+(normalizedB*cos(theta)).toFloat()*s,p1.y-(normalizedB*sin(theta)).toFloat()*s)
            listOf(p1 to p2,p2 to p3,p3 to p4,p4 to p1).forEach { (x,y)->drawLine(shapeColor,x,y,2.5.dp.toPx()) }
            drawLine(surface.copy(alpha=.78f),p1,p3,1.15.dp.toPx())
            drawLine(surface.copy(alpha=.78f),p2,p4,1.15.dp.toPx())
            val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color=shapeColor.toArgb();textSize=13.sp.toPx();textAlign=android.graphics.Paint.Align.CENTER }
            val canvas=drawContext.canvas.nativeCanvas
            canvas.drawText("A",p1.x,p1.y+16.dp.toPx(),paint); canvas.drawText("B",p2.x,p2.y+16.dp.toPx(),paint)
            canvas.drawText("C",p3.x,p3.y-8.dp.toPx(),paint); canvas.drawText("D",p4.x,p4.y-8.dp.toPx(),paint)
            canvas.drawText("a = ${TriangleEngine.format(a)}",(p1.x+p2.x)/2,(p1.y+p2.y)/2+16.dp.toPx(),paint)
            canvas.drawText("b = ${TriangleEngine.format(b)}",(p1.x+p4.x)/2-20.dp.toPx(),(p1.y+p4.y)/2,paint)
            canvas.drawText("${TriangleEngine.format(solution.values["angle"] ?: if (radians) Math.PI/2 else 90.0)}${if (radians) " rad" else "°"}",p1.x+4.dp.toPx(),p1.y+34.dp.toPx(),paint)
            canvas.drawText("${TriangleEngine.format(solution.values["oppositeAngle"] ?: if (radians) Math.PI/2 else 90.0)}${if (radians) " rad" else "°"}",p2.x-4.dp.toPx(),p2.y+34.dp.toPx(),paint)
            val auxiliaryPaint=android.graphics.Paint(paint).apply { color=surface.toArgb();textSize=12.sp.toPx() }
            canvas.drawText("d₁",(p1.x+p3.x)/2+10.dp.toPx(),(p1.y+p3.y)/2-8.dp.toPx(),auxiliaryPaint)
            canvas.drawText("d₂",(p2.x+p4.x)/2-10.dp.toPx(),(p2.y+p4.y)/2-8.dp.toPx(),auxiliaryPaint)
        }
    }
}

/** Existing triangle illustration retained for the SSS and general triangle solver. */
@Composable
fun TriangleCanvas(result: TriangleEngine.Result, modifier: Modifier = Modifier, radians: Boolean = false) {
    val primary=MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val points=listOf(result.pointA,result.pointB,result.pointC)
        val minX=points.minOf{it.x};val maxX=points.maxOf{it.x};val minY=points.minOf{it.y};val maxY=points.maxOf{it.y}
        val padX=size.width*.08f;val padY=size.height*.15f
        val dimension=max(maxX-minX,maxY-minY)
        val normW=(maxX-minX)/dimension;val normH=(maxY-minY)/dimension
        val scale=min((size.width-2*padX)/normW.toFloat(),(size.height-2*padY)/normH.toFloat())
        val usedW=normW.toFloat()*scale;val usedH=normH.toFloat()*scale
        val ox=(size.width-usedW)/2;val oy=(size.height+usedH)/2
        fun pos(p:TriangleEngine.Point)=Offset(ox+((p.x-minX)/dimension).toFloat()*scale,oy-((p.y-minY)/dimension).toFloat()*scale)
        val a=pos(result.pointA);val b=pos(result.pointB);val c=pos(result.pointC)
        drawLine(primary,a,b,3.dp.toPx());drawLine(primary,b,c,3.dp.toPx());drawLine(primary,c,a,3.dp.toPx())
        listOf(a,b,c).forEach{drawCircle(primary,4.dp.toPx(),it)}
        val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color=primary.toArgb();textSize=13.sp.toPx();textAlign=android.graphics.Paint.Align.CENTER
            typeface=android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD)
        }
        val canvas=drawContext.canvas.nativeCanvas
        canvas.drawText("A",a.x,a.y-10.dp.toPx(),paint);canvas.drawText("B",b.x,b.y-10.dp.toPx(),paint);canvas.drawText("C",c.x,c.y+16.dp.toPx(),paint)
        canvas.drawText("c = ${TriangleEngine.format(result.c)}",(a.x+b.x)/2,a.y+14.dp.toPx(),paint)
        val centroidX=(a.x+b.x+c.x)/3f; val centroidY=(a.y+b.y+c.y)/3f
        fun outward(x: Float, y: Float): Offset {
            val dx=x-centroidX; val dy=y-centroidY; val length=hypot(dx,dy).coerceAtLeast(1f)
            return Offset(dx/length*13.dp.toPx(),dy/length*13.dp.toPx())
        }
        val sideA=outward((b.x+c.x)/2,(b.y+c.y)/2); val sideB=outward((a.x+c.x)/2,(a.y+c.y)/2)
        canvas.drawText("a = ${TriangleEngine.format(result.a)}",(b.x+c.x)/2+sideA.x,(b.y+c.y)/2+sideA.y,paint)
        canvas.drawText("b = ${TriangleEngine.format(result.b)}",(a.x+c.x)/2+sideB.x,(a.y+c.y)/2+sideB.y,paint)
        val anglePaint=android.graphics.Paint(paint).apply { color=android.graphics.Color.rgb(225,133,34);textSize=11.sp.toPx();textAlign=android.graphics.Paint.Align.LEFT }
        fun angleLabel(degrees: Double): String = if (!radians) "${TriangleEngine.formatCanvasAngle(degrees)}°" else if (kotlin.math.abs(degrees-90.0)<1e-9) "π/2" else if (kotlin.math.abs(degrees-60.0)<1e-9) "π/3" else "${TriangleEngine.formatCanvasAngle(Math.toRadians(degrees))} rad"
        val angleRowY=a.y+14.dp.toPx()
        val aPrefix=anglePaint.measureText("A =")/2f
        val bPrefix=anglePaint.measureText("B =")/2f
        canvas.drawText("A = ${angleLabel(result.angleA)}",a.x-aPrefix,angleRowY,anglePaint)
        canvas.drawText("B = ${angleLabel(result.angleB)}",b.x-bPrefix,angleRowY,anglePaint)
        anglePaint.textAlign=android.graphics.Paint.Align.CENTER
        canvas.drawText("C = ${angleLabel(result.angleC)}",c.x,c.y-16.dp.toPx(),anglePaint)
    }
}
