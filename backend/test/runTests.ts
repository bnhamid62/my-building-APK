import { createApp } from '../src/index';
import http from 'http';

function runSuite() {
  console.log('=== RUNNING AMARATI AUTHORITATIVE BACKEND VERIFICATION SUITE ===\n');
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
      let syndic1Token = '';
      let syndic2Token = '';
      let ownerToken = '';

      // TEST 1: Secure Authentication & Password Hashing Verification
      await test('Auth: Login with valid credentials & return JWT', async () => {
        const res = await fetch(`${baseUrl}/api/v1/auth/login`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ username: 'apt1', password: 'amarati123' })
        });
        if (res.status !== 200) throw new Error(`Expected 200, got ${res.status}`);
        const data = await res.json() as any;
        if (!data.token || data.user.role !== 'OWNER_SYNDIC') throw new Error('Invalid auth response payload');
        syndic1Token = data.token;
      });

      await test('Auth: Login Syndic 2 (apt2)', async () => {
        const res = await fetch(`${baseUrl}/api/v1/auth/login`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ username: 'apt2', password: 'amarati123' })
        });
        const data = await res.json() as any;
        syndic2Token = data.token;
      });

      await test('Auth: Login Standard Owner (apt5)', async () => {
        const res = await fetch(`${baseUrl}/api/v1/auth/login`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ username: 'apt5', password: 'amarati123' })
        });
        const data = await res.json() as any;
        if (data.user.role !== 'OWNER') throw new Error('Role must be OWNER');
        ownerToken = data.token;
      });

      await test('Auth: Reject incorrect password with 401', async () => {
        const res = await fetch(`${baseUrl}/api/v1/auth/login`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ username: 'apt1', password: 'wrongpassword' })
        });
        if (res.status !== 401) throw new Error(`Expected 401, got ${res.status}`);
      });

      // TEST 2: 40 Apartments Structure & Transparency
      await test('Structure: Authoritative 40 apartments on 10 levels (RDC + Floors 1..9)', async () => {
        const res = await fetch(`${baseUrl}/api/v1/apartments`, {
          headers: { Authorization: `Bearer ${ownerToken}` }
        });
        const apts = await res.json() as any[];
        if (apts.length !== 40) throw new Error(`Expected 40 apartments, got ${apts.length}`);
        
        // Check RDC (apts 1..4)
        for (let i = 1; i <= 4; i++) {
          const apt = apts.find(a => a.number === i);
          if (!apt || apt.floor_label !== 'RDC' || apt.floor !== 0) throw new Error(`Apt ${i} should be on RDC`);
        }

        // Check Floor 9 (apts 37..40)
        for (let i = 37; i <= 40; i++) {
          const apt = apts.find(a => a.number === i);
          if (!apt || apt.floor_label !== '9' || apt.floor !== 9) throw new Error(`Apt ${i} should be on Floor 9`);
        }
      });

      // TEST 3: Dual-Syndic Approval Invariant (Projects)
      let testProjectId = '';
      await test('Dual-Syndic: Syndic 1 creates project -> PENDING_APPROVAL', async () => {
        const res = await fetch(`${baseUrl}/api/v1/projects`, {
          method: 'POST',
          headers: { 
            Authorization: `Bearer ${syndic1Token}`,
            'Content-Type': 'application/json'
          },
          body: JSON.stringify({
            title: 'Rénovation Porte Entrée Principale',
            description: 'Remplacement porte vitrée sécurisée',
            total_cost: 200000
          })
        });
        if (res.status !== 201) throw new Error(`Expected 201, got ${res.status}`);
        const p = await res.json() as any;
        if (p.status !== 'PENDING_APPROVAL') throw new Error('Status must be PENDING_APPROVAL');
        if (p.contribution_per_apt !== 5000) throw new Error('Contribution must be 200000 / 40 = 5000');
        testProjectId = p.id;
      });

      await test('Dual-Syndic: Syndic 1 self-approval attempt is strictly BLOCKED (403)', async () => {
        const res = await fetch(`${baseUrl}/api/v1/projects/${testProjectId}/approve`, {
          method: 'POST',
          headers: { Authorization: `Bearer ${syndic1Token}` }
        });
        if (res.status !== 403) throw new Error(`Expected 403 Forbidden, got ${res.status}`);
        const data = await res.json() as any;
        if (!data.error || !data.error.includes('ERR_SELF_APPROVAL_PROHIBITED')) {
          throw new Error('Expected ERR_SELF_APPROVAL_PROHIBITED');
        }
      });

      await test('Dual-Syndic: Regular Owner cannot approve project (403)', async () => {
        const res = await fetch(`${baseUrl}/api/v1/projects/${testProjectId}/approve`, {
          method: 'POST',
          headers: { Authorization: `Bearer ${ownerToken}` }
        });
        if (res.status !== 403) throw new Error(`Expected 403 Forbidden, got ${res.status}`);
      });

      await test('Dual-Syndic: Syndic 2 approves project -> APPROVED (200)', async () => {
        const res = await fetch(`${baseUrl}/api/v1/projects/${testProjectId}/approve`, {
          method: 'POST',
          headers: { Authorization: `Bearer ${syndic2Token}` }
        });
        if (res.status !== 200) throw new Error(`Expected 200, got ${res.status}`);
        const p = await res.json() as any;
        if (p.status !== 'APPROVED') throw new Error('Status must be APPROVED');
      });

      // TEST 4: Direct Owner Payment (Single Syndic directly locks, sequential collision-safe ID)
      let paymentTxId = '';
      const idempotencyKey = `idemp-pay-${Date.now()}`;
      await test('Payment: Syndic 1 records payment directly -> LOCKED with sequential TX ID', async () => {
        const res = await fetch(`${baseUrl}/api/v1/ledger/payments`, {
          method: 'POST',
          headers: { 
            Authorization: `Bearer ${syndic1Token}`,
            'Content-Type': 'application/json'
          },
          body: JSON.stringify({
            apartment_number: 10,
            project_id: testProjectId,
            amount: 5000,
            payment_method: 'CASH',
            idempotency_key: idempotencyKey
          })
        });
        if (res.status !== 201) throw new Error(`Expected 201, got ${res.status}`);
        const tx = await res.json() as any;
        if (tx.status !== 'LOCKED') throw new Error('Payment must be immediately LOCKED');
        if (!tx.tx_id.startsWith('TX-2026-')) throw new Error('Invalid TX ID format');
        paymentTxId = tx.tx_id;
      });

      await test('Payment: Idempotent duplicate submission returns existing TX without duplicate credit', async () => {
        const res = await fetch(`${baseUrl}/api/v1/ledger/payments`, {
          method: 'POST',
          headers: { 
            Authorization: `Bearer ${syndic1Token}`,
            'Content-Type': 'application/json'
          },
          body: JSON.stringify({
            apartment_number: 10,
            project_id: testProjectId,
            amount: 5000,
            payment_method: 'CASH',
            idempotency_key: idempotencyKey
          })
        });
        const tx = await res.json() as any;
        if (tx.tx_id !== paymentTxId) throw new Error('Idempotency failure: new transaction was generated');
      });

      // TEST 5: Ledger Immutability (Update & Delete blocked)
      await test('Immutability: Attempt to PUT / modify locked transaction returns 409 Conflict', async () => {
        const res = await fetch(`${baseUrl}/api/v1/ledger/${paymentTxId}`, {
          method: 'PUT',
          headers: { 
            Authorization: `Bearer ${syndic1Token}`,
            'Content-Type': 'application/json'
          },
          body: JSON.stringify({ amount: 1000 })
        });
        if (res.status !== 409) throw new Error(`Expected 409 Conflict, got ${res.status}`);
      });

      await test('Immutability: Attempt to DELETE locked transaction returns 409 Conflict', async () => {
        const res = await fetch(`${baseUrl}/api/v1/ledger/${paymentTxId}`, {
          method: 'DELETE',
          headers: { Authorization: `Bearer ${syndic1Token}` }
        });
        if (res.status !== 409) throw new Error(`Expected 409 Conflict, got ${res.status}`);
      });

      // TEST 6: Financial Correction Cycle
      let correctionTxId = '';
      await test('Correction: Syndic 2 requests correction linked to original TX -> PENDING_APPROVAL', async () => {
        const res = await fetch(`${baseUrl}/api/v1/ledger/corrections`, {
          method: 'POST',
          headers: { 
            Authorization: `Bearer ${syndic2Token}`,
            'Content-Type': 'application/json'
          },
          body: JSON.stringify({
            original_tx_id: paymentTxId,
            corrected_amount: 1000,
            correction_type: 'CREDIT',
            reason: 'Erreur saisie montant reçu en espèces'
          })
        });
        if (res.status !== 201) throw new Error(`Expected 201, got ${res.status}`);
        const corr = await res.json() as any;
        if (corr.status !== 'PENDING_APPROVAL') throw new Error('Status must be PENDING_APPROVAL');
        if (corr.original_tx_id !== paymentTxId) throw new Error('Original TX link missing');
        correctionTxId = corr.tx_id;
      });

      await test('Correction: Self-approval by Syndic 2 blocked (403)', async () => {
        const res = await fetch(`${baseUrl}/api/v1/ledger/${correctionTxId}/approve`, {
          method: 'POST',
          headers: { Authorization: `Bearer ${syndic2Token}` }
        });
        if (res.status !== 403) throw new Error(`Expected 403 Forbidden, got ${res.status}`);
      });

      await test('Correction: Approved by Syndic 1 -> LOCKED', async () => {
        const res = await fetch(`${baseUrl}/api/v1/ledger/${correctionTxId}/approve`, {
          method: 'POST',
          headers: { Authorization: `Bearer ${syndic1Token}` }
        });
        if (res.status !== 200) throw new Error(`Expected 200, got ${res.status}`);
        const corr = await res.json() as any;
        if (corr.status !== 'LOCKED') throw new Error('Status must be LOCKED');
      });

      // TEST 7: Public Voting & Single Vote Per Apartment Invariant
      await test('Voting: Owner apt2 casts vote YES', async () => {
        const res = await fetch(`${baseUrl}/api/v1/voting/VOTE-001/vote`, {
          method: 'POST',
          headers: { 
            Authorization: `Bearer ${ownerToken}`,
            'Content-Type': 'application/json'
          },
          body: JSON.stringify({ choice: 'YES' })
        });
        if (res.status !== 201) throw new Error(`Expected 201, got ${res.status}`);
      });

      await test('Voting: Second vote attempt for same apartment is strictly BLOCKED (409)', async () => {
        const res = await fetch(`${baseUrl}/api/v1/voting/VOTE-001/vote`, {
          method: 'POST',
          headers: { 
            Authorization: `Bearer ${ownerToken}`,
            'Content-Type': 'application/json'
          },
          body: JSON.stringify({ choice: 'NO' })
        });
        if (res.status !== 409) throw new Error(`Expected 409 Conflict, got ${res.status}`);
      });

      // TEST 8: Real Offline Sync Batch Push
      await test('Sync Engine: Batch sync creates maintenance and promotes payment', async () => {
        const res = await fetch(`${baseUrl}/api/v1/sync/push`, {
          method: 'POST',
          headers: { 
            Authorization: `Bearer ${syndic1Token}`,
            'Content-Type': 'application/json'
          },
          body: JSON.stringify({
            items: [
              {
                local_id: 'local-maint-001',
                type: 'MAINTENANCE',
                category: 'LIGHTING',
                description: 'Ampoule grillée palier 4e étage'
              },
              {
                local_id: 'local-pay-001',
                type: 'PAYMENT',
                apartment_number: 12,
                project_id: testProjectId,
                amount: 5000,
                payment_method: 'CASH',
                idempotency_key: `offline-pay-sync-${Date.now()}`
              }
            ]
          })
        });
        if (res.status !== 200) throw new Error(`Expected 200, got ${res.status}`);
        const data = await res.json() as any;
        if (data.synced.length !== 2) throw new Error('Expected 2 synced items');
        if (data.synced[1].status !== 'LOCKED') throw new Error('Payment must be server LOCKED');
      });

      console.log(`\n=== BACKEND SUITE COMPLETED: ${passed} PASSED, ${failed} FAILED ===\n`);
    } finally {
      server.close();
      if (failed > 0) {
        process.exit(1);
      }
    }
  });
}

runSuite();
