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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.MaintenanceCategory
import com.example.model.MaintenanceReport
import com.example.model.MaintenanceStatus
import com.example.model.User
import com.example.ui.localization.AppLanguage
import com.example.ui.localization.Strings
import com.example.ui.theme.AtlasEmerald
import com.example.ui.theme.CoralRed
import com.example.ui.theme.SandAmber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaintenanceScreen(
    user: User,
    appLanguage: AppLanguage,
    reports: List<MaintenanceReport>,
    onSubmitReport: (MaintenanceCategory, String, String?) -> Unit,
    onNavigateToElevator: () -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf(MaintenanceCategory.ELEVATOR) }
    var description by remember { mutableStateOf("") }
    var photoAttached by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(Strings.reportProblem(appLanguage), fontWeight = FontWeight.Bold) },
                modifier = Modifier.testTag("report_issue_fab")
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Elevator dedicated facility banner shortcut
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .testTag("elevator_banner_shortcut")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Elevator,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = Strings.elevatorFacility(appLanguage),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (appLanguage == AppLanguage.ARABIC) "متابعة الصيانة الدورية وعقود الإصلاح" else "Suivi des révisions et contrat d'entretien",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    FilledTonalButton(onClick = onNavigateToElevator) {
                        Text(if (appLanguage == AppLanguage.ARABIC) "عرض" else "Voir", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Reports Header
            Text(
                text = if (appLanguage == AppLanguage.ARABIC) "سجل بلاغات الأعطال في العمارة" else "Historique des pannes signalées",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(reports) { report ->
                    val statusColor = when (report.status) {
                        MaintenanceStatus.NEW -> SandAmber
                        MaintenanceStatus.IN_PROGRESS -> MaterialTheme.colorScheme.primary
                        MaintenanceStatus.RESOLVED -> AtlasEmerald
                    }

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
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val icon = when (report.category) {
                                        MaintenanceCategory.ELEVATOR -> Icons.Default.Elevator
                                        MaintenanceCategory.ELECTRICITY -> Icons.Default.Bolt
                                        MaintenanceCategory.WATER -> Icons.Default.WaterDrop
                                        MaintenanceCategory.LIGHTING -> Icons.Default.Lightbulb
                                        MaintenanceCategory.ENTRANCE -> Icons.Default.DoorFront
                                        MaintenanceCategory.DOORS -> Icons.Default.MeetingRoom
                                        MaintenanceCategory.COMMON_AREAS -> Icons.Default.CleaningServices
                                        MaintenanceCategory.OTHER -> Icons.Default.Build
                                    }
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "${report.category.name} (Apt ${report.apartmentNumber})",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                }

                                Surface(
                                    color = statusColor.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = report.status.name,
                                        color = statusColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = report.description,
                                style = MaterialTheme.typography.bodyMedium
                            )

                            if (!report.syndicNotes.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(modifier = Modifier.padding(8.dp)) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Note du Syndic : ${report.syndicNotes}",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Réf: ${report.id} • Signalé par: ${report.reporterName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    // Report Dialog
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(Strings.reportProblem(appLanguage), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = if (appLanguage == AppLanguage.ARABIC) "اختر الفئة:" else "Sélectionner la catégorie :",
                        style = MaterialTheme.typography.labelMedium
                    )

                    // Category Selector
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = selectedCategory.name,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            MaintenanceCategory.values().forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat.name) },
                                    onClick = {
                                        selectedCategory = cat
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text(if (appLanguage == AppLanguage.ARABIC) "وصف المشكلة بالتفصيل" else "Description précise de la panne") },
                        minLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("maintenance_desc_input")
                    )

                    // Photo attachment button
                    OutlinedButton(
                        onClick = { photoAttached = !photoAttached },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = if (photoAttached) Icons.Default.Check else Icons.Default.AddAPhoto,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (photoAttached)
                                (if (appLanguage == AppLanguage.ARABIC) "تم إرفاق الصورة بنجاح" else "Photo attachée")
                            else
                                (if (appLanguage == AppLanguage.ARABIC) "إرفاق صورة للخلل" else "Prendre / Joindre une photo")
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (description.isNotBlank()) {
                            onSubmitReport(
                                selectedCategory,
                                description,
                                if (photoAttached) "content://photo_evidence" else null
                            )
                            showDialog = false
                            description = ""
                            photoAttached = false
                        }
                    },
                    modifier = Modifier.testTag("submit_report_button")
                ) {
                    Text(if (appLanguage == AppLanguage.ARABIC) "إرسال البلاغ" else "Transmettre", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(if (appLanguage == AppLanguage.ARABIC) "إلغاء" else "Annuler")
                }
            }
        )
    }
}
