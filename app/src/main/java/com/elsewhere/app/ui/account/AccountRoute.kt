package com.elsewhere.app.ui.account

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** The account flow with its view model; `onDone` fires once the session (and vault, when there is one) is in place. */
@Composable
fun AccountRoute(entry: AccountEntry, onDone: () -> Unit, onClose: (() -> Unit)?, vm: AccountViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    LaunchedEffect(entry) { vm.begin(entry) }
    LaunchedEffect(ui.done) { if (ui.done) onDone() }
    AccountFlow(ui, vm, onClose?.let { close -> { vm.reset(); close() } })
}
