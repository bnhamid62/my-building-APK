import express, { Request, Response, NextFunction } from 'express';
import cors from 'cors';
import bcrypt from 'bcryptjs';
import jwt from 'jsonwebtoken';
import crypto from 'crypto';
import db, {
  query,
  initDatabase,
  getNextTxId,
  appendSyncEvent,
  appendAuditLog,
  getOfficialBuildingBalance,
  validateBuildingTopology
} from './db';

const JWT_SECRET = process.env.JWT_SECRET || 'amarati_authoritative_pg_jwt_secret_2026';
const PORT = process.env.PORT || 3000;

export interface AuthenticatedUser {
  id: string;
  username: string;
  role: 'SYNDIC' | 'OWNER';
  apartment_id: string;
  apartment_number: number;
  floor: number;
  full_name: string;
}

export interface AuthRequest extends Request {
  user?: AuthenticatedUser;
}

export function authMiddleware(req: AuthRequest, res: Response, next: NextFunction) {
  const authHeader = req.headers.authorization;
  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    return res.status(401).json({ error: 'UNAUTHORIZED: Missing or invalid token' });
  }

  const token = authHeader.split(' ')[1];
  try {
    const decoded = jwt.verify(token, JWT_SECRET) as AuthenticatedUser;
    req.user = decoded;
    next();
  } catch (err) {
    return res.status(401).json({ error: 'UNAUTHORIZED: Token expired or invalid' });
  }
}

export function syndicOnly(req: AuthRequest, res: Response, next: NextFunction) {
  if (req.user?.role !== 'SYNDIC') {
    return res.status(403).json({ error: 'FORBIDDEN: Only the authorized building Syndic can perform this financial or administrative operation' });
  }
  next();
}

/**
 * Robust Permanent Idempotency Guard with Request Hash Verification:
 * A. Existing key + same request_hash -> return existing authoritative response
 * B. Existing key + different request_hash -> HTTP 409 Conflict IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_REQUEST
 */
async function handleIdempotency(
  key: string,
  userId: string,
  requestPayload: any,
  res: Response
): Promise<{ handled: boolean; existingResponse?: any }> {
  const hash = crypto.createHash('sha256').update(JSON.stringify(requestPayload)).digest('hex');

  const existingRes = await query('SELECT * FROM idempotency_keys WHERE key = $1', [key]);
  if (existingRes.rows.length > 0) {
    const existing = existingRes.rows[0];
    if (existing.request_hash === hash) {
      // Identical request -> return existing authoritative response
      res.status(existing.response_code).json(existing.response_body);
      return { handled: true };
    } else {
      // Key reused with different payload -> strict conflict rejection
      res.status(409).json({
        error: 'IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_REQUEST',
        message: 'This idempotency key was previously submitted with different request parameters'
      });
      return { handled: true };
    }
  }

  return { handled: false };
}

async function recordIdempotency(
  key: string,
  userId: string,
  requestPayload: any,
  responseCode: number,
  responseBody: any
): Promise<void> {
  const hash = crypto.createHash('sha256').update(JSON.stringify(requestPayload)).digest('hex');
  await query(`
    INSERT INTO idempotency_keys (key, user_id, request_hash, response_code, response_body)
    VALUES ($1, $2, $3, $4, $5)
    ON CONFLICT (key) DO NOTHING
  `, [key, userId, hash, responseCode, JSON.stringify(responseBody)]);
}

