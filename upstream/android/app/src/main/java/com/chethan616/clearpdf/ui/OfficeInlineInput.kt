package com.chethan616.clearpdf.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.chethan616.clearpdf.ui.theme.Candy

@Composable
fun OfficeInlineInput(
    vm: AppViewModel, doc: OpenDocument, draft: OfficeInlineDraft,
    modifier: Modifier, fontSize: TextUnit, bold: Boolean = false,
    ink: Color = Color.Black, paper: Color = Color.White,
) {
    val focus = remember(draft) { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(draft) { focus.requestFocus() }
    BasicTextField(
        value = draft.value,
        onValueChange = { vm.updateOfficeInline(draft, it) },
        textStyle = TextStyle(color = ink, fontSize = fontSize, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal),
        cursorBrush = SolidColor(Candy.Sky),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { keyboard?.hide(); vm.finishOfficeInline(doc) }),
        modifier = modifier.background(paper).border(1.dp, Candy.Sky).padding(2.dp).focusRequester(focus),
    )
}
