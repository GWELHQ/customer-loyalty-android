package com.example.loyaltyapp.ui.navigation

import androidx.lifecycle.ViewModel
import com.example.loyaltyapp.core.session.AttendantSession
import com.example.loyaltyapp.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class SessionWatcherViewModel @Inject constructor(
    authRepository: AuthRepository
) : ViewModel() {
    val session: StateFlow<AttendantSession?> = authRepository.session
}
