package com.lesego.electrical.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.lesego.electrical.data.models.*
import com.lesego.electrical.data.repository.AppRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Shared ViewModel scoped to the Main activity lifecycle.
 * All UI fragments observe LiveData exposed here.
 *
 * Uses [AppRepository] as the single data source and runs
 * all data operations on coroutines (viewModelScope).
 *
 * @author Lesego Electrical Team
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: AppRepository,
    private val firebaseAuth: FirebaseAuth
) : ViewModel() {

    // ─────────────────────────────────────────────────────────────────────────
    //  Auth state
    // ─────────────────────────────────────────────────────────────────────────

    /** The currently authenticated Firebase user (null if not signed in). */
    val currentUser get() = firebaseAuth.currentUser

    // ─────────────────────────────────────────────────────────────────────────
    //  Services
    // ─────────────────────────────────────────────────────────────────────────

    /** Live list of cached services from Room — updates reactively. */
    val services: LiveData<List<Service>> = repository.getServicesFlow().asLiveData()

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    /** Triggers a fresh API sync of services and updates the Room cache. */
    fun refreshServices() {
        viewModelScope.launch {
            _isLoading.value = true
            repository.refreshServices().onFailure { e ->
                Timber.e(e, "Refresh services failed")
                _errorMessage.value = "Could not load services. Showing cached data."
            }
            _isLoading.value = false
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Bookings
    // ─────────────────────────────────────────────────────────────────────────

    /** Live list of the current user's bookings from Room. */
    val myBookings: LiveData<List<Booking>> = repository.getMyBookingsFlow().asLiveData()

    fun syncMyBookings() {
        viewModelScope.launch {
            repository.syncMyBookings().onFailure { Timber.e(it, "Booking sync failed") }
        }
    }

    private val _bookingResult = MutableLiveData<Result<BookingResponse>?>()
    val bookingResult: LiveData<Result<BookingResponse>?> = _bookingResult

    /**
     * Submits a booking to the API and caches the result locally.
     * Observers watch [bookingResult] to react to success / failure.
     */
    fun createBooking(request: BookingRequest) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = repository.createBooking(request)
            _bookingResult.value = result
            _isLoading.value = false
        }
    }

    /** Clears the booking result (so dialogs don't re-show on rotation). */
    fun clearBookingResult() { _bookingResult.value = null }

    // ─────────────────────────────────────────────────────────────────────────
    //  Contractors
    // ─────────────────────────────────────────────────────────────────────────

    private val _contractors = MutableLiveData<List<Contractor>>()
    val contractors: LiveData<List<Contractor>> = _contractors

    fun loadContractors(area: String? = null, specialty: String? = null, search: String? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            repository.getContractors(area, specialty, search)
                .onSuccess { _contractors.value = it }
                .onFailure { e ->
                    Timber.e(e, "Load contractors failed")
                    _errorMessage.value =
                        "Contractors error: ${e.localizedMessage}"
                }
            _isLoading.value = false
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Jobs
    // ─────────────────────────────────────────────────────────────────────────

    private val _jobs = MutableLiveData<List<Job>>()
    val jobs: LiveData<List<Job>> = _jobs

    fun loadJobs() {
        viewModelScope.launch {
            _isLoading.value = true
            repository.getJobs()
                .onSuccess { _jobs.value = it }
                .onFailure { e ->
                    Timber.e(e, "Load jobs failed")
                    _errorMessage.value = "Could not load job listings."
                }
            _isLoading.value = false
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  User Profile
    // ─────────────────────────────────────────────────────────────────────────

    private val _userProfile = MutableLiveData<User?>()
    val userProfile: LiveData<User?> = _userProfile

    fun loadUserProfile() {
        viewModelScope.launch {
            repository.getCurrentUserProfile()
                .onSuccess { _userProfile.value = it }
                .onFailure { Timber.e(it, "Load profile failed") }
        }
    }

    fun updateProfile(updates: Map<String, Any>) {
        viewModelScope.launch {
            repository.updateUserProfile(updates)
                .onSuccess { loadUserProfile() }
                .onFailure { e ->
                    Timber.e(e, "Load contractors failed")
                    _errorMessage.value = e.localizedMessage
                        ?: "Unknown Firebase error"
                }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Contact
    // ─────────────────────────────────────────────────────────────────────────

    private val _contactSent = MutableLiveData(false)
    val contactSent: LiveData<Boolean> = _contactSent

    fun sendContactMessage(
        name: String, email: String, phone: String,
        service: String, message: String
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            repository.sendContactMessage(name, email, phone, service, message)
                .onSuccess { _contactSent.value = true }
                .onFailure { e ->
                    Timber.e(e, "Send contact failed")
                    _errorMessage.value = "Could not send message. Please try again."
                }
            _isLoading.value = false
        }
    }

    fun clearContactSent() { _contactSent.value = false }

    /** Sign out from Firebase Auth. */
    fun signOut() { firebaseAuth.signOut() }
}
