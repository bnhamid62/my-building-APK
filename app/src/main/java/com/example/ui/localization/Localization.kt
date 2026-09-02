package com.example.ui.localization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

enum class AppLanguage(val code: String, val displayName: String, val layoutDirection: LayoutDirection) {
    ARABIC("ar", "العربية", LayoutDirection.Rtl),
    FRENCH("fr", "Français", LayoutDirection.Ltr)
}

enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.FRENCH }

object Strings {
    fun appName(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "عمارتي | Mon Immeuble"
        AppLanguage.FRENCH -> "عمارتي | Mon Immeuble"
    }

    fun buildingStructure(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "عمارة سكنية: 9 طوابق • 40 شقة"
        AppLanguage.FRENCH -> "Résidence : 9 étages • 40 appartements"
    }

    fun tabHome(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "الرئيسية"
        AppLanguage.FRENCH -> "Accueil"
    }

    fun tabFinance(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "المالية"
        AppLanguage.FRENCH -> "Finances"
    }

    fun tabMaintenance(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "الصيانة"
        AppLanguage.FRENCH -> "Maintenance"
    }

    fun tabMore(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "المزيد"
        AppLanguage.FRENCH -> "Plus"
    }

    fun tabSyndicManage(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "الإدارة"
        AppLanguage.FRENCH -> "Gestion"
    }

    fun syndicModeSwitch(lang: AppLanguage, isSyndicMode: Boolean) = when (lang) {
        AppLanguage.ARABIC -> if (isSyndicMode) "العودة لحسابي كمالك" else "إدارة العمارة (وكيل)"
        AppLanguage.FRENCH -> if (isSyndicMode) "Mode Propriétaire" else "Gestion Immeuble (Syndic)"
    }

    fun myOwnerAccount(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "حسابي كمالك"
        AppLanguage.FRENCH -> "Mon Compte Propriétaire"
    }

    fun buildingManagement(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "إدارة العمارة"
        AppLanguage.FRENCH -> "Gestion de l'Immeuble"
    }

    fun offline(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "غير متصل بالإنترنت"
        AppLanguage.FRENCH -> "Hors ligne"
    }

    fun online(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "متصل بالشبكة"
        AppLanguage.FRENCH -> "En ligne"
    }

    fun lastSync(lang: AppLanguage, time: String) = when (lang) {
        AppLanguage.ARABIC -> "آخر مزامنة: $time"
        AppLanguage.FRENCH -> "Dernière synchronisation : $time"
    }

    fun apartment(lang: AppLanguage, num: Int, floor: Int) = when (lang) {
        AppLanguage.ARABIC -> "الشقة $num • الطابق $floor"
        AppLanguage.FRENCH -> "Apt $num • Étage $floor"
    }

    fun personalStatus(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "الوضعية المالية لشقتي"
        AppLanguage.FRENCH -> "Situation financière de mon appartement"
    }

    fun upToDate(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "الاشتراكات مسددة بالكامل"
        AppLanguage.FRENCH -> "Cotisations à jour"
    }

    fun balanceOwed(lang: AppLanguage, amount: Long) = when (lang) {
        AppLanguage.ARABIC -> "المبلغ المستحق: $amount دج"
        AppLanguage.FRENCH -> "Reste à payer : $amount DZD"
    }

    fun treasuryBalance(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "رصيد صندوق العمارة"
        AppLanguage.FRENCH -> "Solde de la trésorerie"
    }

    fun totalIncome(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "مجموع المداخيل"
        AppLanguage.FRENCH -> "Total Recettes"
    }

    fun totalExpenses(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "مجموع المصاريف"
        AppLanguage.FRENCH -> "Total Dépenses"
    }

    fun activeProjects(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "المشاريع الجارية"
        AppLanguage.FRENCH -> "Projets en cours"
    }

    fun transparencyTable(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "جدول الشفافية (40 شقة)"
        AppLanguage.FRENCH -> "Tableau de Transparence (40 appartements)"
    }

    fun allApartmentsTransparency(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "شفافية الدفع لجميع الملاك"
        AppLanguage.FRENCH -> "Statut des paiements de tous les copropriétaires"
    }

    fun statusPaid(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "مسدد"
        AppLanguage.FRENCH -> "Payé"
    }

