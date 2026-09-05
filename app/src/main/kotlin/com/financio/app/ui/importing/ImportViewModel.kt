package com.financio.app.ui.importing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financio.app.DefaultAccount
import com.financio.core.usecase.ImportPreview
import com.financio.core.usecase.ImportStatementUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ImportUiState {
    data object PickFile : ImportUiState
    data object Loading : ImportUiState
    data class Ready(val fileName: String, val preview: ImportPreview) : ImportUiState
    data class Failed(val message: String) : ImportUiState
    data object Imported : ImportUiState
}

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val importStatementUseCase: ImportStatementUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ImportUiState>(ImportUiState.PickFile)
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    fun onFilePicked(fileName: String, content: String) {
        _uiState.value = ImportUiState.Loading
        viewModelScope.launch {
            _uiState.value = try {
                val preview = importStatementUseCase.preview(content, DefaultAccount.ID)
                ImportUiState.Ready(fileName, preview)
            } catch (e: Exception) {
                ImportUiState.Failed(e.message ?: "Kon het bestand niet lezen.")
            }
        }
    }

    /**
     * Persists only the transactions a rule already categorized. The ones in
     * [ImportPreview.needsCategory] wait for a manual categorization screen — a fase-1
     * follow-up not yet built — rather than being imported uncategorized.
     */
    fun confirm() {
        val current = _uiState.value
        if (current !is ImportUiState.Ready) return
        viewModelScope.launch {
            importStatementUseCase.confirm(current.preview.ready)
            _uiState.value = ImportUiState.Imported
        }
    }
}
