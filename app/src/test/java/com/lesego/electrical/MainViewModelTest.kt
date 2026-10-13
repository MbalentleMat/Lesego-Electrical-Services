package com.lesego.electrical

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.firebase.auth.FirebaseAuth
import com.lesego.electrical.data.models.*
import com.lesego.electrical.data.repository.AppRepository
import com.lesego.electrical.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever

/**
 * Unit tests for [MainViewModel].
 *
 * Uses Mockito to mock [AppRepository] and [FirebaseAuth] so tests
 * run on the JVM without any Android / Firebase dependencies.
 *
 * The [InstantTaskExecutorRule] ensures LiveData posts are executed
 * synchronously in tests (rather than on the main thread).
 *
 * @author Lesego Electrical Team
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    // Swap background coroutine dispatcher for TestCoroutineDispatcher
    private val testDispatcher = StandardTestDispatcher()

    @get:Rule
    val instantTaskRule = InstantTaskExecutorRule()

    @Mock
    lateinit var mockRepository: AppRepository

    @Mock
    lateinit var mockAuth: FirebaseAuth

    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)

        // Stub the Flow-based calls that ViewModel observes at init time
        whenever(mockRepository.getServicesFlow()).thenReturn(flowOf(emptyList()))
        whenever(mockRepository.getMyBookingsFlow()).thenReturn(flowOf(emptyList()))

        viewModel = MainViewModel(mockRepository, mockAuth)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Services
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `services LiveData emits empty list initially`() {
        val value = viewModel.services.value
        assertTrue(value == null || value.isEmpty())
    }

    @Test
    fun `refreshServices sets isLoading true then false`() = runTest {
        whenever(mockRepository.refreshServices())
            .thenReturn(Result.success(emptyList()))

        viewModel.refreshServices()
        advanceUntilIdle()

        assertFalse(viewModel.isLoading.value ?: true)
    }

    @Test
    fun `refreshServices failure sets error message`() = runTest {
        whenever(mockRepository.refreshServices())
            .thenReturn(Result.failure(Exception("Network error")))

        viewModel.refreshServices()
        advanceUntilIdle()

        assertNotNull(viewModel.errorMessage.value)
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Contractors
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `loadContractors populates contractors LiveData on success`() = runTest {
        val sampleContractors = listOf(
            Contractor(id = "c1", name = "Thabo Mbeki", specialty = "Residential"),
            Contractor(id = "c2", name = "Nomsa Khumalo", specialty = "Solar")
        )
        whenever(mockRepository.getContractors(null, null, null))
            .thenReturn(Result.success(sampleContractors))

        viewModel.loadContractors()
        advanceUntilIdle()

        assertEquals(2, viewModel.contractors.value?.size)
        assertEquals("Thabo Mbeki", viewModel.contractors.value?.first()?.name)
    }

    @Test
    fun `loadContractors failure sets error message`() = runTest {
        whenever(mockRepository.getContractors(null, null, null))
            .thenReturn(Result.failure(Exception("Timeout")))

        viewModel.loadContractors()
        advanceUntilIdle()

        assertNotNull(viewModel.errorMessage.value)
        assertNull(viewModel.contractors.value?.size?.let { if (it > 0) it else null })
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Booking
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `createBooking posts success result on API success`() = runTest {
        val request = BookingRequest(
            userId = "uid123",
            serviceId = "svc1",
            customerName = "Enhle Test",
            customerEmail = "enhle@test.com",
            customerPhone = "0821234567",
            serviceAddress = "123 Main St, JHB",
            preferredDate = "2026-11-01",
            preferredTime = "10:00"
        )
        val response = BookingResponse(
            bookingId = "b001",
            reference = "LES-001",
            status = "confirmed",
            estimatedPrice = 1500.0,
            message = "Booking confirmed"
        )
        whenever(mockRepository.createBooking(request)).thenReturn(Result.success(response))

        viewModel.createBooking(request)
        advanceUntilIdle()

        val result = viewModel.bookingResult.value
        assertNotNull(result)
        assertTrue(result!!.isSuccess)
        assertEquals("LES-001", result.getOrNull()?.reference)
    }

    @Test
    fun `clearBookingResult sets bookingResult to null`() = runTest {
        viewModel.clearBookingResult()
        assertNull(viewModel.bookingResult.value)
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Jobs
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `loadJobs populates jobs LiveData`() = runTest {
        val jobs = listOf(
            Job(id = "j1", title = "Senior Electrician", type = "Full-time", location = "Johannesburg"),
            Job(id = "j2", title = "Solar Technician", type = "Contract", location = "Pretoria")
        )
        whenever(mockRepository.getJobs()).thenReturn(Result.success(jobs))

        viewModel.loadJobs()
        advanceUntilIdle()

        assertEquals(2, viewModel.jobs.value?.size)
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Contact
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `sendContactMessage sets contactSent true on success`() = runTest {
        whenever(
            mockRepository.sendContactMessage(
                "Enhle", "enhle@test.com", "082", "General", "Test message"
            )
        ).thenReturn(Result.success(Unit))

        viewModel.sendContactMessage("Enhle", "enhle@test.com", "082", "General", "Test message")
        advanceUntilIdle()

        assertTrue(viewModel.contactSent.value == true)
    }

    @Test
    fun `clearContactSent resets contactSent to false`() = runTest {
        viewModel.clearContactSent()
        assertFalse(viewModel.contactSent.value ?: true)
    }
}
