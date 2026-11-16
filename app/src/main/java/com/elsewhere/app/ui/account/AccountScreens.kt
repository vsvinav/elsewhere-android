package com.elsewhere.app.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.elsewhere.app.ui.onboard.OnboardFrame
import com.elsewhere.app.ui.world.CapsText
import com.elsewhere.app.ui.world.DisplayText
import com.elsewhere.app.ui.world.InkButton
import com.elsewhere.app.ui.world.Poster
import com.elsewhere.app.ui.world.PosterTextField
import com.elsewhere.app.ui.world.PosterType

const val WELCOME_LINE = "Keep going. Your record grows with you."
const val WELCOME_LEDE = "An account keeps your record safe in a vault only you can open, and brings it to your next phone."
const val PASSPHRASE_NOTE = "Only you know this. We can't reset it."
const val MAIL_LINK_NOTE = "Opening the link in the mail works too."
const val START_OVER = "Start over without my vault"

/** The first page a fresh install sees: sign up, log in, or carry on as before. */
@Composable
fun WelcomeScreen(onSignUp: () -> Unit, onLogIn: () -> Unit, onSkip: () -> Unit) {
    OnboardFrame(question = null, onCancel = null) {
        DisplayText("Elsewhere", 72.sp)
        Spacer(Modifier.height(20.dp))
        Text(WELCOME_LINE, color = Poster.Ink, style = PosterType.Lede.copy(fontSize = 22.sp))
        Spacer(Modifier.height(16.dp))
        Text(WELCOME_LEDE, color = Poster.Ink2, style = PosterType.Lede.copy(fontSize = 17.sp))
        Spacer(Modifier.height(40.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            InkButton("Sign up", onSignUp, Modifier.fillMaxWidth())
            InkButton("Log in", onLogIn, Modifier.fillMaxWidth(), quiet = true)
        }
        Spacer(Modifier.height(28.dp))
        CapsText("Continue without an account", Modifier.clickable(onClick = onSkip).padding(vertical = 8.dp), color = Poster.Ink, size = 12.sp)
    }
}

/** The whole Email → Code → Passphrase/Unlock flow on one page frame; `onClose` is the Cancel when there is somewhere to go back to. */
@Composable
fun AccountFlow(ui: AccountUi, actions: AccountActions, onClose: (() -> Unit)?) {
    OnboardFrame(question = null, onCancel = onClose) {
        when (ui.step) {
            AccountStep.Email -> EmailPage(ui, actions)
            AccountStep.Code -> CodePage(ui, actions)
            AccountStep.SetPassphrase -> SetPassphrasePage(ui, actions)
            AccountStep.Unlock -> UnlockPage(ui, actions)
        }
    }
}

@Composable
private fun EmailPage(ui: AccountUi, actions: AccountActions) {
    var email by rememberSaveable { mutableStateOf(ui.email) }
    DisplayText(if (ui.entry == AccountEntry.SignUp) "Sign up" else "Log in", 64.sp)
    Spacer(Modifier.height(28.dp))
    Text("We'll send a six-digit code to your email. No password.", color = Poster.Ink2, style = PosterType.Lede.copy(fontSize = 18.sp))
    Spacer(Modifier.height(32.dp))
    PosterTextField(email, { email = it }, Modifier.fillMaxWidth().testTag("account.email"), placeholder = "you@example.com", label = "Your email")
    ErrorLine(ui.error)
    Spacer(Modifier.height(36.dp))
    InkButton(if (ui.busy) "Sending…" else "Continue", { actions.sendCode(email) }, enabled = !ui.busy && email.isNotBlank())
}

@Composable
private fun CodePage(ui: AccountUi, actions: AccountActions) {
    var code by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(code) { if (code.length == CODE_LENGTH) actions.submitCode(code) }
    DisplayText("Check your mail", 56.sp)
    Spacer(Modifier.height(28.dp))
    Text("We sent a code to ${ui.email}", color = Poster.Ink2, style = PosterType.Lede.copy(fontSize = 18.sp))
    Spacer(Modifier.height(32.dp))
    CodeBoxes(code, { code = it.filter(Char::isDigit).take(CODE_LENGTH) }, enabled = !ui.busy)
    ErrorLine(ui.error)
    Spacer(Modifier.height(20.dp))
    Text(MAIL_LINK_NOTE, color = Poster.Ink3, style = PosterType.Label)
    Spacer(Modifier.height(36.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        InkButton(if (ui.resent) "Sent again" else "Resend code", { actions.resend() }, quiet = true, enabled = !ui.busy && !ui.resent)
        CapsText("Change email", Modifier.clickable(onClick = actions::back).padding(8.dp), color = Poster.Ink, size = 11.sp)
    }
}

/** One hidden field drawn as six printed boxes, so a code paste or autofill lands whole. */
@Composable
private fun CodeBoxes(code: String, onCode: (String) -> Unit, enabled: Boolean) {
    BasicTextField(
        value = code, onValueChange = onCode, enabled = enabled, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        cursorBrush = SolidColor(Poster.Red), textStyle = PosterType.Display.copy(fontSize = 1.sp, color = Poster.Stock),
        modifier = Modifier.fillMaxWidth().testTag("account.code"),
        decorationBox = { inner ->
            Box {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (i in 0 until CODE_LENGTH) {
                        val filled = i < code.length
                        val active = i == code.length
                        Box(
                            Modifier.size(width = 44.dp, height = 56.dp).background(Poster.Stock).border(2.dp, if (active) Poster.Red else Poster.Ink),
                            contentAlignment = Alignment.Center,
                        ) { if (filled) Text(code[i].toString(), color = Poster.Ink, style = PosterType.Display.copy(fontSize = 30.sp, lineHeight = 1.em)) }
                    }
                }
                // The real field sits over the boxes, invisible, so taps and pastes reach it.
                Box(Modifier.matchParentSize()) { inner() }
            }
        },
    )
}

@Composable
private fun SetPassphrasePage(ui: AccountUi, actions: AccountActions) {
    var p by rememberSaveable { mutableStateOf("") }
    var c by rememberSaveable { mutableStateOf("") }
    DisplayText("Set a recovery passphrase", 52.sp)
    Spacer(Modifier.height(24.dp))
    Text("It locks your vault. $PASSPHRASE_NOTE", color = Poster.Ink2, style = PosterType.Lede.copy(fontSize = 18.sp))
    Spacer(Modifier.height(32.dp))
    PosterTextField(p, { p = it }, Modifier.fillMaxWidth().testTag("account.passphrase"), placeholder = "at least 8 characters", label = "Passphrase", hidden = true)
    Spacer(Modifier.height(24.dp))
    PosterTextField(c, { c = it }, Modifier.fillMaxWidth().testTag("account.confirm"), placeholder = "once more", label = "Confirm", hidden = true)
    ErrorLine(ui.error)
    Spacer(Modifier.height(36.dp))
    InkButton(if (ui.busy) "Saving…" else "Save passphrase", { actions.setPassphrase(p, c) }, enabled = !ui.busy && p.isNotEmpty() && c.isNotEmpty())
}

@Composable
private fun UnlockPage(ui: AccountUi, actions: AccountActions) {
    var p by rememberSaveable { mutableStateOf("") }
    val update = ui.entry == AccountEntry.Update
    DisplayText(if (update) "Unlock your vault" else "Welcome back", 56.sp)
    Spacer(Modifier.height(24.dp))
    Text(
        if (update) "Enter your recovery passphrase to send this phone's record to the vault." else "Enter your recovery passphrase to bring your record to this phone.",
        color = Poster.Ink2, style = PosterType.Lede.copy(fontSize = 18.sp),
    )
    Spacer(Modifier.height(32.dp))
    PosterTextField(p, { p = it }, Modifier.fillMaxWidth().testTag("account.passphrase"), placeholder = "your passphrase", label = "Recovery passphrase", hidden = true)
    ErrorLine(ui.error)
    Spacer(Modifier.height(36.dp))
    InkButton(if (ui.busy) "Opening…" else if (update) "Update vault" else "Restore", { actions.unlock(p) }, enabled = !ui.busy && p.isNotBlank())
    if (!update) {
        Spacer(Modifier.height(28.dp))
        CapsText(START_OVER, Modifier.clickable(onClick = actions::startOver).padding(vertical = 8.dp), color = Poster.Ink, size = 11.sp)
    }
}

@Composable
private fun ErrorLine(error: String?) {
    if (error != null) {
        Spacer(Modifier.height(12.dp))
        Text(error, color = Poster.Red, style = PosterType.Lede.copy(fontSize = 15.sp), modifier = Modifier.testTag("account.error"))
    }
}
