package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    lockedLedger: List<FinancialLedgerEntry>,
    onCreateProject: (String, String, Long) -> Unit,
    onRecordPayment: (String, String, Int, Long, String, Long, PaymentMethod) -> Unit,
    onCreateExpense: (String?, String?, String, String, Long, String, String, PaymentMethod) -> Unit,
    onRecordCorrection: (String, Long, Boolean, String) -> Unit,
    onPublishAnnouncement: (String, String, AnnouncementCategory) -> Unit,
    onScheduleMeeting: (String, Long, String, String, String) -> Unit
) {
    var activeDialog by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Management Banner (Single-Syndic Authoritative Model)
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = AtlasEmerald.copy(alpha = 0.12f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = AtlasEmerald, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = Strings.buildingManagement(appLanguage),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = AtlasEmerald
                            )
                            Text(
                                text = "${user.fullName} • Syndic Unique (Apt ${user.apartmentNumber})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = if (appLanguage == AppLanguage.ARABIC)
                            "النموذج المالي المعتمد: وكيل واحد مباشر، وسجل مالي غير قابل للتعديل (LOCKED). التصحيحات تسجل كحركات تعويضية جديدة."
                        else
                            "Modèle Syndic Unique : Actions directes et registre financier immuable (LOCKED). Les corrections créent de nouvelles écritures compensatoires sans modifier l'original.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Quick Action Buttons
        item {
            Text(
                text = if (appLanguage == AppLanguage.ARABIC) "إجراءات التسيير المالي والإداري" else "Actions de Gestion Financière & Administrative",
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
                    onClick = { activeDialog = "RECORD_CORRECTION" },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("action_record_correction"),
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

        // Active Projects Overview
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (appLanguage == AppLanguage.ARABIC) "المشاريع المالية الجارية" else "Projets Financiers Actifs",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        items(projects) { project ->
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(project.title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Badge(containerColor = AtlasEmerald) {
                            Text(project.status.name, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Coût: ${project.totalCost} DZD • Quote-part: ${project.contributionPerApt} DZD/appartement (40 appartements)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val progress = if (project.totalCost > 0) (project.totalCollected.toFloat() / project.totalCost).coerceIn(0f, 1f) else 0f
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = AtlasEmerald
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Collecté: ${project.totalCollected} DZD / ${project.totalCost} DZD (${(progress * 100).toInt()}%)",
                        fontSize = 11.sp,
                        color = AtlasEmerald
                    )
                }
            }
        }

        // Recent Locked Transactions in Authoritative Ledger
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (appLanguage == AppLanguage.ARABIC) "السجل المالي المقفل (LOCKED)" else "Registre Financier Verrouillé (LOCKED)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Badge(containerColor = AtlasEmerald) {
                    Text("${lockedLedger.size} écritures", modifier = Modifier.padding(4.dp))
                }
            }
        }

        items(lockedLedger.take(15)) { tx ->
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(tx.txId, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            if (tx.isCorrection) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = SandAmber.copy(alpha = 0.2f)
                                ) {
                                    Text("CORRECTION", fontSize = 9.sp, color = SandAmber, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp))
                                }
                            }
                        }
                        val desc = when (tx.type) {
                            TransactionType.OWNER_PAYMENT -> "Paiement Apt ${tx.apartmentNumber} (${tx.ownerName})"
                            TransactionType.EXPENSE -> "Dépense: ${tx.supplier} (${tx.invoiceNumber ?: "Facture"})"
                            TransactionType.CORRECTION_CREDIT -> "Correction Crédit (Réf: ${tx.originalTxId ?: "N/A"})"
                            TransactionType.CORRECTION_DEBIT -> "Correction Débit (Réf: ${tx.originalTxId ?: "N/A"})"
                        }
                        Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (tx.originalTxId != null) {
                            Text("Transaction d'origine préservée: ${tx.originalTxId}", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                        }
                    }

                    val isCredit = (tx.type == TransactionType.OWNER_PAYMENT || tx.type == TransactionType.CORRECTION_CREDIT)
                    Text(
                        text = if (isCredit) "+${tx.amount} DZD" else "-${tx.amount} DZD",
                        fontWeight = FontWeight.Bold,
                        color = if (isCredit) AtlasEmerald else CoralRed,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }

    // --- Dialogs ---
    when (activeDialog) {
        "RECORD_PAYMENT" -> {
            RecordPaymentDialog(
                projects = projects,
                appLanguage = appLanguage,
                onDismiss = { activeDialog = null },
                onConfirm = { pId, pTitle, apt, amt, meth ->
                    activeDialog = null
                    onRecordPayment(pId, pTitle, apt, apt.toLong(), "Apt $apt", amt, meth)
                }
            )
        }
        "NEW_PROJECT" -> {
            NewProjectDialog(
                appLanguage = appLanguage,
                onDismiss = { activeDialog = null },
                onConfirm = { title, desc, cost ->
                    activeDialog = null
                    onCreateProject(title, desc, cost)
                }
            )
        }
        "NEW_EXPENSE" -> {
            NewExpenseDialog(
                projects = projects,
                appLanguage = appLanguage,
                onDismiss = { activeDialog = null },
                onConfirm = { cat, desc, amt, sup, inv, meth, pId ->
                    activeDialog = null
                    onCreateExpense(pId, null, cat, desc, amt, sup, inv, meth)
                }
            )
        }
        "RECORD_CORRECTION" -> {
            FinancialCorrectionDialog(
                lockedTransactions = lockedLedger,
                appLanguage = appLanguage,
                onDismiss = { activeDialog = null },
                onConfirm = { origTxId, delta, isDebit, reason ->
                    activeDialog = null
                    onRecordCorrection(origTxId, delta, isDebit, reason)
                }
            )
        }
        "NEW_ANNOUNCEMENT" -> {
            NewAnnouncementDialog(
                appLanguage = appLanguage,
                onDismiss = { activeDialog = null },
                onConfirm = { t, c, cat ->
                    activeDialog = null
                    onPublishAnnouncement(t, c, cat)
                }
            )
        }
        "NEW_MEETING" -> {
            NewMeetingDialog(
                appLanguage = appLanguage,
                onDismiss = { activeDialog = null },
                onConfirm = { t, d, l, desc, ag ->
                    activeDialog = null
                    onScheduleMeeting(t, d, l, desc, ag)
                }
            )
        }
    }
}

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
                Text(
                    text = "Enregistrement direct par le Syndic. La transaction est immédiatement verrouillée (LOCKED).",
                    fontSize = 12.sp,
                    color = AtlasEmerald
                )

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
                    label = { Text("Coût total (DZD)") },
                    modifier = Modifier.fillMaxWidth().testTag("project_cost_input")
                )

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
                Text("Créer & Activer le Projet", fontWeight = FontWeight.Bold)
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
        title = { Text("Enregistrer une Dépense", fontWeight = FontWeight.Bold) },
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
                Text("Enregistrer & Verrouiller", fontWeight = FontWeight.Bold)
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
    onConfirm: (String, Long, Boolean, String) -> Unit
) {
    var selectedTxId by remember { mutableStateOf(lockedTransactions.firstOrNull()?.txId ?: "") }
    var deltaText by remember { mutableStateOf("") }
    var isDebit by remember { mutableStateOf(true) }
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Correction Financière Directe", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "L'écriture d'origine reste IMMUABLE. Une nouvelle transaction corrective sera enregistrée avec référence à la transaction originale.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = selectedTxId,
                    onValueChange = { selectedTxId = it },
                    label = { Text("Identifiant Transaction Originale (original_tx_id)") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = deltaText,
                    onValueChange = { deltaText = it },
                    label = { Text("Montant de régularisation (DZD)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = isDebit,
                        onClick = { isDebit = true },
                        label = { Text("Débit (-)") }
                    )
                    FilterChip(
                        selected = !isDebit,
                        onClick = { isDebit = false },
                        label = { Text("Crédit (+)") }
                    )
                }

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
                    val delta = deltaText.toLongOrNull() ?: 0L
                    if (selectedTxId.isNotBlank() && delta > 0 && reason.isNotBlank()) {
                        onConfirm(selectedTxId, delta, isDebit, reason)
                    }
                }
            ) {
                Text("Enregistrer Correction", fontWeight = FontWeight.Bold)
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
    var agenda by remember { mutableStateOf("1. Bilan financier officiel\n2. Travaux ascenseur\n3. Questions diverses") }
    var description by remember { mutableStateOf("Convocation officielle des 40 copropriétaires.") }

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
