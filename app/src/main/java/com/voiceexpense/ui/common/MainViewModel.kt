package com.voiceexpense.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voiceexpense.data.local.TransactionDao
import com.voiceexpense.data.model.Transaction
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class MainViewModel @Inject constructor(
    dao: TransactionDao
) : ViewModel() {
    val recent = dao.observeAll()
        .map { it.take(10) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<Transaction>())
}

