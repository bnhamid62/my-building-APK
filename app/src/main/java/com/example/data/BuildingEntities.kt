package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: Long,
    val username: String,
    val passwordHash: String,
    val fullName: String,
    val phoneNumber: String,
    val role: String, // OWNER or SYNDIC
    val apartmentNumber: Int,
    val floor: Int
)

@Entity(tableName = "apartments")
data class ApartmentEntity(
    @PrimaryKey val number: Int,
    val floor: Int,
    val ownerId: Long,
    val ownerName: String,
    val ownerPhone: String
)

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val totalCost: Long,
    val apartmentCount: Int = 40,
    val contributionPerApt: Long,
    val creatorSyndicId: Long,
    val creatorName: String,
    val status: String, // ACTIVE, COMPLETED, CANCELLED
    val createdAt: Long
)

@Entity(tableName = "financial_ledger")
data class LedgerEntity(
    @PrimaryKey val txId: String,
    val type: String, // OWNER_PAYMENT, EXPENSE, CORRECTION_CREDIT, CORRECTION_DEBIT
    val projectId: String?,
    val projectTitle: String?,
    val apartmentNumber: Int?,
    val ownerId: Long?,
    val ownerName: String?,
    val amount: Long,
    val paymentMethod: String,
    val creatorSyndicId: Long,
    val creatorName: String,
    val status: String, // LOCKED
    val isCorrection: Boolean = false,
    val originalTxId: String?,
    val correctionReason: String?,
    val supplier: String?,
    val invoiceNumber: String?,
    val expenseCategory: String?,
    val createdAt: Long,
    val isPendingSync: Boolean = false
)

@Entity(tableName = "maintenance_reports")
data class MaintenanceEntity(
    @PrimaryKey val id: String,
    val apartmentNumber: Int,
    val reporterUserId: Long,
    val reporterName: String,
    val category: String,
    val description: String,
    val photoUri: String?,
    val status: String,
    val syndicNotes: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val isPendingSync: Boolean = false
)

@Entity(tableName = "elevator_records")
data class ElevatorEntity(
    @PrimaryKey val id: String,
    val type: String,
    val technicianOrCompany: String,
    val contactPhone: String,
    val cost: Long,
    val invoiceNumber: String?,
    val maintenanceDate: Long,
    val nextScheduledDate: Long,
    val description: String
)

@Entity(tableName = "announcements")
data class AnnouncementEntity(
    @PrimaryKey val id: String,
    val title: String,
    val content: String,
    val category: String,
    val authorName: String,
    val createdAt: Long
)

@Entity(tableName = "meetings")
data class MeetingEntity(
    @PrimaryKey val id: String,
    val title: String,
    val meetingDate: Long,
    val location: String,
    val description: String,
    val agenda: String,
    val decisions: String?
)

@Entity(tableName = "voting_sessions")
data class VotingEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val relatedProjectId: String?,
    val deadline: Long,
    val isClosed: Boolean,
    val createdByName: String,
    val createdAt: Long
)

@Entity(
    tableName = "voting_records"
)
data class VoteRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val votingSessionId: String,
    val userId: Long,
    val userName: String,
    val apartmentNumber: Int,
    val choice: String, // YES, NO, ABSTAIN
    val submittedAt: Long
)

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey val id: String,
    val title: String,
    val category: String,
    val date: Long,
    val fileSizeBytes: Long,
    val invoiceNumber: String?
)

@Entity(tableName = "audit_logs")
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val actorName: String,
    val action: String,
    val entityType: String,
    val entityId: String,
    val details: String,
    val timestamp: Long
)
