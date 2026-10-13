package com.lesego.electrical

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lesego.electrical.data.db.AppDatabase
import com.lesego.electrical.data.db.dao.BookingDao
import com.lesego.electrical.data.db.dao.ServiceDao
import com.lesego.electrical.data.models.Booking
import com.lesego.electrical.data.models.Service
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Unit tests for Room DAOs — [BookingDao] and [ServiceDao].
 *
 * Uses an in-memory Room database so no real disk storage is involved.
 * Tests run on the JVM with Robolectric or on a connected device.
 *
 * @author Lesego Electrical Team
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class BookingDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var bookingDao: BookingDao
    private lateinit var serviceDao: ServiceDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        bookingDao = db.bookingDao()
        serviceDao = db.serviceDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Service DAO tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun upsertServices_storesAndRetrievesAll() = runTest {
        val services = listOf(
            Service("s1", "Home Wiring", "Full home electrical", "residential", 1500.0),
            Service("s2", "Solar Panel Install", "Eco solar setup", "solar", 8000.0),
            Service("s3", "Circuit Breaker Repair", "Quick fix", "maintenance", 800.0)
        )
        serviceDao.upsertServices(services)

        val stored = serviceDao.getAllServices().first()
        assertEquals(3, stored.size)
    }

    @Test
    fun getServicesByCategory_returnsCorrectSubset() = runTest {
        val services = listOf(
            Service("s1", "Home Wiring", "Full home electrical", "residential", 1500.0),
            Service("s2", "Office Wiring", "Commercial setup", "commercial", 5000.0),
            Service("s3", "LED Upgrade", "Lighting upgrade", "residential", 600.0)
        )
        serviceDao.upsertServices(services)

        val residential = serviceDao.getServicesByCategory("residential").first()
        assertEquals(2, residential.size)
        assertTrue(residential.all { it.category == "residential" })
    }

    @Test
    fun deleteAll_removesAllServices() = runTest {
        serviceDao.upsertServices(listOf(
            Service("s1", "Wiring", "Desc", "residential", 1000.0)
        ))
        serviceDao.deleteAll()

        val stored = serviceDao.getAllServices().first()
        assertTrue(stored.isEmpty())
    }

    @Test
    fun upsertService_updatesExistingRecord() = runTest {
        val original = Service("s1", "Old Title", "Desc", "residential", 1000.0)
        serviceDao.upsertServices(listOf(original))

        val updated = Service("s1", "Updated Title", "New Desc", "residential", 1200.0)
        serviceDao.upsertServices(listOf(updated))

        val stored = serviceDao.getAllServices().first()
        assertEquals(1, stored.size)
        assertEquals("Updated Title", stored.first().title)
        assertEquals(1200.0, stored.first().priceFrom, 0.0)
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Booking DAO tests
    // ─────────────────────────────────────────────────────────────────────────

    private fun sampleBooking(id: String, userId: String, status: String = "pending") = Booking(
        id = id,
        userId = userId,
        serviceId = "s1",
        serviceName = "Home Wiring",
        customerName = "Enhle Test",
        customerEmail = "enhle@test.com",
        customerPhone = "0821234567",
        serviceAddress = "123 Main St, JHB",
        preferredDate = "2026-11-01",
        preferredTime = "10:00",
        status = status,
        reference = "LES-$id"
    )

    @Test
    fun upsertBooking_storesAndRetrievesForUser() = runTest {
        bookingDao.upsertBooking(sampleBooking("b1", "user1"))
        bookingDao.upsertBooking(sampleBooking("b2", "user1"))
        bookingDao.upsertBooking(sampleBooking("b3", "user2"))

        val user1Bookings = bookingDao.getBookingsForUser("user1").first()
        assertEquals(2, user1Bookings.size)
        assertTrue(user1Bookings.all { it.userId == "user1" })
    }

    @Test
    fun getBookingById_returnsCorrectBooking() = runTest {
        bookingDao.upsertBooking(sampleBooking("b1", "user1"))

        val found = bookingDao.getBookingById("b1")
        assertNotNull(found)
        assertEquals("b1", found!!.id)
        assertEquals("LES-b1", found.reference)
    }

    @Test
    fun getBookingById_returnsNullForMissingId() = runTest {
        val found = bookingDao.getBookingById("nonexistent")
        assertNull(found)
    }

    @Test
    fun updateStatus_changesStatusCorrectly() = runTest {
        bookingDao.upsertBooking(sampleBooking("b1", "user1", "pending"))

        bookingDao.updateStatus("b1", "confirmed")

        val updated = bookingDao.getBookingById("b1")
        assertEquals("confirmed", updated?.status)
    }

    @Test
    fun deleteBooking_removesFromDatabase() = runTest {
        val booking = sampleBooking("b1", "user1")
        bookingDao.upsertBooking(booking)

        bookingDao.deleteBooking(booking)

        val found = bookingDao.getBookingById("b1")
        assertNull(found)
    }

    @Test
    fun bookings_orderedByCreatedAtDescending() = runTest {
        val old = sampleBooking("b1", "user1").copy(createdAt = 1000L)
        val newer = sampleBooking("b2", "user1").copy(createdAt = 2000L)
        val newest = sampleBooking("b3", "user1").copy(createdAt = 3000L)

        bookingDao.upsertBookings(listOf(old, newer, newest))

        val bookings = bookingDao.getBookingsForUser("user1").first()
        assertEquals("b3", bookings[0].id)   // newest first
        assertEquals("b1", bookings[2].id)   // oldest last
    }
}
