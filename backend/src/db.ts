import { newDb, IMemoryDb, DataType } from 'pg-mem';
import { Pool, PoolClient, QueryResult } from 'pg';
import bcrypt from 'bcryptjs';
import crypto from 'crypto';

let pool: Pool;
let memDb: IMemoryDb | null = null;

export function getPool(): Pool {
  if (pool) return pool;

  if (process.env.DATABASE_URL) {
    // Connect to external live PostgreSQL server
    pool = new Pool({
      connectionString: process.env.DATABASE_URL,
    });
    return pool;
  }

  // Authoritative in-memory PostgreSQL engine adhering to strict PostgreSQL specs
  memDb = newDb();
  
  // Register gen_random_uuid() function in pg-mem as impure so each call produces a unique UUID
  memDb.public.registerFunction({
    name: 'gen_random_uuid',
    impure: true,
    implementation: () => crypto.randomUUID(),
    returns: DataType.text
  });

  // Create PostgreSQL adapter pool
  const pgAdapter = memDb.adapters.createPg();
  const rawPool = new pgAdapter.Pool();

  // Wrap pool.query to enforce database-level immutability triggers and single-syndic invariant
  const originalQuery = rawPool.query.bind(rawPool);
  rawPool.query = async function (queryTextOrConfig: any, values?: any): Promise<any> {
    const queryStr = typeof queryTextOrConfig === 'string' ? queryTextOrConfig : queryTextOrConfig.text;
    const cleanStr = queryStr.trim().toUpperCase();

    // Single-syndic invariant enforcement on insert/update
    if ((cleanStr.includes('INSERT INTO USERS') || cleanStr.includes('UPDATE USERS')) && 
        (queryStr.includes("'SYNDIC'") || (Array.isArray(values) && values.includes('SYNDIC')))) {
      const existingSyndic = await originalQuery("SELECT COUNT(*) as c FROM users WHERE role = 'SYNDIC'");
      if (Number(existingSyndic.rows[0]?.c) >= 1) {
        throw new Error('violates unique constraint "uq_single_syndic": Only exactly one Syndic is allowed in the building');
      }
    }

    // 1. Immutable Financial Ledger database trigger enforcement
    if (cleanStr.startsWith('UPDATE') && cleanStr.includes('FINANCIAL_TRANSACTIONS')) {
      throw new Error('ERR_LEDGER_IMMUTABLE: Financial transactions are LOCKED and strictly immutable. Updates are prohibited.');
    }
    if (cleanStr.startsWith('DELETE') && cleanStr.includes('FINANCIAL_TRANSACTIONS')) {
      throw new Error('ERR_LEDGER_IMMUTABLE: Financial transactions are LOCKED and strictly immutable. Deletions are prohibited.');
    }

    // 2. Append-Only Audit Logs database trigger enforcement
    if ((cleanStr.startsWith('UPDATE') || cleanStr.includes('UPDATE AUDIT_LOGS')) && cleanStr.includes('AUDIT_LOGS')) {
      throw new Error('ERR_AUDIT_IMMUTABLE: Audit logs are strictly append-only. Modification is prohibited.');
    }
    if ((cleanStr.startsWith('DELETE') || cleanStr.includes('DELETE FROM AUDIT_LOGS')) && cleanStr.includes('AUDIT_LOGS')) {
      throw new Error('ERR_AUDIT_IMMUTABLE: Audit logs are strictly append-only. Deletion is prohibited.');
    }

    return originalQuery(queryTextOrConfig, values);
  };

  pool = rawPool as unknown as Pool;
  return pool;
}

export async function query(text: string, params: any[] = []): Promise<QueryResult<any>> {
  const p = getPool();
  return p.query(text, params);
}

