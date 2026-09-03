import Database from 'better-sqlite3';
import bcrypt from 'bcryptjs';
import path from 'path';
import fs from 'fs';

const dbPath = process.env.DATABASE_PATH || path.join(__dirname, '../../amarati_server.db');
const db = new Database(dbPath);

// Enable Foreign Keys & WAL mode for performance & concurrent safety
db.pragma('journal_mode = WAL');
db.pragma('foreign_keys = ON');

export function initDatabase() {
  db.exec(`
    CREATE TABLE IF NOT EXISTS users (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      username TEXT UNIQUE NOT NULL,
      password_hash TEXT NOT NULL,
      full_name TEXT NOT NULL,
      phone TEXT NOT NULL,
      role TEXT NOT NULL CHECK (role IN ('OWNER', 'OWNER_SYNDIC')),
      apartment_number INTEGER UNIQUE NOT NULL CHECK (apartment_number BETWEEN 1 AND 40),
      floor INTEGER NOT NULL CHECK (floor BETWEEN 0 AND 9),
      created_at TEXT NOT NULL DEFAULT (datetime('now'))
    );

    CREATE TABLE IF NOT EXISTS apartments (
      number INTEGER PRIMARY KEY CHECK (number BETWEEN 1 AND 40),
      floor INTEGER NOT NULL CHECK (floor BETWEEN 0 AND 9),
      floor_label TEXT NOT NULL,
      owner_id INTEGER NOT NULL REFERENCES users(id)
    );

    CREATE TABLE IF NOT EXISTS projects (
      id TEXT PRIMARY KEY,
      title TEXT NOT NULL,
      description TEXT NOT NULL,
      total_cost INTEGER NOT NULL CHECK (total_cost > 0),
      apartment_count INTEGER NOT NULL DEFAULT 40,
      contribution_per_apt INTEGER NOT NULL,
      creator_syndic_id INTEGER NOT NULL REFERENCES users(id),
      approver_syndic_id INTEGER REFERENCES users(id),
      status TEXT NOT NULL CHECK (status IN ('PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'COMPLETED')),
      rejection_reason TEXT,
      created_at TEXT NOT NULL DEFAULT (datetime('now')),
      approved_at TEXT
    );

    CREATE TABLE IF NOT EXISTS financial_ledger (
      tx_id TEXT PRIMARY KEY,
      type TEXT NOT NULL CHECK (type IN ('OWNER_PAYMENT', 'EXPENSE', 'CORRECTION_CREDIT', 'CORRECTION_DEBIT')),
      project_id TEXT REFERENCES projects(id),
      apartment_number INTEGER REFERENCES apartments(number),
      owner_id INTEGER REFERENCES users(id),
      amount INTEGER NOT NULL CHECK (amount > 0),
      payment_method TEXT NOT NULL CHECK (payment_method IN ('CASH', 'BANK_TRANSFER', 'CHECK')),
      creator_syndic_id INTEGER NOT NULL REFERENCES users(id),
      approver_syndic_id INTEGER REFERENCES users(id),
      status TEXT NOT NULL CHECK (status IN ('PENDING_APPROVAL', 'LOCKED', 'REJECTED')),
      original_tx_id TEXT REFERENCES financial_ledger(tx_id),
      correction_reason TEXT,
      supplier TEXT,
      invoice_number TEXT,
      expense_category TEXT,
      idempotency_key TEXT UNIQUE NOT NULL,
      created_at TEXT NOT NULL DEFAULT (datetime('now')),
      approved_at TEXT
    );

    -- Enforce Immutability Trigger: Reject UPDATE or DELETE on LOCKED ledger entries
    CREATE TRIGGER IF NOT EXISTS trg_prevent_locked_ledger_update
    BEFORE UPDATE ON financial_ledger
    FOR EACH ROW
    WHEN OLD.status = 'LOCKED'
    BEGIN
      SELECT RAISE(FAIL, 'ERR_LEDGER_IMMUTABLE: Locked financial transactions cannot be modified');
    END;

    CREATE TRIGGER IF NOT EXISTS trg_prevent_locked_ledger_delete
    BEFORE DELETE ON financial_ledger
    FOR EACH ROW
    WHEN OLD.status = 'LOCKED'
    BEGIN
      SELECT RAISE(FAIL, 'ERR_LEDGER_IMMUTABLE: Locked financial transactions cannot be deleted');
    END;

    CREATE TABLE IF NOT EXISTS maintenance_reports (
      id TEXT PRIMARY KEY,
      apartment_number INTEGER NOT NULL REFERENCES apartments(number),
      reporter_id INTEGER NOT NULL REFERENCES users(id),
      category TEXT NOT NULL,
      description TEXT NOT NULL,
      photo_url TEXT,
      status TEXT NOT NULL CHECK (status IN ('NEW', 'IN_PROGRESS', 'RESOLVED')),
      syndic_notes TEXT,
      created_at TEXT NOT NULL DEFAULT (datetime('now')),
      updated_at TEXT NOT NULL DEFAULT (datetime('now'))
    );

    CREATE TABLE IF NOT EXISTS elevator_records (
      id TEXT PRIMARY KEY,
      type TEXT NOT NULL,
      technician_or_company TEXT NOT NULL,
      contact_phone TEXT NOT NULL,
      cost INTEGER NOT NULL DEFAULT 0,
      invoice_number TEXT,
      maintenance_date TEXT NOT NULL,
      next_scheduled_date TEXT NOT NULL,
      description TEXT NOT NULL
    );

    CREATE TABLE IF NOT EXISTS announcements (
      id TEXT PRIMARY KEY,
      title TEXT NOT NULL,
      content TEXT NOT NULL,
      priority TEXT NOT NULL CHECK (priority IN ('NORMAL', 'URGENT')),
      creator_syndic_id INTEGER NOT NULL REFERENCES users(id),
      created_at TEXT NOT NULL DEFAULT (datetime('now'))
    );

    CREATE TABLE IF NOT EXISTS meetings (
      id TEXT PRIMARY KEY,
      title TEXT NOT NULL,
      meeting_date TEXT NOT NULL,
      location TEXT NOT NULL,
      agenda TEXT NOT NULL,
      decisions TEXT,
      creator_syndic_id INTEGER NOT NULL REFERENCES users(id),
      created_at TEXT NOT NULL DEFAULT (datetime('now'))
    );

    CREATE TABLE IF NOT EXISTS voting_sessions (
      id TEXT PRIMARY KEY,
      title TEXT NOT NULL,
      description TEXT NOT NULL,
      is_active INTEGER NOT NULL DEFAULT 1,
      creator_syndic_id INTEGER NOT NULL REFERENCES users(id),
      created_at TEXT NOT NULL DEFAULT (datetime('now')),
      closes_at TEXT NOT NULL
    );

    CREATE TABLE IF NOT EXISTS votes (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      session_id TEXT NOT NULL REFERENCES voting_sessions(id),
      apartment_number INTEGER NOT NULL REFERENCES apartments(number),
      owner_id INTEGER NOT NULL REFERENCES users(id),
      choice TEXT NOT NULL CHECK (choice IN ('YES', 'NO', 'ABSTAIN')),
      cast_at TEXT NOT NULL DEFAULT (datetime('now')),
      UNIQUE(session_id, apartment_number)
    );

    CREATE TABLE IF NOT EXISTS documents (
      id TEXT PRIMARY KEY,
      title TEXT NOT NULL,
      category TEXT NOT NULL,
      file_url TEXT NOT NULL,
      uploaded_by_id INTEGER NOT NULL REFERENCES users(id),
      created_at TEXT NOT NULL DEFAULT (datetime('now'))
    );

    CREATE TABLE IF NOT EXISTS audit_logs (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      actor_id INTEGER REFERENCES users(id),
      actor_name TEXT NOT NULL,
      action TEXT NOT NULL,
      entity_type TEXT NOT NULL,
      entity_id TEXT NOT NULL,
      details TEXT,
      timestamp TEXT NOT NULL DEFAULT (datetime('now'))
    );

    CREATE TABLE IF NOT EXISTS tx_sequence (
      id INTEGER PRIMARY KEY CHECK (id = 1),
      current_value INTEGER NOT NULL DEFAULT 0
    );

    INSERT OR IGNORE INTO tx_sequence (id, current_value) VALUES (1, 100);
  `);

  seedInitialData();
}

