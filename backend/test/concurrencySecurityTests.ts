import { createApp } from '../src/index';
import http from 'http';

async function runConcurrencyAndSecurityTests() {
  console.log('=== RUNNING CONCURRENCY, ATTACK & SECURITY VERIFICATION SUITE ===\n');
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
      // 1. Authenticate actors
      const res1 = await fetch(`${baseUrl}/api/v1/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: 'apt1', password: 'amarati123' })
      });
      const syndic1 = await res1.json() as any;

      const res2 = await fetch(`${baseUrl}/api/v1/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: 'apt2', password: 'amarati123' })
      });
      const syndic2 = await res2.json() as any;

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

      // ATTACK TEST 3: Privilege Escalation Attempt (Regular Owner attempts to create project)
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

      // ATTACK TEST 4: Direct SQL Injection in Login
      await test('Security: SQL injection payload rejected safely', async () => {
        const res = await fetch(`${baseUrl}/api/v1/auth/login`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ username: "' OR '1'='1", password: "' OR '1'='1" })
        });
        if (res.status !== 401) throw new Error(`Expected 401, got ${res.status}`);
      });

      // CONCURRENCY TEST 1: Simultaneous Project Approvals
      // Create project by Syndic 1
      const pRes = await fetch(`${baseUrl}/api/v1/projects`, {
        method: 'POST',
        headers: { 
          Authorization: `Bearer ${syndic1.token}`,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({
          title: 'Sécurité Incendie - Extincteurs',
          description: 'Achat de 10 extincteurs aux normes',
          total_cost: 80000
        })
      });
      const prj = await pRes.json() as any;

      await test('Concurrency: Simultaneous approvals handled safely', async () => {
        const [req1, req2] = await Promise.all([
          fetch(`${baseUrl}/api/v1/projects/${prj.id}/approve`, {
            method: 'POST',
            headers: { Authorization: `Bearer ${syndic2.token}` }
          }),
          fetch(`${baseUrl}/api/v1/projects/${prj.id}/approve`, {
            method: 'POST',
            headers: { Authorization: `Bearer ${syndic2.token}` }
          })
        ]);

        const statuses = [req1.status, req2.status];
        if (!statuses.includes(200)) throw new Error('At least one approval must succeed with 200');
        // The other should either be 200 or 400 (already approved)
      });

      // CONCURRENCY TEST 2: Concurrent identical payments deduplicated via Idempotency
      await test('Concurrency: Race condition on identical payments deduplicated by server', async () => {
        const raceKey = `race-pay-key-${Date.now()}`;
        const payPayload = {
          apartment_number: 15,
          project_id: prj.id,
          amount: 2000,
          payment_method: 'CASH',
          idempotency_key: raceKey
        };

        const [p1, p2] = await Promise.all([
          fetch(`${baseUrl}/api/v1/ledger/payments`, {
            method: 'POST',
            headers: { 
              Authorization: `Bearer ${syndic1.token}`,
              'Content-Type': 'application/json'
            },
            body: JSON.stringify(payPayload)
          }),
          fetch(`${baseUrl}/api/v1/ledger/payments`, {
            method: 'POST',
            headers: { 
              Authorization: `Bearer ${syndic1.token}`,
              'Content-Type': 'application/json'
            },
            body: JSON.stringify(payPayload)
          })
        ]);

        const data1 = await p1.json() as any;
        const data2 = await p2.json() as any;

        if (data1.tx_id !== data2.tx_id) {
          throw new Error(`Race condition failed idempotency: ${data1.tx_id} vs ${data2.tx_id}`);
        }
      });

      console.log(`\n=== CONCURRENCY & SECURITY SUITE COMPLETED: ${passed} PASSED, ${failed} FAILED ===\n`);
    } finally {
      server.close();
      if (failed > 0) process.exit(1);
    }
  });
}

runConcurrencyAndSecurityTests();
