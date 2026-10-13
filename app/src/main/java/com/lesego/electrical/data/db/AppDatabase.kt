package com.lesego.electrical.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.lesego.electrical.data.db.dao.BookingDao
import com.lesego.electrical.data.db.dao.ServiceDao
import com.lesego.electrical.data.models.Booking
import com.lesego.electrical.data.models.Service

/**
 * Room database for Lesego Electrical Services app.
 *
 * Provides offline caching of:
 *  - [Service] entities — so services page loads without internet
 *  - [Booking] entities — local record of submitted bookings
 *
 * Database version must be incremented and a Migration provided
 * whenever the schema changes.
 *
 * @author Lesego Electrical Team
 */
@Database(
    entities = [Service::class, Booking::class],
    version = 1,
    exportSchema = true    // exports schema JSON to /schemas for version tracking
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun serviceDao(): ServiceDao
    abstract fun bookingDao(): BookingDao

    companion object {
        const val DATABASE_NAME = "lesego_electrical.db"
    }
}
