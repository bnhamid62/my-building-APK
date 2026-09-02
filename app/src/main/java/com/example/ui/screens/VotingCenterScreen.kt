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
import com.example.model.*
import com.example.ui.localization.AppLanguage
import com.example.ui.localization.Strings
import com.example.ui.theme.AtlasEmerald
import com.example.ui.theme.CoralRed
import com.example.ui.theme.SandAmber
import kotlinx.coroutines.flow.Flow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VotingCenterScreen(
    user: User,
    appLanguage: AppLanguage,
    votingSessions: List<VotingSession>,
    getVotesForSession: (String) -> Flow<List<VoteRecord>>,
    onCastVote: (String, VoteChoice) -> Unit,
    onBack: () -> Unit
) {
    var showConfirmDialog by remember { mutableStateOf<VoteChoice?>(null) }
    val selectedSession = votingSessions.firstOrNull()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        Strings.transparentVoting(appLanguage),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (selectedSession == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (appLanguage == AppLanguage.ARABIC) "لا توجد جلسات تصويت نشطة." else "Aucune session de vote en cours.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            val votesFlow = remember(selectedSession.id) { getVotesForSession(selectedSession.id) }
            val votes by votesFlow.collectAsState(initial = emptyList())

            val userVote = votes.find { it.userId == user.id }
            val hasVoted = userVote != null

            val yesCount = votes.count { it.choice == VoteChoice.YES }
            val noCount = votes.count { it.choice == VoteChoice.NO }
            val abstainCount = votes.count { it.choice == VoteChoice.ABSTAIN }
            val totalParticipation = votes.size

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header / Question card
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.HowToVote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (selectedSession.isClosed) "Vote Clôturé" else "Scrutin Ouvert",
                                    fontWeight = FontWeight.Bold,
                                    color = if (selectedSession.isClosed) MaterialTheme.colorScheme.onSurfaceVariant else AtlasEmerald,
                                    fontSize = 12.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = selectedSession.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = selectedSession.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Invariant reminder: Voting is transparent and locked
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = SandAmber.copy(alpha = 0.12f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Lock, contentDescription = null, tint = SandAmber, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = Strings.votePrompt(appLanguage),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = SandAmber
                                    )
                                }
                            }
                        }
                    }
                }

                // Real-time Results Tally
                item {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = if (appLanguage == AppLanguage.ARABIC) "النتائج ونسبة المشاركة (40 شقة)" else "Résultats & Participation (40 appartements)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = AtlasEmerald.copy(alpha = 0.15f),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(Strings.voteYes(appLanguage), color = AtlasEmerald, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text("$yesCount", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = AtlasEmerald)
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = CoralRed.copy(alpha = 0.15f),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(Strings.voteNo(appLanguage), color = CoralRed, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text("$noCount", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = CoralRed)
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(Strings.voteAbstain(appLanguage), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text("$abstainCount", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "Participation : $totalParticipation / 40 copropriétaires (${((totalParticipation / 40f) * 100).toInt()}%)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // User voting buttons (or current locked choice)
                item {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (hasVoted) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            if (hasVoted) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Verified, contentDescription = null, tint = AtlasEmerald)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (appLanguage == AppLanguage.ARABIC)
                                            "لقد شاركت في هذا التصويت باختيار: ${userVote?.choice?.name}"
                                        else
                                            "Vous avez voté : ${userVote?.choice?.name} (Verrouillé)",
                                        fontWeight = FontWeight.Bold,
                                        color = AtlasEmerald
                                    )
                                }
                            } else {
                                Text(
                                    text = if (appLanguage == AppLanguage.ARABIC) "أدلِ بصوتك لشقتك (الشقة ${user.apartmentNumber}):" else "Exprimez votre vote pour l'Apt ${user.apartmentNumber} :",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { showConfirmDialog = VoteChoice.YES },
                                        colors = ButtonDefaults.buttonColors(containerColor = AtlasEmerald),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("vote_yes_button"),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(Strings.voteYes(appLanguage), fontWeight = FontWeight.Bold)
                                    }

                                    Button(
                                        onClick = { showConfirmDialog = VoteChoice.NO },
                                        colors = ButtonDefaults.buttonColors(containerColor = CoralRed),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("vote_no_button"),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(Strings.voteNo(appLanguage), fontWeight = FontWeight.Bold)
                                    }

                                    OutlinedButton(
                                        onClick = { showConfirmDialog = VoteChoice.ABSTAIN },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("vote_abstain_button"),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(Strings.voteAbstain(appLanguage), fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                // TRANSPARENT PUBLIC VOTES TABLE (Requirement 17: Owner Name + Apartment + Choice)
                item {
                    Text(
                        text = if (appLanguage == AppLanguage.ARABIC) "السجل العلني لأصوات الملاك" else "Registre Public des Votes par Copropriétaire",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(votes) { record ->
                    val choiceColor = when (record.choice) {
                        VoteChoice.YES -> AtlasEmerald
                        VoteChoice.NO -> CoralRed
                        VoteChoice.ABSTAIN -> MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(1.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "${record.apartmentNumber}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column {
                                    Text(
                                        text = record.userName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "Apt ${record.apartmentNumber}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = choiceColor.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = record.choice.name,
                                    color = choiceColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Confirmation Dialog before locking the vote
            if (showConfirmDialog != null) {
                val choice = showConfirmDialog!!
                AlertDialog(
                    onDismissRequest = { showConfirmDialog = null },
                    title = {
                        Text(
                            text = if (appLanguage == AppLanguage.ARABIC) "تأكيد التصويت النهائي" else "Confirmation Définitive du Vote",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Text(
                            text = if (appLanguage == AppLanguage.ARABIC)
                                "هل أنت متأكد من اختيار (${choice.name})؟ سيتم تسجيل اسمك ورقم شقتك (${user.apartmentNumber}) علناً ولن تتمكن من تعديل صوتك بعد التأكيد."
                            else
                                "Confirmez-vous votre vote (${choice.name}) pour l'appartement ${user.apartmentNumber} ? Ce vote est public et ne pourra plus être modifié."
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                onCastVote(selectedSession.id, choice)
                                showConfirmDialog = null
                            },
                            modifier = Modifier.testTag("confirm_vote_dialog_button")
                        ) {
                            Text(if (appLanguage == AppLanguage.ARABIC) "تأكيد وقفل الصوت" else "Confirmer et Verrouiller", fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showConfirmDialog = null }) {
                            Text(if (appLanguage == AppLanguage.ARABIC) "تراجع" else "Annuler")
                        }
                    }
                )
            }
        }
    }
}
