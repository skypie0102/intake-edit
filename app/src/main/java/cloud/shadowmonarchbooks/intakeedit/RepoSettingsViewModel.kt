package cloud.shadowmonarchbooks.intakeedit

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class RepoSettingsUiState(
    val settings: RepoSettings,
    val token: String,
    val showSettings: Boolean,
    val saving: Boolean = false,
    val notice: String? = null,
)

internal class RepoSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsStore = SettingsStore(application)
    private val tokenStore = SecureTokenStore(application)
    private val initialSettings = settingsStore.load()
    private val initialToken = tokenStore.load()

    private val _uiState = MutableStateFlow(
        RepoSettingsUiState(
            settings = initialSettings,
            token = initialToken,
            showSettings = initialToken.isBlank(),
        ),
    )
    val uiState: StateFlow<RepoSettingsUiState> = _uiState.asStateFlow()

    fun openSettings() {
        _uiState.update { it.copy(showSettings = true, notice = null) }
    }

    fun closeSettings() {
        if (_uiState.value.token.isNotBlank()) {
            _uiState.update { it.copy(showSettings = false, notice = null) }
        }
    }

    fun save(next: RepoSettings, nextToken: String) {
        if (_uiState.value.saving) return
        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, notice = null) }
            try {
                val candidate = GitHubApi(next, nextToken)
                val login = candidate.verifyUser()
                withContext(Dispatchers.IO) {
                    settingsStore.save(next)
                    tokenStore.save(nextToken)
                }
                _uiState.update {
                    it.copy(
                        settings = next,
                        token = nextToken,
                        showSettings = false,
                        saving = false,
                        notice = "Connected as $login.",
                    )
                }
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        saving = false,
                        notice = t.message ?: "Could not verify GitHub connection.",
                    )
                }
            }
        }
    }
}
