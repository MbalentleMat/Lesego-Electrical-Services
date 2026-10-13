package com.lesego.electrical.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.lesego.electrical.data.api.RetrofitClient
import com.lesego.electrical.data.db.dao.BookingDao
import com.lesego.electrical.data.db.dao.ServiceDao
import com.lesego.electrical.data.models.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for all app data.
 *
 * Follows the repository pattern:
 *  - Local Room database is the primary source for lists (offline-first).
 *  - Remote API / Firestore used to fetch fresh data and sync to Room.
 *  - ViewModels observe Room Flows; they never call the API directly.
 *
 * @author Lesego Electrical Team
 */
@Singleton
class AppRepository @Inject constructor(
    private val serviceDao: ServiceDao,
    private val bookingDao: BookingDao,
    private val retrofitClient: RetrofitClient,
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {
    // ─────────────────────────────────────────────────────────────────────────
    //  Services
    // ─────────────────────────────────────────────────────────────────────────

    /** Observable stream of all cached services from Room. */
    fun getServicesFlow(): Flow<List<Service>> = serviceDao.getAllServices()

    /** Observable stream of services filtered by category. */
    fun getServicesByCategoryFlow(category: String): Flow<List<Service>> =
        serviceDao.getServicesByCategory(category)

    /**
     * Fetches services from the remote API and caches them in Room.
     * Returns a [Result] wrapping the fresh list or an error.
     */
    suspend fun refreshServices(): Result<List<Service>> = try {
        val snapshot = firestore.collection("services").get().await()
        val services = snapshot.documents.mapNotNull { doc ->
            val data = doc.data ?: return@mapNotNull null
            Service(
                id = doc.id,
                title = data["title"] as? String ?: return@mapNotNull null,
                description = data["description"] as? String ?: "",
                category = data["category"] as? String ?: "residential",
                priceFrom = (data["priceFrom"] as? Number)?.toDouble() ?: 0.0,
                iconRes = data["iconRes"] as? String ?: "",
                isAvailable = data["isAvailable"] as? Boolean ?: true
            )
        }
        if (services.isEmpty()) error("No services found. Run the Firebase seed script.")
        serviceDao.upsertServices(services)
        Result.success(services)
    } catch (e: Exception) {
        Timber.e(e, "Firestore service refresh failed")
        Result.failure(e)
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Bookings
    // ─────────────────────────────────────────────────────────────────────────

    /** Observable stream of bookings for the currently signed-in user. */
    fun getMyBookingsFlow(): Flow<List<Booking>> {
        val uid = firebaseAuth.currentUser?.uid ?: ""
        return bookingDao.getBookingsForUser(uid)
    }

    /**
     * Submits a new booking via the REST API.
     * On success, persists the booking locally in Room.
     */
    suspend fun createBooking(request: BookingRequest): Result<BookingResponse> = try {
        val uid = firebaseAuth.currentUser?.uid ?: error("Please sign in first")
        require(request.serviceId.isNotBlank()) { "Select a service" }
        require(request.customerName.isNotBlank()) { "Enter your name" }
        require(request.customerPhone.isNotBlank()) { "Enter your phone number" }
        require(request.serviceAddress.isNotBlank()) { "Enter a service address" }
        require(request.preferredDate.isNotBlank() && request.preferredTime.isNotBlank()) { "Select a date and time" }
        val serviceDoc = firestore.collection("services").document(request.serviceId).get().await()
        require(serviceDoc.exists()) { "Service is no longer available" }
        val ref = firestore.collection("bookings").document()
        val reference = "LE-" + ref.id.take(8).uppercase()
        val price = (serviceDoc.get("priceFrom") as? Number)?.toDouble() ?: 0.0
        val booking = Booking(
            id = ref.id, userId = uid, serviceId = request.serviceId,
            serviceName = serviceDoc.getString("title") ?: "Electrical service",
            customerName = request.customerName, customerEmail = firebaseAuth.currentUser?.email ?: "",
            customerPhone = request.customerPhone, serviceAddress = request.serviceAddress,
            preferredDate = request.preferredDate, preferredTime = request.preferredTime,
            notes = request.notes, status = "pending", price = price, reference = reference
        )

        val jobRef = firestore.collection("electrical_jobs")
            .document(ref.id)

        val batch = firestore.batch()

        batch.set(ref, mapOf(
            "userId" to uid,
            "serviceId" to booking.serviceId,
            "serviceName" to booking.serviceName,
            "customerName" to booking.customerName,
            "customerEmail" to booking.customerEmail,
            "customerPhone" to booking.customerPhone,
            "serviceAddress" to booking.serviceAddress,
            "preferredDate" to booking.preferredDate,
            "preferredTime" to booking.preferredTime,
            "notes" to booking.notes,
            "status" to "pending",
            "price" to price,
            "reference" to reference,
            "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        ))

        batch.set(jobRef, mapOf(
            "bookingId" to ref.id,
            "customerId" to uid,
            "serviceId" to booking.serviceId,
            "serviceName" to booking.serviceName,
            "contractorId" to "",
            "contractorName" to "",
            "serviceAddress" to booking.serviceAddress,
            "scheduledDate" to booking.preferredDate,
            "scheduledTime" to booking.preferredTime,
            "status" to "pending",
            "description" to booking.notes,
            "notes" to "",
            "estimatedCost" to price,
            "finalCost" to 0.0,
            "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            "completedAt" to null
        ))

        batch.commit().await()

        bookingDao.upsertBooking(booking)
        Result.success(BookingResponse(ref.id, reference, "pending", price, "Booking received"))
    } catch (e: Exception) {
        Timber.e(e, "Firestore booking failed")
        Result.failure(e)
    }

    suspend fun syncMyBookings(): Result<Unit> = try {
        val uid = firebaseAuth.currentUser?.uid ?: error("Please sign in")
        val snapshot = firestore.collection("bookings").whereEqualTo("userId", uid).get().await()
        val bookings = snapshot.documents.mapNotNull { doc ->
            val d = doc.data ?: return@mapNotNull null
            fun str(key: String) = d[key] as? String ?: ""
            Booking(doc.id, uid, str("serviceId"), str("serviceName"), str("customerName"),
                str("customerEmail"), str("customerPhone"), str("serviceAddress"),
                str("preferredDate"), str("preferredTime"), str("notes"), str("status"),
                (d["price"] as? Number)?.toDouble() ?: 0.0, str("reference"),
                (doc.getTimestamp("createdAt")?.toDate()?.time ?: 0L))
        }
        bookingDao.upsertBookings(bookings)
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }

    // ─────────────────────────────────────────────────────────────────────────
    //  Contractors
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Fetches contractors from the API with optional filters.
     * Contractors are not cached locally (they change frequently).
     */


    suspend fun getContractors(
        area: String? = null,
        specialty: String? = null,
        search: String? = null
    ): Result<List<Contractor>> = try {

        val docs = firestore.collection("contractors")
            .get()
            .await()
            .documents

        val all = docs.map { d ->

            // Safely read Firebase fields
            fun text(key: String): String {
                return d.get(key) as? String ?: ""
            }

            val locationValue = d.get("location")

            val contractorLocation = when (locationValue) {
                is String -> locationValue

                is Map<*, *> -> {
                    listOfNotNull(
                        locationValue["city"]?.toString(),
                        locationValue["province"]?.toString()
                    ).joinToString(", ")
                }

                else -> ""
            }

            Contractor(
                id = d.id,
                name = text("name"),
                specialty = text("specialty"),
                location = contractorLocation,

                yearsExperience =
                    (d.get("yearsExperience") as? Number)
                        ?.toInt() ?: 0,

                licenseNumber = text("licenseNumber"),

                rating =
                    (d.get("rating") as? Number)
                        ?.toFloat() ?: 0f,

                reviewCount =
                    (d.get("reviewCount") as? Number)
                        ?.toInt() ?: 0,

                profileImageUrl = text("profileImageUrl"),
                phone = text("phone"),
                email = text("email"),
                bio = text("bio"),

                areas = (d.get("areas") as? List<*>)
                    ?.filterIsInstance<String>()
                    ?: emptyList(),

                isAvailable =
                    d.get("isAvailable") as? Boolean ?: true
            )
        }

        val filtered = all.filter { contractor ->

            val matchesArea =
                area.isNullOrBlank() ||
                        area.equals("All Areas", ignoreCase = true) ||
                        contractor.location.contains(area, ignoreCase = true) ||
                        contractor.areas.any {
                            it.contains(area, ignoreCase = true)
                        }

            val matchesSpecialty =
                specialty.isNullOrBlank() ||
                        specialty.equals("All Specialties", ignoreCase = true) ||
                        contractor.specialty.contains(
                            specialty,
                            ignoreCase = true
                        )

            val matchesSearch =
                search.isNullOrBlank() ||
                        contractor.name.contains(search, ignoreCase = true) ||
                        contractor.specialty.contains(search, ignoreCase = true) ||
                        contractor.licenseNumber.contains(search, ignoreCase = true)

            matchesArea && matchesSpecialty && matchesSearch
        }

        Result.success(filtered)

    } catch (e: Exception) {

        Timber.e(e, "Failed to load contractors")
        Result.failure(e)

    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Jobs
    // ─────────────────────────────────────────────────────────────────────────

    /** Fetches open job listings from the API. */
    suspend fun getJobs(): Result<List<Job>> = try {
        val jobs = firestore.collection("jobs").get().await().documents.map { d ->
            Job(id = d.id, title = d.getString("title") ?: "",
                type = d.getString("type") ?: "", location = d.getString("location") ?: "",
                description = d.getString("description") ?: "",
                salaryRange = d.getString("salaryRange") ?: "",
                postedAt = d.getString("postedAt") ?: "")
        }
        Result.success(jobs)
    } catch (e: Exception) { Result.failure(e) }

    // ─────────────────────────────────────────────────────────────────────────
    //  User Profile (Firestore)
    // ─────────────────────────────────────────────────────────────────────────

    /** Fetches the current user's Firestore profile document. */
    suspend fun getCurrentUserProfile(): Result<User> {
        val uid = firebaseAuth.currentUser?.uid
            ?: return Result.failure(Exception("Not logged in"))
        return try {
            val doc = firestore.collection("users").document(uid).get().await()
            val user = doc.toObject(User::class.java)
                ?: return Result.failure(Exception("User document not found"))
            Result.success(user)
        } catch (e: Exception) {
            Timber.e(e, "Get user profile error")
            Result.failure(e)
        }
    }

    /** Updates fields in the Firestore user document. */
    suspend fun updateUserProfile(updates: Map<String, Any>): Result<Unit> {
        val uid = firebaseAuth.currentUser?.uid
            ?: return Result.failure(Exception("Not logged in"))
        return try {
            firestore.collection("users").document(uid).update(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Update user profile error")
            Result.failure(e)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Contact
    // ─────────────────────────────────────────────────────────────────────────

    /** Sends a contact / support message via the API. */
    suspend fun sendContactMessage(name: String, email: String, phone: String,
                                   service: String, message: String): Result<Unit> = try {
        require(name.isNotBlank() && email.isNotBlank() && message.isNotBlank()) { "Complete the required fields" }
        val uid = firebaseAuth.currentUser?.uid ?: error("Please sign in")
        firestore.collection("inquiries").add(mapOf(
            "userId" to uid, "name" to name, "email" to email,
            "phone" to phone, "service" to service, "message" to message,
            "status" to "new", "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )).await()
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }
}
