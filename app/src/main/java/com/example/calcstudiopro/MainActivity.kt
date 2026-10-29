package com.example.calcstudiopro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.calcstudiopro.domain.AngleUnit
import com.example.calcstudiopro.domain.CalculatorEngine
import com.example.calcstudiopro.domain.NumberBase
import com.example.calcstudiopro.domain.ProgrammerCalculatorEngine
import com.example.calcstudiopro.domain.ProgrammerExpressionConverter
import com.example.calcstudiopro.domain.ApproximationResult
import com.example.calcstudiopro.domain.ContinuedFractionApproximator
import com.example.calcstudiopro.ui.theme.CalcStudioProTheme
import com.example.calcstudiopro.settings.CalculatorNotation
import com.example.calcstudiopro.settings.ContinuedFractionSettings
import com.example.calcstudiopro.settings.DataStoreSettingsRepository
import com.example.calcstudiopro.settings.ResultPresentation
import com.example.calcstudiopro.settings.SettingsRoute
import com.example.calcstudiopro.settings.SettingsViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import java.math.BigDecimal

private val HEX_LETTER_LABELS = mapOf(
    "1" to "A",
    "2" to "B",
    "3" to "C",
    "4" to "D",
    "5" to "E",
    "6" to "F"
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            CalcStudioProTheme {
                val navController = rememberNavController()
                val settingsRepository = remember {
                    DataStoreSettingsRepository(applicationContext)
                }
                val settingsViewModel: SettingsViewModel = viewModel(
                    factory = remember { SettingsViewModel.Factory(settingsRepository) }
                )
                val appSettings by settingsViewModel.state.collectAsState()
                NavHost(navController = navController, startDestination = "calculator") {
                    composable("calculator") {
                        CalculatorScreen(
                            onOpenSettings = { navController.navigate("settings") },
                            initialAngleUnit = appSettings.settings.angleUnit,
                            initialNotation = appSettings.settings.notation,
                            continuedFractionSettings = appSettings.settings.continuedFractions,
                            resultPresentation = appSettings.settings.continuedFractions.resultPresentation,
                            settingsLoaded = !appSettings.isLoading,
                            onAngleUnitChange = settingsViewModel::updateAngleUnit,
                            onNotationChange = settingsViewModel::updateNotation
                        )
                    }
                    composable("settings") {
                        SettingsRoute(settingsViewModel) { navController.popBackStack() }
                    }
                }
            }
        }
    }
}

