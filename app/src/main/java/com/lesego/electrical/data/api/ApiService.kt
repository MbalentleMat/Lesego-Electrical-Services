package com.lesego.electrical.data.api

import com.lesego.electrical.data.models.ApiResponse
import com.lesego.electrical.data.models.BookingRequest
import com.lesego.electrical.data.models.BookingResponse
import com.lesego.electrical.data.models.Contractor
import com.lesego.electrical.data.models.Job
import com.lesego.electrical.data.models.Service
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit interface defining the Lesego Electrical REST API.
 *
 * Base URL: https://api.lesesoelectric.co.za/v1/
 *
 * All endpoints require a Bearer token in the Authorization header,
 * which is automatically injected by [AuthInterceptor].
 *
 * @author Lesego Electrical Team
 */
interface ApiService {

    // ─────────────────────────────────────────────────────────────────────────
    //  Services
    // ─────────────────────────────────────────────────────────────────────────

    /** Returns all available electrical services. */
    @GET("services")
    suspend fun getServices(
        @Query("category") category: String? = null
    ): Response<ApiResponse<List<Service>>>

    /** Returns a single service by ID. */
    @GET("services/{id}")
    suspend fun getServiceById(
        @Path("id") serviceId: String
    ): Response<ApiResponse<Service>>

    // ─────────────────────────────────────────────────────────────────────────
    //  Bookings
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Submits a new service booking.
     * Returns the booking confirmation with reference number.
     */
    @POST("bookings")
    suspend fun createBooking(
        @Body request: BookingRequest,
        @Header("Authorization") token: String
    ): Response<ApiResponse<BookingResponse>>

    /** Retrieves all bookings for the authenticated user. */
    @GET("bookings/my")
    suspend fun getMyBookings(
        @Header("Authorization") token: String
    ): Response<ApiResponse<List<com.lesego.electrical.data.models.Booking>>>

    // ─────────────────────────────────────────────────────────────────────────
    //  Contractors
    // ─────────────────────────────────────────────────────────────────────────

    /** Returns a list of licensed contractors, optionally filtered. */
    @GET("contractors")
    suspend fun getContractors(
        @Query("area") area: String? = null,
        @Query("specialty") specialty: String? = null,
        @Query("search") search: String? = null
    ): Response<ApiResponse<List<Contractor>>>

    /** Returns a single contractor profile. */
    @GET("contractors/{id}")
    suspend fun getContractorById(
        @Path("id") contractorId: String
    ): Response<ApiResponse<Contractor>>

    // ─────────────────────────────────────────────────────────────────────────
    //  Jobs
    // ─────────────────────────────────────────────────────────────────────────

    /** Returns all open job listings. */
    @GET("jobs")
    suspend fun getJobs(): Response<ApiResponse<List<Job>>>

    // ─────────────────────────────────────────────────────────────────────────
    //  Contact / Support
    // ─────────────────────────────────────────────────────────────────────────

    /** Submits a contact / support message. */
    @POST("contact")
    suspend fun sendContactMessage(
        @Body body: Map<String, String>
    ): Response<ApiResponse<Unit>>
}
