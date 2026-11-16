package com.elsewhere.app.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elsewhere.app.account.Accounts
import com.elsewhere.app.account.NoVault
import com.elsewhere.app.account.Vault
import com.elsewhere.app.account.WrongPassphrase
import com.elsewhere.app.account.userMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Why the flow opened: the two Welcome buttons, Settings' "Log in or sign up", or Settings' "Update" on a stale vault. */
enum class AccountEntry { SignUp, LogIn, Update }

enum class AccountStep { Email, Code, SetPassphrase, Unlock }

/** One page of the flow at a time; `done` closes it, `error` prints under the field that caused it. */
data class AccountUi(
    val entry: AccountEntry = AccountEntry.LogIn,
    val step: AccountStep = AccountStep.Email,
    val email: String = "",
    val error: String? = null,
    val busy: Boolean = false,
    val done: Boolean = false,
    /** Set after a resend, so the page can say so. */
    val resent: Boolean = false,
)

/** Everything the account pages can do; the view model is the real one, snapshots pass a no-op. */
interface AccountActions {
    fun begin(entry: AccountEntry)
    fun sendCode(email: String)
    fun resend()
    fun submitCode(code: String)
    fun setPassphrase(passphrase: String, confirm: String)
    fun unlock(passphrase: String)
    fun startOver()
    fun back()
}

const val CODE_LENGTH = 6
const val PASSPHRASE_TOO_SHORT = "At least 8 characters."
const val PASSPHRASES_DIFFER = "Those don't match."
const val NOT_AN_EMAIL = "That doesn't look like an email."
const val WRONG_PASSPHRASE = "That's not it"
const val NO_VAULT = "This account has no vault yet. Set a passphrase instead."

@HiltViewModel
class AccountViewModel @Inject constructor(private val accounts: Accounts) : ViewModel(), AccountActions {
    private val _ui = MutableStateFlow(AccountUi())
    val ui: StateFlow<AccountUi> = _ui

    override fun begin(entry: AccountEntry) {
        _ui.value = AccountUi(entry = entry, step = if (entry == AccountEntry.Update) AccountStep.Unlock else AccountStep.Email)
    }

    override fun sendCode(email: String) {
        val e = email.trim()
        if (!looksLikeEmail(e)) { _ui.update { it.copy(error = NOT_AN_EMAIL) }; return }
        run(after = { it.copy(step = AccountStep.Code, email = e.lowercase()) }) { accounts.start(e) }
    }

    override fun resend() = run(after = { it.copy(resent = true) }) { accounts.start(_ui.value.email) }

    override fun submitCode(code: String) {
        if (code.length != CODE_LENGTH || !code.all { it.isDigit() }) return
        run(after = { it }) {
            val account = accounts.finish(_ui.value.email, code)
            _ui.update { it.copy(step = if (account.hasVault) AccountStep.Unlock else AccountStep.SetPassphrase, resent = false) }
        }
    }

    override fun setPassphrase(passphrase: String, confirm: String) {
        val p = passphrase.trim()
        when {
            p.length < Vault.MIN_PASSPHRASE -> _ui.update { it.copy(error = PASSPHRASE_TOO_SHORT) }
            p != confirm.trim() -> _ui.update { it.copy(error = PASSPHRASES_DIFFER) }
            else -> run(after = { it.copy(done = true) }) { accounts.setPassphrase(p) }
        }
    }

    /** Logging in restores from the vault; Update proves the passphrase and sends this phone's contents. */
    override fun unlock(passphrase: String) {
        if (passphrase.trim().isEmpty()) return
        run(after = { it.copy(done = true) }) {
            if (_ui.value.entry == AccountEntry.Update) accounts.unlock(passphrase) else accounts.restore(passphrase)
        }
    }

    /** "Start over without my vault": keep the session, carry on as a fresh device. */
    override fun startOver() { _ui.update { it.copy(done = true) } }

    override fun back() {
        _ui.update { when (it.step) { AccountStep.Code -> it.copy(step = AccountStep.Email, error = null, resent = false); else -> it } }
    }

    private fun run(after: (AccountUi) -> AccountUi, block: suspend () -> Unit) {
        if (_ui.value.busy) return
        _ui.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val r = runCatching { block() }
            _ui.update { ui ->
                r.fold({ after(ui).copy(busy = false) }, { e -> ui.copy(busy = false, error = messageOf(e)) })
            }
        }
    }

    private fun messageOf(e: Throwable): String = when (e) {
        is WrongPassphrase -> WRONG_PASSPHRASE
        is NoVault -> NO_VAULT
        else -> e.userMessage()
    }

    private fun looksLikeEmail(s: String) = s.length <= 254 && Regex("""^[^\s@]+@[^\s@]+\.[^\s@]+$""").matches(s)
}