@Composable
fun CalculatorScreen(
    onOpenSettings: () -> Unit = {},
    initialAngleUnit: AngleUnit = AngleUnit.DEGREES,
    initialNotation: CalculatorNotation = CalculatorNotation.STANDARD,
    continuedFractionSettings: ContinuedFractionSettings = ContinuedFractionSettings(),
    resultPresentation: ResultPresentation = ResultPresentation.FRACTION,
    settingsLoaded: Boolean = true,
    onAngleUnitChange: (AngleUnit) -> Unit = {},
    onNotationChange: (CalculatorNotation) -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme
    val engine = remember { CalculatorEngine() }
    val programmerEngine = remember { ProgrammerCalculatorEngine() }
    val expressionConverter = remember { ProgrammerExpressionConverter() }
    val continuedFractionApproximator = remember { ContinuedFractionApproximator() }
    var expression by rememberSaveable { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var numberBase by remember { mutableStateOf(NumberBase.DEC) }
    var completedDecimalResult by remember { mutableStateOf<Double?>(null) }
    var completedIntegerResult by remember { mutableStateOf<Long?>(null) }
    var decimalResultOverride by remember { mutableStateOf<Double?>(null) }
    var integerResultOverride by remember { mutableStateOf<Long?>(null) }
    var displayedFraction by remember { mutableStateOf<ApproximationResult.Success?>(null) }
    var isShiftActive by remember { mutableStateOf(false) }
    var isRadians by rememberSaveable { mutableStateOf(initialAngleUnit == AngleUnit.RADIANS) }
    var isScientific by rememberSaveable { mutableStateOf(initialNotation == CalculatorNotation.SCIENTIFIC) }
    var memoryValue by remember { mutableDoubleStateOf(0.0) }
    var memoryIntegerValue by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(initialAngleUnit, initialNotation) {
        isRadians = initialAngleUnit == AngleUnit.RADIANS
        isScientific = initialNotation == CalculatorNotation.SCIENTIFIC
    }

    LaunchedEffect(resultPresentation) {
        if (resultPresentation == ResultPresentation.DECIMAL) displayedFraction = null
    }

    val angleUnit = if (isRadians) AngleUnit.RADIANS else AngleUnit.DEGREES

    val decimalResult = remember(expression, angleUnit, numberBase) {
        if (numberBase != NumberBase.DEC || expression.isBlank()) null
        else runCatching {
            engine.evaluate(expression, angleUnit)
        }.getOrNull()
    }
    val integerResult = remember(expression, numberBase) {
        if (numberBase == NumberBase.DEC || expression.isBlank()) null
        else runCatching {
            programmerEngine.evaluate(expression, numberBase)
        }.getOrNull()
    }


    fun formatExpressionNumber(number: Double): String {
        return BigDecimal.valueOf(number)
            .setScale(10, java.math.RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()
    }

    fun decimalToInteger(number: Double): Long = try {
        BigDecimal.valueOf(number).toBigInteger().longValueExact()
    } catch (_: ArithmeticException) {
        throw ArithmeticException("Overflow")
    }

    fun currentDisplayedDecimalResult(): Double? = decimalResultOverride ?: decimalResult

    fun currentDisplayedIntegerResult(): Long? = integerResultOverride ?: integerResult

    fun cycleNumberBase() {
        displayedFraction = null
        val nextBase = when (numberBase) {
            NumberBase.DEC -> NumberBase.BIN
            NumberBase.BIN -> NumberBase.OCT
            NumberBase.OCT -> NumberBase.HEX
            NumberBase.HEX -> NumberBase.DEC
        }
        val wasCompleted = completedDecimalResult != null || completedIntegerResult != null

        try {
            val convertedExpression = expressionConverter.convert(expression, numberBase, nextBase)
            val sourceDecimal = if (numberBase == NumberBase.DEC) currentDisplayedDecimalResult() else null
            val sourceInteger = if (numberBase == NumberBase.DEC) null else currentDisplayedIntegerResult()
            val convertedInteger = when {
                nextBase == NumberBase.DEC -> null
                sourceInteger != null -> sourceInteger
                sourceDecimal != null -> decimalToInteger(sourceDecimal)
                else -> null
            }
            val convertedDecimal = when {
                nextBase != NumberBase.DEC -> null
                sourceInteger != null -> sourceInteger.toDouble()
                else -> null
            }

            expression = convertedExpression
            numberBase = nextBase
            integerResultOverride = convertedInteger
            decimalResultOverride = convertedDecimal
            completedIntegerResult = if (wasCompleted && nextBase != NumberBase.DEC) convertedInteger else null
            completedDecimalResult = if (wasCompleted && nextBase == NumberBase.DEC) convertedDecimal else null
            errorMessage = null
        } catch (error: ArithmeticException) {
            errorMessage = if (error.message == "Overflow") "Overflow" else "Erreur de calcul"
        } catch (_: IllegalArgumentException) {
            errorMessage = "Syntaxe incorrecte"
        }
    }

    fun toggleSign() {
        if (expression.isEmpty()) {
            expression = "−"
            return
        }

        // Cette fonction agit sur le dernier nombre saisi.
        val end = expression.length
        var start = end

        while (start > 0 && (numberBase.accepts(expression[start - 1].uppercaseChar()) ||
                (numberBase == NumberBase.DEC && expression[start - 1] == '.'))
        ) {
            start--
        }

        if (start == end) return

        val signIndex = start - 1
        val previousIndex = signIndex - 1
        val hasUnaryMinus = signIndex >= 0 &&
                expression[signIndex] == '−' &&
                (previousIndex < 0 ||
                        expression[previousIndex] in listOf('+', '−', '×', '÷', '('))

        expression = if (hasUnaryMinus) {
            expression.removeRange(signIndex, signIndex + 1)
        } else {
            expression.substring(0, start) + "−" + expression.substring(start)
        }
    }


    fun endsWithOperand(value: String): Boolean = value.lastOrNull()?.let { character ->
        numberBase.accepts(character.uppercaseChar()) ||
            (numberBase == NumberBase.DEC && character in listOf('π', 'e', '%')) ||
            character in listOf(')', '!')
    } == true

    fun appendExpressionToken(token: String) {
        if (endsWithOperand(expression)) {
            expression += "×"
        }
        expression += token
    }

    fun canInsertPowArgumentSeparator(value: String): Boolean {
        data class FunctionFrame(val name: String?, var hasSeparator: Boolean = false)

        val frames = mutableListOf<FunctionFrame>()
        value.forEachIndexed { index, character ->
            when (character) {
                '(' -> {
                    var nameStart = index
                    while (nameStart > 0 && value[nameStart - 1].isLetter()) {
                        nameStart--
                    }
                    val name = value.substring(nameStart, index)
                        .takeIf { it.isNotEmpty() }
                        ?.lowercase()
                    frames += FunctionFrame(name)
                }

                ')' -> if (frames.isNotEmpty()) frames.removeAt(frames.lastIndex)
                ',' -> frames.lastOrNull()?.hasSeparator = true
            }
        }

        val activeFunction = frames.lastOrNull() ?: return false
        return activeFunction.name == "pow" &&
            !activeFunction.hasSeparator && endsWithOperand(value)
    }

    fun onKey(key: String) {
        val isDisplayShift = key == "LSh" || key == "RSh"
        if (key != "=" && key != "Shift") displayedFraction = null
        if (!isDisplayShift && key != "=" && key != "Shift") {
            val previousDecimal = completedDecimalResult
            val previousInteger = completedIntegerResult
            if (previousDecimal != null || previousInteger != null) {
                val continuesFromResult = key in setOf("+", "−", "×", "÷", "1/x", "x!") ||
                    (key == "x²" && !isShiftActive)
                expression = if (!continuesFromResult) {
                    ""
                } else if (numberBase == NumberBase.DEC && previousDecimal != null) {
                    formatExpressionNumber(previousDecimal)
                } else {
                    formatProgrammerResult(previousInteger ?: 0L, numberBase)
                }
                completedDecimalResult = null
                completedIntegerResult = null
            }
            decimalResultOverride = null
            integerResultOverride = null
        }

        if (key != "=") {
            errorMessage = null
        }
        when (key) {
            "AC" -> {
                expression = ""
                isShiftActive = false
            }

            "⌫" -> {
                if (expression.isNotEmpty()) {
                    expression = expression.dropLast(1)
                }
            }

            "STO/RCL" -> {
                if (isShiftActive) {
                    // RCL : rappeler la mémoire dans l'expression
                    val recalledValue = if (numberBase == NumberBase.DEC) {
                        formatExpressionNumber(memoryValue)
                    } else {
                        formatProgrammerResult(memoryIntegerValue ?: memoryValue.toLong(), numberBase)
                    }
                    appendExpressionToken(recalledValue)
                    isShiftActive = false
                } else {
                    // STO : mémoriser le résultat courant
                    if (numberBase == NumberBase.DEC && expression.isNotBlank() && decimalResult != null) {
                        memoryValue = decimalResult
                        memoryIntegerValue = null
                        completedDecimalResult = decimalResult
                        completedIntegerResult = null
                    } else if (numberBase != NumberBase.DEC && expression.isNotBlank() && integerResult != null) {
                        memoryIntegerValue = integerResult
                        memoryValue = integerResult.toDouble()
                        completedIntegerResult = integerResult
                        completedDecimalResult = null
                    }
                }
            }

            "CLM" -> {
                // Effacer la mémoire
                memoryValue = 0.0
                memoryIntegerValue = null
            }

            "Shift" -> {
                isShiftActive = !isShiftActive
            }

            "STO/RCL", "CLM", "MODE" -> Unit

            "±" -> toggleSign()

            "%" -> {
                if (numberBase != NumberBase.DEC) return
                val last = expression.lastOrNull()
                if (last?.isDigit() == true || last == ')' || last == '%') {
                    expression += "%"
                }
            }


            "x²" -> {
                if (isShiftActive) {
                    if (numberBase == NumberBase.DEC) {
                        appendExpressionToken("sqrt(")
                        isShiftActive = false
                    }
                } else {
                    val last = expression.lastOrNull()

                    if (last?.let { numberBase.accepts(it.uppercaseChar()) } == true || last == ')' || last == '%') {
                        expression += "^2"
                    }
                }
            }

            "xʸ" -> {
                if (isShiftActive) {
                    appendExpressionToken("pow(")
                    isShiftActive = false
                } else {
                    val last = expression.lastOrNull()
                    if (last?.let { numberBase.accepts(it.uppercaseChar()) } == true || last == ')' || last == '%') {
                        expression += "^"
                    }
                }
            }

            "Sin", "Cos", "Tan" -> {
                if (numberBase != NumberBase.DEC) return
                val function = when (key) {
                    "Sin" -> if (isShiftActive) "asin" else "sin"
                    "Cos" -> if (isShiftActive) "acos" else "cos"
                    else -> if (isShiftActive) "atan" else "tan"
                }
                appendExpressionToken("$function(")
                if (isShiftActive) isShiftActive = false
            }

            "ln", "Log" -> {
                if (numberBase != NumberBase.DEC) return
                val function = when {
                    !isShiftActive && key == "ln" -> "ln("
                    !isShiftActive && key == "Log" -> "log("
                    isShiftActive && key == "ln" -> "exp("
                    else -> "pow(10,"
                }
                appendExpressionToken(function)
                if (isShiftActive) isShiftActive = false
            }
            "x!" -> {
                if (expression.lastOrNull()?.let {
                        numberBase.accepts(it.uppercaseChar()) || it == ')' || it == '%' || it == '!'
                    } == true) {
                    expression += "!"
                }
            }

            "1/x" -> {
                if (numberBase != NumberBase.DEC) return
                if (expression.isNotBlank()) {
                    expression = "1/($expression)"
                } else {
                    expression = "1/("
                }
            }

            "π" -> {
                if (numberBase != NumberBase.DEC) return
                appendExpressionToken(if (isShiftActive) "e" else "π")
                if (isShiftActive) isShiftActive = false
            }

            "=" -> {
                if (isShiftActive && numberBase == NumberBase.DEC) {
                    if (!settingsLoaded) return
                    if (resultPresentation == ResultPresentation.DECIMAL) {
                        displayedFraction = null
                        isShiftActive = false
                        errorMessage = null
                        return
                    }

                    if (displayedFraction != null) {
                        displayedFraction = null
                        isShiftActive = false
                        errorMessage = null
                        return
                    }

                    val numericResult = currentDisplayedDecimalResult()
                        ?.takeIf { it.isFinite() }
                        ?: return
                    when (val approximation = continuedFractionApproximator.approximate(
                        numericResult,
                        continuedFractionSettings
                    )) {
                        is ApproximationResult.Success -> {
                            displayedFraction = approximation
                            errorMessage = null
                        }

                        is ApproximationResult.Failure -> {
                            displayedFraction = null
                            errorMessage = "Aucune fraction conforme aux limites"
                        }
                    }
                    isShiftActive = false
                    return
                }

                displayedFraction = null
                decimalResultOverride = null
                integerResultOverride = null

                if (expression.isNotBlank()) {
                    try {
                        if (numberBase == NumberBase.DEC) {
                            completedDecimalResult = engine.evaluate(expression, angleUnit)
                            completedIntegerResult = null
                        } else {
                            completedIntegerResult = programmerEngine.evaluate(expression, numberBase)
                            completedDecimalResult = null
                        }
                        errorMessage = null

                    } catch (e: ArithmeticException) {
                        completedDecimalResult = null
                        completedIntegerResult = null
                        errorMessage = if (e.message == "Overflow") "Overflow" else "Erreur de calcul"
                    } catch (e: IllegalArgumentException) {
                        completedDecimalResult = null
                        completedIntegerResult = null
                        errorMessage = when (e.message) {
                            "Division par zéro" -> "Division par zéro impossible"
                            else -> "Syntaxe incorrecte"
                        }
                    } catch (e: Exception) {
                        completedDecimalResult = null
                        completedIntegerResult = null
                        errorMessage = "Erreur de calcul"
                    }
                }
            }

            "AND", "OR", "XOR" -> {
                if (numberBase != NumberBase.DEC && endsWithOperand(expression)) {
                    expression += " $key "
                }
            }

            "NOT" -> {
                if (numberBase != NumberBase.DEC) {
                    expression = if (endsWithOperand(expression)) {
                        "NOT ($expression)"
                    } else {
                        expression + "NOT "
                    }
                }
            }

            "LSh", "RSh" -> {
                if (numberBase != NumberBase.DEC) {
                    val currentResult = currentDisplayedIntegerResult()
                    if (currentResult != null) {
                        try {
                            val shifted = if (key == "LSh") {
                                programmerEngine.shiftLeft(currentResult)
                            } else {
                                programmerEngine.shiftRight(currentResult)
                            }
                            integerResultOverride = shifted
                            if (completedIntegerResult != null) completedIntegerResult = shifted
                            errorMessage = null
                        } catch (error: ArithmeticException) {
                            errorMessage = if (error.message == "Overflow") "Overflow" else "Erreur de calcul"
                        }
                    }
                }
            }

            else -> {
                val operators = listOf("+", "−", "×", "÷")
                val last = expression.lastOrNull()?.toString()

                if (key in operators) {
                    if (expression.isEmpty()) {
                        if (key == "−") expression = "−"
                    } else if (last in operators) {
                        expression = expression.dropLast(1) + key
                    } else if (last == "(") {
                        if (key == "−") expression += key
                    } else {
                        expression += key
                    }
                } else if (key == "(" || key == ")") {
                    expression += key
                } else if (key == ".") {
                    if (isShiftActive) {
                        if (canInsertPowArgumentSeparator(expression)) {
                            expression += ","
                            isShiftActive = false
                        }
                    } else if (numberBase == NumberBase.DEC) {
                        val currentNumber = expression
                            .takeLastWhile { it.isDigit() || it == '.' }
                        if (!currentNumber.contains('.')) {
                            expression += if (currentNumber.isEmpty()) "0." else "."
                        }
                    }
                } else {
                    val shiftedHexDigit = if (numberBase == NumberBase.HEX && isShiftActive) {
                        ('1'..'6').indexOf(key.singleOrNull() ?: '\u0000').takeIf { it >= 0 }
                            ?.let { ('A'.code + it).toChar().toString() }
                    } else {
                        null
                    }
                    if (shiftedHexDigit != null) {
                        expression += shiftedHexDigit
                        isShiftActive = false
                    } else if (key.length == 1 && numberBase != NumberBase.DEC &&
                        !numberBase.accepts(key[0].uppercaseChar())
                    ) {
                        return
                    } else {
                        expression += key
                    }
                }
            }
        }
    }


    val decimalKeys = listOf(
        listOf("Shift", "STO/RCL", "CLM", "MODE"),
        listOf("AC", "ln", "Log", "x!"),
        listOf("π", "Sin", "Cos", "Tan"),
        listOf("1/x", "x²", "xʸ", "%"),
        listOf("⌫", "(", ")", "÷"),
        listOf("7", "8", "9", "×"),
        listOf("4", "5", "6", "−"),
        listOf("1", "2", "3", "+"),
        listOf("±", "0", ".", "=")
    )
    val programmerKeys = listOf(
        listOf("Shift", "STO/RCL", "CLM", "MODE"),
        listOf("AC", "ln", "Log", "x!"),
        listOf("AND", "OR", "XOR", "NOT"),
        listOf("1/x", "x²", "xʸ", "%"),
        listOf("⌫", "(", ")", "÷"),
        listOf("7", "8", "9", "×"),
        listOf("4", "5", "6", "−"),
        listOf("1", "2", "3", "+"),
        listOf("±", "0", ".", "=")
    )
    val keys = if (numberBase == NumberBase.DEC) decimalKeys else programmerKeys


    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = colors.background
    ) { innerPadding ->

        // Conteneur principal
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // EN-TÊTE
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "CalcStudio",
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.onBackground
                    )

                    IconButton(onClick = onOpenSettings, modifier = Modifier.testTag("calculator-settings")) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Paramètres",
                            tint = colors.onBackground
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (numberBase == NumberBase.DEC) {
                        Text(
                            text = if (isRadians) "RAD" else "DEG",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.primary,
                            modifier = Modifier
                                .background(
                                    colors.primary.copy(alpha = 0.12f),
                                    RoundedCornerShape(4.dp)
                                )
                                .clickable {
                                    isRadians = !isRadians
                                    onAngleUnitChange(if (isRadians) AngleUnit.RADIANS else AngleUnit.DEGREES)
                                }
                                .testTag("calculator-angle-toggle")
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        )

                        Text(
                            text = if (isScientific) "SCI" else "NRM",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.primary,
                            modifier = Modifier
                                .background(
                                    colors.primary.copy(alpha = 0.12f),
                                    RoundedCornerShape(4.dp)
                                )
                                .clickable {
                                    isScientific = !isScientific
                                    onNotationChange(
                                        if (isScientific) CalculatorNotation.SCIENTIFIC else CalculatorNotation.STANDARD
                                    )
                                }
                                .testTag("calculator-number-format-toggle")
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                    }

                    Text(
                        text = numberBase.label,
                        modifier = Modifier
                            .background(
                                colors.primary.copy(alpha = 0.12f),
                                RoundedCornerShape(4.dp)
                            )
                            .clickable { cycleNumberBase() }
                            .testTag("calculator-base-toggle")
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.primary
                    )
                }
            }

            // AFFICHAGE DE L'EXPRESSION ET DU RÉSULTAT
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Bottom
            ) {
                Text(
                    text = expression.ifEmpty { "0" },
                    modifier = Modifier.testTag("calculator-expression"),
                    fontSize = 22.sp,
                    color = colors.onSurfaceVariant,
                    maxLines = 2
                )

                Spacer(modifier = Modifier.height(12.dp))

                BoxWithConstraints(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    val resultText = if (numberBase == NumberBase.DEC) {
                        displayedFraction
                            ?.takeIf { resultPresentation == ResultPresentation.FRACTION }
                            ?.let(::formatFraction)
                            ?: currentDisplayedDecimalResult()?.let {
                                if (isScientific) formatEngineeringResult(it) else formatResult(it)
                            }
                    } else {
                        currentDisplayedIntegerResult()?.let { formatProgrammerResultForDisplay(it, numberBase) }
                    } ?: ""

                    val availableWidth = maxWidth.value
                    val calculatedSize = if (resultText.isEmpty()) {
                        32f
                    } else {
                        (availableWidth / (resultText.length * 0.62f))
                            .coerceIn(15f, 32f)
                    }

                    Text(
                        text = resultText,
                        modifier = Modifier
                            .testTag("calculator-result")
                            .then(if (numberBase == NumberBase.DEC) Modifier else Modifier.horizontalScroll(rememberScrollState())),
                        fontSize = calculatedSize.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onBackground,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip
                    )
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        modifier = Modifier.testTag("calculator-error"),
                        fontSize = 16.sp,
                        color = colors.error,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
            }

            // CLAVIER
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                keys.forEachIndexed { rowIndex, row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        row.forEachIndexed { colIndex, key ->
                            run {
                                val displayKey = when {
                                    numberBase != NumberBase.DEC && key == "Log" -> "LSh"
                                    key == "STO/RCL" -> "STO"
                                    else -> key
                                }
                                val isEquals = key == "="
                                val isOperator = displayKey in listOf(
                                    "÷", "×", "−", "+", "AND", "OR", "XOR", "NOT", "LSh", "RSh"
                                )
                                val isFunction = rowIndex < 4 || key == "⌫"
                                val isShift = key == "Shift"
                                val isNumber = key.length == 1 && key[0].isDigit()
                                val isEnabled = when {
                                    numberBase != NumberBase.DEC && key in listOf("1/x", "%") -> false
                                    numberBase != NumberBase.DEC && key == "x²" && isShiftActive -> false
                                    numberBase != NumberBase.DEC && key == "ln" -> false
                                    numberBase != NumberBase.DEC && key == "." ->
                                        isShiftActive && canInsertPowArgumentSeparator(expression)
                                    numberBase != NumberBase.DEC && isNumber -> numberBase.accepts(key[0])
                                    else -> true
                                }
                                val secondaryLabel = when {
                                    key == "STO/RCL" -> "RCL"
                                    numberBase == NumberBase.HEX && key in HEX_LETTER_LABELS -> HEX_LETTER_LABELS[key]
                                    numberBase != NumberBase.DEC && key == "Log" -> "RSh"
                                    key == "ln" -> "eˣ"
                                    key == "Log" -> "10ˣ"
                                    key == "π" -> "e"
                                    key == "Sin" -> "sin⁻¹"
                                    key == "Cos" -> "cos⁻¹"
                                    key == "Tan" -> "tan⁻¹"
                                    key == "." -> ","
                                    key == "x²" -> "√"
                                    key == "xʸ" && numberBase != NumberBase.DEC -> "pow"
                                    key == "xʸ" -> "pow"
                                    key == "=" -> "≈"
                                    else -> null
                                }
                                val backgroundColor = when {
                                    isEquals -> colors.primary
                                    isShift && isShiftActive -> colors.primaryContainer
                                    !isEnabled -> colors.surfaceVariant.copy(alpha = 0.45f)
                                    isFunction || isOperator -> colors.surfaceVariant
                                    else -> colors.surface
                                }
                                val textColor = when {
                                    isEquals -> colors.onPrimary
                                    isShift && isShiftActive -> colors.onPrimaryContainer
                                    !isEnabled -> colors.onSurface.copy(alpha = 0.38f)
                                    isFunction || isOperator -> colors.primary
                                    else -> colors.onSurface
                                }
                                val actionKey = if (numberBase != NumberBase.DEC && key == "Log" && isShiftActive) {
                                    "RSh"
                                } else if (key == "STO/RCL") {
                                    "STO/RCL"
                                } else {
                                    displayKey
                                }

                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(59.dp)
                                        .clickable(enabled = isEnabled) { onKey(actionKey) }
                                        .testTag(if (isShift) {
                                            "calculator-shift-${if (isShiftActive) "active" else "inactive"}"
                                        } else {
                                            "calculator-key-${if (key == "STO/RCL") key else displayKey}"
                                        }),
                                    shape = RoundedCornerShape(22.dp),
                                    color = backgroundColor,
                                    shadowElevation = if (isEquals) 3.dp else 0.dp
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        if (secondaryLabel != null) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                Text(
                                                    text = secondaryLabel,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = textColor.copy(alpha = 0.75f)
                                                )
                                                Text(
                                                    text = displayKey,
                                                    fontSize = if (displayKey.length > 1) 16.sp else 22.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = textColor
                                                )
                                            }
                                        } else {
                                            Text(
                                                text = displayKey,
                                                fontSize = if (displayKey.length > 1) 16.sp else 22.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = textColor
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
