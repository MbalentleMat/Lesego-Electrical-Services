package com.lesego.electrical.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

// ─────────────────────────────────────────────────────────────────────────────
//  User
// ─────────────────────────────────────────────────────────────────────────────

/** Represents a registered user of the app. */
data class User(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val role: String = "customer",   // "customer" | "contractor" | "admin"
    val profileImageUrl: String = ""
)

// ─────────────────────────────────────────────────────────────────────────────
//  Service  (Room + API model)
// ─────────────────────────────────────────────────────────────────────────────

/** An electrical service offering (stored locally in Room for offline use). */
@Entity(tableName = "services")
data class Service(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val category: String,     // residential | commercial | industrial | solar | maintenance
    val priceFrom: Double,
    val iconRes: String = "", // resource name for local icon
    val isAvailable: Boolean = true
)

// ─────────────────────────────────────────────────────────────────────────────
//  Booking  (Room)
// ─────────────────────────────────────────────────────────────────────────────

/** A service booking submitted by the customer. */
@Entity(tableName = "bookings")
data class Booking(
    @PrimaryKey val id: String,
    val userId: String,
    val serviceId: String,
    val serviceName: String,
    val customerName: String,
    val customerEmail: String,
    val customerPhone: String,
    val serviceAddress: String,
    val preferredDate: String,
    val preferredTime: String,
    val notes: String = "",
    val status: String = "pending",  // pending | confirmed | completed | cancelled
    val price: Double = 0.0,
    val reference: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

// ─────────────────────────────────────────────────────────────────────────────
//  Contractor
// ─────────────────────────────────────────────────────────────────────────────

/** A licensed electrical contractor in the directory. */
data class Contractor(
    val id: String = "",
    val name: String = "",
    val specialty: String = "",
    val location: String = "",
    val yearsExperience: Int = 0,
    val licenseNumber: String = "",
    val rating: Float = 0f,
    val reviewCount: Int = 0,
    val profileImageUrl: String = "",
    val phone: String = "",
    val email: String = "",
    val bio: String = "",
    val areas: List<String> = emptyList(),
    val isAvailable: Boolean = true
)

// ─────────────────────────────────────────────────────────────────────────────
//  Job Listing
// ─────────────────────────────────────────────────────────────────────────────

/** An open job position at Lesego Electrical. */
data class Job(
    val id: String = "",
    val title: String = "",
    val type: String = "",         // full-time | part-time | contract
    val location: String = "",
    val description: String = "",
    val requirements: List<String> = emptyList(),
    val responsibilities: List<String> = emptyList(),
    val salaryRange: String = "",
    val postedAt: String = ""
)

data class ElectricalJob(
    val id: String = "",
    val bookingId: String = "",
    val customerId: String = "",
    val serviceId: String = "",
    val serviceName: String = "",
    val contractorId: String = "",
    val contractorName: String = "",
    val serviceAddress: String = "",
    val scheduledDate: String = "",
    val scheduledTime: String = "",
    val status: String = "pending",
    val description: String = "",
    val notes: String = "",
    val estimatedCost: Double = 0.0,
    val finalCost: Double = 0.0,
    val createdAt: Long = 0L,
    val completedAt: Long = 0L
)

// ─────────────────────────────────────────────────────────────────────────────
//  Support Article / FAQ
// ─────────────────────────────────────────────────────────────────────────────

/** A help center FAQ item. */
data class SupportArticle(
    val id: String = "",
    val question: String = "",
    val answer: String = "",
    val category: String = ""   // general | technical | billing | appointments
)

// ─────────────────────────────────────────────────────────────────────────────
//  API Response wrappers
// ─────────────────────────────────────────────────────────────────────────────

data class ApiResponse<T>(
    val success: Boolean,
    val data: T?,
    val message: String = "",
    val errorCode: Int = 0
)

data class BookingRequest(
    val userId: String,
    val serviceId: String,
    val customerName: String,
    val customerEmail: String,
    val customerPhone: String,
    val serviceAddress: String,
    val preferredDate: String,
    val preferredTime: String,
    val notes: String = ""
)

data class BookingResponse(
    val bookingId: String,
    val reference: String,
    val status: String,
    val estimatedPrice: Double,
    val message: String
)
