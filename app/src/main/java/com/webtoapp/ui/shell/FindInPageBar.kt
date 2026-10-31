package com.webtoapp.ui.shell

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.webtoapp.core.engine.BrowserSurface
import com.webtoapp.core.i18n.Strings
import com.webtoapp.core.logging.AppLogger







@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FindInPageBar(
    surface: BrowserSurface?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }
    var activeMatchOrdinal by remember { mutableIntStateOf(-1) }
    var numberOfMatches by remember { mutableIntStateOf(0) }
    val inputFocusRequester = remember { FocusRequester() }
    var inputFocused by remember { mutableStateOf(false) }

    fun onFindResult(active: Int, total: Int) {
        activeMatchOrdinal = active
        numberOfMatches = total
    }

    
    DisposableEffect(surface) {
        onDispose {
            try {
                surface?.clearFindMatches()
            } catch (e: Exception) {
                AppLogger.w("FindInPageBar", "cleanup failed", e)
            }
        }
    }

    
    
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(surface) {
        if (surface != null) {
            inputFocusRequester.requestFocus()
            withFrameNanos { }
            if (inputFocused) keyboard?.show()
        }
    }

    
    LaunchedEffect(query, surface) {
        val s = surface ?: return@LaunchedEffect
        if (query.isBlank()) {
            s.clearFindMatches()
            activeMatchOrdinal = -1
            numberOfMatches = 0
            return@LaunchedEffect
        }
        kotlinx.coroutines.delay(300)
        try {
            s.findInPage(query, forward = null, onResult = ::onFindResult)
            
            
            
            if (!inputFocused) inputFocusRequester.requestFocus()
        } catch (e: Exception) {
            AppLogger.w("FindInPageBar", "findInPage failed", e)
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                Icons.Outlined.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )

            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(200) },
                placeholder = {
                    Text(
                        Strings.nativeBridgeCapsFindInPage,
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(inputFocusRequester)
                    .onFocusChanged { inputFocused = it.isFocused },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        if (query.isNotBlank()) {
                            try {
                                surface?.findInPage(query, forward = true, onResult = ::onFindResult)
                            } catch (e: Exception) {
                                AppLogger.w("FindInPageBar", "findNext failed", e)
                            }
                        }
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                )
            )

            Text(
                text = if (query.isBlank() || numberOfMatches <= 0) {
                    "0/0"
                } else {
                    "${(activeMatchOrdinal + 1).coerceIn(1, numberOfMatches)}/$numberOfMatches"
                },
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                color = if (numberOfMatches > 0) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(horizontal = 2.dp)
            )

            IconButton(
                onClick = {
                    if (query.isNotBlank()) {
                        try {
                            surface?.findInPage(query, forward = false, onResult = ::onFindResult)
                        } catch (e: Exception) {
                            AppLogger.w("FindInPageBar", "findPrev failed", e)
                        }
                    }
                },
                enabled = numberOfMatches > 0,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Filled.KeyboardArrowUp, Strings.codeEditorFindPrev, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            IconButton(
                onClick = {
                    if (query.isNotBlank()) {
                        try {
                            surface?.findInPage(query, forward = true, onResult = ::onFindResult)
                        } catch (e: Exception) {
                            AppLogger.w("FindInPageBar", "findNext failed", e)
                        }
                    }
                },
                enabled = numberOfMatches > 0,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Filled.KeyboardArrowDown, Strings.codeEditorFindNext, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            IconButton(
                onClick = {
                    query = ""
                    keyboard?.hide()
                    onClose()
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Filled.Close, Strings.close, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