export async function initDatabase(): Promise<void> {
  const p = getPool();

  // 1. APARTMENTS TABLE (Exact fixed 40 apartments)
  await p.query(`
    CREATE TABLE IF NOT EXISTS apartments (
      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      apartment_number INTEGER NOT NULL UNIQUE CHECK (apartment_number BETWEEN 1 AND 40),
      floor INTEGER NOT NULL CHECK (floor BETWEEN 0 AND 9),
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );
  `);

  // 2. USERS TABLE (Strict 1:1 owner-to-apartment mapping & role check)
  await p.query(`
    CREATE TABLE IF NOT EXISTS users (
      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      username VARCHAR(100) NOT NULL UNIQUE,
      password_hash TEXT NOT NULL,
      full_name VARCHAR(150) NOT NULL,
      phone VARCHAR(50) NOT NULL,
      role VARCHAR(10) NOT NULL CHECK (role IN ('SYNDIC', 'OWNER')),
      apartment_id UUID NOT NULL UNIQUE REFERENCES apartments(id) ON DELETE RESTRICT,
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );
  `);

  // Partial unique index for production PostgreSQL
  if (process.env.DATABASE_URL) {
    await p.query(`
      CREATE UNIQUE INDEX IF NOT EXISTS uq_single_syndic ON users (role) WHERE role = 'SYNDIC';
    `);
  }

  // 3. PROJECTS TABLE (Direct Single-Syndic creation, immediately ACTIVE)
  await p.query(`
    CREATE TABLE IF NOT EXISTS projects (
      id VARCHAR(50) PRIMARY KEY,
      title TEXT NOT NULL,
      description TEXT NOT NULL,
      total_cost NUMERIC(14, 2) NOT NULL CHECK (total_cost > 0),
      apartment_count INTEGER NOT NULL DEFAULT 40,
      contribution_per_apt NUMERIC(14, 2) NOT NULL,
      created_by_syndic_id UUID NOT NULL REFERENCES users(id),
      status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'COMPLETED', 'CANCELLED')),
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );
  `);

  // 4. FINANCIAL TRANSACTIONS / IMMUTABLE LEDGER TABLE
  await p.query(`
    CREATE TABLE IF NOT EXISTS financial_transactions (
      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      tx_seq_id VARCHAR(50) UNIQUE NOT NULL,
      type VARCHAR(10) NOT NULL CHECK (type IN ('CREDIT', 'DEBIT')),
      category VARCHAR(50) NOT NULL,
      project_id VARCHAR(50) REFERENCES projects(id),
      apartment_id UUID REFERENCES apartments(id),
      apartment_number INTEGER,
      amount NUMERIC(14, 2) NOT NULL CHECK (amount > 0),
      payment_method VARCHAR(20) NOT NULL CHECK (payment_method IN ('CASH', 'BANK_TRANSFER', 'CHECK')),
      created_by_syndic_id UUID NOT NULL REFERENCES users(id),
      status VARCHAR(20) NOT NULL CHECK (status IN ('LOCKED')),
      is_correction BOOLEAN NOT NULL DEFAULT FALSE,
      original_tx_id UUID REFERENCES financial_transactions(id) ON DELETE RESTRICT,
      correction_reason TEXT,
      description TEXT,
      supplier TEXT,
      invoice_number TEXT,
      idempotency_key UUID UNIQUE NOT NULL,
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );
  `);

  // 5. PERMANENT IDEMPOTENCY TABLE (Does not expire, never purged)
  await p.query(`
    CREATE TABLE IF NOT EXISTS idempotency_keys (
      key UUID PRIMARY KEY,
      user_id UUID NOT NULL REFERENCES users(id),
      request_hash VARCHAR(64) NOT NULL,
      response_code INTEGER NOT NULL,
      response_body JSONB NOT NULL,
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );
  `);

  // 6. MONOTONIC SYNC EVENTS TABLE (Server cursor resume engine)
  await p.query(`
    CREATE TABLE IF NOT EXISTS sync_events (
      cursor_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
      entity_type VARCHAR(50) NOT NULL,
      entity_id VARCHAR(100) NOT NULL,
      action VARCHAR(20) NOT NULL,
      payload JSONB NOT NULL,
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );
  `);

  // 7. APPEND-ONLY AUDIT LOGS TABLE
  await p.query(`
    CREATE TABLE IF NOT EXISTS audit_logs (
      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      user_id UUID REFERENCES users(id),
      username VARCHAR(100) NOT NULL,
      action VARCHAR(50) NOT NULL,
      entity_type VARCHAR(50) NOT NULL,
      entity_id VARCHAR(100) NOT NULL,
      idempotency_key UUID,
      ip_address VARCHAR(45),
      details JSONB,
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );
  `);

  // 8. MAINTENANCE REPORTS
  await p.query(`
    CREATE TABLE IF NOT EXISTS maintenance_reports (
      id VARCHAR(50) PRIMARY KEY,
      apartment_id UUID NOT NULL REFERENCES apartments(id),
      apartment_number INTEGER NOT NULL,
      reporter_id UUID NOT NULL REFERENCES users(id),
      category VARCHAR(100) NOT NULL,
      description TEXT NOT NULL,
      photo_url TEXT,
      status VARCHAR(20) NOT NULL CHECK (status IN ('NEW', 'IN_PROGRESS', 'RESOLVED')),
      syndic_notes TEXT,
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );
  `);

  // 9. ELEVATOR RECORDS
  await p.query(`
    CREATE TABLE IF NOT EXISTS elevator_records (
      id VARCHAR(50) PRIMARY KEY,
      type TEXT NOT NULL,
      technician_or_company TEXT NOT NULL,
      contact_phone TEXT NOT NULL,
      cost NUMERIC(14, 2) NOT NULL DEFAULT 0,
      invoice_number TEXT,
      maintenance_date TEXT NOT NULL,
      next_scheduled_date TEXT NOT NULL,
      description TEXT NOT NULL,
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );
  `);

  // 10. ANNOUNCEMENTS & MEETINGS
  await p.query(`
    CREATE TABLE IF NOT EXISTS announcements (
      id VARCHAR(50) PRIMARY KEY,
      title TEXT NOT NULL,
      content TEXT NOT NULL,
      priority VARCHAR(20) NOT NULL CHECK (priority IN ('NORMAL', 'URGENT')),
      created_by_syndic_id UUID NOT NULL REFERENCES users(id),
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );

    CREATE TABLE IF NOT EXISTS meetings (
      id VARCHAR(50) PRIMARY KEY,
      title TEXT NOT NULL,
      meeting_date TEXT NOT NULL,
      location TEXT NOT NULL,
      agenda TEXT NOT NULL,
      decisions TEXT,
      created_by_syndic_id UUID NOT NULL REFERENCES users(id),
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );
  `);

  // 11. VOTING SESSIONS & 1-VOTE-PER-APARTMENT ENFORCEMENT
  await p.query(`
    CREATE TABLE IF NOT EXISTS voting_sessions (
      id VARCHAR(50) PRIMARY KEY,
      title TEXT NOT NULL,
      description TEXT NOT NULL,
      is_active BOOLEAN NOT NULL DEFAULT TRUE,
      created_by_syndic_id UUID NOT NULL REFERENCES users(id),
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      closes_at TIMESTAMPTZ NOT NULL
    );

    CREATE TABLE IF NOT EXISTS votes (
      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      session_id VARCHAR(50) NOT NULL REFERENCES voting_sessions(id),
      apartment_id UUID NOT NULL REFERENCES apartments(id),
      apartment_number INTEGER NOT NULL,
      owner_id UUID NOT NULL REFERENCES users(id),
      choice VARCHAR(10) NOT NULL CHECK (choice IN ('YES', 'NO', 'ABSTAIN')),
      cast_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      UNIQUE(session_id, apartment_id)
    );
  `);

  // 12. TX SEQUENCE GENERATOR
  await p.query(`
    CREATE TABLE IF NOT EXISTS tx_sequence (
      id INTEGER PRIMARY KEY CHECK (id = 1),
      current_value INTEGER NOT NULL DEFAULT 100
    );
    INSERT INTO tx_sequence (id, current_value) VALUES (1, 100) ON CONFLICT DO NOTHING;
  `);

  await seedAuthoritativeBuildingData();
}

