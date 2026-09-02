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
import com.example.data.ApartmentTransparencyItem
import com.example.model.*
import com.example.ui.localization.AppLanguage
import com.example.ui.localization.Strings
import com.example.ui.theme.AtlasEmerald
import com.example.ui.theme.CoralRed
import com.example.ui.theme.SandAmber

@Composable
fun FinanceScreen(
    appLanguage: AppLanguage,
    projects: List<Project>,
    transparencyList: List<ApartmentTransparencyItem>,
    lockedLedger: List<FinancialLedgerEntry>,
    selectedProjectId: String?,
    onSelectProjectFilter: (String?) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFloorFilter by remember { mutableStateOf<Int?>(null) }

    val totalContributionsCollected = lockedLedger.filter { it.type == TransactionType.OWNER_PAYMENT }.sumOf { it.amount }
    val totalExpensesPaid = lockedLedger.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
    val currentBalance = totalContributionsCollected - totalExpensesPaid

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // High-level Financial KPIs
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 2.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = Strings.treasuryBalance(appLanguage),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "$currentBalance DZD",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = AtlasEmerald.copy(alpha = 0.1f),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = Strings.totalIncome(appLanguage),
                                style = MaterialTheme.typography.labelSmall,
                                color = AtlasEmerald
                            )
                            Text(
                                text = "+$totalContributionsCollected DZD",
                                fontWeight = FontWeight.Bold,
                                color = AtlasEmerald,
                                fontSize = 14.sp
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = CoralRed.copy(alpha = 0.1f),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = Strings.totalExpenses(appLanguage),
                                style = MaterialTheme.typography.labelSmall,
                                color = CoralRed
                            )
                            Text(
                                text = "-$totalExpensesPaid DZD",
                                fontWeight = FontWeight.Bold,
                                color = CoralRed,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }

        // 3 Sub-tabs
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text(Strings.activeProjects(appLanguage), fontSize = 13.sp) },
                modifier = Modifier.testTag("tab_projects")
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text(Strings.transparencyTable(appLanguage), fontSize = 13.sp) },
                modifier = Modifier.testTag("tab_transparency")
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text(Strings.expensesAndInvoices(appLanguage), fontSize = 13.sp) },
                modifier = Modifier.testTag("tab_expenses")
            )
        }

        when (selectedTab) {
            0 -> ProjectsTabContent(projects, appLanguage)
            1 -> TransparencyTabContent(
                transparencyList = transparencyList,
                projects = projects,
                selectedProjectId = selectedProjectId,
                searchQuery = searchQuery,
                onSearchChange = { searchQuery = it },
                selectedFloorFilter = selectedFloorFilter,
                onFloorFilterChange = { selectedFloorFilter = it },
                onSelectProjectFilter = onSelectProjectFilter,
                appLanguage = appLanguage
            )
            2 -> ExpensesTabContent(lockedLedger.filter { it.type == TransactionType.EXPENSE }, appLanguage)
        }
    }
}

