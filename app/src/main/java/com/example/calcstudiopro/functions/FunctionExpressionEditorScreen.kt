package com.example.calcstudiopro.functions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.calcstudiopro.domain.functions.MathExpressionError
import com.example.calcstudiopro.functions.domain.FunctionSlot
import com.example.calcstudiopro.functions.domain.FunctionSlotId
import com.example.calcstudiopro.functions.domain.FunctionStudy
import com.example.calcstudiopro.functions.domain.StudyValidationResult
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Popup
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.awaitCancellation

private enum class PaletteKind { LETTERS, ADVANCED }
private data class EditorSnapshot(val text: String, val selection: TextRange)

@Composable
fun FunctionExpressionEditorRoute(
    viewModel: FunctionStudyViewModel,
    slotId: FunctionSlotId,
    onBack: () -> Unit,
    onValidated: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val slot = state.study?.slot(slotId)
    if (slot == null) {
        FunctionEditorUnavailable(state.loadError ?: "Cette fonction n’est pas disponible.", onBack)
        return
    }
    FunctionExpressionEditorScreen(
        slot = slot,
        study = state.study!!,
        saveError = state.saveError,
        onDraftChange = { viewModel.updateDraft(slotId, it) },
        onValidate = { viewModel.validateDraft(slotId) },
        onBack = onBack,
        onValidated = onValidated
    )
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
fun FunctionExpressionEditorScreen(
    slot: FunctionSlot,
    study: FunctionStudy,
    saveError: String? = null,
    onDraftChange: (String) -> Unit,
    onValidate: () -> StudyValidationResult?,
    onBack: () -> Unit,
    onValidated: () -> Unit
) {
    val darkTheme = isSystemInDarkTheme()
    val editorState = rememberTextFieldState(initialText = slot.draft)
    val editorText = editorState.text.toString()
    val editorSelection = editorState.selection
    var undoStack by remember(slot.id) { mutableStateOf(emptyList<EditorSnapshot>()) }
    var redoStack by remember(slot.id) { mutableStateOf(emptyList<EditorSnapshot>()) }
    var shiftEnabled by remember(slot.id) { mutableStateOf(false) }
    var palette by remember(slot.id) { mutableStateOf<PaletteKind?>(null) }
    var showClearConfirmation by remember(slot.id) { mutableStateOf(false) }
    var validationError by remember(slot.id) { mutableStateOf<MathExpressionError?>(null) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(slot.draft) {
        if (editorText != slot.draft) {
            editorState.edit {
                replace(0, length, slot.draft)
                selection = TextRange(editorSelection.start.coerceIn(0, slot.draft.length), editorSelection.end.coerceIn(0, slot.draft.length))
            }
            undoStack = emptyList()
            redoStack = emptyList()
        }
    }
    LaunchedEffect(editorText) {
        if (editorText != slot.draft) onDraftChange(editorText)
    }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.hide()
    }

    fun snapshot() = EditorSnapshot(editorState.text.toString(), editorState.selection)

    fun publish(value: EditorSnapshot, recordUndo: Boolean = true) {
        val old = snapshot()
        if (recordUndo && value.text != old.text) {
            undoStack = (undoStack + old).takeLast(100)
            redoStack = emptyList()
        }
        editorState.edit {
            replace(0, length, value.text)
            selection = value.selection
        }
        validationError = null
        onDraftChange(value.text)
    }

    fun replaceSelection(replacement: String, caretFromEnd: Int = 0) {
        val current = editorState.text.toString()
        val selection = editorState.selection
        val start = selection.min.coerceIn(0, current.length)
        val end = selection.max.coerceIn(start, current.length)
        val text = current.replaceRange(start, end, replacement)
        val caret = (start + replacement.length - caretFromEnd).coerceIn(0, text.length)
        publish(EditorSnapshot(text, TextRange(caret)))
    }

    fun insertFunction(name: String) {
        val current = editorState.text.toString()
        val selection = editorState.selection
        val start = selection.min.coerceIn(0, current.length)
        val end = selection.max.coerceIn(start, current.length)
        val selected = current.substring(start, end)
        if (selected.isNotEmpty()) {
            val replacement = "$name($selected)"
            val text = current.replaceRange(start, end, replacement)
            publish(EditorSnapshot(text, TextRange(start + replacement.length)))
        } else {
            replaceSelection("$name(")
        }
    }

    fun insertSquare() {
        val current = editorState.text.toString()
        val selection = editorState.selection
        val selected = current.substring(selection.min, selection.max)
        if (selected.isEmpty()) replaceSelection("^2") else replaceSelection("($selected)^2")
    }

    fun moveCursor(direction: Int) {
        val current = editorState.selection
        val textLength = editorState.text.length
        val position = if (!current.collapsed) {
            if (direction < 0) current.min else current.max
        } else {
            (current.start + direction).coerceIn(0, textLength)
        }
        editorState.edit { selection = TextRange(position) }
    }

    fun applyKey(key: String) {
        if (key == "Shift") {
            shiftEnabled = !shiftEnabled
            return
        }
        when (key) {
            "AC" -> showClearConfirmation = true
            "⌫" -> {
                val selection = editorState.selection
                if (!selection.collapsed) replaceSelection("")
                else if (selection.start > 0) {
                    val current = editorState.text.toString()
                    val start = current.offsetByCodePoints(selection.start, -1)
                    val text = current.removeRange(start, selection.start)
                    publish(EditorSnapshot(text, TextRange(start)))
                }
            }
            "a–z" -> palette = PaletteKind.LETTERS
            "ƒ" -> palette = PaletteKind.ADVANCED
            "x²" -> {
                if (shiftEnabled) {
                    shiftEnabled = false
                    insertFunction("sqrt")
                } else {
                    insertSquare()
                }
            }
            "xʸ" -> {
                val selected = editorState.text.substring(editorState.selection.min, editorState.selection.max)
                if (selected.isEmpty()) replaceSelection("^(")
                else replaceSelection("($selected)^(")
            }
            "√" -> if (shiftEnabled) {
                shiftEnabled = false
                insertSquare()
            } else insertFunction("sqrt")
            "sin", "cos", "tan" -> {
                val function = if (!shiftEnabled) key else "a$key"
                shiftEnabled = false
                insertFunction(function)
            }
            "ln" -> {
                val function = if (shiftEnabled) "exp" else "ln"
                shiftEnabled = false
                insertFunction(function)
            }
            "log" -> {
                if (shiftEnabled) {
                    val selected = editorState.text.substring(editorState.selection.min, editorState.selection.max)
                    if (selected.isEmpty()) replaceSelection("10^(") else replaceSelection("10^($selected)")
                } else insertFunction("log")
                shiftEnabled = false
            }
            else -> {
                val literal = when (key) {
                    "÷" -> "÷"
                    "×" -> "×"
                    "−" -> "-"
                    "Valider", "Shift" -> return
                    else -> key
                }
                replaceSelection(literal)
            }
        }
    }

    val rows = listOf(
        listOf("Shift", "sin", "cos", "tan", "⌫"),
        listOf("ln", "log", "x²", "xʸ", "√"),
        listOf("7", "8", "9", "÷", "("),
        listOf("4", "5", "6", "×", ")"),
        listOf("1", "2", "3", "−", "a–z"),
        listOf("0", ",", "π", "+", "ƒ"),
        listOf("AC", "x", "e", ";", "Valider")
    )

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding()
    ) {
        val keyHeight = ((maxHeight - 168.dp) / 7).coerceIn(40.dp, 52.dp)
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
        Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp).testTag("editor-back")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour sans valider", tint = MaterialTheme.colorScheme.onSurface)
            }
            Column(Modifier.weight(1f)) {
                Text("Édition de ${slot.id.editorLabel}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                if (slot.isDraftModified) Text("Brouillon non validé", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
            }
            Text(study.angleMode.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .heightIn(max = 240.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (darkTheme) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f))
                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)), RoundedCornerShape(12.dp))
                .padding(12.dp)
                .testTag("editor-expression-area")
        ) {
            val selectionColors = if (darkTheme) {
                TextSelectionColors(
                    handleColor = Color(0xFF93C5FD),
                    backgroundColor = Color(0xFF60A5FA).copy(alpha = 0.42f)
                )
            } else LocalTextSelectionColors.current
            CompositionLocalProvider(LocalTextSelectionColors provides selectionColors) {
            InterceptPlatformTextInput(
                interceptor = { _, _ -> awaitCancellation() }
            ) {
            BasicTextField(
                state = editorState,
                modifier = Modifier.fillMaxSize().focusRequester(focusRequester)
                    .onFocusChanged { if (it.isFocused) keyboardController?.hide() }
                    .testTag("editor-expression"),
                inputTransformation = InputTransformation {
                    val previous = originalText.toString()
                    val previousSelection = originalSelection
                    val original = asCharSequence().toString()
                    if ('.' in original) {
                        val oldSelection = selection
                        val normalized = original.replace('.', ',')
                        replace(0, length, normalized)
                        selection = TextRange(
                            oldSelection.start.coerceIn(0, normalized.length),
                            oldSelection.end.coerceIn(0, normalized.length)
                        )
                    }
                    val transformed = asCharSequence().toString()
                    if (transformed != previous) {
                        undoStack = (undoStack + EditorSnapshot(previous, previousSelection)).takeLast(100)
                        redoStack = emptyList()
                    }
                },
                textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 22.sp, lineHeight = 30.sp),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    imeAction = ImeAction.Default,
                    showKeyboardOnFocus = false,
                    autoCorrectEnabled = false
                ),
                cursorBrush = SolidColor(if (darkTheme) Color(0xFF93C5FD) else MaterialTheme.colorScheme.primary),
                lineLimits = TextFieldLineLimits.MultiLine()
            )
            }
            }
            if (editorText.isEmpty()) {
                Text("Expression", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 22.sp, modifier = Modifier.align(Alignment.TopStart))
            }
        }

        val message = validationError?.let(::formatMathError)
        if (message != null) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f).testTag("editor-validation-error"))
                validationError?.position?.let { position ->
                    TextButtonCompat("Aller à l’erreur", "editor-go-to-error") {
                        val target = position.coerceIn(0, editorText.length)
                        editorState.edit { selection = TextRange(target) }
                        focusRequester.requestFocus()
                        keyboardController?.hide()
                    }
                }
            }
        }
        saveError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("editor-save-error")) }
        Spacer(Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth().height(40.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                if (undoStack.isNotEmpty()) {
                    redoStack = redoStack + snapshot()
                    val target = undoStack.last()
                    undoStack = undoStack.dropLast(1)
                    publish(target, recordUndo = false)
                }
            }, enabled = undoStack.isNotEmpty(), modifier = Modifier.size(44.dp).testTag("editor-undo")) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Annuler", tint = MaterialTheme.colorScheme.onSurface)
            }
            IconButton(onClick = {
                if (redoStack.isNotEmpty()) {
                    undoStack = (undoStack + snapshot()).takeLast(100)
                    val target = redoStack.last()
                    redoStack = redoStack.dropLast(1)
                    publish(target, recordUndo = false)
                }
            }, enabled = redoStack.isNotEmpty(), modifier = Modifier.size(44.dp).testTag("editor-redo")) {
                Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Rétablir", tint = MaterialTheme.colorScheme.onSurface)
            }
            CursorMoveButton("‹", -1, "editor-cursor-left", ::moveCursor)
            CursorMoveButton("›", 1, "editor-cursor-right", ::moveCursor)
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth().testTag("math-keyboard")) {
                rows.forEachIndexed { rowIndex, row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEachIndexed { columnIndex, key ->
                            KeyButton(
                                label = key,
                                selected = key == "Shift" && shiftEnabled,
                                modifier = Modifier.weight(1f).height(keyHeight).testTag("key-$rowIndex-$columnIndex"),
                                onClick = {
                                    if (key == "Valider") {
                                        when (val result = onValidate()) {
                                            is StudyValidationResult.Accepted -> { validationError = null; onValidated() }
                                            is StudyValidationResult.Rejected -> validationError = result.error
                                            null -> Unit
                                        }
                                    } else applyKey(key)
                                }
                            )
                        }
                    }
                }
        }
    }
    }

    if (palette != null) {
        Popup(alignment = Alignment.BottomCenter, onDismissRequest = { palette = null }) {
            if (palette == PaletteKind.LETTERS) {
                LetterPalette(
                    used = study.usedParameters,
                    onSelect = { letter ->
                        shiftEnabled = false
                        replaceSelection(letter.toString())
                        palette = null
                    }
                )
            } else {
                AdvancedFunctionPalette(
                    onSelect = { name ->
                        shiftEnabled = false
                        insertFunction(name)
                        palette = null
                    }
                )
            }
        }
    }

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text("Effacer l’expression ?") },
            text = { Text("Le texte actuel sera effacé. Vous pourrez annuler cette action.") },
            confirmButton = {
                TextButtonCompat("Effacer", "editor-confirm-clear") {
                    showClearConfirmation = false
                    publish(EditorSnapshot("", TextRange(0)))
                }
            },
            dismissButton = { TextButtonCompat("Annuler", "editor-cancel-clear") { showClearConfirmation = false } },
            modifier = Modifier.testTag("editor-clear-dialog")
        )
    }
}