export function getNextTxId(): string {
  const stmt = db.prepare(`
    UPDATE tx_sequence 
    SET current_value = current_value + 1 
    WHERE id = 1 
    RETURNING current_value
  `);
  const row = stmt.get() as { current_value: number };
  const seq = String(row.current_value).padStart(6, '0');
  return `TX-2026-${seq}`;
}

function seedInitialData() {
  const countStmt = db.prepare('SELECT COUNT(*) as count FROM users');
  const count = (countStmt.get() as { count: number }).count;
  if (count > 0) return;

  console.log('Seeding initial authoritative building database with 40 apartments...');

  // Hash the default initial password with bcryptjs (10 rounds)
  const defaultHash = bcrypt.hashSync('amarati123', 10);

  // Exact 10 levels x 4 apartments = 40 apartments (RDC: 1..4, Floor 1: 5..8, ... Floor 9: 37..40)
  const floorLabels = ['RDC', '1', '2', '3', '4', '5', '6', '7', '8', '9'];

  const insertUser = db.prepare(`
    INSERT INTO users (username, password_hash, full_name, phone, role, apartment_number, floor)
    VALUES (?, ?, ?, ?, ?, ?, ?)
  `);

  const insertApartment = db.prepare(`
    INSERT INTO apartments (number, floor, floor_label, owner_id)
    VALUES (?, ?, ?, ?)
  `);

  const userIds: Record<number, number> = {};

  db.transaction(() => {
    for (let apt = 1; apt <= 40; apt++) {
      const floor = Math.floor((apt - 1) / 4);
      const floorLabel = floorLabels[floor];
      const username = `apt${apt}`;
      const isSyndic = apt === 1 || apt === 2;
      const role = isSyndic ? 'OWNER_SYNDIC' : 'OWNER';
      
      let fullName = `Copropriétaire Appt ${apt}`;
      let phone = `+213 555 ${String(1000 + apt).slice(-4)}`;
      if (apt === 1) {
        fullName = 'Ahmed Benali (Syndic 1)';
        phone = '+213 555 1020';
      } else if (apt === 2) {
        fullName = 'Karim Mansouri (Syndic 2)';
        phone = '+213 555 3040';
      }

      const res = insertUser.run(username, defaultHash, fullName, phone, role, apt, floor);
      const userId = Number(res.lastInsertRowid);
      userIds[apt] = userId;

      insertApartment.run(apt, floor, floorLabel, userId);
    }

    // Pre-seed the two approved projects
    const syndic1Id = userIds[1];
    const syndic2Id = userIds[2];

    db.prepare(`
      INSERT INTO projects (id, title, description, total_cost, apartment_count, contribution_per_apt, creator_syndic_id, approver_syndic_id, status, created_at, approved_at)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'APPROVED', '2026-01-10 10:00:00', '2026-01-11 14:30:00')
    `).run('PRJ-2026-001', 'Rénovation Complète Ascenseur', 'Changement câbles et variateur de fréquence', 400000, 40, 10000, syndic1Id, syndic2Id);

    db.prepare(`
      INSERT INTO projects (id, title, description, total_cost, apartment_count, contribution_per_apt, creator_syndic_id, approver_syndic_id, status, created_at, approved_at)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'APPROVED', '2026-02-01 09:00:00', '2026-02-02 11:15:00')
    `).run('PRJ-2026-002', 'Étanchéité Terrasse & Toiture', 'Pose multicouche et isolation thermique', 600000, 40, 15000, syndic2Id, syndic1Id);

    // Pre-seed some confirmed locked payments
    const insertPayment = db.prepare(`
      INSERT INTO financial_ledger (tx_id, type, project_id, apartment_number, owner_id, amount, payment_method, creator_syndic_id, approver_syndic_id, status, idempotency_key, created_at, approved_at)
      VALUES (?, 'OWNER_PAYMENT', ?, ?, ?, ?, 'CASH', ?, ?, 'LOCKED', ?, '2026-02-05 10:00:00', '2026-02-05 10:00:00')
    `);

    // Payments for Apt 1 to 5
    for (let apt = 1; apt <= 5; apt++) {
      insertPayment.run(
        `TX-2026-00000${apt}`,
        'PRJ-2026-001',
        apt,
        userIds[apt],
        10000,
        syndic1Id,
        syndic1Id,
        `seed-payment-prj1-apt${apt}`
      );
    }

    // Elevator record
    db.prepare(`
      INSERT INTO elevator_records (id, type, technician_or_company, contact_phone, cost, invoice_number, maintenance_date, next_scheduled_date, description)
      VALUES ('ELEV-001', 'Maintenance Mensuelle', 'Otis Algérie / SARL El-Badr', '+213 21 44 55 66', 15000, 'FAC-2026-042', '2026-08-15', '2026-09-15', 'Vérification câblage, graissage rails et test freinage parachute')
    `).run();

    // Announcements
    db.prepare(`
      INSERT INTO announcements (id, title, content, priority, creator_syndic_id)
      VALUES ('ANN-001', 'Assemblée Générale Ordinaire', 'Réunion annuelle des copropriétaires fixée pour le samedi 20 Septembre 2026 à 18h au hall de l''immeuble.', 'NORMAL', ?)
    `).run(syndic1Id);

    // Meetings
    db.prepare(`
      INSERT INTO meetings (id, title, meeting_date, location, agenda, decisions, creator_syndic_id)
      VALUES ('MTG-001', 'Assemblée Générale 2026', '2026-09-20 18:00', 'Hall Immeuble RDC', 'Bilan financier, approbation budget ascenseur, vote devis étanchéité', 'En attente', ?)
    `).run(syndic1Id);

    // Voting Session
    db.prepare(`
      INSERT INTO voting_sessions (id, title, description, is_active, creator_syndic_id, created_at, closes_at)
      VALUES ('VOTE-001', 'Installation Caméras de Surveillance', 'Projet d''installation de 6 caméras HD (Entrée, Hall, Parking, Escaliers)', 1, ?, '2026-08-20', '2026-09-25')
    `).run(syndic1Id);

    // Seed some initial votes
    db.prepare(`
      INSERT INTO votes (session_id, apartment_number, owner_id, choice, cast_at)
      VALUES ('VOTE-001', 1, ?, 'YES', '2026-08-21 11:00:00')
    `).run(syndic1Id);

    db.prepare(`
      INSERT INTO votes (session_id, apartment_number, owner_id, choice, cast_at)
      VALUES ('VOTE-001', 20, ?, 'YES', '2026-08-21 11:30:00')
    `).run(syndic2Id);

    // Audit log
    db.prepare(`
      INSERT INTO audit_logs (actor_id, actor_name, action, entity_type, entity_id, details)
      VALUES (?, 'Amine Benali (Syndic 1)', 'SYSTEM_INIT', 'BUILDING', '40_APTS', 'Initialisation officielle du système de gestion Amarati')
    `).run(syndic1Id);
  })();

  console.log('Authoritative building database seeded successfully.');
}

export default db;
