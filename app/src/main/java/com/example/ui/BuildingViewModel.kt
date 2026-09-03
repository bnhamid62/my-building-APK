package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.ApartmentTransparencyItem
import com.example.data.BuildingRepository
import com.example.data.sync.SyncEngine
import com.example.model.*
import com.example.ui.localization.AppLanguage
import com.example.ui.localization.AppThemeMode
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BuildingViewModel(private val repository: BuildingRepository) : ViewModel() {

    private val syncEngine = SyncEngine(repository.dao)

    private val _authToken = MutableStateFlow<String?>("demo-token-amarati")
    val authToken: StateFlow<String?> = _authToken.asStateFlow()

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _isSyndicMode = MutableStateFlow(false)
    val isSyndicMode: StateFlow<Boolean> = _isSyndicMode.asStateFlow()

    private val _appLanguage = MutableStateFlow(AppLanguage.FRENCH)
    val appLanguage: StateFlow<AppLanguage> = _appLanguage.asStateFlow()

    private val _themeMode = MutableStateFlow(AppThemeMode.SYSTEM)
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    private val _isOffline = MutableStateFlow(false)
    val isOffline: StateFlow<Boolean> = _isOffline.asStateFlow()

    private val _lastSyncTime = MutableStateFlow(
        SimpleDateFormat("HH:mm - dd/MM/yyyy", Locale.getDefault()).format(Date())
    )
    val lastSyncTime: StateFlow<String> = _lastSyncTime.asStateFlow()

    private val _snackMessage = MutableStateFlow<String?>(null)
    val snackMessage: StateFlow<String?> = _snackMessage.asStateFlow()

    // Data streams
    val projects: StateFlow<List<Project>> = repository.getAllProjectsFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val lockedLedger: StateFlow<List<FinancialLedgerEntry>> = repository.getLockedLedgerFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val pendingSyncLedger: StateFlow<List<FinancialLedgerEntry>> = repository.getPendingSyncLedgerFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // OFFICIAL BALANCE FORMULA: Official Balance = SUM(CREDIT) - SUM(DEBIT)
    // Only server-confirmed LOCKED transactions affect the official balance (PENDING_SYNC excluded).
    val officialBalance: StateFlow<Long> = lockedLedger.map { entries ->
        val confirmedLocked = entries.filter { !it.isPendingSync }
        val credits = confirmedLocked.filter { 
            it.type == TransactionType.OWNER_PAYMENT || it.type == TransactionType.CORRECTION_CREDIT 
        }.sumOf { it.amount }
        val debits = confirmedLocked.filter { 
            it.type == TransactionType.EXPENSE || it.type == TransactionType.CORRECTION_DEBIT 
        }.sumOf { it.amount }
        credits - debits
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0L)

    val maintenanceReports: StateFlow<List<MaintenanceReport>> = repository.getAllMaintenanceFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val elevatorRecords: StateFlow<List<ElevatorRecord>> = repository.getAllElevatorRecordsFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val announcements: StateFlow<List<Announcement>> = repository.getAllAnnouncementsFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val meetings: StateFlow<List<Meeting>> = repository.getAllMeetingsFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val votingSessions: StateFlow<List<VotingSession>> = repository.getAllVotingSessionsFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val documents: StateFlow<List<DocumentItem>> = repository.getAllDocumentsFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val auditLogs: StateFlow<List<AuditLogEntry>> = repository.getAllAuditLogsFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _transparencyList = MutableStateFlow<List<ApartmentTransparencyItem>>(emptyList())
    val transparencyList: StateFlow<List<ApartmentTransparencyItem>> = _transparencyList.asStateFlow()

    private val _selectedProjectIdForTransparency = MutableStateFlow<String?>(null)
    val selectedProjectIdForTransparency: StateFlow<String?> = _selectedProjectIdForTransparency.asStateFlow()

    init {
        viewModelScope.launch {
            repository.initializeSeedDataIfNeeded()
            refreshTransparency()
            // Auto login as Syndic 1 by default so the streaming emulator works immediately
            login("apt1", "amarati123")
        }
    }

    fun setLanguage(lang: AppLanguage) {
        _appLanguage.value = lang
    }

    fun setThemeMode(mode: AppThemeMode) {
        _themeMode.value = mode
    }

    fun toggleOffline() {
        _isOffline.value = !_isOffline.value
        if (!_isOffline.value) {
            _lastSyncTime.value = SimpleDateFormat("HH:mm - dd/MM/yyyy", Locale.getDefault()).format(Date())
            viewModelScope.launch {
                val syncRes = syncEngine.syncPendingOperations(_authToken.value)
                refreshTransparency()
                _snackMessage.value = if (_appLanguage.value == AppLanguage.ARABIC) 
                    "تم الاتصال: ${syncRes.message}" 
                else 
                    "En ligne : ${syncRes.message}"
            }
        } else {
            _snackMessage.value = if (_appLanguage.value == AppLanguage.ARABIC) "وضع عدم الاتصال نشط" else "Mode hors ligne actif"
        }
    }

    fun toggleSyndicMode() {
        val user = _currentUser.value ?: return
        if (user.role == UserRole.SYNDIC) {
            _isSyndicMode.value = !_isSyndicMode.value
        }
    }

    fun login(username: String, password: String): Boolean {
        var success = false
        viewModelScope.launch {
            val user = repository.login(username, password)
            if (user != null) {
                _currentUser.value = user
                _isSyndicMode.value = (user.role == UserRole.SYNDIC)
                refreshTransparency()
                success = true
            } else {
                _snackMessage.value = if (_appLanguage.value == AppLanguage.ARABIC) "اسم المستخدم أو كلمة المرور غير صحيحة" else "Identifiants invalides"
            }
        }
        return success
    }

    fun logout() {
        _currentUser.value = null
        _isSyndicMode.value = false
    }

    fun selectProjectForTransparency(projectId: String?) {
        _selectedProjectIdForTransparency.value = projectId
        refreshTransparency()
    }

    fun refreshTransparency() {
        viewModelScope.launch {
            _transparencyList.value = repository.getTransparencyApartmentsList(_selectedProjectIdForTransparency.value)
        }
    }

    // --- Financial Operations (Single-Syndic Direct Finalization) ---
    fun createProject(title: String, description: String, totalCost: Long) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            val result = repository.createFinancialProject(title, description, totalCost, user)
            result.onSuccess {
                _snackMessage.value = if (_appLanguage.value == AppLanguage.ARABIC)
                    "تم إنشاء وتفعيل المشروع بنجاح بواسطة الوكيل."
                else
                    "Projet créé et activé par le syndic."
            }.onFailure {
                _snackMessage.value = it.message
            }
        }
    }

    fun recordOwnerPayment(
        projectId: String,
        projectTitle: String,
        apartmentNumber: Int,
        ownerId: Long,
        ownerName: String,
        amount: Long,
        paymentMethod: PaymentMethod
    ) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            val result = repository.recordOwnerPayment(
                projectId, projectTitle, apartmentNumber, ownerId, ownerName,
                amount, paymentMethod, user, isOffline = _isOffline.value
            )
            result.onSuccess { txId ->
                refreshTransparency()
                _snackMessage.value = if (_appLanguage.value == AppLanguage.ARABIC)
                    "تم تسجيل الدفعة بنجاح ($txId). المعاملة مقفلة في السجل المالي."
                else
                    "Paiement enregistré ($txId) et verrouillé dans le registre immuable."
            }.onFailure {
                _snackMessage.value = it.message
            }
        }
    }

    fun createExpense(
        projectId: String?,
        projectTitle: String?,
        category: String,
        description: String,
        amount: Long,
        supplier: String,
        invoiceNumber: String,
        paymentMethod: PaymentMethod
    ) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            val result = repository.createExpense(
                projectId, projectTitle, category, description,
                amount, supplier, invoiceNumber, paymentMethod, user
            )
            result.onSuccess { txId ->
                _snackMessage.value = if (_appLanguage.value == AppLanguage.ARABIC)
                    "تم تسجيل المصروف بنجاح ($txId). المعاملة مقفلة في السجل المالي."
                else
                    "Dépense enregistrée ($txId) et verrouillée dans le registre."
            }.onFailure {
                _snackMessage.value = it.message
            }
        }
    }

    fun recordFinancialCorrection(
        originalTxId: String,
        delta: Long,
        isDebit: Boolean,
        reason: String
    ) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            val result = repository.recordFinancialCorrection(originalTxId, delta, isDebit, reason, user)
            result.onSuccess { corrTxId ->
                refreshTransparency()
                _snackMessage.value = if (_appLanguage.value == AppLanguage.ARABIC)
                    "تم تسجيل التصحيح المالي ($corrTxId). المعاملة الأصلية محفوظة بدون تعديل."
                else
                    "Correction enregistrée ($corrTxId). La transaction originale reste préservée et immuable."
            }.onFailure {
                _snackMessage.value = it.message
            }
        }
    }

    // --- Maintenance ---
    fun reportProblem(category: MaintenanceCategory, description: String, photoUri: String?) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            val result = repository.createMaintenanceReport(
                user.apartmentNumber, user, category, description, photoUri,
                isOffline = _isOffline.value
            )
            result.onSuccess { repId ->
                _snackMessage.value = if (_appLanguage.value == AppLanguage.ARABIC)
                    "تم إرسال بلاغ العطل ($repId) إلى الوكيلين."
                else
                    "Signalement ($repId) transmis aux syndics."
            }.onFailure {
                _snackMessage.value = it.message
            }
        }
    }

    fun updateMaintenanceStatus(reportId: String, newStatus: MaintenanceStatus, syndicNotes: String?) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            val result = repository.updateMaintenanceStatus(reportId, newStatus, syndicNotes, user)
            result.onSuccess {
                _snackMessage.value = if (_appLanguage.value == AppLanguage.ARABIC) "تم تحديث حالة البلاغ." else "Statut du signalement mis à jour."
            }
        }
    }

    // --- Announcements & Meetings ---
    fun publishAnnouncement(title: String, content: String, category: AnnouncementCategory) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            repository.publishAnnouncement(title, content, category, user)
            _snackMessage.value = if (_appLanguage.value == AppLanguage.ARABIC) "تم نشر التبليغ لجميع الملاك." else "Annonce diffusée à tous les copropriétaires."
        }
    }

    fun createMeeting(title: String, date: Long, location: String, description: String, agenda: String) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            val meeting = Meeting(
                id = "MTG-${System.currentTimeMillis()}",
                title = title,
                meetingDate = date,
                location = location,
                description = description,
                agenda = agenda,
                decisions = null
            )
            repository.createMeeting(meeting, user)
            _snackMessage.value = if (_appLanguage.value == AppLanguage.ARABIC) "تمت برمجة الاجتماع بنجاح." else "Réunion programmée avec succès."
        }
    }

    // --- Voting ---
    fun castVote(sessionId: String, choice: VoteChoice) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            val result = repository.castVote(sessionId, user, choice)
            result.onSuccess {
                _snackMessage.value = if (_appLanguage.value == AppLanguage.ARABIC)
                    "تم تسجيل تصويتك علنياً وتثبيته نهائياً."
                else
                    "Votre vote a été enregistré publiquement et verrouillé."
            }.onFailure {
                _snackMessage.value = it.message
            }
        }
    }

    fun getVotesForSession(sessionId: String): Flow<List<VoteRecord>> {
        return repository.getVotesForSessionFlow(sessionId)
    }

    fun dismissSnack() {
        _snackMessage.value = null
    }
}

class BuildingViewModelFactory(private val repository: BuildingRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BuildingViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return BuildingViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
