package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.*
import com.example.ui.localization.AppLanguage
import com.example.ui.localization.AppThemeMode
import com.example.ui.localization.Strings
import com.example.ui.theme.AtlasEmerald
import com.example.ui.theme.SandAmber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MoreHubScreen(
    user: User,
    appLanguage: AppLanguage,
    themeMode: AppThemeMode,
    announcements: List<Announcement>,
    meetings: List<Meeting>,
    documents: List<DocumentItem>,
    auditLogs: List<AuditLogEntry>,
    onNavigateToElevator: () -> Unit,
    onNavigateToVoting: () -> Unit,
    onSetLanguage: (AppLanguage) -> Unit,
    onSetThemeMode: (AppThemeMode) -> Unit,
    onLogout: () -> Unit
) {
    var activeSubView by remember { mutableStateOf<String?>(null) }
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    if (activeSubView != null) {
        when (activeSubView) {
            "ANNOUNCEMENTS" -> AnnouncementsSubView(announcements, appLanguage) { activeSubView = null }
            "MEETINGS" -> MeetingsSubView(meetings, appLanguage) { activeSubView = null }
            "DOCUMENTS" -> DocumentsSubView(documents, appLanguage) { activeSubView = null }
            "AUDIT_LOGS" -> AuditLogsSubView(auditLogs, appLanguage) { activeSubView = null }
            "SETTINGS" -> SettingsSubView(appLanguage, themeMode, onSetLanguage, onSetThemeMode) { activeSubView = null }
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = Strings.tabMore(appLanguage),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        item {
            MoreHubItem(
                icon = Icons.Default.HowToVote,
                title = Strings.transparentVoting(appLanguage),
                subtitle = if (appLanguage == AppLanguage.ARABIC) "المشاركة ومشاهدة أصوات الملاك علناً" else "Scrutins en cours & résultats publics",
                tint = MaterialTheme.colorScheme.primary,
                onClick = onNavigateToVoting
            )
        }

        item {
            MoreHubItem(
                icon = Icons.Default.Elevator,
                title = Strings.elevatorFacility(appLanguage),
                subtitle = if (appLanguage == AppLanguage.ARABIC) "متابعة الصيانة الدورية وعقود الإصلاح" else "Visites périodiques, réparations & assistance",
                tint = SandAmber,
                onClick = onNavigateToElevator
            )
        }

        item {
            MoreHubItem(
                icon = Icons.Default.Campaign,
                title = Strings.announcements(appLanguage),
                subtitle = "${announcements.size} " + if (appLanguage == AppLanguage.ARABIC) "تبليغ مسجل" else "annonces diffusées",
                tint = MaterialTheme.colorScheme.secondary,
                onClick = { activeSubView = "ANNOUNCEMENTS" }
            )
        }

        item {
            MoreHubItem(
                icon = Icons.Default.Groups,
                title = Strings.meetings(appLanguage),
                subtitle = if (appLanguage == AppLanguage.ARABIC) "الجمعيات العامة وجدول الأعمال" else "Assemblées générales & ordres du jour",
                tint = AtlasEmerald,
                onClick = { activeSubView = "MEETINGS" }
            )
        }

        item {
            MoreHubItem(
                icon = Icons.Default.Folder,
                title = Strings.documents(appLanguage),
                subtitle = if (appLanguage == AppLanguage.ARABIC) "عقود الصيانة، الفواتير، والقانون الداخلي" else "Contrats, factures & procès-verbaux",
                tint = MaterialTheme.colorScheme.primary,
                onClick = { activeSubView = "DOCUMENTS" }
            )
        }

        item {
            MoreHubItem(
                icon = Icons.Default.HistoryEdu,
                title = Strings.auditLog(appLanguage),
                subtitle = if (appLanguage == AppLanguage.ARABIC) "السجل الثابت لجميع العمليات المالية والإدارية" else "Audit permanent et traçabilité immuable",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = { activeSubView = "AUDIT_LOGS" }
            )
        }

        item {
            MoreHubItem(
                icon = Icons.Default.Settings,
                title = Strings.settings(appLanguage),
                subtitle = if (appLanguage == AppLanguage.ARABIC) "اللغة، المظهر، وكلمة المرور" else "Langue, mode sombre & préférences",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = { activeSubView = "SETTINGS" }
            )
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.ExitToApp, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(Strings.logout(appLanguage), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun MoreHubItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = tint.copy(alpha = 0.12f),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = tint)
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnouncementsSubView(announcements: List<Announcement>, appLanguage: AppLanguage, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Strings.announcements(appLanguage), fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = null) } }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(announcements) { ann ->
                Card(
                    shape = RoundedCornerShape(12.dp),
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
                            Text(ann.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                            AssistChip(onClick = {}, label = { Text(ann.category.name, fontSize = 11.sp) })
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(ann.content, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Par ${ann.authorName}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeetingsSubView(meetings: List<Meeting>, appLanguage: AppLanguage, onBack: () -> Unit) {
    val dateFormat = SimpleDateFormat("dd MMMM yyyy 'à' HH:mm", Locale.getDefault())
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Strings.meetings(appLanguage), fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = null) } }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(meetings) { mtg ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(mtg.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(dateFormat.format(Date(mtg.meetingDate)), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Place, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(mtg.location, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(mtg.description, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Ordre du jour :", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(mtg.agenda, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentsSubView(documents: List<DocumentItem>, appLanguage: AppLanguage, onBack: () -> Unit) {
    val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Strings.documents(appLanguage), fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = null) } }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(documents) { doc ->
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(doc.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("${doc.category} • ${dateFormat.format(Date(doc.date))} • ${doc.fileSizeBytes / 1000} KB", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Icon(Icons.Default.Download, contentDescription = "Download", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditLogsSubView(auditLogs: List<AuditLogEntry>, appLanguage: AppLanguage, onBack: () -> Unit) {
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Strings.auditLog(appLanguage), fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = null) } }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(auditLogs) { log ->
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(log.action, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                            Text(dateFormat.format(Date(log.timestamp)), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(log.details, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Opérateur : ${log.actorName} [${log.entityType}:${log.entityId}]", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSubView(
    appLanguage: AppLanguage,
    themeMode: AppThemeMode,
    onSetLanguage: (AppLanguage) -> Unit,
    onSetThemeMode: (AppThemeMode) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Strings.settings(appLanguage), fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = null) } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Langue de l'interface / لغة التطبيق", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilterChip(
                            selected = appLanguage == AppLanguage.ARABIC,
                            onClick = { onSetLanguage(AppLanguage.ARABIC) },
                            label = { Text("العربية (RTL)", fontWeight = FontWeight.Bold) }
                        )
                        FilterChip(
                            selected = appLanguage == AppLanguage.FRENCH,
                            onClick = { onSetLanguage(AppLanguage.FRENCH) },
                            label = { Text("Français (LTR)", fontWeight = FontWeight.Bold) }
                        )
                    }
                }
            }

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Thème visuel / المظهر", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = themeMode == AppThemeMode.SYSTEM,
                            onClick = { onSetThemeMode(AppThemeMode.SYSTEM) },
                            label = { Text("Système") }
                        )
                        FilterChip(
                            selected = themeMode == AppThemeMode.LIGHT,
                            onClick = { onSetThemeMode(AppThemeMode.LIGHT) },
                            label = { Text("Clair") }
                        )
                        FilterChip(
                            selected = themeMode == AppThemeMode.DARK,
                            onClick = { onSetThemeMode(AppThemeMode.DARK) },
                            label = { Text("Sombre") }
                        )
                    }
                }
            }
        }
    }
}
