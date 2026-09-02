package com.example.model

enum class UserRole {
    OWNER,
    OWNER_SYNDIC
}

data class User(
    val id: Long,
    val username: String,
    val fullName: String,
    val phoneNumber: String,
    val role: UserRole,
    val apartmentNumber: Int,
    val floor: Int
)

data class Apartment(
    val number: Int,
    val floor: Int,
    val ownerId: Long,
    val ownerName: String,
    val ownerPhone: String,
    val balanceOwed: Long = 0L
)

enum class ProjectStatus {
    PENDING_APPROVAL,
    APPROVED,
    REJECTED,
    COMPLETED
}

data class Project(
    val id: String,
    val title: String,
    val description: String,
    val totalCost: Long,
    val apartmentCount: Int = 40,
    val contributionPerApt: Long = totalCost / 40,
    val creatorSyndicId: Long,
    val creatorName: String,
    val approverSyndicId: Long? = null,
    val approverName: String? = null,
    val status: ProjectStatus = ProjectStatus.PENDING_APPROVAL,
    val rejectionReason: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val approvedAt: Long? = null,
    val totalCollected: Long = 0L
)

enum class TransactionType {
    OWNER_PAYMENT,
    EXPENSE,
    CORRECTION_CREDIT,
    CORRECTION_DEBIT
}

enum class TransactionStatus {
    PENDING_APPROVAL,
    LOCKED,
    REJECTED
}

enum class PaymentMethod {
    CASH,
    BANK_TRANSFER,
    CHECK
}

data class FinancialLedgerEntry(
    val txId: String,
    val type: TransactionType,
    val projectId: String? = null,
    val projectTitle: String? = null,
    val apartmentNumber: Int? = null,
    val ownerId: Long? = null,
    val ownerName: String? = null,
    val amount: Long,
    val paymentMethod: PaymentMethod = PaymentMethod.CASH,
    val creatorSyndicId: Long,
    val creatorName: String,
    val approverSyndicId: Long? = null,
    val approverName: String? = null,
    val status: TransactionStatus = TransactionStatus.LOCKED,
    val originalTxId: String? = null,
    val correctionReason: String? = null,
    val supplier: String? = null,
    val invoiceNumber: String? = null,
    val expenseCategory: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val approvedAt: Long? = null,
    val isPendingSync: Boolean = false
)

enum class MaintenanceCategory {
    ELEVATOR,
    ELECTRICITY,
    WATER,
    LIGHTING,
    ENTRANCE,
    DOORS,
    COMMON_AREAS,
    OTHER
}

enum class MaintenanceStatus {
    NEW,
    IN_PROGRESS,
    RESOLVED
}

data class MaintenanceReport(
    val id: String,
    val apartmentNumber: Int,
    val reporterUserId: Long,
    val reporterName: String,
    val category: MaintenanceCategory,
    val description: String,
    val photoUri: String? = null,
    val status: MaintenanceStatus = MaintenanceStatus.NEW,
    val syndicNotes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class ElevatorRecord(
    val id: String,
    val type: String,
    val technicianOrCompany: String,
    val contactPhone: String,
    val cost: Long = 0L,
    val invoiceNumber: String? = null,
    val maintenanceDate: Long,
    val nextScheduledDate: Long,
    val description: String
)

enum class AnnouncementCategory {
    URGENT,
    WATER_INTERRUPTION,
    ELEVATOR,
    CLEANING,
    GENERAL
}

data class Announcement(
    val id: String,
    val title: String,
    val content: String,
    val category: AnnouncementCategory,
    val authorName: String,
    val createdAt: Long = System.currentTimeMillis()
)

data class Meeting(
    val id: String,
    val title: String,
    val meetingDate: Long,
    val location: String,
    val description: String,
    val agenda: String,
    val decisions: String? = null
)

data class VotingSession(
    val id: String,
    val title: String,
    val description: String,
    val relatedProjectId: String? = null,
    val deadline: Long,
    val isClosed: Boolean = false,
    val createdByName: String,
    val createdAt: Long = System.currentTimeMillis()
)

enum class VoteChoice {
    YES,
    NO,
    ABSTAIN
}

data class VoteRecord(
    val id: Long = 0,
    val votingSessionId: String,
    val userId: Long,
    val userName: String,
    val apartmentNumber: Int,
    val choice: VoteChoice,
    val submittedAt: Long = System.currentTimeMillis()
)

data class DocumentItem(
    val id: String,
    val title: String,
    val category: String,
    val date: Long,
    val fileSizeBytes: Long,
    val invoiceNumber: String? = null
)

data class AuditLogEntry(
    val id: Long = 0,
    val actorName: String,
    val action: String,
    val entityType: String,
    val entityId: String,
    val details: String,
    val timestamp: Long = System.currentTimeMillis()
)