export function createApp() {
  const app = express();
  app.use(cors());
  app.use(express.json());

  // Health check & System Topology Status
  app.get('/api/health', async (req, res) => {
    const isTopologyValid = await validateBuildingTopology();
    res.json({
      status: 'OK',
      database: 'PostgreSQL Authoritative Engine',
      service: 'Amarati Central Backend',
      topology_valid_40_apts_1_syndic: isTopologyValid,
      timestamp: new Date().toISOString()
    });
  });

  // 1. AUTHENTICATION & SECURITY
  app.post('/api/v1/auth/login', async (req, res) => {
    const { username, password } = req.body;
    if (!username || !password) {
      return res.status(400).json({ error: 'BAD_REQUEST: Username and password are required' });
    }

    const userRes = await query(`
      SELECT u.*, a.apartment_number, a.floor
      FROM users u
      JOIN apartments a ON u.apartment_id = a.id
      WHERE u.username = $1
    `, [username.trim()]);

    if (userRes.rows.length === 0) {
      return res.status(401).json({ error: 'UNAUTHORIZED: Invalid username or password' });
    }

    const user = userRes.rows[0];
    const isMatch = bcrypt.compareSync(password.trim(), user.password_hash);
    if (!isMatch) {
      return res.status(401).json({ error: 'UNAUTHORIZED: Invalid username or password' });
    }

    const payload: AuthenticatedUser = {
      id: user.id,
      username: user.username,
      role: user.role,
      apartment_id: user.apartment_id,
      apartment_number: user.apartment_number,
      floor: user.floor,
      full_name: user.full_name
    };

    const token = jwt.sign(payload, JWT_SECRET, { expiresIn: '7d' });

    await appendAuditLog(
      user.id,
      user.full_name,
      'LOGIN',
      'USER',
      user.id,
      { ip: req.ip || '127.0.0.1' }
    );

    res.json({
      token,
      user: {
        id: user.id,
        username: user.username,
        full_name: user.full_name,
        phone: user.phone,
        role: user.role,
        apartment_id: user.apartment_id,
        apartment_number: user.apartment_number,
        floor: user.floor
      }
    });
  });

  app.post('/api/v1/auth/change-password', authMiddleware, async (req: AuthRequest, res) => {
    const { old_password, new_password } = req.body;
    if (!old_password || !new_password || new_password.length < 6) {
      return res.status(400).json({ error: 'BAD_REQUEST: New password must be at least 6 characters' });
    }

    const userRes = await query('SELECT * FROM users WHERE id = $1', [req.user!.id]);
    const user = userRes.rows[0];

    if (!bcrypt.compareSync(old_password, user.password_hash)) {
      return res.status(401).json({ error: 'UNAUTHORIZED: Current password incorrect' });
    }

    const newHash = bcrypt.hashSync(new_password, 10);
    await query('UPDATE users SET password_hash = $1 WHERE id = $2', [newHash, req.user!.id]);

    await appendAuditLog(
      req.user!.id,
      req.user!.full_name,
      'PASSWORD_CHANGE',
      'USER',
      req.user!.id,
      { message: 'Mot de passe mis à jour' }
    );

    res.json({ success: true, message: 'Password updated successfully' });
  });

  // 2. APARTMENTS TRANSPARENCY (All 40 apartments on 10 levels)
  app.get('/api/v1/apartments', authMiddleware, async (req, res) => {
    const rows = (await query(`
      SELECT 
        a.id as apartment_id,
        a.apartment_number,
        a.floor,
        CASE WHEN a.floor = 0 THEN 'RDC' ELSE a.floor::text END as floor_label,
        u.id as owner_id,
        u.full_name as owner_name,
        u.phone as owner_phone,
        u.role as owner_role,
        COALESCE((
          SELECT SUM(t.amount)
          FROM financial_transactions t
          WHERE t.apartment_id = a.id AND t.status = 'LOCKED' AND t.type = 'CREDIT'
        ), 0) as total_paid
      FROM apartments a
      JOIN users u ON u.apartment_id = a.id
      ORDER BY a.apartment_number ASC
    `)).rows;

    res.json(rows);
  });

  // 3. FINANCIAL PROJECTS (Single-Syndic creates directly -> Status is immediately ACTIVE)
  app.get('/api/v1/projects', authMiddleware, async (req, res) => {
    const rows = (await query(`
      SELECT 
        p.*,
        u.full_name as creator_name
      FROM projects p
      JOIN users u ON p.created_by_syndic_id = u.id
      ORDER BY p.created_at DESC
    `)).rows;
    res.json(rows);
  });

  app.post('/api/v1/projects', authMiddleware, syndicOnly, async (req: AuthRequest, res) => {
    const { title, description, total_cost } = req.body;
    if (!title || !description || !total_cost || Number(total_cost) <= 0) {
      return res.status(400).json({ error: 'BAD_REQUEST: Valid title, description, and total_cost are required' });
    }

    const cost = Math.floor(Number(total_cost));
    const contribution = Math.floor(cost / 40);
    const projectId = `PRJ-2026-${Date.now().toString().slice(-4)}`;

    // Projects created by the single Syndic are immediately ACTIVE and authoritative
    await query(`
      INSERT INTO projects (id, title, description, total_cost, apartment_count, contribution_per_apt, created_by_syndic_id, status)
      VALUES ($1, $2, $3, $4, 40, $5, $6, 'ACTIVE')
    `, [projectId, title, description, cost, contribution, req.user!.id]);

    await appendAuditLog(
      req.user!.id,
      req.user!.full_name,
      'PROJECT_CREATED',
      'PROJECT',
      projectId,
      { title, total_cost: cost, status: 'ACTIVE' }
    );

    await appendSyncEvent('PROJECT', projectId, 'CREATED', {
      id: projectId,
      title,
      total_cost: cost,
      status: 'ACTIVE'
    });

    const created = (await query('SELECT * FROM projects WHERE id = $1', [projectId])).rows[0];
    res.status(201).json(created);
  });

  // 4. OWNER PAYMENTS (Single Syndic records directly -> LOCKED, CREDIT, official balance updated)
  app.post('/api/v1/ledger/payments', authMiddleware, syndicOnly, async (req: AuthRequest, res) => {
    const { apartment_number, project_id, amount, payment_method, idempotency_key } = req.body;
    if (!apartment_number || !project_id || !amount || Number(amount) <= 0 || !payment_method) {
      return res.status(400).json({ error: 'BAD_REQUEST: apartment_number, project_id, amount, and payment_method are required' });
    }

    const key = idempotency_key || crypto.randomUUID();

    // Idempotency check with request_hash verification
    const idempCheck = await handleIdempotency(key, req.user!.id, req.body, res);
    if (idempCheck.handled) return;

    const aptRes = await query('SELECT * FROM apartments WHERE apartment_number = $1', [apartment_number]);
    if (aptRes.rows.length === 0) {
      return res.status(404).json({ error: 'NOT_FOUND: Apartment number invalid' });
    }
    const apt = aptRes.rows[0];

    const txId = crypto.randomUUID();
    const seqId = await getNextTxId();
    const finalAmount = Math.floor(Number(amount));

    try {
      await query(`
        INSERT INTO financial_transactions (
          id, tx_seq_id, type, category, project_id, apartment_id, apartment_number,
          amount, payment_method, created_by_syndic_id, status, is_correction,
          idempotency_key
        ) VALUES ($1, $2, 'CREDIT', 'COTISATION_PROJET', $3, $4, $5, $6, $7, $8, 'LOCKED', FALSE, $9)
      `, [txId, seqId, project_id, apt.id, apartment_number, finalAmount, payment_method, req.user!.id, key]);
    } catch (err: any) {
      if (err.message && (err.message.includes('unique') || err.message.includes('idempotency_key') || err.code === '23505')) {
        const existingTx = await query('SELECT * FROM financial_transactions WHERE idempotency_key = $1', [key]);
        if (existingTx.rows.length > 0) {
          const balanceInfo = await getOfficialBuildingBalance();
          return res.status(200).json({
            transaction: existingTx.rows[0],
            official_balance: balanceInfo.official_balance,
            total_credit: balanceInfo.total_credit,
            total_debit: balanceInfo.total_debit
          });
        }
      }
      throw err;
    }

    await appendAuditLog(
      req.user!.id,
      req.user!.full_name,
      'PAYMENT_RECORDED',
      'FINANCIAL_TRANSACTION',
      seqId,
      { apartment_number, amount: finalAmount, payment_method },
      key,
      req.ip
    );

    await appendSyncEvent('FINANCIAL_TRANSACTION', seqId, 'PAYMENT_RECORDED', {
      tx_seq_id: seqId,
      apartment_number,
      amount: finalAmount,
      status: 'LOCKED'
    });

    const created = (await query('SELECT * FROM financial_transactions WHERE id = $1', [txId])).rows[0];
    const balanceInfo = await getOfficialBuildingBalance();

    const responsePayload = {
      transaction: created,
      official_balance: balanceInfo.official_balance,
      total_credit: balanceInfo.total_credit,
      total_debit: balanceInfo.total_debit
    };

    // Store in permanent idempotency table
    await recordIdempotency(key, req.user!.id, req.body, 201, responsePayload);

    res.status(201).json(responsePayload);
  });

  // 5. EXPENSES (Single Syndic records directly -> LOCKED, DEBIT, official balance updated)
  app.post('/api/v1/ledger/expenses', authMiddleware, syndicOnly, async (req: AuthRequest, res) => {
    const { amount, supplier, invoice_number, expense_category, description, idempotency_key, project_id } = req.body;
    if (!amount || Number(amount) <= 0 || !supplier || !expense_category) {
      return res.status(400).json({ error: 'BAD_REQUEST: amount, supplier, and expense_category are required' });
    }

    const key = idempotency_key || crypto.randomUUID();

    // Idempotency check with request_hash verification
    const idempCheck = await handleIdempotency(key, req.user!.id, req.body, res);
    if (idempCheck.handled) return;

    const txId = crypto.randomUUID();
    const seqId = await getNextTxId();
    const finalAmount = Math.floor(Number(amount));

    await query(`
      INSERT INTO financial_transactions (
        id, tx_seq_id, type, category, project_id, amount, payment_method,
        created_by_syndic_id, status, is_correction, supplier, invoice_number,
        description, idempotency_key
      ) VALUES ($1, $2, 'DEBIT', $3, $4, $5, 'BANK_TRANSFER', $6, 'LOCKED', FALSE, $7, $8, $9, $10)
    `, [
      txId,
      seqId,
      expense_category,
      project_id || null,
      finalAmount,
      req.user!.id,
      supplier,
      invoice_number || null,
      description || null,
      key
    ]);

    await appendAuditLog(
      req.user!.id,
      req.user!.full_name,
      'EXPENSE_RECORDED',
      'FINANCIAL_TRANSACTION',
      seqId,
      { supplier, amount: finalAmount, category: expense_category },
      key,
      req.ip
    );

    await appendSyncEvent('FINANCIAL_TRANSACTION', seqId, 'EXPENSE_RECORDED', {
      tx_seq_id: seqId,
      supplier,
      amount: finalAmount,
      status: 'LOCKED'
    });

    const created = (await query('SELECT * FROM financial_transactions WHERE id = $1', [txId])).rows[0];
    const balanceInfo = await getOfficialBuildingBalance();

    const responsePayload = {
      transaction: created,
      official_balance: balanceInfo.official_balance,
      total_credit: balanceInfo.total_credit,
      total_debit: balanceInfo.total_debit
    };

    await recordIdempotency(key, req.user!.id, req.body, 201, responsePayload);
    res.status(201).json(responsePayload);
  });

  // 6. COMPENSATING FINANCIAL CORRECTIONS (Creates NEW transaction referencing original_tx_id)
  app.post('/api/v1/ledger/corrections', authMiddleware, syndicOnly, async (req: AuthRequest, res) => {
    const { original_tx_id, amount, correction_type, reason, idempotency_key } = req.body;
    if (!original_tx_id || !amount || Number(amount) <= 0 || !reason || !correction_type) {
      return res.status(400).json({ error: 'BAD_REQUEST: original_tx_id, amount, correction_type, and reason are required' });
    }

    if (!['CREDIT', 'DEBIT'].includes(correction_type)) {
      return res.status(400).json({ error: 'BAD_REQUEST: correction_type must be CREDIT or DEBIT' });
    }

    const key = idempotency_key || crypto.randomUUID();

    // Idempotency check with request_hash verification
    const idempCheck = await handleIdempotency(key, req.user!.id, req.body, res);
    if (idempCheck.handled) return;

    // Find original transaction by UUID or by sequential sequence ID
    const origRes = await query(`
      SELECT * FROM financial_transactions 
      WHERE id::text = $1 OR tx_seq_id = $1
    `, [original_tx_id]);

    if (origRes.rows.length === 0) {
      return res.status(404).json({ error: 'NOT_FOUND: Original financial transaction not found' });
    }
    const origTx = origRes.rows[0];

    // Create a NEW compensating transaction referencing original_tx_id
    // ORIGINAL TRANSACTION REMAINS UNTOUCHED AND LOCKED
    const newTxId = crypto.randomUUID();
    const newSeqId = await getNextTxId();
    const finalAmount = Math.floor(Number(amount));

    await query(`
      INSERT INTO financial_transactions (
        id, tx_seq_id, type, category, project_id, apartment_id, apartment_number,
        amount, payment_method, created_by_syndic_id, status, is_correction,
        original_tx_id, correction_reason, idempotency_key
      ) VALUES ($1, $2, $3, 'CORRECTION', $4, $5, $6, $7, $8, $9, 'LOCKED', TRUE, $10, $11, $12)
    `, [
      newTxId,
      newSeqId,
      correction_type,
      origTx.project_id,
      origTx.apartment_id,
      origTx.apartment_number,
      finalAmount,
      origTx.payment_method,
      req.user!.id,
      origTx.id,
      reason,
      key
    ]);

    await appendAuditLog(
      req.user!.id,
      req.user!.full_name,
      'CORRECTION_ISSUED',
      'FINANCIAL_TRANSACTION',
      newSeqId,
      { original_tx_id: origTx.tx_seq_id, amount: finalAmount, type: correction_type, reason },
      key,
      req.ip
    );

    await appendSyncEvent('FINANCIAL_TRANSACTION', newSeqId, 'CORRECTION_ISSUED', {
      tx_seq_id: newSeqId,
      original_tx_id: origTx.tx_seq_id,
      amount: finalAmount,
      type: correction_type
    });

    const createdCorrection = (await query('SELECT * FROM financial_transactions WHERE id = $1', [newTxId])).rows[0];
    const balanceInfo = await getOfficialBuildingBalance();

    const responsePayload = {
      correction_transaction: createdCorrection,
      original_transaction: origTx,
      official_balance: balanceInfo.official_balance,
      total_credit: balanceInfo.total_credit,
      total_debit: balanceInfo.total_debit
    };

    await recordIdempotency(key, req.user!.id, req.body, 201, responsePayload);
    res.status(201).json(responsePayload);
  });

  // 7. STRICT IMMUTABILITY ENFORCEMENT ON FINANCIAL LEDGER
  app.put('/api/v1/ledger/:id', authMiddleware, (req, res) => {
    res.status(409).json({
      error: 'ERR_LEDGER_IMMUTABLE',
      message: 'Financial transactions are LOCKED and immutable. Direct updates are prohibited. Use compensating corrections instead.'
    });
  });

  app.delete('/api/v1/ledger/:id', authMiddleware, (req, res) => {
    res.status(409).json({
      error: 'ERR_LEDGER_IMMUTABLE',
      message: 'Financial transactions are LOCKED and immutable. Direct deletions are prohibited.'
    });
  });

  // 8. AUTHORITATIVE LEDGER LIST & OFFICIAL BALANCE
  app.get('/api/v1/ledger', authMiddleware, async (req, res) => {
    const transactions = (await query(`
      SELECT 
        t.*,
        u.full_name as creator_name,
        orig.tx_seq_id as original_tx_seq_id
      FROM financial_transactions t
      JOIN users u ON t.created_by_syndic_id = u.id
      LEFT JOIN financial_transactions orig ON t.original_tx_id = orig.id
      ORDER BY t.created_at DESC
    `)).rows;

    const balanceInfo = await getOfficialBuildingBalance();

    res.json({
      transactions,
      official_balance: balanceInfo.official_balance,
      total_credit: balanceInfo.total_credit,
      total_debit: balanceInfo.total_debit
    });
  });

  // 9. MAINTENANCE
  app.get('/api/v1/maintenance', authMiddleware, async (req, res) => {
    const rows = (await query(`
      SELECT m.*, u.full_name as reporter_name
      FROM maintenance_reports m
      JOIN users u ON m.reporter_id = u.id
      ORDER BY m.created_at DESC
    `)).rows;
    res.json(rows);
  });

  app.post('/api/v1/maintenance', authMiddleware, async (req: AuthRequest, res) => {
    const { category, description, photo_url } = req.body;
    if (!category || !description) {
      return res.status(400).json({ error: 'BAD_REQUEST: Category and description are required' });
    }

    const reportId = `REP-2026-${Date.now().toString().slice(-4)}`;
    await query(`
      INSERT INTO maintenance_reports (id, apartment_id, apartment_number, reporter_id, category, description, photo_url, status)
      VALUES ($1, $2, $3, $4, $5, $6, $7, 'NEW')
    `, [reportId, req.user!.apartment_id, req.user!.apartment_number, req.user!.id, category, description, photo_url || null]);

    await appendAuditLog(
      req.user!.id,
      req.user!.full_name,
      'MAINTENANCE_REPORTED',
      'MAINTENANCE',
      reportId,
      { category, apartment_number: req.user!.apartment_number }
    );

    await appendSyncEvent('MAINTENANCE', reportId, 'CREATED', {
      id: reportId,
      category,
      apartment_number: req.user!.apartment_number,
      status: 'NEW'
    });

    const created = (await query('SELECT * FROM maintenance_reports WHERE id = $1', [reportId])).rows[0];
    res.status(201).json(created);
  });

  app.put('/api/v1/maintenance/:id/status', authMiddleware, syndicOnly, async (req: AuthRequest, res) => {
    const { id } = req.params;
    const { status, notes } = req.body;
    if (!['NEW', 'IN_PROGRESS', 'RESOLVED'].includes(status)) {
      return res.status(400).json({ error: 'BAD_REQUEST: Status must be NEW, IN_PROGRESS, or RESOLVED' });
    }

    await query(`
      UPDATE maintenance_reports 
      SET status = $1, syndic_notes = $2, updated_at = NOW()
      WHERE id = $3
    `, [status, notes || null, id]);

    await appendAuditLog(
      req.user!.id,
      req.user!.full_name,
      'MAINTENANCE_STATUS_UPDATED',
      'MAINTENANCE',
      id,
      { status, notes }
    );

    const updated = (await query('SELECT * FROM maintenance_reports WHERE id = $1', [id])).rows[0];
    res.json(updated);
  });

  // 10. ELEVATOR
  app.get('/api/v1/elevator', authMiddleware, async (req, res) => {
    const rows = (await query('SELECT * FROM elevator_records ORDER BY maintenance_date DESC')).rows;
    res.json(rows);
  });

  // 11. ANNOUNCEMENTS & MEETINGS
  app.get('/api/v1/announcements', authMiddleware, async (req, res) => {
    const rows = (await query(`
      SELECT a.*, u.full_name as creator_name
      FROM announcements a
      JOIN users u ON a.created_by_syndic_id = u.id
      ORDER BY a.created_at DESC
    `)).rows;
    res.json(rows);
  });

  app.post('/api/v1/announcements', authMiddleware, syndicOnly, async (req: AuthRequest, res) => {
    const { title, content, priority } = req.body;
    if (!title || !content) return res.status(400).json({ error: 'Title and content required' });

    const annId = `ANN-${Date.now().toString().slice(-4)}`;
    await query(`
      INSERT INTO announcements (id, title, content, priority, created_by_syndic_id)
      VALUES ($1, $2, $3, $4, $5)
    `, [annId, title, content, priority || 'NORMAL', req.user!.id]);

    const created = (await query('SELECT * FROM announcements WHERE id = $1', [annId])).rows[0];
    res.status(201).json(created);
  });

  app.get('/api/v1/meetings', authMiddleware, async (req, res) => {
    const rows = (await query('SELECT * FROM meetings ORDER BY meeting_date DESC')).rows;
    res.json(rows);
  });

  // 12. GENERAL ASSEMBLY VOTING (Exactly 1 vote per apartment)
  app.get('/api/v1/voting', authMiddleware, async (req, res) => {
    const sessions = (await query('SELECT * FROM voting_sessions ORDER BY created_at DESC')).rows;
    const result: any[] = [];

    for (const s of sessions) {
      const votes = (await query(`
        SELECT v.*, u.full_name as owner_name
        FROM votes v
        JOIN users u ON v.owner_id = u.id
        WHERE v.session_id = $1
        ORDER BY v.apartment_number ASC
      `, [s.id])).rows;

      result.push({
        ...s,
        votes
      });
    }

    res.json(result);
  });

  app.post('/api/v1/voting/:id/vote', authMiddleware, async (req: AuthRequest, res) => {
    const { id } = req.params;
    const { choice } = req.body;
    if (!['YES', 'NO', 'ABSTAIN'].includes(choice)) {
      return res.status(400).json({ error: 'BAD_REQUEST: Choice must be YES, NO, or ABSTAIN' });
    }

    try {
      await query(`
        INSERT INTO votes (session_id, apartment_id, apartment_number, owner_id, choice)
        VALUES ($1, $2, $3, $4, $5)
      `, [id, req.user!.apartment_id, req.user!.apartment_number, req.user!.id, choice]);

      await appendAuditLog(
        req.user!.id,
        req.user!.full_name,
        'VOTE_CAST',
        'VOTING',
        id,
        { apartment_number: req.user!.apartment_number, choice }
      );

      res.status(201).json({ success: true, choice, apartment_number: req.user!.apartment_number });
    } catch (err: any) {
      if (err.message && (err.message.includes('unique') || err.message.includes('UNIQUE'))) {
        return res.status(409).json({ error: 'CONFLICT: Apartment has already cast a vote for this session. Exactly 1 vote per apartment is permitted.' });
      }
      return res.status(500).json({ error: err.message });
    }
  });

  // 13. IMMUTABLE AUDIT LOGS
  app.get('/api/v1/audit-logs', authMiddleware, async (req, res) => {
    const logs = (await query('SELECT * FROM audit_logs ORDER BY created_at DESC LIMIT 100')).rows;
    res.json(logs);
  });

  app.put('/api/v1/audit-logs/:id', authMiddleware, (req, res) => {
    res.status(409).json({ error: 'ERR_AUDIT_IMMUTABLE: Audit logs are strictly append-only and cannot be modified.' });
  });

  app.delete('/api/v1/audit-logs/:id', authMiddleware, (req, res) => {
    res.status(409).json({ error: 'ERR_AUDIT_IMMUTABLE: Audit logs are strictly append-only and cannot be deleted.' });
  });

  // 14. MONOTONIC SYNC CURSOR ENGINE
  // Pull sync using monotonic server-side cursor to resume without missing records
  app.get('/api/v1/sync/pull', authMiddleware, async (req, res) => {
    const cursor = Number(req.query.cursor || 0);

    const eventsRes = await query(`
      SELECT * FROM sync_events 
      WHERE cursor_id > $1 
      ORDER BY cursor_id ASC 
      LIMIT 200
    `, [cursor]);

    const maxCursorRes = await query('SELECT COALESCE(MAX(cursor_id), 0) as max_cursor FROM sync_events');
    const currentCursor = Number(maxCursorRes.rows[0].max_cursor);

    res.json({
      events: eventsRes.rows,
      current_cursor: currentCursor,
      has_more: eventsRes.rows.length === 200
    });
  });

  // Push sync: Upload offline queued records with client-generated idempotency keys
  app.post('/api/v1/sync/push', authMiddleware, async (req: AuthRequest, res) => {
    const { items } = req.body;
    if (!Array.isArray(items)) {
      return res.status(400).json({ error: 'BAD_REQUEST: items array expected' });
    }

    const results: any[] = [];
    for (const item of items) {
      try {
        if (item.type === 'MAINTENANCE') {
          const reportId = `REP-2026-${Date.now().toString().slice(-4)}`;
          await query(`
            INSERT INTO maintenance_reports (id, apartment_id, apartment_number, reporter_id, category, description, photo_url, status)
            VALUES ($1, $2, $3, $4, $5, $6, $7, 'NEW')
          `, [reportId, req.user!.apartment_id, req.user!.apartment_number, req.user!.id, item.category, item.description, item.photo_url || null]);

          await appendSyncEvent('MAINTENANCE', reportId, 'CREATED', {
            id: reportId,
            category: item.category,
            apartment_number: req.user!.apartment_number
          });

          results.push({ local_id: item.local_id, server_id: reportId, status: 'SYNCED' });
        } else if (item.type === 'PAYMENT' && req.user!.role === 'SYNDIC') {
          const key = item.idempotency_key || crypto.randomUUID();
          const hash = crypto.createHash('sha256').update(JSON.stringify(item)).digest('hex');

          // Check if already processed
          const existingKeyRes = await query('SELECT * FROM idempotency_keys WHERE key = $1', [key]);
          if (existingKeyRes.rows.length > 0) {
            results.push({
              local_id: item.local_id,
              server_id: existingKeyRes.rows[0].response_body.transaction?.tx_seq_id,
              status: 'LOCKED',
              message: 'Already recorded idempotently'
            });
            continue;
          }

          const aptRes = await query('SELECT * FROM apartments WHERE apartment_number = $1', [item.apartment_number]);
          if (aptRes.rows.length === 0) throw new Error('Apartment not found');

          const txId = crypto.randomUUID();
          const seqId = await getNextTxId();
          const finalAmount = Math.floor(Number(item.amount));

          await query(`
            INSERT INTO financial_transactions (
              id, tx_seq_id, type, category, project_id, apartment_id, apartment_number,
              amount, payment_method, created_by_syndic_id, status, is_correction,
              idempotency_key
            ) VALUES ($1, $2, 'CREDIT', 'COTISATION_PROJET', $3, $4, $5, $6, $7, $8, 'LOCKED', FALSE, $9)
          `, [txId, seqId, item.project_id, aptRes.rows[0].id, item.apartment_number, finalAmount, item.payment_method || 'CASH', req.user!.id, key]);

          await appendSyncEvent('FINANCIAL_TRANSACTION', seqId, 'PAYMENT_RECORDED', {
            tx_seq_id: seqId,
            apartment_number: item.apartment_number,
            amount: finalAmount,
            status: 'LOCKED'
          });

          const createdTx = (await query('SELECT * FROM financial_transactions WHERE id = $1', [txId])).rows[0];
          await recordIdempotency(key, req.user!.id, item, 201, { transaction: createdTx });

          results.push({ local_id: item.local_id, server_id: seqId, status: 'LOCKED' });
        }
      } catch (err: any) {
        results.push({ local_id: item.local_id, error: err.message, status: 'FAILED' });
      }
    }

    const balanceInfo = await getOfficialBuildingBalance();
    res.json({
      synced: results,
      official_balance: balanceInfo.official_balance,
      server_time: new Date().toISOString()
    });
  });

  return app;
}

if (require.main === module) {
  initDatabase().then(() => {
    const app = createApp();
    app.listen(PORT, () => {
      console.log(`Amarati Authoritative Central Backend running on port ${PORT}`);
    });
  });
}
