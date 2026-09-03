import { createApp } from '../src/index';
import { initDatabase, query } from '../src/db';
import http from 'http';

async function runConcurrencyAndSecurityTests() {
  console.log('=== RUNNING AMARATI CONCURRENCY, ATTACK & SECURITY VERIFICATION SUITE ===\n');
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
      // 1. Authenticate Single Syndic and Regular Owner
      const resSyndic = await fetch(`${baseUrl}/api/v1/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: 'apt1', password: 'amarati123' })
      });
      const syndic = await resSyndic.json() as any;

      const resOwner = await fetch(`${baseUrl}/api/v1/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: 'apt5', password: 'amarati123' })
      });
      const owner = await resOwner.json() as any;

      // ATTACK TEST 1: Unauthenticated request rejection
      await test('Security: Missing token rejected with 401 on protected route', async () => {
        const res = await fetch(`${baseUrl}/api/v1/apartments`);
        if (res.status !== 401) throw new Error(`Expected 401, got ${res.status}`);
      });

      // ATTACK TEST 2: Forged token rejection
      await test('Security: Forged token rejected with 401', async () => {
        const res = await fetch(`${baseUrl}/api/v1/apartments`, {
          headers: { Authorization: 'Bearer forged.jwt.token' }
        });
        if (res.status !== 401) throw new Error(`Expected 401, got ${res.status}`);
      });

      // ATTACK TEST 3: Privilege Escalation Attempt (Regular Owner attempts Syndic operations)
      await test('Security: Regular owner cannot create project (403 Forbidden)', async () => {
        const res = await fetch(`${baseUrl}/api/v1/projects`, {
          method: 'POST',
          headers: { 
            Authorization: `Bearer ${owner.token}`,
            'Content-Type': 'application/json'
          },
          body: JSON.stringify({
            title: 'Hacked Project',
            description: 'Unauthorized creation',
            total_cost: 50000
          })
        });
        if (res.status !== 403) throw new Error(`Expected 403, got ${res.status}`);
      });

      await test('Security: Regular owner cannot record payment (403 Forbidden)', async () => {
        const res = await fetch(`${baseUrl}/api/v1/ledger/payments`, {
          method: 'POST',
          headers: { 
            Authorization: `Bearer ${owner.token}`,
            'Content-Type': 'application/json'
          },
          body: JSON.stringify({
            apartment_number: 5,
            project_id: 'PRJ-2026-001',
            amount: 5000,
            payment_method: 'CASH'
          })
        });
        if (res.status !== 403) throw new Error(`Expected 403, got ${res.status}`);
      });

      // ATTACK TEST 4: Direct SQL Injection in Login
      await test('Security: SQL injection payload safely rejected', async () => {
        const res = await fetch(`${baseUrl}/api/v1/auth/login`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ username: "' OR '1'='1", password: "' OR '1'='1" })
        });
        if (res.status !== 401) throw new Error(`Expected 401, got ${res.status}`);
      });

      // CONCURRENCY TEST 1: Concurrent identical payments deduplicated via Permanent Idempotency
      await test('Concurrency: Race condition on identical payment requests deduplicated by server', async () => {
        const raceKey = 'c0a80101-0000-0000-0000-' + Date.now().toString(16).padStart(12, '0');
        const payPayload = {
          apartment_number: 15,
          project_id: 'PRJ-2026-001',
          amount: 10000,
          payment_method: 'CASH',
          idempotency_key: raceKey
        };

        const [p1, p2] = await Promise.all([
          fetch(`${baseUrl}/api/v1/ledger/payments`, {
            method: 'POST',
            headers: { 
              Authorization: `Bearer ${syndic.token}`,
              'Content-Type': 'application/json'
            },
            body: JSON.stringify(payPayload)
          }),
          fetch(`${baseUrl}/api/v1/ledger/payments`, {
            method: 'POST',
            headers: { 
              Authorization: `Bearer ${syndic.token}`,
              'Content-Type': 'application/json'
            },
            body: JSON.stringify(payPayload)
          })
        ]);

        const data1 = await p1.json() as any;
        const data2 = await p2.json() as any;

        const seq1 = data1.transaction?.tx_seq_id;
        const seq2 = data2.transaction?.tx_seq_id;

        if (!seq1 || !seq2 || seq1 !== seq2) {
          throw new Error(`Race condition failed deduplication: ${seq1} vs ${seq2}`);
        }

        // Verify that only 1 record exists with this idempotency key in DB
        const countRes = await query('SELECT COUNT(*) as count FROM financial_transactions WHERE idempotency_key = $1', [raceKey]);
        if (Number(countRes.rows[0].count) !== 1) {
          throw new Error(`Expected 1 transaction in DB, found ${countRes.rows[0].count}`);
        }
      });

      // CONCURRENCY TEST 2: Multiple concurrent payments for different apartments serialized correctly
      await test('Concurrency: Multiple simultaneous payments for distinct apartments serialize monotonically', async () => {
        const promises = [16, 17, 18, 19].map(apt => {
          return fetch(`${baseUrl}/api/v1/ledger/payments`, {
            method: 'POST',
            headers: { 
              Authorization: `Bearer ${syndic.token}`,
              'Content-Type': 'application/json'
            },
            body: JSON.stringify({
              apartment_number: apt,
              project_id: 'PRJ-2026-001',
              amount: 10000,
              payment_method: 'BANK_TRANSFER',
              idempotency_key: `00000000-0000-0000-0000-${String(apt).padStart(12, '0')}`
            })
          });
        });

        const responses = await Promise.all(promises);
        for (const r of responses) {
          if (r.status !== 201) throw new Error(`Concurrent payment returned status ${r.status}`);
        }

        const datas = await Promise.all(responses.map(r => r.json())) as any[];
        const txSeqs = datas.map(d => d.transaction.tx_seq_id);
        const uniqueSeqs = new Set(txSeqs);

        if (uniqueSeqs.size !== 4) {
          throw new Error('Concurrent payments must receive distinct sequential TX IDs');
        }
      });

      // CONCURRENCY TEST 3: Concurrent voting attempts for the same apartment safely handled
      await test('Concurrency: Simultaneous votes for same apartment rejected with 409 Conflict', async () => {
        const [v1, v2] = await Promise.all([
          fetch(`${baseUrl}/api/v1/voting/VOTE-001/vote`, {
            method: 'POST',
            headers: { 
              Authorization: `Bearer ${owner.token}`,
              'Content-Type': 'application/json'
            },
            body: JSON.stringify({ choice: 'YES' })
          }),
          fetch(`${baseUrl}/api/v1/voting/VOTE-001/vote`, {
            method: 'POST',
            headers: { 
              Authorization: `Bearer ${owner.token}`,
              'Content-Type': 'application/json'
            },
            body: JSON.stringify({ choice: 'NO' })
          })
        ]);

        const statuses = [v1.status, v2.status];
        if (!statuses.includes(201) || !statuses.includes(409)) {
          throw new Error(`Expected exactly one 201 and one 409, got ${statuses.join(', ')}`);
        }
      });

      console.log(`\n=== CONCURRENCY & SECURITY SUITE COMPLETED: ${passed} PASSED, ${failed} FAILED ===\n`);
    } finally {
      server.close();
      if (failed > 0) process.exit(1);
    }
  });
}

runConcurrencyAndSecurityTests().catch(err => {
  console.error('Test error:', err);
  process.exit(1);
});
