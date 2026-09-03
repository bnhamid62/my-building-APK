package com.example.data.sync

import android.util.Log
import com.example.data.BuildingDao
import com.example.data.api.ApiClient
import com.example.data.api.SyncPushItem
import com.example.data.api.SyncPushRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SyncEngine(
    private val dao: BuildingDao
) {
    suspend fun syncPendingOperations(authToken: String?): SyncResult = withContext(Dispatchers.IO) {
        val pendingLedger = dao.getPendingSyncLedgerEntries()
        val pendingMaintenance = dao.getPendingSyncMaintenanceReports()

        if (pendingLedger.isEmpty() && pendingMaintenance.isEmpty()) {
            return@withContext SyncResult(syncedCount = 0, message = "Rien à synchroniser")
        }

        if (authToken.isNullOrBlank()) {
            return@withContext SyncResult(syncedCount = 0, message = "Authentification requise pour synchroniser")
        }

        val items = mutableListOf<SyncPushItem>()

        // 1. Map pending maintenance reports
        for (m in pendingMaintenance) {
            items.add(
                SyncPushItem(
                    localId = m.id,
                    type = "MAINTENANCE",
                    category = m.category,
                    description = m.description
                )
            )
        }

        // 2. Map pending payments
        for (l in pendingLedger) {
            if (l.type == "OWNER_PAYMENT") {
                items.add(
                    SyncPushItem(
                        localId = l.txId,
                        type = "PAYMENT",
                        apartmentNumber = l.apartmentNumber,
                        projectId = l.projectId,
                        amount = l.amount,
                        paymentMethod = l.paymentMethod,
                        idempotencyKey = "sync-${l.txId}"
                    )
                )
            }
        }

        try {
            val response = ApiClient.service.pushSyncBatch(
                token = "Bearer $authToken",
                request = SyncPushRequest(items = items)
            )

            if (response.isSuccessful && response.body() != null) {
                val syncBody = response.body()!!
                var successCount = 0

                for (res in syncBody.synced) {
                    if (res.status == "SYNCED" || res.status == "LOCKED") {
                        successCount++
                        // Update local maintenance record to clear pending flag
                        val localM = dao.getMaintenanceById(res.localId)
                        if (localM != null) {
                            dao.updateMaintenance(localM.copy(isPendingSync = false))
                        }

                        // Update local ledger record to promote to server confirmed and clear pending flag
                        val localL = dao.getLedgerEntryById(res.localId)
                        if (localL != null) {
                            dao.updateLedgerEntry(
                                localL.copy(
                                    status = "LOCKED",
                                    isPendingSync = false
                                )
                            )
                        }
                    }
                }

                return@withContext SyncResult(
                    syncedCount = successCount,
                    message = "$successCount opérations synchronisées et verrouillées sur le serveur"
                )
            } else {
                return@withContext SyncResult(
                    syncedCount = 0,
                    message = "Serveur indisponible (code ${response.code()}), réessai ultérieur"
                )
            }
        } catch (e: Exception) {
            Log.w("SyncEngine", "Sync push failed, will retry when online: ${e.message}")
            return@withContext SyncResult(
                syncedCount = 0,
                message = "Mode hors ligne actif. Données conservées localement avec statut En attente"
            )
        }
    }
}

data class SyncResult(
    val syncedCount: Int,
    val message: String
)