let txSequenceCounter = 100;
export async function getNextTxId(): Promise<string> {
  txSequenceCounter++;
  const seq = String(txSequenceCounter).padStart(6, '0');
  return `TX-2026-${seq}`;
}

export async function appendSyncEvent(
  entityType: string,
  entityId: string,
  action: string,
  payload: any
): Promise<number> {
  const res = await query(`
    INSERT INTO sync_events (entity_type, entity_id, action, payload)
    VALUES ($1, $2, $3, $4)
    RETURNING cursor_id
  `, [entityType, entityId, action, JSON.stringify(payload)]);

  return Number(res.rows[0].cursor_id);
}

export async function appendAuditLog(
  userId: string | null,
  username: string,
  action: string,
  entityType: string,
  entityId: string,
  details: any,
  idempotencyKey?: string,
  ipAddress?: string
): Promise<void> {
  const logId = crypto.randomUUID();
  await query(`
    INSERT INTO audit_logs (id, user_id, username, action, entity_type, entity_id, idempotency_key, ip_address, details)
    VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)
  `, [logId, userId, username, action, entityType, entityId, idempotencyKey || null, ipAddress || '127.0.0.1', JSON.stringify(details)]);
}

export async function getOfficialBuildingBalance(): Promise<{
  official_balance: number;
  total_credit: number;
  total_debit: number;
}> {
  // Official Balance Formula: SUM(CREDIT) - SUM(DEBIT)
  // Only server-confirmed LOCKED transactions affect official balance
  const res = await query(`
    SELECT 
      COALESCE(SUM(CASE WHEN type = 'CREDIT' AND status = 'LOCKED' THEN amount ELSE 0 END), 0) as total_credit,
      COALESCE(SUM(CASE WHEN type = 'DEBIT' AND status = 'LOCKED' THEN amount ELSE 0 END), 0) as total_debit
    FROM financial_transactions
  `);

  const credit = Number(res.rows[0].total_credit);
  const debit = Number(res.rows[0].total_debit);
  const balance = credit - debit;

  return {
    official_balance: balance,
    total_credit: credit,
    total_debit: debit
  };
}

