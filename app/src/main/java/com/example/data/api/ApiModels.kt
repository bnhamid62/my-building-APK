package com.example.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class LoginRequest(
    @Json(name = "username") val username: String,
    @Json(name = "password") val password: String
)

@JsonClass(generateAdapter = true)
data class AuthResponse(
    @Json(name = "token") val token: String,
    @Json(name = "user") val user: UserDto
)

@JsonClass(generateAdapter = true)
data class UserDto(
    @Json(name = "id") val id: Long,
    @Json(name = "username") val username: String,
    @Json(name = "full_name") val fullName: String,
    @Json(name = "phone") val phone: String,
    @Json(name = "role") val role: String,
    @Json(name = "apartment_number") val apartmentNumber: Int,
    @Json(name = "floor") val floor: Int
)

@JsonClass(generateAdapter = true)
data class ApartmentDto(
    @Json(name = "number") val number: Int,
    @Json(name = "floor") val floor: Int,
    @Json(name = "floor_label") val floorLabel: String,
    @Json(name = "owner_name") val ownerName: String,
    @Json(name = "owner_phone") val ownerPhone: String,
    @Json(name = "owner_role") val ownerRole: String,
    @Json(name = "total_paid") val totalPaid: Long
)

@JsonClass(generateAdapter = true)
data class ProjectDto(
    @Json(name = "id") val id: String,
    @Json(name = "title") val title: String,
    @Json(name = "description") val description: String,
    @Json(name = "total_cost") val totalCost: Long,
    @Json(name = "apartment_count") val apartmentCount: Int,
    @Json(name = "contribution_per_apt") val contributionPerApt: Long,
    @Json(name = "creator_syndic_id") val creatorSyndicId: Long,
    @Json(name = "approver_syndic_id") val approverSyndicId: Long?,
    @Json(name = "status") val status: String,
    @Json(name = "rejection_reason") val rejectionReason: String?,
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "approved_at") val approvedAt: String?
)

@JsonClass(generateAdapter = true)
data class CreateProjectRequest(
    @Json(name = "title") val title: String,
    @Json(name = "description") val description: String,
    @Json(name = "total_cost") val totalCost: Long
)

@JsonClass(generateAdapter = true)
data class PaymentRequest(
    @Json(name = "apartment_number") val apartmentNumber: Int,
    @Json(name = "project_id") val projectId: String,
    @Json(name = "amount") val amount: Long,
    @Json(name = "payment_method") val paymentMethod: String,
    @Json(name = "idempotency_key") val idempotencyKey: String
)

@JsonClass(generateAdapter = true)
data class LedgerDto(
    @Json(name = "tx_id") val txId: String,
    @Json(name = "type") val type: String,
    @Json(name = "project_id") val projectId: String?,
    @Json(name = "apartment_number") val apartmentNumber: Int?,
    @Json(name = "owner_id") val ownerId: Long?,
    @Json(name = "amount") val amount: Long,
    @Json(name = "payment_method") val paymentMethod: String,
    @Json(name = "creator_syndic_id") val creatorSyndicId: Long,
    @Json(name = "approver_syndic_id") val approverSyndicId: Long?,
    @Json(name = "status") val status: String,
    @Json(name = "original_tx_id") val originalTxId: String?,
    @Json(name = "correction_reason") val correctionReason: String?,
    @Json(name = "supplier") val supplier: String?,
    @Json(name = "invoice_number") val invoiceNumber: String?,
    @Json(name = "expense_category") val expenseCategory: String?,
    @Json(name = "idempotency_key") val idempotencyKey: String,
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "approved_at") val approvedAt: String?
)

@JsonClass(generateAdapter = true)
data class LedgerResponse(
    @Json(name = "transactions") val transactions: List<LedgerDto>,
    @Json(name = "authoritative_balance") val authoritativeBalance: Long,
    @Json(name = "total_collected") val totalCollected: Long,
    @Json(name = "total_spent") val totalSpent: Long
)

@JsonClass(generateAdapter = true)
data class SyncPushItem(
    @Json(name = "local_id") val localId: String,
    @Json(name = "type") val type: String,
    @Json(name = "category") val category: String? = null,
    @Json(name = "description") val description: String? = null,
    @Json(name = "apartment_number") val apartmentNumber: Int? = null,
    @Json(name = "project_id") val projectId: String? = null,
    @Json(name = "amount") val amount: Long? = null,
    @Json(name = "payment_method") val paymentMethod: String? = null,
    @Json(name = "idempotency_key") val idempotencyKey: String? = null
)

@JsonClass(generateAdapter = true)
data class SyncPushRequest(
    @Json(name = "items") val items: List<SyncPushItem>
)

@JsonClass(generateAdapter = true)
data class SyncPushResultItem(
    @Json(name = "local_id") val localId: String,
    @Json(name = "server_id") val serverId: String?,
    @Json(name = "status") val status: String,
    @Json(name = "error") val error: String? = null
)

@JsonClass(generateAdapter = true)
data class SyncPushResponse(
    @Json(name = "synced") val synced: List<SyncPushResultItem>,
    @Json(name = "server_time") val serverTime: String
)
