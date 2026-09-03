package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BuildingDao {

    // --- Users & Apartments ---
    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    suspend fun getUserByUsername(username: String): UserEntity?

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    suspend fun getUserById(id: Long): UserEntity?

    @Query("SELECT * FROM users ORDER BY apartmentNumber ASC")
    fun getAllUsersFlow(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users ORDER BY apartmentNumber ASC")
    suspend fun getAllUsers(): List<UserEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsers(users: List<UserEntity>)

    @Query("SELECT * FROM apartments ORDER BY number ASC")
    fun getAllApartmentsFlow(): Flow<List<ApartmentEntity>>

    @Query("SELECT * FROM apartments ORDER BY number ASC")
    suspend fun getAllApartments(): List<ApartmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApartments(apartments: List<ApartmentEntity>)

    // --- Projects ---
    @Query("SELECT * FROM projects ORDER BY createdAt DESC")
    fun getAllProjectsFlow(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    suspend fun getProjectById(id: String): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ProjectEntity)

    @Update
    suspend fun updateProject(project: ProjectEntity)

    // --- Financial Ledger (Immutable Invariant) ---
    @Query("SELECT * FROM financial_ledger ORDER BY createdAt DESC")
    fun getAllLedgerFlow(): Flow<List<LedgerEntity>>

    @Query("SELECT * FROM financial_ledger WHERE txId = :txId LIMIT 1")
    suspend fun getLedgerEntryById(txId: String): LedgerEntity?

    @Query("SELECT * FROM financial_ledger WHERE status = 'LOCKED' ORDER BY createdAt DESC")
    fun getLockedLedgerFlow(): Flow<List<LedgerEntity>>

    @Query("SELECT * FROM financial_ledger WHERE isPendingSync = 1 ORDER BY createdAt DESC")
    fun getPendingSyncLedgerFlow(): Flow<List<LedgerEntity>>

    @Query("SELECT * FROM financial_ledger WHERE isPendingSync = 1")
    suspend fun getPendingSyncLedgerEntries(): List<LedgerEntity>

    @Query("SELECT * FROM maintenance_reports WHERE isPendingSync = 1")
    suspend fun getPendingSyncMaintenanceReports(): List<MaintenanceEntity>

    @Query("SELECT * FROM financial_ledger WHERE apartmentNumber = :aptNumber AND status = 'LOCKED' ORDER BY createdAt DESC")
    fun getLedgerForApartmentFlow(aptNumber: Int): Flow<List<LedgerEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLedgerEntry(entry: LedgerEntity)

    @Update
    suspend fun updateLedgerEntry(entry: LedgerEntity)

    // --- Maintenance Reports ---
    @Query("SELECT * FROM maintenance_reports ORDER BY createdAt DESC")
    fun getAllMaintenanceFlow(): Flow<List<MaintenanceEntity>>

    @Query("SELECT * FROM maintenance_reports WHERE apartmentNumber = :aptNumber ORDER BY createdAt DESC")
    fun getMaintenanceForApartmentFlow(aptNumber: Int): Flow<List<MaintenanceEntity>>

    @Query("SELECT * FROM maintenance_reports WHERE id = :id LIMIT 1")
    suspend fun getMaintenanceById(id: String): MaintenanceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMaintenance(report: MaintenanceEntity)

    @Update
    suspend fun updateMaintenance(report: MaintenanceEntity)

    // --- Elevator ---
    @Query("SELECT * FROM elevator_records ORDER BY maintenanceDate DESC")
    fun getAllElevatorRecordsFlow(): Flow<List<ElevatorEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertElevatorRecord(record: ElevatorEntity)

    // --- Announcements ---
    @Query("SELECT * FROM announcements ORDER BY createdAt DESC")
    fun getAllAnnouncementsFlow(): Flow<List<AnnouncementEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnnouncement(announcement: AnnouncementEntity)

    // --- Meetings ---
    @Query("SELECT * FROM meetings ORDER BY meetingDate DESC")
    fun getAllMeetingsFlow(): Flow<List<MeetingEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeeting(meeting: MeetingEntity)

    // --- Voting ---
    @Query("SELECT * FROM voting_sessions ORDER BY createdAt DESC")
    fun getAllVotingSessionsFlow(): Flow<List<VotingEntity>>

    @Query("SELECT * FROM voting_sessions WHERE id = :id LIMIT 1")
    suspend fun getVotingSessionById(id: String): VotingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVotingSession(session: VotingEntity)

    @Update
    suspend fun updateVotingSession(session: VotingEntity)

    @Query("SELECT * FROM voting_records WHERE votingSessionId = :sessionId")
    fun getVotesForSessionFlow(sessionId: String): Flow<List<VoteRecordEntity>>

    @Query("SELECT * FROM voting_records WHERE votingSessionId = :sessionId")
    suspend fun getVotesForSession(sessionId: String): List<VoteRecordEntity>

    @Query("SELECT * FROM voting_records WHERE votingSessionId = :sessionId AND userId = :userId LIMIT 1")
    suspend fun getUserVote(sessionId: String, userId: Long): VoteRecordEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertVoteRecord(record: VoteRecordEntity)

    // --- Documents ---
    @Query("SELECT * FROM documents ORDER BY date DESC")
    fun getAllDocumentsFlow(): Flow<List<DocumentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: DocumentEntity)

    // --- Audit Log (Append Only) ---
    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC")
    fun getAllAuditLogsFlow(): Flow<List<AuditLogEntity>>

    @Insert
    suspend fun insertAuditLog(log: AuditLogEntity)
}