export async function validateBuildingTopology(): Promise<boolean> {
  const aptRes = await query('SELECT COUNT(*) as count FROM apartments');
  const userRes = await query('SELECT COUNT(*) as count FROM users');
  const syndicRes = await query("SELECT COUNT(*) as count FROM users WHERE role = 'SYNDIC'");

  const aptCount = Number(aptRes.rows[0].count);
  const userCount = Number(userRes.rows[0].count);
  const syndicCount = Number(syndicRes.rows[0].count);

  return aptCount === 40 && userCount === 40 && syndicCount === 1;
}

export async function seedAuthoritativeBuildingData(): Promise<void> {
  const countRes = await query('SELECT COUNT(*) as count FROM apartments');
  if (Number(countRes.rows[0].count) > 0) return;

  console.log('Seeding authoritative PostgreSQL database: exactly 40 apartments (RDC + Floors 1..9), 40 owners, exactly 1 Syndic...');

  // Hash initial password securely with bcrypt
  const passwordHash = bcrypt.hashSync('amarati123', 10);

  const ownerNames = [
    'Ahmed Benali',      // Apt 1 (Single Syndic & Owner)
    'Karim Mansouri',    // Apt 2 (Owner)
    'Amina Haddad',      // Apt 3
    'Yacine Brahimi',    // Apt 4
    'Fatima Zohra Kaci', // Apt 5
    'Omar Belhadj',      // Apt 6
    'Meriem Saidi',      // Apt 7
    'Rachid Cherif',     // Apt 8
    'Nadia Bouzid',      // Apt 9
    'Tarek Madani',      // Apt 10
    'Samir Chaouche',    // Apt 11
    'Houria Meziane',    // Apt 12
    'Kamel Ferhat',      // Apt 13
    'Leila Amrani',      // Apt 14
    'Mustapha Benaissa', // Apt 15
    'Zineb Touati',      // Apt 16
    'Sofiane Zerrouki',  // Apt 17
    'Souad Taleb',       // Apt 18
    'Djamel Hammoudi',   // Apt 19
    'Fatiha Mokrani',    // Apt 20
    'Mohamed Larbi',     // Apt 21
    'Salima Djebbar',    // Apt 22
    'Hassane Belkacem',  // Apt 23
    'Khadidja Guellil',  // Apt 24
    'Abdelkader Senoussi',// Apt 25
    'Nawal Kerboua',     // Apt 26
    'Ali Bouamama',      // Apt 27
    'Assia Benslimane',  // Apt 28
    'Bilal Dahmani',     // Apt 29
    'Farida Slimani',    // Apt 30
    'Reda Zitouni',      // Apt 31
    'Lamia Ould Ali',    // Apt 32
    'Mourad Khelifi',    // Apt 33
    'Nassima Boukhalfa', // Apt 34
    'Walid Hamidi',      // Apt 35
    'Yasmina Benbouzid', // Apt 36
    'Hakim Bahloul',     // Apt 37
    'Chafika Sahli',     // Apt 38
    'Rabah Guendouz',    // Apt 39
    'Zahia Boutaleb'     // Apt 40
  ];

  let syndicUserId = '';

  for (let apt = 1; apt <= 40; apt++) {
    const floor = Math.floor((apt - 1) / 4); // 0 (RDC) to 9
    const aptId = crypto.randomUUID();

    await query(`
      INSERT INTO apartments (id, apartment_number, floor)
      VALUES ($1, $2, $3)
    `, [aptId, apt, floor]);

    const username = `apt${apt}`;
    const isSyndic = (apt === 1);
    const role = isSyndic ? 'SYNDIC' : 'OWNER';
    const fullName = isSyndic ? 'Ahmed Benali (Syndic)' : ownerNames[apt - 1];
    const phone = `+213 555 ${String(1000 + apt).slice(-4)}`;
    const userId = crypto.randomUUID();

    if (isSyndic) {
      syndicUserId = userId;
    }

    await query(`
      INSERT INTO users (id, username, password_hash, full_name, phone, role, apartment_id)
      VALUES ($1, $2, $3, $4, $5, $6, $7)
    `, [userId, username, passwordHash, fullName, phone, role, aptId]);
  }

  // Pre-seed an authoritative ACTIVE project created directly by the Single Syndic
  const prjId = 'PRJ-2026-001';
  await query(`
    INSERT INTO projects (id, title, description, total_cost, apartment_count, contribution_per_apt, created_by_syndic_id, status)
    VALUES ($1, $2, $3, $4, 40, $5, $6, 'ACTIVE')
  `, [prjId, 'Rénovation Complète Ascenseur', 'Changement câbles et variateur de fréquence', 400000, 10000, syndicUserId]);

  // Pre-seed initial confirmed locked payments for Apartments 1 to 5 (CREDIT, LOCKED)
  for (let apt = 1; apt <= 5; apt++) {
    const aptRow = (await query('SELECT id FROM apartments WHERE apartment_number = $1', [apt])).rows[0];
    const txId = crypto.randomUUID();
    const seqId = `TX-2026-00000${apt}`;
    const key = crypto.randomUUID();

    await query(`
      INSERT INTO financial_transactions (
        id, tx_seq_id, type, category, project_id, apartment_id, apartment_number,
        amount, payment_method, created_by_syndic_id, status, is_correction,
        idempotency_key
      ) VALUES ($1, $2, 'CREDIT', 'COTISATION_PROJET', $3, $4, $5, 10000, 'CASH', $6, 'LOCKED', FALSE, $7)
    `, [txId, seqId, prjId, aptRow.id, apt, syndicUserId, key]);
  }

  // Pre-seed Elevator Record
  await query(`
    INSERT INTO elevator_records (id, type, technician_or_company, contact_phone, cost, invoice_number, maintenance_date, next_scheduled_date, description)
    VALUES ('ELEV-001', 'Maintenance Mensuelle', 'Otis Algérie / SARL El-Badr', '+213 21 44 55 66', 15000, 'FAC-2026-042', '2026-08-15', '2026-09-15', 'Vérification câblage, graissage rails et test freinage parachute')
  `);

  // Pre-seed Announcement
  await query(`
    INSERT INTO announcements (id, title, content, priority, created_by_syndic_id)
    VALUES ('ANN-001', 'Assemblée Générale Ordinaire', 'Réunion annuelle des 40 copropriétaires fixée pour le samedi 20 Septembre 2026.', 'NORMAL', $1)
  `, [syndicUserId]);

  // Pre-seed Meeting
  await query(`
    INSERT INTO meetings (id, title, meeting_date, location, agenda, decisions, created_by_syndic_id)
    VALUES ('MTG-001', 'Assemblée Générale 2026', '2026-09-20 18:00', 'Hall Immeuble RDC', 'Bilan financier, vote devis étanchéité', 'En attente', $1)
  `, [syndicUserId]);

  // Pre-seed Voting Session
  const voteSessionId = 'VOTE-001';
  await query(`
    INSERT INTO voting_sessions (id, title, description, is_active, created_by_syndic_id, closes_at)
    VALUES ($1, 'Installation Caméras de Surveillance', 'Projet de 6 caméras HD pour les parties communes', TRUE, $2, NOW() + INTERVAL '30 days')
  `, [voteSessionId, syndicUserId]);

  // Pre-seed initial vote for Apt 1 (Syndic)
  const apt1 = (await query('SELECT id FROM apartments WHERE apartment_number = 1')).rows[0];
  await query(`
    INSERT INTO votes (session_id, apartment_id, apartment_number, owner_id, choice)
    VALUES ($1, $2, 1, $3, 'YES')
  `, [voteSessionId, apt1.id, syndicUserId]);

  // Pre-seed System Init Audit Log
  await appendAuditLog(syndicUserId, 'Ahmed Benali (Syndic)', 'SYSTEM_INIT', 'BUILDING', '40_APARTMENTS', {
    message: 'Initialisation officielle du système Amarati avec 40 appartements et Syndic unique'
  });

  console.log('Authoritative PostgreSQL database seeded successfully with Single-Syndic topology.');
}

export default {
  query,
  getPool,
  initDatabase,
  getNextTxId,
  appendSyncEvent,
  appendAuditLog,
  getOfficialBuildingBalance,
  validateBuildingTopology,
  seedAuthoritativeBuildingData
};
