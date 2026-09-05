package com.financio.app.ui.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financio.core.categorize.ManualRule
import com.financio.core.model.Category
import com.financio.core.model.CategoryRule
import com.financio.core.model.MatchType
import com.financio.core.repository.CategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CategoryManagementUiState(
    val categories: List<Category> = emptyList(),
    val rules: List<CategoryRule> = emptyList(),
)

/** A small rotation, not a picker: keeps "add a category" down to just typing a name. */
private val COLOR_ROTATION = listOf(
    "#5B7A52", "#4C6E77", "#8A4A3D", "#7A6A45", "#6B6485",
    "#4A5A8A", "#3D8A6E", "#9C7A3D", "#3D8FA3", "#A35D82",
)

@HiltViewModel
class CategoryManagementViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository,
) : ViewModel() {

    val uiState: StateFlow<CategoryManagementUiState> = combine(
        categoryRepository.observeCategories(),
        categoryRepository.observeRules(),
    ) { categories, rules -> CategoryManagementUiState(categories, rules) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoryManagementUiState())

    fun addCategory(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            val color = COLOR_ROTATION[uiState.value.categories.size % COLOR_ROTATION.size]
            categoryRepository.addCategory(trimmed, color)
        }
    }

    fun deleteCategory(categoryId: Long) {
        viewModelScope.launch { categoryRepository.deleteCategory(categoryId) }
    }

    fun addRule(categoryId: Long, matchType: MatchType, pattern: String) {
        val trimmed = pattern.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch { categoryRepository.addRule(ManualRule.from(categoryId, matchType, trimmed)) }
    }

    fun deleteRule(ruleId: Long) {
        viewModelScope.launch { categoryRepository.deleteRule(ruleId) }
    }
}
