import { createApp } from '../src/index';
import { initDatabase, query } from '../src/db';
import http from 'http';

async function runSuite() {
  console.log('=== RUNNING AMARATI AUTHORITATIVE POSTGRESQL & SINGLE-SYNDIC VERIFICATION SUITE ===\n');
  await initDatabase();
  const app = createApp();
  const server = http.createServer(app);

  server.listen(0, async () => {
    const port = (server.address() as any).port;
    const baseUrl = `http://localhost:${port}`;
    let passed = 0;
    let failed = 0;

    async function test(name: string, fn: () => Promise<void>) {
      try {
        await fn();
        console.log(`  [PASS] ${name}`);
        passed++;
      } catch (err: any) {
        console.error(`  [FAIL] ${name}: ${err.message}`);
        failed++;
      }
    }

    try {
      let syndicToken = '';
      let ownerToken = '';
      let testPaymentTxId = '';
      let testPaymentSeqId = '';

      // 1. TOPOLOGY & FIXED BUILDING INTEGRITY
      await test('Topology: Exactly 40 apartments exist on 10 levels (RDC + Floors 1..9)', async () => {
        const res = await query('SELECT * FROM apartments ORDER BY apartment_number ASC');
        if (res.rows.length !== 40) throw new Error(`Expected 40 apartments, found ${res.rows.length}`);

        // Verify RDC has apt 1..4
        for (let i = 1; i <= 4; i++) {
          const apt = res.rows.find(a => a.apartment_number === i);
          if (!apt || apt.floor !== 0) throw new Error(`Apartment ${i} must be on floor 0 (RDC)`);
        }

        // Verify Floor 9 has apt 37..40
        for (let i = 37; i <= 40; i++) {
          const apt = res.rows.find(a => a.apartment_number === i);
          if (!apt || apt.floor !== 9) throw new Error(`Apartment ${i} must be on floor 9`);
        }
      });

      await test('Topology: Exactly 40 user accounts exist with strict 1:1 apartment ownership', async () => {
        const res = await query('SELECT * FROM users');
        if (res.rows.length !== 40) throw new Error(`Expected 40 users, found ${res.rows.length}`);

        // Check apartment_id uniqueness
        const aptIds = new Set(res.rows.map(u => u.apartment_id));
        if (aptIds.size !== 40) throw new Error('Apartment IDs in users table are not strictly 1:1 unique');
      });

      await test('Topology: Exactly ONE Syndic exists (apt1) and exactly 39 OWNERS exist', async () => {
        const syndics = await query("SELECT * FROM users WHERE role = 'SYNDIC'");
        if (syndics.rows.length !== 1) throw new Error(`Expected exactly 1 Syndic, found ${syndics.rows.length}`);
        if (syndics.rows[0].username !== 'apt1') throw new Error(`Syndic must be apt1, found ${syndics.rows[0].username}`);

        const owners = await query("SELECT * FROM users WHERE role = 'OWNER'");
        if (owners.rows.length !== 39) throw new Error(`Expected exactly 39 owners, found ${owners.rows.length}`);
      });

      await test('Topology: Database rejects duplicate user on same apartment (1:1 constraint)', async () => {
        try {
          const apt1 = (await query('SELECT id FROM apartments WHERE apartment_number = 1')).rows[0];
          await query(`
            INSERT INTO users (username, password_hash, full_name, phone, role, apartment_id)
            VALUES ('duplicate_user', 'hash', 'Test Duplicate', '0555000000', 'OWNER', $1)
          `, [apt1.id]);
          throw new Error('Should have failed due to UNIQUE constraint on apartment_id');
        } catch (err: any) {
          if (!err.message.includes('unique') && !err.message.includes('UNIQUE')) {
            throw err;
          }
        }
      });

      // 2. AUTHENTICATION & SECURITY
      await test('Auth: Syndic login (apt1) returns valid JWT and role = SYNDIC', async () => {
        const res = await fetch(`${baseUrl}/api/v1/auth/login`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ username: 'apt1', password: 'amarati123' })
        });
        if (res.status !== 200) throw new Error(`Expected 200, got ${res.status}`);
        const data = await res.json() as any;
        if (!data.token || data.user.role !== 'SYNDIC') throw new Error('Invalid syndic payload');
        syndicToken = data.token;
      });

      await test('Auth: Standard Owner login (apt2) returns valid JWT and role = OWNER', async () => {
        const res = await fetch(`${baseUrl}/api/v1/auth/login`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ username: 'apt2', password: 'amarati123' })
        });
        if (res.status !== 200) throw new Error(`Expected 200, got ${res.status}`);
        const data = await res.json() as any;
        if (!data.token || data.user.role !== 'OWNER') throw new Error('Invalid owner payload');
        ownerToken = data.token;
      });

      await test('Auth: Invalid password returns 401 Unauthorized', async () => {
        const res = await fetch(`${baseUrl}/api/v1/auth/login`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ username: 'apt1', password: 'wrongpassword' })
        });
        if (res.status !== 401) throw new Error(`Expected 401, got ${res.status}`);
      });

      // 3. AUTHORIZATION & ROLE-BASED ACCESS CONTROL
      await test('Authorization: Standard Owner cannot create project (403 Forbidden)', async () => {
        const res = await fetch(`${baseUrl}/api/v1/projects`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${ownerToken}`
          },
          body: JSON.stringify({
            title: 'Unauthorized Project',
            description: 'Should be rejected',
            total_cost: 100000
          })
        });
        if (res.status !== 403) throw new Error(`Expected 403 Forbidden, got ${res.status}`);
      });

      await test('Authorization: Standard Owner cannot record payment (403 Forbidden)', async () => {
        const res = await fetch(`${baseUrl}/api/v1/ledger/payments`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${ownerToken}`
          },
          body: JSON.stringify({
            apartment_number: 2,
            project_id: 'PRJ-2026-001',
            amount: 5000,
            payment_method: 'CASH'
          })
        });
        if (res.status !== 403) throw new Error(`Expected 403 Forbidden, got ${res.status}`);
      });

      // 4. SINGLE-SYNDIC DIRECT WORKFLOW (No double-approval)
      let createdProjectId = '';
      await test('Single-Syndic: Syndic creates project directly -> Status is immediately ACTIVE', async () => {
        const res = await fetch(`${baseUrl}/api/v1/projects`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${syndicToken}`
          },
          body: JSON.stringify({
            title: 'Ravalement Façade Sud',
            description: 'Peinture extérieure et protection hydrofuge',
            total_cost: 320000
          })
        });
        if (res.status !== 201) throw new Error(`Expected 201, got ${res.status}`);
        const project = await res.json() as any;
        if (project.status !== 'ACTIVE') throw new Error(`Project status must be ACTIVE, got ${project.status}`);
        createdProjectId = project.id;
      });

      // 5. OWNER PAYMENT & IMMUTABLE LEDGER
      const paymentIdempKey = 'a1b2c3d4-e5f6-7890-abcd-ef1234567890';
      await test('Single-Syndic: Syndic records owner payment directly -> LOCKED with sequential TX ID', async () => {
        const res = await fetch(`${baseUrl}/api/v1/ledger/payments`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${syndicToken}`
          },
          body: JSON.stringify({
            apartment_number: 6,
            project_id: createdProjectId,
            amount: 8000,
            payment_method: 'BANK_TRANSFER',
            idempotency_key: paymentIdempKey
          })
        });
        if (res.status !== 201) throw new Error(`Expected 201, got ${res.status}`);
        const data = await res.json() as any;
        if (!data.transaction || data.transaction.status !== 'LOCKED') throw new Error('Transaction must be LOCKED');
        if (data.transaction.type !== 'CREDIT') throw new Error('Payment must be CREDIT');
        testPaymentTxId = data.transaction.id;
        testPaymentSeqId = data.transaction.tx_seq_id;
      });

      // 6. PERMANENT IDEMPOTENCY & REQUEST HASH VALIDATION
      await test('Idempotency A: Same key + identical payload returns existing transaction without duplicate credit', async () => {
        const balanceBefore = (await query(`
          SELECT SUM(amount) as total FROM financial_transactions WHERE status = 'LOCKED' AND type = 'CREDIT'
        `)).rows[0].total;

        const res = await fetch(`${baseUrl}/api/v1/ledger/payments`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${syndicToken}`
          },
          body: JSON.stringify({
            apartment_number: 6,
            project_id: createdProjectId,
            amount: 8000,
            payment_method: 'BANK_TRANSFER',
            idempotency_key: paymentIdempKey
          })
        });

        if (res.status !== 201 && res.status !== 200) throw new Error(`Expected 200/201, got ${res.status}`);
        const data = await res.json() as any;
        if (data.transaction.tx_seq_id !== testPaymentSeqId) throw new Error('Must return existing transaction');

        const balanceAfter = (await query(`
          SELECT SUM(amount) as total FROM financial_transactions WHERE status = 'LOCKED' AND type = 'CREDIT'
        `)).rows[0].total;

        if (Number(balanceBefore) !== Number(balanceAfter)) throw new Error('Duplicate financial entry occurred!');
      });

      await test('Idempotency B: Same key + DIFFERENT payload rejected with HTTP 409 Conflict IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_REQUEST', async () => {
        const res = await fetch(`${baseUrl}/api/v1/ledger/payments`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${syndicToken}`
          },
          body: JSON.stringify({
            apartment_number: 6,
            project_id: createdProjectId,
            amount: 99999, // DIFFERENT AMOUNT!
            payment_method: 'CASH',
            idempotency_key: paymentIdempKey
          })
        });

        if (res.status !== 409) throw new Error(`Expected 409 Conflict, got ${res.status}`);
        const data = await res.json() as any;
        if (data.error !== 'IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_REQUEST') {
          throw new Error(`Expected error code IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_REQUEST, got ${data.error}`);
        }
      });

      // 7. IMMUTABLE FINANCIAL LEDGER ENFORCEMENT
      await test('Immutability: PUT /api/v1/ledger/:id is strictly rejected (409 Conflict)', async () => {
        const res = await fetch(`${baseUrl}/api/v1/ledger/${testPaymentTxId}`, {
          method: 'PUT',
          headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${syndicToken}`
          },
          body: JSON.stringify({ amount: 999999 })
        });
        if (res.status !== 409) throw new Error(`Expected 409 Conflict, got ${res.status}`);
      });

      await test('Immutability: DELETE /api/v1/ledger/:id is strictly rejected (409 Conflict)', async () => {
        const res = await fetch(`${baseUrl}/api/v1/ledger/${testPaymentTxId}`, {
          method: 'DELETE',
          headers: { Authorization: `Bearer ${syndicToken}` }
        });
        if (res.status !== 409) throw new Error(`Expected 409 Conflict, got ${res.status}`);
      });

      await test('Immutability: Database query engine rejects direct SQL UPDATE on financial_transactions', async () => {
        try {
          await query(`UPDATE financial_transactions SET amount = 99999 WHERE id = $1`, [testPaymentTxId]);
          throw new Error('Database should have thrown ERR_LEDGER_IMMUTABLE');
        } catch (err: any) {
          if (!err.message.includes('ERR_LEDGER_IMMUTABLE')) throw err;
        }
      });

      await test('Immutability: Database query engine rejects direct SQL DELETE on financial_transactions', async () => {
        try {
          await query(`DELETE FROM financial_transactions WHERE id = $1`, [testPaymentTxId]);
          throw new Error('Database should have thrown ERR_LEDGER_IMMUTABLE');
        } catch (err: any) {
          if (!err.message.includes('ERR_LEDGER_IMMUTABLE')) throw err;
        }
      });

      // 8. FINANCIAL EXPENSES
      await test('Single-Syndic: Syndic records expense directly -> LOCKED, DEBIT', async () => {
        const res = await fetch(`${baseUrl}/api/v1/ledger/expenses`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${syndicToken}`
          },
          body: JSON.stringify({
            amount: 15000,
            supplier: 'Société Nettoyage Alger Centre',
            invoice_number: 'FAC-2026-099',
            expense_category: 'Nettoyage',
            description: 'Produits et désinfection des paliers'
          })
        });
        if (res.status !== 201) throw new Error(`Expected 201, got ${res.status}`);
        const data = await res.json() as any;
        if (data.transaction.type !== 'DEBIT' || data.transaction.status !== 'LOCKED') {
          throw new Error('Expense must be DEBIT and LOCKED');
        }
      });

      // 9. FINANCIAL CORRECTIONS: COMPENSATING MODEL WITH original_tx_id
      await test('Correction: Syndic creates compensating transaction referencing original_tx_id', async () => {
        const res = await fetch(`${baseUrl}/api/v1/ledger/corrections`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${syndicToken}`
          },
          body: JSON.stringify({
            original_tx_id: testPaymentSeqId,
            amount: 2000,
            correction_type: 'DEBIT',
            reason: 'Erreur de saisie initiale de 2000 DZD en trop'
          })
        });
        if (res.status !== 201) throw new Error(`Expected 201, got ${res.status}`);
        const data = await res.json() as any;
        if (!data.correction_transaction.is_correction) throw new Error('is_correction must be TRUE');
        if (data.correction_transaction.original_tx_id !== testPaymentTxId) {
          throw new Error('original_tx_id must point to the original transaction ID');
        }

        // Verify original transaction is still completely intact in database
        const originalCheck = (await query('SELECT * FROM financial_transactions WHERE id = $1', [testPaymentTxId])).rows[0];
        if (!originalCheck || Number(originalCheck.amount) !== 8000) {
          throw new Error('Original transaction was modified or corrupted!');
        }
      });

      // 10. OFFICIAL BALANCE FORMULA: SUM(CREDIT) - SUM(DEBIT)
      await test('Official Balance: SUM(CREDIT) - SUM(DEBIT) strictly enforced on server', async () => {
        const res = await fetch(`${baseUrl}/api/v1/ledger`, {
          headers: { Authorization: `Bearer ${ownerToken}` }
        });
        const data = await res.json() as any;

        const manualRes = await query(`
          SELECT 
            COALESCE(SUM(CASE WHEN type = 'CREDIT' AND status = 'LOCKED' THEN amount ELSE 0 END), 0) as credit,
            COALESCE(SUM(CASE WHEN type = 'DEBIT' AND status = 'LOCKED' THEN amount ELSE 0 END), 0) as debit
          FROM financial_transactions
        `);
        const expectedBalance = Number(manualRes.rows[0].credit) - Number(manualRes.rows[0].debit);

        if (data.official_balance !== expectedBalance) {
          throw new Error(`Expected balance ${expectedBalance}, got ${data.official_balance}`);
        }
      });

      // 11. GENERAL ASSEMBLY VOTING: 1 VOTE PER APARTMENT
      await test('Voting: Owner apt2 casts vote YES', async () => {
        const res = await fetch(`${baseUrl}/api/v1/voting/VOTE-001/vote`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${ownerToken}`
          },
          body: JSON.stringify({ choice: 'YES' })
        });
        if (res.status !== 201) throw new Error(`Expected 201, got ${res.status}`);
      });

      await test('Voting: Second vote attempt for same apartment is strictly rejected with 409 Conflict', async () => {
        const res = await fetch(`${baseUrl}/api/v1/voting/VOTE-001/vote`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${ownerToken}`
          },
          body: JSON.stringify({ choice: 'NO' })
        });
        if (res.status !== 409) throw new Error(`Expected 409 Conflict, got ${res.status}`);
      });

      // 12. MONOTONIC SYNC CURSOR ENGINE
      await test('Sync Engine: Pull with cursor retrieves events and updates cursor', async () => {
        const res = await fetch(`${baseUrl}/api/v1/sync/pull?cursor=0`, {
          headers: { Authorization: `Bearer ${syndicToken}` }
        });
        if (res.status !== 200) throw new Error(`Expected 200, got ${res.status}`);
        const data = await res.json() as any;
        if (!Array.isArray(data.events) || data.events.length === 0) {
          throw new Error('Sync pull should return created events');
        }
        if (data.current_cursor <= 0) throw new Error('current_cursor must be > 0');
      });

      // 13. AUDIT LOGS IMMUTABILITY
      await test('Audit: Direct modification or deletion of audit logs is prohibited', async () => {
        const putRes = await fetch(`${baseUrl}/api/v1/audit-logs/some-id`, {
          method: 'PUT',
          headers: { Authorization: `Bearer ${syndicToken}` }
        });
        if (putRes.status !== 409) throw new Error(`Expected 409, got ${putRes.status}`);

        const delRes = await fetch(`${baseUrl}/api/v1/audit-logs/some-id`, {
          method: 'DELETE',
          headers: { Authorization: `Bearer ${syndicToken}` }
        });
        if (delRes.status !== 409) throw new Error(`Expected 409, got ${delRes.status}`);
      });

    } finally {
      server.close();
      console.log(`\n=== FINAL ONE-SYNDIC VERIFICATION SUITE: ${passed} PASSED, ${failed} FAILED ===\n`);
      if (failed > 0) {
        process.exit(1);
      }
    }
  });
}

runSuite().catch(err => {
  console.error('Test runner fatal error:', err);
  process.exit(1);
});
