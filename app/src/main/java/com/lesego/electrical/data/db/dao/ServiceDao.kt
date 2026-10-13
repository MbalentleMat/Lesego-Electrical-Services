package com.lesego.electrical.data.db.dao

import androidx.room.*
import com.lesego.electrical.data.models.Booking
import com.lesego.electrical.data.models.Service
import kotlinx.coroutines.flow.Flow

/** Data access object for the [Service] table. */
@Dao
interface ServiceDao {

    /** Returns all cached services as a reactive [Flow]. */
    @Query("SELECT * FROM services ORDER BY category, title")
    fun getAllServices(): Flow<List<Service>>

    /** Returns services filtered by category. */
    @Query("SELECT * FROM services WHERE category = :category ORDER BY title")
    fun getServicesByCategory(category: String): Flow<List<Service>>

    /** Inserts or replaces a list of services (used when syncing from API). */
    @Upsert
    suspend fun upsertServices(services: List<Service>)

    /** Deletes all services (called before a fresh sync). */
    @Query("DELETE FROM services")
    suspend fun deleteAll()
}

/** Data access object for the [Booking] table. */
@Dao
interface BookingDao {

    /** Returns all bookings for the current user, newest first. */
    @Query("SELECT * FROM bookings WHERE userId = :userId ORDER BY createdAt DESC")
    fun getBookingsForUser(userId: String): Flow<List<Booking>>

    /** Returns a single booking by its ID. */
    @Query("SELECT * FROM bookings WHERE id = :bookingId")
    suspend fun getBookingById(bookingId: String): Booking?

    /** Inserts or replaces a booking locally. */
    @Upsert
    suspend fun upsertBooking(booking: Booking)

    /** Inserts a list of bookings (sync from API). */
    @Upsert
    suspend fun upsertBookings(bookings: List<Booking>)

    /** Updates the status of a booking. */
    @Query("UPDATE bookings SET status = :newStatus WHERE id = :bookingId")
    suspend fun updateStatus(bookingId: String, newStatus: String)

    /** Deletes a booking record. */
    @Delete
    suspend fun deleteBooking(booking: Booking)
}
