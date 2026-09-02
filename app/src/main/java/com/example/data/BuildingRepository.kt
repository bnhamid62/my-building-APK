package com.example.data

import com.example.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class BuildingRepository(private val dao: BuildingDao) {

    suspend fun initializeSeedDataIfNeeded() {
        val existingUsers = dao.getAllUsers()
        if (existingUsers.isNotEmpty()) return

        // 40 Algerian Owners (2 Syndics + 38 Owners)
        val ownerNames = listOf(
            "Ahmed Benali",      // Apt 1 (Syndic 1)
            "Karim Mansouri",    // Apt 2 (Syndic 2)
            "Amina Haddad",      // Apt 3
            "Yacine Brahimi",    // Apt 4
            "Fatima Zohra Kaci", // Apt 5
            "Omar Belhadj",      // Apt 6
            "Meriem Saidi",      // Apt 7
            "Rachid Cherif",     // Apt 8
            "Nadia Bouzid",      // Apt 9
            "Tarek Madani",      // Apt 10
            "Samir Chaouche",    // Apt 11
            "Houria Meziane",    // Apt 12
            "Kamel Ferhat",      // Apt 13
            "Leila Amrani",      // Apt 14
            "Mustapha Benaissa", // Apt 15
            "Zineb Touati",      // Apt 16
            "Sofiane Zerrouki",  // Apt 17
            "Souad Taleb",       // Apt 18
            "Djamel Hammoudi",   // Apt 19
            "Fatiha Mokrani",    // Apt 20
            "Mohamed Larbi",     // Apt 21
            "Salima Djebbar",    // Apt 22
            "Hassane Belkacem",  // Apt 23
            "Khadidja Guellil",  // Apt 24
            "Abdelkader Senoussi",// Apt 25
            "Nawal Kerboua",     // Apt 26
            "Ali Bouamama",      // Apt 27
            "Assia Benslimane",  // Apt 28
            "Bilal Dahmani",     // Apt 29
            "Farida Slimani",    // Apt 30
            "Reda Zitouni",      // Apt 31
            "Lamia Ould Ali",    // Apt 32
            "Mourad Khelifi",    // Apt 33
            "Nassima Boukhalfa", // Apt 34
            "Walid Hamidi",      // Apt 35
            "Yasmina Benbouzid", // Apt 36
            "Hakim Bahloul",     // Apt 37
            "Chafika Sahli",     // Apt 38
            "Rabah Guendouz",    // Apt 39
            "Zahia Boutaleb"     // Apt 40
        )

        val userEntities = mutableListOf<UserEntity>()
        val apartmentEntities = mutableListOf<ApartmentEntity>()

        for (i in 1..40) {
            val name = ownerNames[i - 1]
            val floor = (i - 1) / 4 // Floors 0 to 9 (4 apartments per floor)
            val username = "apt$i"
            val isSyndic = (i == 1 || i == 2)
            val role = if (isSyndic) "OWNER_SYNDIC" else "OWNER"
            val phone = "0550${100000 + i}"

            userEntities.add(
                UserEntity(
                    id = i.toLong(),
                    username = username,
                    passwordHash = "amarati123", // standard initial password for all apartments
                    fullName = name,
                    phoneNumber = phone,
                    role = role,
                    apartmentNumber = i,
                    floor = floor
                )
            )

            apartmentEntities.add(
                ApartmentEntity(
                    number = i,
                    floor = floor,
                    ownerId = i.toLong(),
                    ownerName = name,
                    ownerPhone = phone
                )
            )
        }

        dao.insertUsers(userEntities)
        dao.insertApartments(apartmentEntities)

        // Seed Project 1: Elevator Maintenance & Modernization
        val p1Id = "PRJ-2026-001"
        dao.insertProject(
            ProjectEntity(
                id = p1Id,
                title = "Réparation et Modernisation de l'Ascenseur",
                description = "Changement des câbles de traction, révision du moteur et mise aux normes du tableau de commande.",
                totalCost = 200000L,
                apartmentCount = 40,
                contributionPerApt = 5000L,
                creatorSyndicId = 1L,
                creatorName = "Ahmed Benali (Syndic 1)",
                approverSyndicId = 2L,
                approverName = "Karim Mansouri (Syndic 2)",
                status = "APPROVED",
                rejectionReason = null,
                createdAt = System.currentTimeMillis() - (15L * 24 * 3600 * 1000),
                approvedAt = System.currentTimeMillis() - (14L * 24 * 3600 * 1000)
            )
        )

        // Seed Project 2: Staircase Painting & LED Lighting
        val p2Id = "PRJ-2026-002"
        dao.insertProject(
            ProjectEntity(
                id = p2Id,
                title = "Peinture et Rénovation de la Cage d'Escalier",
                description = "Peinture lavable sur les 9 étages, installation de luminaires LED avec détecteurs de mouvement.",
                totalCost = 120000L,
                apartmentCount = 40,
                contributionPerApt = 3000L,
                creatorSyndicId = 2L,
                creatorName = "Karim Mansouri (Syndic 2)",
                approverSyndicId = 1L,
                approverName = "Ahmed Benali (Syndic 1)",
                status = "APPROVED",
                rejectionReason = null,
                createdAt = System.currentTimeMillis() - (7L * 24 * 3600 * 1000),
                approvedAt = System.currentTimeMillis() - (6L * 24 * 3600 * 1000)
            )
        )

        // Seed Locked Payments for apartments 1 through 28 for Project 1
        var txCounter = 100
        for (apt in 1..28) {
            txCounter++
            val txId = "TX-2026-${String.format(Locale.US, "%06d", txCounter)}"
            dao.insertLedgerEntry(
                LedgerEntity(
                    txId = txId,
                    type = "OWNER_PAYMENT",
                    projectId = p1Id,
                    projectTitle = "Réparation et Modernisation de l'Ascenseur",
                    apartmentNumber = apt,
                    ownerId = apt.toLong(),
                    ownerName = ownerNames[apt - 1],
                    amount = 5000L,
                    paymentMethod = if (apt % 2 == 0) "CASH" else "BANK_TRANSFER",
                    creatorSyndicId = if (apt % 2 == 0) 1L else 2L,
                    creatorName = if (apt % 2 == 0) "Ahmed Benali" else "Karim Mansouri",
                    approverSyndicId = null,
                    approverName = null,
                    status = "LOCKED",
                    originalTxId = null,
                    correctionReason = null,
                    supplier = null,
                    invoiceNumber = null,
                    expenseCategory = null,
                    createdAt = System.currentTimeMillis() - ((30 - apt) * 12L * 3600 * 1000),
                    approvedAt = System.currentTimeMillis() - ((30 - apt) * 12L * 3600 * 1000),
                    isPendingSync = false
                )
            )
        }

        // Seed Locked Approved Expense 1
        txCounter++
        dao.insertLedgerEntry(
            LedgerEntity(
                txId = "TX-2026-${String.format(Locale.US, "%06d", txCounter)}",
                type = "EXPENSE",
                projectId = p1Id,
                projectTitle = "Réparation et Modernisation de l'Ascenseur",
                apartmentNumber = null,
                ownerId = null,
                ownerName = null,
                amount = 45000L,
                paymentMethod = "BANK_TRANSFER",
                creatorSyndicId = 1L,
                creatorName = "Ahmed Benali",
                approverSyndicId = 2L,
                approverName = "Karim Mansouri",
                status = "LOCKED",
                originalTxId = null,
                correctionReason = null,
                supplier = "SARL Ascenseurs d'Alger",
                invoiceNumber = "FAC-2026-088",
                expenseCategory = "Ascenseur",
                createdAt = System.currentTimeMillis() - (5L * 24 * 3600 * 1000),
                approvedAt = System.currentTimeMillis() - (4L * 24 * 3600 * 1000),
                isPendingSync = false
            )
        )

        // Seed Elevator Record
        dao.insertElevatorRecord(
            ElevatorEntity(
                id = "ELV-01",
                type = "Révision annuelle et graissage des glissières",
                technicianOrCompany = "SARL Ascenseurs d'Alger (Technicien: M. Brahim)",
                contactPhone = "021 54 22 10 / 0555 12 34 56",
                cost = 45000L,
                invoiceNumber = "FAC-2026-088",
                maintenanceDate = System.currentTimeMillis() - (5L * 24 * 3600 * 1000),
                nextScheduledDate = System.currentTimeMillis() + (25L * 24 * 3600 * 1000),
                description = "Changement de 2 galets de guidage et vérification du parachute de sécurité. Conforme aux normes."
            )
        )

        // Seed Announcements
        dao.insertAnnouncement(
            AnnouncementEntity(
                id = "ANN-01",
                title = "Coupure d'eau programmée par la SEAAL",
                content = "La SEAAL informe les résidents d'une coupure temporaire d'eau pour travaux sur la conduite principale ce Jeudi de 09h00 à 17h00. Veuillez prendre vos dispositions.",
                category = "WATER_INTERRUPTION",
                authorName = "Ahmed Benali (Syndic 1)",
                createdAt = System.currentTimeMillis() - (2L * 24 * 3600 * 1000)
            )
        )
        dao.insertAnnouncement(
            AnnouncementEntity(
                id = "ANN-02",
                title = "Campagne de nettoyage des parties communes",
                content = "L'équipe de nettoyage interviendra ce Samedi matin sur les 9 étages et le parking extérieur. Merci de ne laisser aucun encombrant dans les paliers.",
                category = "CLEANING",
                authorName = "Karim Mansouri (Syndic 2)",
                createdAt = System.currentTimeMillis() - (4L * 24 * 3600 * 1000)
            )
        )

        // Seed Meeting
        dao.insertMeeting(
            MeetingEntity(
                id = "MTG-2026-01",
                title = "Assemblée Générale Ordinaire 2026",
                meetingDate = System.currentTimeMillis() + (10L * 24 * 3600 * 1000),
                location = "Hall d'entrée du bâtiment",
                description = "Bilan financier de l'année écoulée, approbation des travaux de sécurisation et renouvellement du contrat d'entretien de l'ascenseur.",
                agenda = "1. Rapport moral et financier\n2. Présentation du devis caméras\n3. Vote des résolutions\n4. Questions diverses",
                decisions = "À venir"
            )
        )

        // Seed Voting Session with real transparent votes
        val voteSessionId = "VOTE-2026-01"
        dao.insertVotingSession(
            VotingEntity(
                id = voteSessionId,
                title = "Installation d'un système de vidéosurveillance 8 caméras",
                description = "Installation de 8 caméras haute définition (hall, entrées, parking et ascenseur) avec enregistreur sécurisé pour un coût global de 160.000 DZD (4.000 DZD / appartement).",
                relatedProjectId = null,
                deadline = System.currentTimeMillis() + (7L * 24 * 3600 * 1000),
                isClosed = false,
                createdByName = "Ahmed Benali (Syndic)",
                createdAt = System.currentTimeMillis() - (3L * 24 * 3600 * 1000)
            )
        )

        // Seed individual votes for apartments 1 to 18
        for (apt in 1..18) {
            val choice = when {
                apt in listOf(1, 2, 4, 5, 7, 8, 10, 11, 13, 14, 16, 17) -> "YES"
                apt in listOf(3, 9, 15) -> "NO"
                else -> "ABSTAIN"
            }
            dao.insertVoteRecord(
                VoteRecordEntity(
                    votingSessionId = voteSessionId,
                    userId = apt.toLong(),
                    userName = ownerNames[apt - 1],
                    apartmentNumber = apt,
                    choice = choice,
                    submittedAt = System.currentTimeMillis() - ((20 - apt) * 3600 * 1000)
                )
            )
        }

        // Seed Documents
        dao.insertDocument(
            DocumentEntity(
                id = "DOC-01",
                title = "Facture SARL Ascenseurs d'Alger",
                category = "Factures",
                date = System.currentTimeMillis() - (5L * 24 * 3600 * 1000),
                fileSizeBytes = 245000L,
                invoiceNumber = "FAC-2026-088"
            )
        )
        dao.insertDocument(
            DocumentEntity(
                id = "DOC-02",
                title = "Contrat annuel d'entretien ascenseur 2026",
                category = "Contrats",
                date = System.currentTimeMillis() - (60L * 24 * 3600 * 1000),
                fileSizeBytes = 890000L,
                invoiceNumber = "CTR-2026-ASC"
            )
        )
        dao.insertDocument(
            DocumentEntity(
                id = "DOC-03",
                title = "Règlement intérieur de la copropriété",
                category = "Règlements",
                date = System.currentTimeMillis() - (120L * 24 * 3600 * 1000),
                fileSizeBytes = 512000L,
                invoiceNumber = "DOC-REG-01"
            )
        )

        // Seed Initial Maintenance Report
        dao.insertMaintenance(
            MaintenanceEntity(
                id = "REP-2026-0001",
                apartmentNumber = 14,
                reporterUserId = 14L,
                reporterName = "Leila Amrani",
                category = "LIGHTING",
                description = "L'ampoule du palier du 3ème étage clignote et s'éteint constamment.",
                photoUri = null,
                status = "IN_PROGRESS",
                syndicNotes = "Ampoule de rechange commandée, remplacement prévu demain.",
                createdAt = System.currentTimeMillis() - (2L * 24 * 3600 * 1000),
                updatedAt = System.currentTimeMillis() - (1L * 24 * 3600 * 1000)
            )
        )

        // Seed Initial Audit Log
        dao.insertAuditLog(
            AuditLogEntity(
                actorName = "Ahmed Benali (Syndic 1)",
                action = "INITIALIZATION",
                entityType = "SYSTEM",
                entityId = "BLD-01",
                details = "Initialisation de la copropriété : 40 appartements, 9 étages, 2 syndics.",
                timestamp = System.currentTimeMillis() - (20L * 24 * 3600 * 1000)
            )
        )
    }

    // --- Authentication ---
    suspend fun login(username: String, password: String): User? {
        val userEntity = dao.getUserByUsername(username.trim().lowercase(Locale.ROOT)) ?: return null
        if (userEntity.passwordHash == password.trim()) {
            return userEntity.toUser()
        }
        return null
    }

    suspend fun getUserById(id: Long): User? {
        return dao.getUserById(id)?.toUser()
    }

    fun getAllUsersFlow(): Flow<List<User>> = dao.getAllUsersFlow().map { list ->
        list.map { it.toUser() }
    }

    // --- Projects & Double Approval ---
    fun getAllProjectsFlow(): Flow<List<Project>> = dao.getAllProjectsFlow().map { list ->
        list.map { entity ->
            val collected = calculateProjectCollected(entity.id)
            entity.toProject(collected)
        }
    }

    private suspend fun calculateProjectCollected(projectId: String): Long {
        val entries = dao.getLockedLedgerFlow().first()
        return entries.filter { it.projectId == projectId && it.type == "OWNER_PAYMENT" }.sumOf { it.amount }
    }

    suspend fun createFinancialProject(
        title: String,
        description: String,
        totalCost: Long,
        creator: User
    ): Result<String> {
        if (creator.role != UserRole.OWNER_SYNDIC) {
            return Result.failure(IllegalStateException("Seul un syndic peut créer un projet financier."))
        }
        val id = "PRJ-${SimpleDateFormat("yyyy", Locale.US).format(Date())}-${UUID.randomUUID().toString().take(6).uppercase(Locale.US)}"
        val entity = ProjectEntity(
            id = id,
            title = title,
            description = description,
            totalCost = totalCost,
            apartmentCount = 40,
            contributionPerApt = totalCost / 40,
            creatorSyndicId = creator.id,
            creatorName = creator.fullName,
            approverSyndicId = null,
            approverName = null,
            status = "PENDING_APPROVAL",
            rejectionReason = null,
            createdAt = System.currentTimeMillis(),
            approvedAt = null
        )
        dao.insertProject(entity)

        dao.insertAuditLog(
            AuditLogEntity(
                actorName = creator.fullName,
                action = "CREATE_PROJECT",
                entityType = "PROJECT",
                entityId = id,
                details = "Création du projet: $title ($totalCost DZD) - En attente d'approbation du 2ème syndic.",
                timestamp = System.currentTimeMillis()
            )
        )
        return Result.success(id)
    }

    suspend fun approveFinancialProject(
        projectId: String,
        approver: User,
        approve: Boolean,
        reason: String? = null
    ): Result<Unit> {
        if (approver.role != UserRole.OWNER_SYNDIC) {
            return Result.failure(IllegalStateException("Seul un syndic peut approuver un projet."))
        }
        val project = dao.getProjectById(projectId)
            ?: return Result.failure(IllegalArgumentException("Projet introuvable."))

        // CRITICAL BUSINESS RULE: A Syndic CANNOT approve their own request!
        if (project.creatorSyndicId == approver.id) {
            return Result.failure(IllegalStateException("Règle de sécurité : Un syndic ne peut pas approuver son propre projet. L'approbation du second syndic est obligatoire."))
        }

        val updated = project.copy(
            approverSyndicId = approver.id,
            approverName = approver.fullName,
            status = if (approve) "APPROVED" else "REJECTED",
            rejectionReason = if (!approve) reason else null,
            approvedAt = if (approve) System.currentTimeMillis() else null
        )
        dao.updateProject(updated)

        dao.insertAuditLog(
            AuditLogEntity(
                actorName = approver.fullName,
                action = if (approve) "APPROVE_PROJECT" else "REJECT_PROJECT",
                entityType = "PROJECT",
                entityId = projectId,
                details = if (approve) "Approbation du projet par le 2ème syndic." else "Rejet du projet: $reason",
                timestamp = System.currentTimeMillis()
            )
        )
        return Result.success(Unit)
    }

    // --- Financial Ledger & Double Approval for Expenses ---
    fun getAllLedgerFlow(): Flow<List<FinancialLedgerEntry>> = dao.getAllLedgerFlow().map { list ->
        list.map { it.toFinancialLedgerEntry() }
    }

    fun getLockedLedgerFlow(): Flow<List<FinancialLedgerEntry>> = dao.getLockedLedgerFlow().map { list ->
        list.map { it.toFinancialLedgerEntry() }
    }

    fun getPendingApprovalLedgerFlow(): Flow<List<FinancialLedgerEntry>> = dao.getPendingApprovalLedgerFlow().map { list ->
        list.map { it.toFinancialLedgerEntry() }
    }

    fun getLedgerForApartmentFlow(aptNumber: Int): Flow<List<FinancialLedgerEntry>> = dao.getLedgerForApartmentFlow(aptNumber).map { list ->
        list.map { it.toFinancialLedgerEntry() }
    }

    /**
     * OWNER PAYMENT: Recorded directly by a Syndic.
     * Does NOT require double approval. Becomes LOCKED immediately.
     */
    suspend fun recordOwnerPayment(
        projectId: String,
        projectTitle: String,
        apartmentNumber: Int,
        ownerId: Long,
        ownerName: String,
        amount: Long,
        paymentMethod: PaymentMethod,
        recorder: User,
        isOffline: Boolean = false
    ): Result<String> {
        if (recorder.role != UserRole.OWNER_SYNDIC) {
            return Result.failure(IllegalStateException("Seul un syndic peut enregistrer un paiement."))
        }
        val txYear = SimpleDateFormat("yyyy", Locale.US).format(Date())
        val randomSuffix = String.format(Locale.US, "%06d", (100000..999999).random())
        val txId = "TX-$txYear-$randomSuffix"

        val entity = LedgerEntity(
            txId = txId,
            type = "OWNER_PAYMENT",
            projectId = projectId,
            projectTitle = projectTitle,
            apartmentNumber = apartmentNumber,
            ownerId = ownerId,
            ownerName = ownerName,
            amount = amount,
            paymentMethod = paymentMethod.name,
            creatorSyndicId = recorder.id,
            creatorName = recorder.fullName,
            approverSyndicId = null,
            approverName = null,
            status = "LOCKED",
            originalTxId = null,
            correctionReason = null,
            supplier = null,
            invoiceNumber = null,
            expenseCategory = null,
            createdAt = System.currentTimeMillis(),
            approvedAt = System.currentTimeMillis(),
            isPendingSync = isOffline
        )
        dao.insertLedgerEntry(entity)

        dao.insertAuditLog(
            AuditLogEntity(
                actorName = recorder.fullName,
                action = "RECORD_PAYMENT",
                entityType = "TRANSACTION",
                entityId = txId,
                details = "Paiement enregistré: Apt $apartmentNumber ($ownerName) - $amount DZD pour $projectTitle. Verrouillé immédiatement.",
                timestamp = System.currentTimeMillis()
            )
        )
        return Result.success(txId)
    }

    /**
     * EXPENSE: Requires double approval from the second Syndic before becoming LOCKED.
     */
    suspend fun createExpense(
        projectId: String?,
        projectTitle: String?,
        category: String,
        description: String,
        amount: Long,
        supplier: String,
        invoiceNumber: String,
        paymentMethod: PaymentMethod,
        creator: User
    ): Result<String> {
        if (creator.role != UserRole.OWNER_SYNDIC) {
            return Result.failure(IllegalStateException("Seul un syndic peut créer une dépense."))
        }
        val txYear = SimpleDateFormat("yyyy", Locale.US).format(Date())
        val randomSuffix = String.format(Locale.US, "%06d", (100000..999999).random())
        val txId = "TX-$txYear-$randomSuffix"

        val entity = LedgerEntity(
            txId = txId,
            type = "EXPENSE",
            projectId = projectId,
            projectTitle = projectTitle ?: description,
            apartmentNumber = null,
            ownerId = null,
            ownerName = null,
            amount = amount,
            paymentMethod = paymentMethod.name,
            creatorSyndicId = creator.id,
            creatorName = creator.fullName,
            approverSyndicId = null,
            approverName = null,
            status = "PENDING_APPROVAL",
            originalTxId = null,
            correctionReason = null,
            supplier = supplier,
            invoiceNumber = invoiceNumber,
            expenseCategory = category,
            createdAt = System.currentTimeMillis(),
            approvedAt = null,
            isPendingSync = false
        )
        dao.insertLedgerEntry(entity)

        dao.insertAuditLog(
            AuditLogEntity(
                actorName = creator.fullName,
                action = "CREATE_EXPENSE",
                entityType = "TRANSACTION",
                entityId = txId,
                details = "Dépense créée: $supplier ($amount DZD, Facture: $invoiceNumber) - En attente d'approbation du 2ème syndic.",
                timestamp = System.currentTimeMillis()
            )
        )
        return Result.success(txId)
    }

    suspend fun approveExpense(
        txId: String,
        approver: User,
        approve: Boolean,
        rejectionReason: String? = null
    ): Result<Unit> {
        if (approver.role != UserRole.OWNER_SYNDIC) {
            return Result.failure(IllegalStateException("Seul un syndic peut approuver une dépense."))
        }
        val entry = dao.getLedgerEntryById(txId)
            ?: return Result.failure(IllegalArgumentException("Dépense introuvable."))

        // CRITICAL: Cannot approve self-created expense!
        if (entry.creatorSyndicId == approver.id) {
            return Result.failure(IllegalStateException("Règle de sécurité : Un syndic ne peut pas approuver sa propre dépense. L'approbation du second syndic est obligatoire."))
        }

        val updated = entry.copy(
            approverSyndicId = approver.id,
            approverName = approver.fullName,
            status = if (approve) "LOCKED" else "REJECTED",
            correctionReason = if (!approve) rejectionReason else entry.correctionReason,
            approvedAt = if (approve) System.currentTimeMillis() else null
        )
        dao.updateLedgerEntry(updated)

        dao.insertAuditLog(
            AuditLogEntity(
                actorName = approver.fullName,
                action = if (approve) "APPROVE_EXPENSE" else "REJECT_EXPENSE",
                entityType = "TRANSACTION",
                entityId = txId,
                details = if (approve) "Dépense $txId approuvée et verrouillée." else "Dépense $txId rejetée: $rejectionReason",
                timestamp = System.currentTimeMillis()
            )
        )
        return Result.success(Unit)
    }

    /**
     * FINANCIAL CORRECTION: Original transaction is NEVER edited or deleted.
     * Syndic 1 requests correction, Syndic 2 approves it.
     * When approved, creates a brand new CORRECTION transaction referencing originalTxId.
     */
    suspend fun requestFinancialCorrection(
        originalTxId: String,
        newAmount: Long,
        reason: String,
        requester: User
    ): Result<String> {
        if (requester.role != UserRole.OWNER_SYNDIC) {
            return Result.failure(IllegalStateException("Seul un syndic peut demander une correction."))
        }
        val original = dao.getLedgerEntryById(originalTxId)
            ?: return Result.failure(IllegalArgumentException("Transaction originale introuvable."))

        val delta = newAmount - original.amount
        val correctionType = if (delta > 0) "CORRECTION_DEBIT" else "CORRECTION_CREDIT"
        val txYear = SimpleDateFormat("yyyy", Locale.US).format(Date())
        val randomSuffix = String.format(Locale.US, "%06d", (100000..999999).random())
        val corrTxId = "TX-$txYear-$randomSuffix"

        val entity = LedgerEntity(
            txId = corrTxId,
            type = correctionType,
            projectId = original.projectId,
            projectTitle = original.projectTitle,
            apartmentNumber = original.apartmentNumber,
            ownerId = original.ownerId,
            ownerName = original.ownerName,
            amount = kotlin.math.abs(delta),
            paymentMethod = original.paymentMethod,
            creatorSyndicId = requester.id,
            creatorName = requester.fullName,
            approverSyndicId = null,
            approverName = null,
            status = "PENDING_APPROVAL",
            originalTxId = originalTxId,
            correctionReason = reason,
            supplier = original.supplier,
            invoiceNumber = original.invoiceNumber,
            expenseCategory = original.expenseCategory,
            createdAt = System.currentTimeMillis(),
            approvedAt = null,
            isPendingSync = false
        )
        dao.insertLedgerEntry(entity)

        dao.insertAuditLog(
            AuditLogEntity(
                actorName = requester.fullName,
                action = "REQUEST_CORRECTION",
                entityType = "TRANSACTION",
                entityId = corrTxId,
                details = "Demande de correction sur $originalTxId (Montant initial: ${original.amount} DZD, Nouveau: $newAmount DZD, Raison: $reason). En attente d'approbation du 2ème syndic.",
                timestamp = System.currentTimeMillis()
            )
        )
        return Result.success(corrTxId)
    }

    suspend fun approveCorrection(
        corrTxId: String,
        approver: User,
        approve: Boolean,
        rejectionReason: String? = null
    ): Result<Unit> {
        if (approver.role != UserRole.OWNER_SYNDIC) {
            return Result.failure(IllegalStateException("Seul un syndic peut approuver une correction."))
        }
        val corrEntry = dao.getLedgerEntryById(corrTxId)
            ?: return Result.failure(IllegalArgumentException("Demande de correction introuvable."))

        if (corrEntry.creatorSyndicId == approver.id) {
            return Result.failure(IllegalStateException("Règle de sécurité : Un syndic ne peut pas approuver sa propre demande de correction. L'approbation du second syndic est obligatoire."))
        }

        val updated = corrEntry.copy(
            approverSyndicId = approver.id,
            approverName = approver.fullName,
            status = if (approve) "LOCKED" else "REJECTED",
            correctionReason = if (!approve) rejectionReason else corrEntry.correctionReason,
            approvedAt = if (approve) System.currentTimeMillis() else null
        )
        dao.updateLedgerEntry(updated)

        dao.insertAuditLog(
            AuditLogEntity(
                actorName = approver.fullName,
                action = if (approve) "APPROVE_CORRECTION" else "REJECT_CORRECTION",
                entityType = "TRANSACTION",
                entityId = corrTxId,
                details = if (approve) "Correction approuvée et verrouillée. Transaction originale ${corrEntry.originalTxId} préservée dans l'historique immuable." else "Correction rejetée: $rejectionReason",
                timestamp = System.currentTimeMillis()
            )
        )
        return Result.success(Unit)
    }

    // --- Transparency Table for All 40 Apartments ---
    suspend fun getTransparencyApartmentsList(projectId: String?): List<ApartmentTransparencyItem> {
        val apartments = dao.getAllApartments()
        val lockedLedger = dao.getLockedLedgerFlow().first()
        val project = if (projectId != null) dao.getProjectById(projectId) else null

        val requiredPerApt = project?.contributionPerApt ?: 5000L

        return apartments.map { apt ->
            val payments = lockedLedger.filter {
                it.apartmentNumber == apt.number &&
                        (projectId == null || it.projectId == projectId) &&
                        it.type == "OWNER_PAYMENT"
            }
            val totalPaid = payments.sumOf { it.amount }
            val isPaid = totalPaid >= requiredPerApt

            ApartmentTransparencyItem(
                apartmentNumber = apt.number,
                floor = apt.floor,
                ownerName = apt.ownerName,
                ownerPhone = apt.ownerPhone,
                requiredAmount = requiredPerApt,
                paidAmount = totalPaid,
                isPaid = isPaid,
                lastPaymentDate = payments.maxByOrNull { it.createdAt }?.createdAt
            )
        }
    }

    // --- Maintenance Reports ---
    fun getAllMaintenanceFlow(): Flow<List<MaintenanceReport>> = dao.getAllMaintenanceFlow().map { list ->
        list.map { it.toMaintenanceReport() }
    }

    fun getMaintenanceForApartmentFlow(aptNumber: Int): Flow<List<MaintenanceReport>> = dao.getMaintenanceForApartmentFlow(aptNumber).map { list ->
        list.map { it.toMaintenanceReport() }
    }

    suspend fun createMaintenanceReport(
        apartmentNumber: Int,
        user: User,
        category: MaintenanceCategory,
        description: String,
        photoUri: String?
    ): Result<String> {
        val year = SimpleDateFormat("yyyy", Locale.US).format(Date())
        val randomSuffix = String.format(Locale.US, "%05d", (1000..9999).random())
        val id = "REP-$year-$randomSuffix"

        val entity = MaintenanceEntity(
            id = id,
            apartmentNumber = apartmentNumber,
            reporterUserId = user.id,
            reporterName = user.fullName,
            category = category.name,
            description = description,
            photoUri = photoUri,
            status = "NEW",
            syndicNotes = null,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        dao.insertMaintenance(entity)

        dao.insertAuditLog(
            AuditLogEntity(
                actorName = user.fullName,
                action = "REPORT_MAINTENANCE",
                entityType = "MAINTENANCE",
                entityId = id,
                details = "Signalement de panne ($category): $description (Apt $apartmentNumber)",
                timestamp = System.currentTimeMillis()
            )
        )
        return Result.success(id)
    }

    suspend fun updateMaintenanceStatus(
        reportId: String,
        newStatus: MaintenanceStatus,
        syndicNotes: String?,
        syndic: User
    ): Result<Unit> {
        val report = dao.getMaintenanceById(reportId)
            ?: return Result.failure(IllegalArgumentException("Signalement introuvable."))

        val updated = report.copy(
            status = newStatus.name,
            syndicNotes = syndicNotes ?: report.syndicNotes,
            updatedAt = System.currentTimeMillis()
        )
        dao.updateMaintenance(updated)

        dao.insertAuditLog(
            AuditLogEntity(
                actorName = syndic.fullName,
                action = "UPDATE_MAINTENANCE",
                entityType = "MAINTENANCE",
                entityId = reportId,
                details = "Statut mis à jour: ${newStatus.name}. Notes: $syndicNotes",
                timestamp = System.currentTimeMillis()
            )
        )
        return Result.success(Unit)
    }

    // --- Elevator ---
    fun getAllElevatorRecordsFlow(): Flow<List<ElevatorRecord>> = dao.getAllElevatorRecordsFlow().map { list ->
        list.map { it.toElevatorRecord() }
    }

    suspend fun addElevatorRecord(record: ElevatorRecord, syndic: User) {
        dao.insertElevatorRecord(
            ElevatorEntity(
                id = record.id,
                type = record.type,
                technicianOrCompany = record.technicianOrCompany,
                contactPhone = record.contactPhone,
                cost = record.cost,
                invoiceNumber = record.invoiceNumber,
                maintenanceDate = record.maintenanceDate,
                nextScheduledDate = record.nextScheduledDate,
                description = record.description
            )
        )
        dao.insertAuditLog(
            AuditLogEntity(
                actorName = syndic.fullName,
                action = "ADD_ELEVATOR_RECORD",
                entityType = "ELEVATOR",
                entityId = record.id,
                details = "Intervention ascenseur: ${record.type} par ${record.technicianOrCompany} (${record.cost} DZD)",
                timestamp = System.currentTimeMillis()
            )
        )
    }

    // --- Announcements ---
    fun getAllAnnouncementsFlow(): Flow<List<Announcement>> = dao.getAllAnnouncementsFlow().map { list ->
        list.map { it.toAnnouncement() }
    }

    suspend fun publishAnnouncement(title: String, content: String, category: AnnouncementCategory, syndic: User) {
        val id = "ANN-${System.currentTimeMillis()}"
        dao.insertAnnouncement(
            AnnouncementEntity(
                id = id,
                title = title,
                content = content,
                category = category.name,
                authorName = syndic.fullName,
                createdAt = System.currentTimeMillis()
            )
        )
        dao.insertAuditLog(
            AuditLogEntity(
                actorName = syndic.fullName,
                action = "PUBLISH_ANNOUNCEMENT",
                entityType = "ANNOUNCEMENT",
                entityId = id,
                details = "Annonce publiée: $title",
                timestamp = System.currentTimeMillis()
            )
        )
    }

    // --- Meetings ---
    fun getAllMeetingsFlow(): Flow<List<Meeting>> = dao.getAllMeetingsFlow().map { list ->
        list.map { it.toMeeting() }
    }

    suspend fun createMeeting(meeting: Meeting, syndic: User) {
        dao.insertMeeting(
            MeetingEntity(
                id = meeting.id,
                title = meeting.title,
                meetingDate = meeting.meetingDate,
                location = meeting.location,
                description = meeting.description,
                agenda = meeting.agenda,
                decisions = meeting.decisions
            )
        )
        dao.insertAuditLog(
            AuditLogEntity(
                actorName = syndic.fullName,
                action = "SCHEDULE_MEETING",
                entityType = "MEETING",
                entityId = meeting.id,
                details = "Réunion programmée: ${meeting.title}",
                timestamp = System.currentTimeMillis()
            )
        )
    }

    // --- Transparent Voting ---
    fun getAllVotingSessionsFlow(): Flow<List<VotingSession>> = dao.getAllVotingSessionsFlow().map { list ->
        list.map { it.toVotingSession() }
    }

    fun getVotesForSessionFlow(sessionId: String): Flow<List<VoteRecord>> = dao.getVotesForSessionFlow(sessionId).map { list ->
        list.map { it.toVoteRecord() }
    }

    suspend fun getUserVote(sessionId: String, userId: Long): VoteRecord? {
        return dao.getUserVote(sessionId, userId)?.toVoteRecord()
    }

    suspend fun castVote(
        sessionId: String,
        user: User,
        choice: VoteChoice
    ): Result<Unit> {
        val existing = dao.getUserVote(sessionId, user.id)
        if (existing != null) {
            return Result.failure(IllegalStateException("Le vote est verrouillé et ne peut pas être modifié une fois soumis."))
        }

        val entity = VoteRecordEntity(
            votingSessionId = sessionId,
            userId = user.id,
            userName = user.fullName,
            apartmentNumber = user.apartmentNumber,
            choice = choice.name,
            submittedAt = System.currentTimeMillis()
        )
        dao.insertVoteRecord(entity)

        dao.insertAuditLog(
            AuditLogEntity(
                actorName = user.fullName,
                action = "CAST_VOTE",
                entityType = "VOTE",
                entityId = sessionId,
                details = "Vote public exprimé par Apt ${user.apartmentNumber} (${user.fullName}): ${choice.name}",
                timestamp = System.currentTimeMillis()
            )
        )
        return Result.success(Unit)
    }

    suspend fun createVotingSession(
        title: String,
        description: String,
        deadline: Long,
        syndic: User
    ) {
        val id = "VOTE-${SimpleDateFormat("yyyy", Locale.US).format(Date())}-${UUID.randomUUID().toString().take(4).uppercase(Locale.US)}"
        dao.insertVotingSession(
            VotingEntity(
                id = id,
                title = title,
                description = description,
                relatedProjectId = null,
                deadline = deadline,
                isClosed = false,
                createdByName = syndic.fullName,
                createdAt = System.currentTimeMillis()
            )
        )
        dao.insertAuditLog(
            AuditLogEntity(
                actorName = syndic.fullName,
                action = "CREATE_VOTE",
                entityType = "VOTE",
                entityId = id,
                details = "Nouveau scrutin ouvert: $title",
                timestamp = System.currentTimeMillis()
            )
        )
    }

    // --- Documents & Audit Logs ---
    fun getAllDocumentsFlow(): Flow<List<DocumentItem>> = dao.getAllDocumentsFlow().map { list ->
        list.map { it.toDocumentItem() }
    }

    fun getAllAuditLogsFlow(): Flow<List<AuditLogEntry>> = dao.getAllAuditLogsFlow().map { list ->
        list.map { it.toAuditLogEntry() }
    }
}

data class ApartmentTransparencyItem(
    val apartmentNumber: Int,
    val floor: Int,
    val ownerName: String,
    val ownerPhone: String,
    val requiredAmount: Long,
    val paidAmount: Long,
    val isPaid: Boolean,
    val lastPaymentDate: Long?
)

// Extension converters
fun UserEntity.toUser() = User(
    id = id,
    username = username,
    fullName = fullName,
    phoneNumber = phoneNumber,
    role = UserRole.valueOf(role),
    apartmentNumber = apartmentNumber,
    floor = floor
)

fun ProjectEntity.toProject(collected: Long) = Project(
    id = id,
    title = title,
    description = description,
    totalCost = totalCost,
    apartmentCount = apartmentCount,
    contributionPerApt = contributionPerApt,
    creatorSyndicId = creatorSyndicId,
    creatorName = creatorName,
    approverSyndicId = approverSyndicId,
    approverName = approverName,
    status = ProjectStatus.valueOf(status),
    rejectionReason = rejectionReason,
    createdAt = createdAt,
    approvedAt = approvedAt,
    totalCollected = collected
)

fun LedgerEntity.toFinancialLedgerEntry() = FinancialLedgerEntry(
    txId = txId,
    type = TransactionType.valueOf(type),
    projectId = projectId,
    projectTitle = projectTitle,
    apartmentNumber = apartmentNumber,
    ownerId = ownerId,
    ownerName = ownerName,
    amount = amount,
    paymentMethod = PaymentMethod.valueOf(paymentMethod),
    creatorSyndicId = creatorSyndicId,
    creatorName = creatorName,
    approverSyndicId = approverSyndicId,
    approverName = approverName,
    status = TransactionStatus.valueOf(status),
    originalTxId = originalTxId,
    correctionReason = correctionReason,
    supplier = supplier,
    invoiceNumber = invoiceNumber,
    expenseCategory = expenseCategory,
    createdAt = createdAt,
    approvedAt = approvedAt,
    isPendingSync = isPendingSync
)

fun MaintenanceEntity.toMaintenanceReport() = MaintenanceReport(
    id = id,
    apartmentNumber = apartmentNumber,
    reporterUserId = reporterUserId,
    reporterName = reporterName,
    category = MaintenanceCategory.valueOf(category),
    description = description,
    photoUri = photoUri,
    status = MaintenanceStatus.valueOf(status),
    syndicNotes = syndicNotes,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun ElevatorEntity.toElevatorRecord() = ElevatorRecord(
    id = id,
    type = type,
    technicianOrCompany = technicianOrCompany,
    contactPhone = contactPhone,
    cost = cost,
    invoiceNumber = invoiceNumber,
    maintenanceDate = maintenanceDate,
    nextScheduledDate = nextScheduledDate,
    description = description
)

fun AnnouncementEntity.toAnnouncement() = Announcement(
    id = id,
    title = title,
    content = content,
    category = AnnouncementCategory.valueOf(category),
    authorName = authorName,
    createdAt = createdAt
)

fun MeetingEntity.toMeeting() = Meeting(
    id = id,
    title = title,
    meetingDate = meetingDate,
    location = location,
    description = description,
    agenda = agenda,
    decisions = decisions
)

fun VotingEntity.toVotingSession() = VotingSession(
    id = id,
    title = title,
    description = description,
    relatedProjectId = relatedProjectId,
    deadline = deadline,
    isClosed = isClosed,
    createdByName = createdByName,
    createdAt = createdAt
)

fun VoteRecordEntity.toVoteRecord() = VoteRecord(
    id = id,
    votingSessionId = votingSessionId,
    userId = userId,
    userName = userName,
    apartmentNumber = apartmentNumber,
    choice = VoteChoice.valueOf(choice),
    submittedAt = submittedAt
)

fun DocumentEntity.toDocumentItem() = DocumentItem(
    id = id,
    title = title,
    category = category,
    date = date,
    fileSizeBytes = fileSizeBytes,
    invoiceNumber = invoiceNumber
)

fun AuditLogEntity.toAuditLogEntry() = AuditLogEntry(
    id = id,
    actorName = actorName,
    action = action,
    entityType = entityType,
    entityId = entityId,
    details = details,
    timestamp = timestamp
)
