package com.fertipos.agroshop.ui.common

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Clear
import androidx.compose.ui.text.style.TextAlign
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    label: String,
    value: Long?,
    onChange: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var show by remember { mutableStateOf(false) }
    val df = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val text = remember(value) { value?.let { df.format(Date(it)) } ?: "" }

    if (show) {
        val state = rememberDatePickerState(initialSelectedDateMillis = value ?: System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { show = false },
            confirmButton = {
                TextButton(onClick = {
                    onChange(state.selectedDateMillis)
                    show = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { show = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = state)
        }
    }

    OutlinedTextField(
        value = text,
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        trailingIcon = {
            Row {
                if (value != null) {
                    IconButton(onClick = { onChange(null) }) { Icon(Icons.Filled.Clear, contentDescription = "Clear date") }
                }
                IconButton(onClick = { show = true }) { Icon(Icons.Filled.CalendarToday, contentDescription = "Pick date") }
            }
        },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Start)
    )
}