    fun statusUnpaid(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "غير مسدد"
        AppLanguage.FRENCH -> "Non payé"
    }

    fun expensesAndInvoices(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "المصاريف والفواتير"
        AppLanguage.FRENCH -> "Dépenses & Factures"
    }

    fun doubleApprovalRequired(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "موافقة ثنائية إلزامية من الوكيلين"
        AppLanguage.FRENCH -> "Double approbation obligatoire des 2 syndics"
    }

    fun pendingSecondApproval(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "في انتظار موافقة الوكيل الثاني"
        AppLanguage.FRENCH -> "En attente d'approbation du 2ème syndic"
    }

    fun approvedAndLocked(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "معتمد ومقفل في السجل المالي"
        AppLanguage.FRENCH -> "Approuvé et verrouillé dans le registre"
    }

    fun recordPayment(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "تسجيل دفعة مالك"
        AppLanguage.FRENCH -> "Enregistrer un paiement"
    }

    fun directLockNotice(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "الدفعات تسجل مباشرة وتُقفل نهائياً بدون إمكانية التعديل أو الحذف"
        AppLanguage.FRENCH -> "Les paiements sont enregistrés directement et verrouillés sans modification ni suppression"
    }

    fun reportProblem(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "الإبلاغ عن عطل"
        AppLanguage.FRENCH -> "Signaler une panne"
    }

    fun elevatorFacility(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "قسم المصعد"
        AppLanguage.FRENCH -> "Espace Ascenseur"
    }

    fun announcements(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "الإعلانات والتبليغات"
        AppLanguage.FRENCH -> "Annonces & Avis"
    }

    fun meetings(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "الاجتماعات والجمعيات العامة"
        AppLanguage.FRENCH -> "Réunions & Assemblées"
    }

    fun transparentVoting(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "التصويت العلني الشفاف"
        AppLanguage.FRENCH -> "Votes Publics & Transparents"
    }

    fun documents(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "الوثائق والعقود"
        AppLanguage.FRENCH -> "Documents & Contrats"
    }

    fun auditLog(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "سجل العمليات الدائم (Audit Log)"
        AppLanguage.FRENCH -> "Journal d'Audit Immuable"
    }

    fun settings(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "الإعدادات"
        AppLanguage.FRENCH -> "Paramètres"
    }

    fun logout(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "تسجيل الخروج"
        AppLanguage.FRENCH -> "Déconnexion"
    }

    fun loginTitle(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "تسجيل الدخول إلى عمارتي"
        AppLanguage.FRENCH -> "Connexion à Mon Immeuble"
    }

    fun username(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "اسم المستخدم (مثال: apt1)"
        AppLanguage.FRENCH -> "Nom d'utilisateur (ex: apt1)"
    }

    fun password(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "كلمة المرور"
        AppLanguage.FRENCH -> "Mot de passe"
    }

    fun signIn(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "دخول"
        AppLanguage.FRENCH -> "Se connecter"
    }

    fun quickAccountSwitch(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "تبديل سريع للحساب (للتجربة والتحقق)"
        AppLanguage.FRENCH -> "Sélection rapide du compte (Test & Démo)"
    }

    fun syndic1(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "وكيل 1: أحمد بن علي (شقة 1)"
        AppLanguage.FRENCH -> "Syndic 1 : Ahmed Benali (Apt 1)"
    }

    fun syndic2(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "وكيل 2: كريم منصوري (شقة 2)"
        AppLanguage.FRENCH -> "Syndic 2 : Karim Mansouri (Apt 2)"
    }

    fun ownerSample(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "مالك عادي: ليلى عمراني (شقة 14)"
        AppLanguage.FRENCH -> "Copropriétaire : Leila Amrani (Apt 14)"
    }

    fun votePrompt(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "التصويت علني ومقفل، لا يمكن تغييره بعد الإرسال"
        AppLanguage.FRENCH -> "Le vote est public et définitif, impossible de le modifier après validation"
    }

    fun voteYes(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "موافق (نعم)"
        AppLanguage.FRENCH -> "Oui"
    }

    fun voteNo(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "غير موافق (لا)"
        AppLanguage.FRENCH -> "Non"
    }

    fun voteAbstain(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "امتناع"
        AppLanguage.FRENCH -> "Abstention"
    }
}
