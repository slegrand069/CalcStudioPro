package com.example.calcstudiopro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.calcstudiopro.domain.AngleUnit
import com.example.calcstudiopro.domain.CalculatorEngine
import com.example.calcstudiopro.ui.theme.CalcStudioProTheme
import java.math.BigDecimal

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            CalcStudioProTheme {
                CalculatorScreen()
            }
        }
    }
}

@Composable
fun CalculatorScreen() {
    val colors = MaterialTheme.colorScheme
    val engine = remember { CalculatorEngine() }
    var expression by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var completedResult by remember { mutableStateOf<Double?>(null) }
    var isShiftActive by remember { mutableStateOf(false) }
    var isRadians by remember { mutableStateOf(false) }
    var isScientific by remember { mutableStateOf(false) }
    var memoryValue by remember { mutableDoubleStateOf(0.0) }

    val angleUnit = if (isRadians) AngleUnit.RADIANS else AngleUnit.DEGREES

    val result = remember(expression, angleUnit) {
        if (expression.isBlank()) null
        else runCatching {
            engine.evaluate(expression, angleUnit)
        }.getOrNull()
    }


    fun formatExpressionNumber(number: Double): String {
        return BigDecimal.valueOf(number)
            .setScale(10, java.math.RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()
    }
    fun toggleSign() {
        if (expression.isEmpty()) {
            expression = "−"
            return
        }

        // Cette fonction agit sur le dernier nombre saisi.
        val end = expression.length
        var start = end

        while (start > 0 &&
            (expression[start - 1].isDigit() || expression[start - 1] == '.')
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


    fun endsWithOperand(value: String): Boolean =
        value.lastOrNull()?.let { character ->
            character.isDigit() || character in listOf(')', 'π', 'e', '%', '!')
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
        completedResult?.let { previousResult ->
            if (key != "=") {
                val continuesFromResult = key in setOf("+", "−", "×", "÷", "1/x", "x!") ||
                    (key == "x²" && !isShiftActive)
                expression = if (continuesFromResult) {
                    formatExpressionNumber(previousResult)
                } else {
                    ""
                }
                completedResult = null
            }
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
                    appendExpressionToken(formatExpressionNumber(memoryValue))
                    isShiftActive = false
                } else {
                    // STO : mémoriser le résultat courant
                    if (expression.isNotBlank() && result != null) {
                        memoryValue = result
                    }
                }
            }

            "CLM" -> {
                // Effacer la mémoire
                memoryValue = 0.0
            }

            "Shift" -> {
                isShiftActive = !isShiftActive
            }

            "STO/RCL", "CLM", "MODE" -> Unit

            "±" -> toggleSign()

            "%" -> {
                val last = expression.lastOrNull()
                if (last?.isDigit() == true || last == ')' || last == '%') {
                    expression += "%"
                }
            }


            "x²" -> {
                if (isShiftActive) {
                    appendExpressionToken("sqrt(")
                    isShiftActive = false
                } else {
                    val last = expression.lastOrNull()

                    if (last?.isDigit() == true || last == ')' || last == '%') {
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
                    if (last?.isDigit() == true || last == ')' || last == '%') {
                        expression += "^"
                    }
                }
            }

            "Sin", "Cos", "Tan" -> {
                val function = when (key) {
                    "Sin" -> if (isShiftActive) "asin" else "sin"
                    "Cos" -> if (isShiftActive) "acos" else "cos"
                    else -> if (isShiftActive) "atan" else "tan"
                }
                appendExpressionToken("$function(")
                if (isShiftActive) isShiftActive = false
            }

            "ln", "Log" -> {
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
                        it.isDigit() || it == ')' || it == '%' || it == '!'
                    } == true) {
                    expression += "!"
                }
            }

            "1/x" -> {
                if (expression.isNotBlank()) {
                    expression = "1/($expression)"
                } else {
                    expression = "1/("
                }
            }

            "π" -> {
                appendExpressionToken(if (isShiftActive) "e" else "π")
                if (isShiftActive) isShiftActive = false
            }

            "=" -> {

                if (expression.isNotBlank()) {
                    try {
                        completedResult = engine.evaluate(expression, angleUnit)
                        errorMessage = null

                    } catch (e: IllegalArgumentException) {
                        completedResult = null
                        errorMessage = when (e.message) {
                            "Division par zéro" -> "Division par zéro impossible"
                            else -> "Syntaxe incorrecte"
                        }
                    } catch (e: Exception) {
                        completedResult = null
                        errorMessage = "Erreur de calcul"
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
                } else if (key == ".") {
                    if (isShiftActive) {
                        if (canInsertPowArgumentSeparator(expression)) {
                            expression += ","
                            isShiftActive = false
                        }
                    } else {
                        val currentNumber = expression
                            .takeLastWhile { it.isDigit() || it == '.' }
                        if (!currentNumber.contains('.')) {
                            expression += if (currentNumber.isEmpty()) "0." else "."
                        }
                    }
                } else {
                    expression += key
                }
            }
        }
    }


    val keys = listOf(
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

                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Paramètres",
                        tint = colors.onBackground
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                            .clickable { isRadians = !isRadians }
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
                            .clickable { isScientific = !isScientific }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
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
                    val resultText = result?.let {
                        formatResult(it)
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
                        modifier = Modifier.testTag("calculator-result"),
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
                            val isEquals = key == "="
                            val isOperator = key in listOf(
                                "÷", "×", "−", "+"
                            )
                            val isFunction = rowIndex < 4 || key == "⌫"
                            val isShift = key == "Shift"

                            val secondaryLabel = when (key) {
                                "ln" -> "eˣ"
                                "Log" -> "10ˣ"
                                "π" -> "e"
                                "Sin" -> "sin⁻¹"
                                "Cos" -> "cos⁻¹"
                                "Tan" -> "tan⁻¹"
                                "." -> ","
                                "x²" -> "√"
                                "xʸ" -> "ʸ√x"
                                else -> null
                            }

                            val backgroundColor = when {
                                isEquals -> colors.primary
                                isShift && isShiftActive ->
                                    colors.primaryContainer

                                isFunction || isOperator ->
                                    colors.surfaceVariant

                                else -> colors.surface
                            }

                            val textColor = when {
                                isEquals -> colors.onPrimary
                                isShift && isShiftActive ->
                                    colors.onPrimaryContainer

                                isFunction || isOperator ->
                                    colors.primary

                                else -> colors.onSurface
                            }

                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(59.dp)
                                    .clickable { onKey(key) }
                                    .testTag(if (isShift) {
                                        "calculator-shift-${if (isShiftActive) "active" else "inactive"}"
                                    } else {
                                        "calculator-key-$key"
                                    }),
                                shape = RoundedCornerShape(22.dp),
                                color = backgroundColor,
                                shadowElevation =
                                    if (isEquals) 3.dp else 0.dp
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (secondaryLabel != null) {
                                        Column(
                                            horizontalAlignment =
                                                Alignment.CenterHorizontally,
                                            verticalArrangement =
                                                Arrangement.Center
                                        ) {
                                            Text(
                                                text = secondaryLabel,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = textColor.copy(
                                                    alpha = 0.75f
                                                )
                                            )

                                            Text(
                                                text = key,
                                                fontSize =
                                                    if (key.length > 1) 16.sp
                                                    else 22.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = textColor
                                            )
                                        }
                                    } else {
                                        Text(
                                            text = key,
                                            fontSize =
                                                if (key.length > 1) 16.sp
                                                else 22.sp,
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
