package com.example.janithmobile.ui.main

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainScreenViewModel : ViewModel() {
  private val _uiState = MutableStateFlow<MainScreenUiState>(MainScreenUiState.Loading)
  val uiState: StateFlow<MainScreenUiState> = _uiState.asStateFlow()
}

sealed interface MainScreenUiState {
  object Loading : MainScreenUiState

  data class Error(val throwable: Throwable) : MainScreenUiState

  data class Success(val data: List<String>) : MainScreenUiState
}
