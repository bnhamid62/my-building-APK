package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.*
import com.example.ui.localization.AppLanguage
import com.example.ui.localization.Strings
import com.example.ui.theme.AtlasEmerald
import com.example.ui.theme.CoralRed
import com.example.ui.theme.SandAmber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyndicManagementScreen(
    user: User,
    appLanguage: AppLanguage,
    projects: List<Project>,
    pendingLedger: List<FinancialLedgerEntry>,
    lockedLedger: List<FinancialLedgerEntry>,
    onApproveProject: (String, Boolean, String?) -> Unit,
    onCreateProject: (String, String, Long) -> Unit,
    onRecordPayment: (String, String, Int, Long, String, Long, PaymentMethod) -> Unit,
    onCreateExpense: (String?, String?, String, String, Long, String, String, PaymentMethod) -> Unit,
    onApproveExpense: (String, Boolean, String?) -> Unit,
    onRequestCorrection: (String, Long, String) -> Unit,
    onApproveCorrection: (String, Boolean, String?) -> Unit,
    onPublishAnnouncement: (String, String, AnnouncementCategory) -> Unit,
    onScheduleMeeting: (String, Long, String, String, String) -> Unit
) {
    var activeDialog by remember { mutableStateOf<String?>(null) }

    val pendingProjects = projects.filter { it.status == ProjectStatus.PENDING_APPROVAL }
    val pendingExpenses = pendingLedger.filter { it.type == TransactionType.EXPENSE && it.status == TransactionStatus.PENDING_APPROVAL }
    val pendingCorrections = pendingLedger.filter {
        (it.type == TransactionType.CORRECTION_CREDIT || it.type == TransactionType.CORRECTION_DEBIT) &&
                it.status == TransactionStatus.PENDING_APPROVAL
    }
    val totalPendingCount = pendingProjects.size + pendingExpenses.size + pendingCorrections.size

    val otherSyndicName = if (user.id == 1L) "Karim Mansouri (Syndic 2)" else "Ahmed Benali (Syndic 1)"

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Management Banner
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SandAmber.copy(alpha = 0.15f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = SandAmber, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = Strings.buildingManagement(appLanguage),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = SandAmber
                            )
                            Text(
                                text = "Connecté en tant que ${user.fullName} • Co-Syndic: $otherSyndicName",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Rappel règles de gestion : Les paiements propriétaires sont verrouillés immédiatement. Les projets, dépenses et corrections nécessitent obligatoirement l'approbation du 2ème syndic sans auto-approbation.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Quick Action Grid (Large, readable buttons)
        item {
            Text(
                text = if (appLanguage == AppLanguage.ARABIC) "إجراءات التسيير المالي والإداري" else "Actions de Gestion Rapide",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { activeDialog = "RECORD_PAYMENT" },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .testTag("action_record_payment"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AtlasEmerald)
                ) {
                    Icon(Icons.Default.Payment, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(Strings.recordPayment(appLanguage), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                Button(
                    onClick = { activeDialog = "NEW_PROJECT" },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .testTag("action_new_project"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.AddBusiness, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (appLanguage == AppLanguage.ARABIC) "مشروع جديد" else "Nouveau Projet", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { activeDialog = "NEW_EXPENSE" },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("action_new_expense"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Receipt, contentDescription = null, tint = CoralRed)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (appLanguage == AppLanguage.ARABIC) "تسجيل مصروف" else "Créer Dépense", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                OutlinedButton(
                    onClick = { activeDialog = "REQUEST_CORRECTION" },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("action_request_correction"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.AutoFixHigh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (appLanguage == AppLanguage.ARABIC) "تصحيح مالي" else "Correction", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilledTonalButton(
                    onClick = { activeDialog = "NEW_ANNOUNCEMENT" },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Campaign, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (appLanguage == AppLanguage.ARABIC) "نشر إعلان" else "Publier Avis", fontSize = 12.sp)
                }

                FilledTonalButton(
                    onClick = { activeDialog = "NEW_MEETING" },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Groups, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (appLanguage == AppLanguage.ARABIC) "برمجة اجتماع" else "Réunion AG", fontSize = 12.sp)
                }
            }
        }

        // DOUBLE APPROVAL QUEUE SECTION
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (appLanguage == AppLanguage.ARABIC) "قائمة الانتظار للموافقة الثنائية" else "File des Doubles Approbations",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Badge(containerColor = if (totalPendingCount > 0) SandAmber else AtlasEmerald) {
                    Text("$totalPendingCount en attente", modifier = Modifier.padding(4.dp))
                }
            }
        }

        // 1. Pending Projects
        if (pendingProjects.isNotEmpty()) {
            item {
                Text("Projets en attente de validation :", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
            }
            items(pendingProjects) { p ->
                val isCreator = p.creatorSyndicId == user.id
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(p.title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("${p.totalCost} DZD (${p.contributionPerApt} DZD/apt) • Créé par : ${p.creatorName}", style = MaterialTheme.typography.bodySmall)

                        Spacer(modifier = Modifier.height(8.dp))

                        if (isCreator) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = SandAmber.copy(alpha = 0.15f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.HourglassEmpty, contentDescription = null, tint = SandAmber, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Vous avez créé ce projet. En attente de validation par l'autre syndic (Règle de sécurité).",
                                        fontSize = 11.sp,
                                        color = SandAmber
                                    )
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = { onApproveProject(p.id, false, "Refusé par co-syndic") }) {
                                    Text("Rejeter", color = CoralRed)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = { onApproveProject(p.id, true, null) },
                                    colors = ButtonDefaults.buttonColors(containerColor = AtlasEmerald),
                                    modifier = Modifier.testTag("approve_project_${p.id}")
                                ) {
                                    Text("Valider & Activer", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Pending Expenses
        if (pendingExpenses.isNotEmpty()) {
            item {
                Text("Dépenses en attente de double approbation :", fontWeight = FontWeight.Bold, color = CoralRed, fontSize = 14.sp)
            }
            items(pendingExpenses) { exp ->
                val isCreator = exp.creatorSyndicId == user.id
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(exp.supplier ?: "Dépense", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("${exp.amount} DZD", fontWeight = FontWeight.Bold, color = CoralRed)
                        }
                        Text("Facture N°: ${exp.invoiceNumber ?: "N/A"} • Créé par: ${exp.creatorName}", style = MaterialTheme.typography.bodySmall)

                        Spacer(modifier = Modifier.height(8.dp))

                        if (isCreator) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = SandAmber.copy(alpha = 0.15f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.HourglassEmpty, contentDescription = null, tint = SandAmber, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Créé par vous. Seul l'autre syndic peut approuver cette dépense.", fontSize = 11.sp, color = SandAmber)
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = { onApproveExpense(exp.txId, false, "Rejeté") }) {
                                    Text("Rejeter", color = CoralRed)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = { onApproveExpense(exp.txId, true, null) },
                                    colors = ButtonDefaults.buttonColors(containerColor = AtlasEmerald),
                                    modifier = Modifier.testTag("approve_expense_${exp.txId}")
                                ) {
                                    Text("Approuver & Verrouiller", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Pending Corrections
        if (pendingCorrections.isNotEmpty()) {
            item {
                Text("Demandes de correction financière :", fontWeight = FontWeight.Bold, color = SandAmber, fontSize = 14.sp)
            }
            items(pendingCorrections) { corr ->
                val isCreator = corr.creatorSyndicId == user.id
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Correction sur transaction : ${corr.originalTxId}", fontWeight = FontWeight.Bold)
                        Text("Montant ajusté : ${corr.amount} DZD (${corr.type})", style = MaterialTheme.typography.bodyMedium)
                        Text("Motif : ${corr.correctionReason}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                        Spacer(modifier = Modifier.height(8.dp))

                        if (isCreator) {
                            Text("En attente de validation par le 2ème syndic.", fontSize = 11.sp, color = SandAmber)
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = { onApproveCorrection(corr.txId, false, "Refusé") }) {
                                    Text("Rejeter", color = CoralRed)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = { onApproveCorrection(corr.txId, true, null) },
                                    colors = ButtonDefaults.buttonColors(containerColor = AtlasEmerald)
                                ) {
                                    Text("Valider Correction", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (totalPendingCount == 0) {
            item {
                Surface(
                    color = AtlasEmerald.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AtlasEmerald)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (appLanguage == AppLanguage.ARABIC) "لا توجد عمليات معلقة، كل السجلات معتمدة ومقفلة." else "Aucune opération en attente, le registre financier est à jour.",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = AtlasEmerald
                        )
                    }
                }
            }
        }
    }

    // Dialog 1: Record Owner Payment
    if (activeDialog == "RECORD_PAYMENT") {
        RecordPaymentDialog(
            projects = projects.filter { it.status == ProjectStatus.APPROVED },
            appLanguage = appLanguage,
            onDismiss = { activeDialog = null },
            onConfirm = { projId, projTitle, aptNum, amt, method ->
                onRecordPayment(projId, projTitle, aptNum, aptNum.toLong(), "Copropriétaire Apt $aptNum", amt, method)
                activeDialog = null
            }
        )
    }

    // Dialog 2: New Financial Project
    if (activeDialog == "NEW_PROJECT") {
        NewProjectDialog(
            appLanguage = appLanguage,
            onDismiss = { activeDialog = null },
            onConfirm = { title, desc, cost ->
                onCreateProject(title, desc, cost)
                activeDialog = null
            }
        )
    }

    // Dialog 3: New Expense
    if (activeDialog == "NEW_EXPENSE") {
        NewExpenseDialog(
            projects = projects.filter { it.status == ProjectStatus.APPROVED },
            appLanguage = appLanguage,
            onDismiss = { activeDialog = null },
            onConfirm = { cat, desc, amt, supp, inv, method, projId ->
                onCreateExpense(projId, null, cat, desc, amt, supp, inv, method)
                activeDialog = null
            }
        )
    }

    // Dialog 4: Financial Correction
    if (activeDialog == "REQUEST_CORRECTION") {
        FinancialCorrectionDialog(
            lockedTransactions = lockedLedger,
            appLanguage = appLanguage,
            onDismiss = { activeDialog = null },
            onConfirm = { origTxId, newAmt, reason ->
                onRequestCorrection(origTxId, newAmt, reason)
                activeDialog = null
            }
        )
    }

    // Dialog 5: New Announcement
    if (activeDialog == "NEW_ANNOUNCEMENT") {
        NewAnnouncementDialog(
            appLanguage = appLanguage,
            onDismiss = { activeDialog = null },
            onConfirm = { title, content, cat ->
                onPublishAnnouncement(title, content, cat)
                activeDialog = null
            }
        )
    }

    // Dialog 6: New Meeting
    if (activeDialog == "NEW_MEETING") {
        NewMeetingDialog(
            appLanguage = appLanguage,
            onDismiss = { activeDialog = null },
            onConfirm = { title, date, loc, desc, agenda ->
                onScheduleMeeting(title, date, loc, desc, agenda)
                activeDialog = null
            }
        )
    }
}

// --- SUB-DIALOGS ---

@Composable
fun RecordPaymentDialog(
    projects: List<Project>,
    appLanguage: AppLanguage,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Int, Long, PaymentMethod) -> Unit
) {
    var selectedAptNumber by remember { mutableIntStateOf(1) }
    var amountText by remember { mutableStateOf("5000") }
    var selectedMethod by remember { mutableStateOf(PaymentMethod.CASH) }
    val defaultProject = projects.firstOrNull()
    var selectedProjectId by remember { mutableStateOf(defaultProject?.id ?: "PRJ-01") }
    var selectedProjectTitle by remember { mutableStateOf(defaultProject?.title ?: "Cotisation générale") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Strings.recordPayment(appLanguage), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(Strings.directLockNotice(appLanguage), fontSize = 12.sp, color = SandAmber)

                // Apartment Number (1..40)
                OutlinedTextField(
                    value = selectedAptNumber.toString(),
                    onValueChange = { selectedAptNumber = it.toIntOrNull()?.coerceIn(1, 40) ?: 1 },
                    label = { Text("Numéro d'appartement (1 à 40)") },
                    modifier = Modifier.fillMaxWidth().testTag("payment_apt_input")
                )

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Montant reçu (DZD)") },
                    modifier = Modifier.fillMaxWidth().testTag("payment_amount_input")
                )

                Text("Mode de règlement :", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PaymentMethod.values().forEach { method ->
                        FilterChip(
                            selected = selectedMethod == method,
                            onClick = { selectedMethod = method },
                            label = { Text(method.name) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountText.toLongOrNull() ?: 0L
                    if (amt > 0) {
                        onConfirm(selectedProjectId, selectedProjectTitle, selectedAptNumber, amt, selectedMethod)
                    }
                },
                modifier = Modifier.testTag("confirm_record_payment_button")
            ) {
                Text("Valider & Verrouiller", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

@Composable
fun NewProjectDialog(
    appLanguage: AppLanguage,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Long) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var costText by remember { mutableStateOf("200000") }

    val totalCost = costText.toLongOrNull() ?: 0L
    val perApt = totalCost / 40

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nouveau Projet Financier", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Titre du projet (ex: Réfection étanchéité)") },
                    modifier = Modifier.fillMaxWidth().testTag("project_title_input")
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description & Devis") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = costText,
                    onValueChange = { costText = it },
                    label = { Text("Coût total estimé (DZD)") },
                    modifier = Modifier.fillMaxWidth().testTag("project_cost_input")
                )

                // Auto calculation preview for the 40 apartments
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Calcul automatique pour les 40 appartements :", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text("$totalCost DZD / 40 = $perApt DZD par appartement", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }

                Text("Validation obligatoire par le second syndic avant ouverture.", fontSize = 11.sp, color = SandAmber)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank() && totalCost > 0) {
                        onConfirm(title, description, totalCost)
                    }
                },
                modifier = Modifier.testTag("submit_new_project_button")
            ) {
                Text("Soumettre au 2ème Syndic", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

@Composable
fun NewExpenseDialog(
    projects: List<Project>,
    appLanguage: AppLanguage,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Long, String, String, PaymentMethod, String?) -> Unit
) {
    var supplier by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var invoiceNumber by remember { mutableStateOf("FAC-2026-") }
    var amountText by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Ascenseur") }
    var method by remember { mutableStateOf(PaymentMethod.BANK_TRANSFER) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Créer une Dépense d'Immeuble", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = supplier,
                    onValueChange = { supplier = it },
                    label = { Text("Fournisseur / Prestataire") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = invoiceNumber,
                    onValueChange = { invoiceNumber = it },
                    label = { Text("Numéro de Facture") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Montant en DZD") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description des travaux ou achats") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Double approbation requise du second syndic.", fontSize = 11.sp, color = SandAmber)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountText.toLongOrNull() ?: 0L
                    if (supplier.isNotBlank() && amt > 0) {
                        onConfirm(category, description, amt, supplier, invoiceNumber, method, null)
                    }
                }
            ) {
                Text("Soumettre pour approbation", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

@Composable
fun FinancialCorrectionDialog(
    lockedTransactions: List<FinancialLedgerEntry>,
    appLanguage: AppLanguage,
    onDismiss: () -> Unit,
    onConfirm: (String, Long, String) -> Unit
) {
    var selectedTxId by remember { mutableStateOf(lockedTransactions.firstOrNull()?.txId ?: "") }
    var newAmountText by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Demande de Correction Financière", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "L'enregistrement initial restera immuable. Une nouvelle transaction corrective sera soumise au 2ème syndic.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = selectedTxId,
                    onValueChange = { selectedTxId = it },
                    label = { Text("Identifiant Transaction (ex: TX-2026-000101)") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = newAmountText,
                    onValueChange = { newAmountText = it },
                    label = { Text("Montant corrigé réel (DZD)") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Motif obligatoire de la correction") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = newAmountText.toLongOrNull() ?: 0L
                    if (selectedTxId.isNotBlank() && reason.isNotBlank()) {
                        onConfirm(selectedTxId, amt, reason)
                    }
                }
            ) {
                Text("Soumettre Correction", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

@Composable
fun NewAnnouncementDialog(
    appLanguage: AppLanguage,
    onDismiss: () -> Unit,
    onConfirm: (String, String, AnnouncementCategory) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(AnnouncementCategory.GENERAL) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nouvelle Annonce aux Copropriétaires", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Titre de l'annonce") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Message de l'annonce") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank() && content.isNotBlank()) {
                        onConfirm(title, content, category)
                    }
                }
            ) {
                Text("Diffuser", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

@Composable
fun NewMeetingDialog(
    appLanguage: AppLanguage,
    onDismiss: () -> Unit,
    onConfirm: (String, Long, String, String, String) -> Unit
) {
    var title by remember { mutableStateOf("Assemblée Générale Ordinaire 2026") }
    var location by remember { mutableStateOf("Hall de l'immeuble") }
    var agenda by remember { mutableStateOf("1. Bilan financier\n2. Travaux ascenseur\n3. Questions diverses") }
    var description by remember { mutableStateOf("Convocation officielle de tous les 40 copropriétaires.") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Programmer une Réunion / AG", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Titre") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = location, onValueChange = { location = it }, label = { Text("Lieu") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = agenda, onValueChange = { agenda = it }, label = { Text("Ordre du jour") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        val futureDate = System.currentTimeMillis() + (14L * 24 * 3600 * 1000)
                        onConfirm(title, futureDate, location, description, agenda)
                    }
                }
            ) {
                Text("Planifier", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}
