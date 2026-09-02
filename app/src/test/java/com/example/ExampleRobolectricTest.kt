package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.BuildingDatabase
import com.example.data.BuildingRepository
import com.example.model.PaymentMethod
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
        role = UserRole.OWNER_SYNDIC,
        apartmentNumber = 1,
        floor = 0
    )
    private val syndic2 = User(
        id = 2L,
        username = "apt2",
        fullName = "Karim Mansouri",
        phoneNumber = "0550223344",
        role = UserRole.OWNER_SYNDIC,
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
    fun testFinancialProjectDoubleApprovalInvariant() = runBlocking {
        // Syndic 1 creates project
        val createResult = repository.createFinancialProject(
            "Travaux Ascenseur Test",
            "Changement des câbles",
            200_000L,
            syndic1
        )
        assertTrue(createResult.isSuccess)
        val projId = createResult.getOrThrow()

        // Rule: Syndic 1 CANNOT approve their own project!
        val selfApproveResult = repository.approveFinancialProject(projId, syndic1, true)
        assertTrue("Self-approval must be rejected", selfApproveResult.isFailure)

        // Rule: Syndic 2 CAN approve the project
        val secondSyndicApproveResult = repository.approveFinancialProject(projId, syndic2, true)
        assertTrue("Second syndic approval must succeed", secondSyndicApproveResult.isSuccess)
    }

    @Test
    fun testExpenseDoubleApprovalInvariant() = runBlocking {
        // Syndic 2 creates an expense
        val createExpenseResult = repository.createExpense(
            null,
            null,
            "Électricité",
            "Remplacement disjoncteur général",
            45_000L,
            "SARL Élec Alger",
            "FAC-2026-999",
            PaymentMethod.BANK_TRANSFER,
            syndic2
        )
        assertTrue(createExpenseResult.isSuccess)
        val txId = createExpenseResult.getOrThrow()

        // Rule: Syndic 2 cannot self-approve
        val selfApprove = repository.approveExpense(txId, syndic2, true)
        assertTrue("Self-approval of expense must fail", selfApprove.isFailure)

        // Rule: Syndic 1 approves
        val syndic1Approve = repository.approveExpense(txId, syndic1, true)
        assertTrue("Syndic 1 approval must succeed", syndic1Approve.isSuccess)
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
}
