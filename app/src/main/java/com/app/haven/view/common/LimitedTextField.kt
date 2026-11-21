package com.app.haven.view.common

import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.VisualTransformation

//OutlinedTextField but with a max limit defined
//reusable component
@Composable
fun LimitedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    maxLength: Int,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    showCounter: Boolean = true,
    error: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    shape: Shape = OutlinedTextFieldDefaults.shape as Shape
) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= maxLength) onValueChange(it) },
        label = { Text(label) },
        singleLine = singleLine,
        isError = error != null,
        supportingText = when {
            error != null -> {
                { Text(error, color = MaterialTheme.colorScheme.error) }
            }
            showCounter -> {
                { Text("${value.length} / $maxLength") }
            }
            else -> null
        },
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        modifier = modifier,
        shape = shape,
        )
}