@Composable
private fun KeyButton(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val fill = when (label) {
        "Shift" -> Color(0xFFFACC15)
        "⌫" -> Color(0xFFFFB74D)
        "AC" -> Color(0xFFB91C1C)
        "Valider" -> Color(0xFF047857)
        else -> if (selected) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant
    }
    Surface(
        modifier = modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick),
        color = fill,
        contentColor = when (label) {
            "AC", "Valider" -> Color.White
            "Shift" -> Color(0xFF422006)
            "⌫" -> Color(0xFF431407)
            else -> MaterialTheme.colorScheme.onSurface
        },
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.tertiary) else null,
        shape = RoundedCornerShape(8.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, fontSize = if (label.length > 4) 12.sp else 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 1)
        }
    }
}

@Composable
private fun LetterPalette(used: Set<Char>, onSelect: (Char) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        shadowElevation = 10.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.padding(bottom = 432.dp).testTag("letter-palette")
    ) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ('a'..'z').toList().chunked(7).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { letter ->
                        val reserved = letter == 't'
                        val distinct = letter == 'x' || letter == 'e'
                        val active = letter in used
                        Surface(
                            modifier = Modifier.size(width = 38.dp, height = 38.dp)
                                .testTag("letter-$letter")
                                .then(if (reserved) Modifier.semantics { disabled() } else Modifier.clickable { onSelect(letter) }),
                            color = when {
                                reserved -> MaterialTheme.colorScheme.surfaceVariant
                                distinct -> MaterialTheme.colorScheme.tertiaryContainer
                                active -> MaterialTheme.colorScheme.primaryContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            },
                            shape = RoundedCornerShape(8.dp),
                            border = if (active) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(letter.toString(), color = if (reserved) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f) else MaterialTheme.colorScheme.onSurface, fontWeight = if (distinct || active) FontWeight.Bold else FontWeight.Normal)
                                if (letter == 'x') Text("variable", fontSize = 6.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 1.dp))
                                if (letter == 'e') Text("constante", fontSize = 5.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 1.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdvancedFunctionPalette(onSelect: (String) -> Unit) {
    val functions = listOf("abs", "cbrt", "floor", "ceil", "sign", "min", "max")
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        shadowElevation = 10.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.padding(bottom = 432.dp).testTag("advanced-function-palette")
    ) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            functions.chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { function ->
                        Surface(
                            modifier = Modifier.height(38.dp).width(72.dp).clickable { onSelect(function) }.testTag("advanced-$function"),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) { Text(function, fontSize = 12.sp, fontWeight = FontWeight.Medium) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CursorMoveButton(label: String, direction: Int, tag: String, onMove: (Int) -> Unit) {
    Text(
        label,
        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
            .pointerInput(direction) {
                awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val longPress = awaitLongPressOrCancellation(down.id)
                        if (longPress == null) {
                            onMove(direction)
                        } else {
                            while (true) {
                                val event = withTimeoutOrNull(65L) { awaitPointerEvent() }
                                val pressed = event?.changes?.firstOrNull { it.id == down.id }?.pressed
                                    ?: currentEvent.changes.firstOrNull { it.id == down.id }?.pressed
                                    ?: false
                                if (!pressed) break
                                onMove(direction)
                            }
                        }
                }
            }
            .semantics {
                contentDescription = if (direction < 0) "Déplacer le curseur vers la gauche" else "Déplacer le curseur vers la droite"
                onClick { onMove(direction); true }
            }
            .testTag(tag),
        textAlign = TextAlign.Center,
        fontSize = 23.sp,
        color = MaterialTheme.colorScheme.onSurface,
        lineHeight = 44.sp
    )
}

@Composable
private fun TextButtonCompat(label: String, tag: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.testTag(tag)) { Text(label) }
}

@Composable
private fun FunctionEditorUnavailable(message: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(message, color = MaterialTheme.colorScheme.error)
        Button(onClick = onBack) { Text("Retour") }
    }
}

private fun formatMathError(error: MathExpressionError): String {
    val detail = when (error) {
        is MathExpressionError.Syntax -> error.detail
        is MathExpressionError.MissingParenthesis -> "Parenthèse fermante manquante."
        is MathExpressionError.UnknownFunction -> "Fonction inconnue : ${error.name}."
        is MathExpressionError.ArgumentCount -> "Nombre d’arguments incorrect pour ${error.name} (attendu ${error.expected})."
        is MathExpressionError.InvalidVariable -> "Variable ou paramètre invalide : ${error.name}."
        is MathExpressionError.Domain -> error.detail
        is MathExpressionError.NonFiniteResult -> "Le résultat n’est pas fini."
    }
    return error.position?.let { "$detail (position ${it + 1})" } ?: detail
}

private val FunctionSlotId.editorLabel: String
    get() = "f${number.toSubscript()}(x)"

private fun Int.toSubscript(): String = toString().map { digit ->
    when (digit) {
        '0' -> '₀'; '1' -> '₁'; '2' -> '₂'; '3' -> '₃'; '4' -> '₄'
        '5' -> '₅'; '6' -> '₆'; '7' -> '₇'; '8' -> '₈'; else -> '₉'
    }
}.joinToString("")
