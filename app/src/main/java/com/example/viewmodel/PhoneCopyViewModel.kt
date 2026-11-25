package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.FlashShareApp
import com.example.data.model.PhoneCopyCategoryItem
import com.example.data.model.PhoneCopyCategoryType
import com.example.data.model.PhoneCopyRole
import com.example.data.model.TransferItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PhoneCopyUiState(
    val role: PhoneCopyRole = PhoneCopyRole.NONE,
    val isScanning: Boolean = false,
    val isPreparingBatch: Boolean = false,
    val categories: List<PhoneCopyCategoryItem> = emptyList()
) {
    val selectedCategories: Set<PhoneCopyCategoryType>
        get() = categories.filter { it.selected && it.count > 0 }.map { it.type }.toSet()

    val totalSelectedItems: Int
        get() = categories.filter { it.selected }.sumOf { it.count }

    val totalSelectedBytes: Long
        get() = categories.filter { it.selected }.sumOf { it.totalBytes }
}

class PhoneCopyViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as FlashShareApp

    private val _uiState = MutableStateFlow(PhoneCopyUiState())
    val uiState: StateFlow<PhoneCopyUiState> = _uiState.asStateFlow()

    fun selectRole(role: PhoneCopyRole) {
        _uiState.update { it.copy(role = role) }
        if (role == PhoneCopyRole.OLD_PHONE) {
            scanDeviceData()
        }
    }

    fun scanDeviceData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true) }
            val summary = app.phoneCopyManager.scanPhoneCopySummary()
            _uiState.update { it.copy(isScanning = false, categories = summary) }
        }
    }

    fun toggleCategory(type: PhoneCopyCategoryType) {
        _uiState.update { state ->
            state.copy(
                categories = state.categories.map { cat ->
                    if (cat.type == type) cat.copy(selected = !cat.selected) else cat
                }
            )
        }
    }

    fun buildBatchAndProceed(onBatchReady: (List<TransferItem>) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isPreparingBatch = true) }
            val batch = app.phoneCopyManager.buildMigrationBatch(_uiState.value.selectedCategories)
            _uiState.update { it.copy(isPreparingBatch = false) }
            onBatchReady(batch)
        }
    }

    fun resetRole() {
        _uiState.update { it.copy(role = PhoneCopyRole.NONE) }
    }
}
