package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.BuildingDatabase
import com.example.data.BuildingRepository
import com.example.model.PaymentMethod
import com.example.model.TransactionType
import com.example.model.User
import com.example.model.UserRole
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    private lateinit var db: BuildingDatabase
    private lateinit var repository: BuildingRepository

    private val syndic1 = User(
        id = 1L,
        username = "apt1",
        fullName = "Ahmed Benali",
        phoneNumber = "0550112233",
        role = UserRole.SYNDIC,
        apartmentNumber = 1,
        floor = 0
    )
    private val owner2 = User(
        id = 2L,
        username = "apt2",
        fullName = "Karim Mansouri",
        phoneNumber = "0550223344",
        role = UserRole.OWNER,
        apartmentNumber = 2,
        floor = 0
    )
    private val owner14 = User(
        id = 14L,
        username = "apt14",
        fullName = "Leila Amrani",
        phoneNumber = "0550141414",
        role = UserRole.OWNER,
        apartmentNumber = 14,
        floor = 3
    )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, BuildingDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = BuildingRepository(db.buildingDao())
        runBlocking {
            repository.initializeSeedDataIfNeeded()
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun readStringFromContext() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Amarati", appName)
    }

    @Test
    fun testAll40ApartmentsTransparency() = runBlocking {
        val list = repository.getTransparencyApartmentsList(null)
        assertEquals(40, list.size)
        // Verify apartment 1 to 40 are all present
        for (i in 1..40) {
            assertTrue("Apartment $i should exist", list.any { it.apartmentNumber == i })
        }
    }

    @Test
    fun testSingleSyndicDirectProjectCreation() = runBlocking {
        // Non-syndic owner cannot create project
        val nonSyndicResult = repository.createFinancialProject(
            "Travaux Non Autorises",
            "Tentative copropriétaire",
            100_000L,
            owner2
        )
        assertTrue("Non-syndic cannot create project", nonSyndicResult.isFailure)

        // Single Syndic creates project directly without double approval
        val createResult = repository.createFinancialProject(
            "Travaux Ascenseur Test",
            "Changement des câbles",
            200_000L,
            syndic1
        )
        assertTrue("Syndic direct project creation must succeed", createResult.isSuccess)
        val projId = createResult.getOrThrow()

        val projects = repository.getAllProjectsFlow().first()
        val proj = projects.find { it.id == projId }
        assertNotNull(proj)
        assertEquals("ACTIVE", proj?.status?.name)
    }

    @Test
    fun testSingleSyndicDirectExpenseCreation() = runBlocking {
        // Single Syndic creates an expense directly into LOCKED ledger
        val createExpenseResult = repository.createExpense(
            null,
            null,
            "Électricité",
            "Remplacement disjoncteur général",
            45_000L,
            "SARL Élec Alger",
            "FAC-2026-999",
            PaymentMethod.BANK_TRANSFER,
            syndic1
        )
        assertTrue("Direct expense creation must succeed", createExpenseResult.isSuccess)
        val txId = createExpenseResult.getOrThrow()

        val ledger = repository.getLockedLedgerFlow().first()
        val tx = ledger.find { it.txId == txId }
        assertNotNull(tx)
        assertEquals("LOCKED", tx?.status?.name)
        assertEquals(45_000L, tx?.amount)
    }

    @Test
    fun testOwnerPaymentIsLockedImmediately() = runBlocking {
        val paymentResult = repository.recordOwnerPayment(
            "PRJ-01",
            "Ravalement et Peinture Façades",
            14,
            owner14.id,
            owner14.fullName,
            5000L,
            PaymentMethod.CASH,
            syndic1
        )
        assertTrue(paymentResult.isSuccess)
        val txId = paymentResult.getOrThrow()

        val ledger = repository.getLockedLedgerFlow().first()
        val tx = ledger.find { it.txId == txId }
        assertNotNull(tx)
        assertEquals("LOCKED", tx?.status?.name)
    }

    @Test
    fun testFinancialCorrectionPreservesOriginalTransaction() = runBlocking {
        // 1. Record original payment
        val paymentResult = repository.recordOwnerPayment(
            "PRJ-01",
            "Ravalement et Peinture Façades",
            2,
            owner2.id,
            owner2.fullName,
            6000L,
            PaymentMethod.CASH,
            syndic1
        )
        assertTrue(paymentResult.isSuccess)
        val originalTxId = paymentResult.getOrThrow()

        // 2. Record financial correction referencing originalTxId
        val correctionResult = repository.recordFinancialCorrection(
            originalTxId = originalTxId,
            correctionDelta = 1000L,
            isDebit = true,
            reason = "Erreur de saisie: montant réel 5000 DZD au lieu de 6000 DZD",
            syndic = syndic1
        )
        assertTrue(correctionResult.isSuccess)
        val correctionTxId = correctionResult.getOrThrow()

        // 3. Verify original transaction is completely unchanged and still LOCKED
        val ledger = repository.getLockedLedgerFlow().first()
        val originalTx = ledger.find { it.txId == originalTxId }
        assertNotNull("Original transaction must still exist", originalTx)
        assertEquals(6000L, originalTx?.amount)
        assertEquals("LOCKED", originalTx?.status?.name)

        // 4. Verify correction transaction references originalTxId
        val corrTx = ledger.find { it.txId == correctionTxId }
        assertNotNull("Correction transaction must exist", corrTx)
        assertEquals(originalTxId, corrTx?.originalTxId)
        assertTrue(corrTx?.isCorrection == true)
        assertEquals(1000L, corrTx?.amount)
        assertEquals(TransactionType.CORRECTION_DEBIT, corrTx?.type)
    }
}