@Composable
fun ProjectsTabContent(projects: List<Project>, appLanguage: AppLanguage) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(projects) { project ->
            val progress = if (project.totalCost > 0) {
                (project.totalCollected.toFloat() / project.totalCost.toFloat()).coerceIn(0f, 1f)
            } else 0f

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = project.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        AssistChip(
                            onClick = {},
                            label = { Text(project.status.name, fontSize = 11.sp) },
                            colors = AssistChipDefaults.assistChipColors(
                                labelColor = if (project.status == ProjectStatus.APPROVED) AtlasEmerald else SandAmber
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = project.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Calculation breakdown: Total / 40 = per apartment
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = if (appLanguage == AppLanguage.ARABIC) "التكلفة الإجمالية" else "Coût total",
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Text(
                                    text = "${project.totalCost} DZD",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                            Column {
                                Text(
                                    text = if (appLanguage == AppLanguage.ARABIC) "عدد الشقق" else "Appartements",
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Text(
                                    text = "40",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                            Column {
                                Text(
                                    text = if (appLanguage == AppLanguage.ARABIC) "مساهمة الشقة" else "Part / Appartement",
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Text(
                                    text = "${project.contributionPerApt} DZD",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Progress bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${if (appLanguage == AppLanguage.ARABIC) "المحصل" else "Collecté"}: ${project.totalCollected} DZD",
                            style = MaterialTheme.typography.labelSmall,
                            color = AtlasEmerald
                        )
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = AtlasEmerald,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Créé par : ${project.creatorName} • Approuvé par : ${project.approverName ?: "En attente"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun TransparencyTabContent(
    transparencyList: List<ApartmentTransparencyItem>,
    projects: List<Project>,
    selectedProjectId: String?,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    selectedFloorFilter: Int?,
    onFloorFilterChange: (Int?) -> Unit,
    onSelectProjectFilter: (String?) -> Unit,
    appLanguage: AppLanguage
) {
    val filteredList = transparencyList.filter { item ->
        val matchesSearch = searchQuery.isBlank() ||
                item.ownerName.contains(searchQuery, ignoreCase = true) ||
                item.apartmentNumber.toString().contains(searchQuery)
        val matchesFloor = selectedFloorFilter == null || item.floor == selectedFloorFilter
        matchesSearch && matchesFloor
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Search and Floor filters
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    placeholder = {
                        Text(
                            if (appLanguage == AppLanguage.ARABIC) "بحث عن شقة أو اسم المالك..."
                            else "Rechercher par n° d'appartement ou nom..."
                        )
                    },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("transparency_search_field")
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Floor filter buttons (All, Floor 0..9)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = selectedFloorFilter == null,
                        onClick = { onFloorFilterChange(null) },
                        label = { Text(if (appLanguage == AppLanguage.ARABIC) "الكل" else "Tous", fontSize = 11.sp) }
                    )
                    (0..4).forEach { floor ->
                        FilterChip(
                            selected = selectedFloorFilter == floor,
                            onClick = { onFloorFilterChange(if (selectedFloorFilter == floor) null else floor) },
                            label = { Text("Et.$floor", fontSize = 11.sp) }
                        )
                    }
                }
            }
        }

        // Table Header
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (appLanguage == AppLanguage.ARABIC) "الشقة" else "Apt",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(60.dp)
                )
                Text(
                    text = if (appLanguage == AppLanguage.ARABIC) "المالك" else "Copropriétaire",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (appLanguage == AppLanguage.ARABIC) "المسدد" else "Payé",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(80.dp)
                )
                Text(
                    text = if (appLanguage == AppLanguage.ARABIC) "الوضعية" else "Statut",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(70.dp)
                )
            }
        }

        // 40 Apartments List
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("transparency_list")
        ) {
            items(filteredList) { apt ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.width(60.dp)) {
                        Text(
                            text = "N° ${apt.apartmentNumber}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Et. ${apt.floor}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = apt.ownerName,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = apt.ownerPhone,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Text(
                        text = "${apt.paidAmount} DZD",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.width(80.dp)
                    )

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (apt.isPaid) AtlasEmerald.copy(alpha = 0.15f) else CoralRed.copy(alpha = 0.15f),
                        modifier = Modifier.width(70.dp)
                    ) {
                        Text(
                            text = if (apt.isPaid) Strings.statusPaid(appLanguage) else Strings.statusUnpaid(appLanguage),
                            color = if (apt.isPaid) AtlasEmerald else CoralRed,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
fun ExpensesTabContent(expenses: List<FinancialLedgerEntry>, appLanguage: AppLanguage) {
    if (expenses.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (appLanguage == AppLanguage.ARABIC) "لا توجد مصاريف مسجلة حالياً." else "Aucune dépense enregistrée.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(expenses) { exp ->
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ReceiptLong,
                                contentDescription = null,
                                tint = CoralRed
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = exp.supplier ?: exp.projectTitle ?: "Dépense",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = "-${exp.amount} DZD",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = CoralRed
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Catégorie : ${exp.expenseCategory ?: "Général"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (exp.invoiceNumber != null) {
                            Text(
                                text = "Facture : ${exp.invoiceNumber}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Double approval lock seal
                    Surface(
                        color = AtlasEmerald.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = AtlasEmerald,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Verrouillé • Créé: ${exp.creatorName} • Approuvé: ${exp.approverName ?: "2ème Syndic"}",
                                fontSize = 11.sp,
                                color = AtlasEmerald,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}
