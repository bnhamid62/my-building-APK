import express, { Request, Response, NextFunction } from 'express';
import cors from 'cors';
import bcrypt from 'bcryptjs';
import jwt from 'jsonwebtoken';
import db, { initDatabase, getNextTxId } from './db';

const JWT_SECRET = process.env.JWT_SECRET || 'amarati_super_secure_jwt_secret_key_2026';
const PORT = process.env.PORT || 3000;

export interface AuthenticatedUser {
  id: number;
  username: string;
  role: 'OWNER' | 'OWNER_SYNDIC';
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
  if (req.user?.role !== 'OWNER_SYNDIC') {
    return res.status(403).json({ error: 'FORBIDDEN: Only a Syndic can perform this administrative operation' });
  }
  next();
}

export function createApp() {
  initDatabase();
  const app = express();
  app.use(cors());
  app.use(express.json());

  // Health check
  app.get('/api/health', (req, res) => {
    res.json({ status: 'OK', service: 'Amarati Central Backend', timestamp: new Date().toISOString() });
  });

  // 1. AUTHENTICATION
  app.post('/api/v1/auth/login', (req, res) => {
    const { username, password } = req.body;
    if (!username || !password) {
      return res.status(400).json({ error: 'BAD_REQUEST: Username and password are required' });
    }

    const stmt = db.prepare('SELECT * FROM users WHERE username = ?');
    const user = stmt.get(username.trim()) as any;

    if (!user) {
      return res.status(401).json({ error: 'UNAUTHORIZED: Invalid username or password' });
    }

    const isMatch = bcrypt.compareSync(password.trim(), user.password_hash);
    if (!isMatch) {
      return res.status(401).json({ error: 'UNAUTHORIZED: Invalid username or password' });
    }

    const payload: AuthenticatedUser = {
      id: user.id,
      username: user.username,
      role: user.role,
      apartment_number: user.apartment_number,
      floor: user.floor,
      full_name: user.full_name
    };

    const token = jwt.sign(payload, JWT_SECRET, { expiresIn: '7d' });

    // Record login audit
    db.prepare(`
      INSERT INTO audit_logs (actor_id, actor_name, action, entity_type, entity_id, details)
      VALUES (?, ?, 'LOGIN', 'USER', ?, 'Connexion réussie')
    `).run(user.id, user.full_name, String(user.id));

    res.json({
      token,
      user: {
        id: user.id,
        username: user.username,
        full_name: user.full_name,
        phone: user.phone,
        role: user.role,
        apartment_number: user.apartment_number,
        floor: user.floor
      }
    });
  });

  app.post('/api/v1/auth/change-password', authMiddleware, (req: AuthRequest, res) => {
    const { old_password, new_password } = req.body;
    if (!old_password || !new_password || new_password.length < 6) {
      return res.status(400).json({ error: 'BAD_REQUEST: New password must be at least 6 characters' });
    }

    const user = db.prepare('SELECT * FROM users WHERE id = ?').get(req.user!.id) as any;
    if (!bcrypt.compareSync(old_password, user.password_hash)) {
      return res.status(401).json({ error: 'UNAUTHORIZED: Current password incorrect' });
    }

    const newHash = bcrypt.hashSync(new_password, 10);
    db.prepare('UPDATE users SET password_hash = ? WHERE id = ?').run(newHash, req.user!.id);

    db.prepare(`
      INSERT INTO audit_logs (actor_id, actor_name, action, entity_type, entity_id, details)
      VALUES (?, ?, 'PASSWORD_CHANGE', 'USER', ?, 'Mot de passe modifié avec succès')
    `).run(req.user!.id, req.user!.full_name, String(req.user!.id));

    res.json({ success: true, message: 'Password updated successfully' });
  });

  // 2. APARTMENTS TRANSPARENCY
  app.get('/api/v1/apartments', authMiddleware, (req, res) => {
    const rows = db.prepare(`
      SELECT 
        a.number,
        a.floor,
        a.floor_label,
        u.full_name as owner_name,
        u.phone as owner_phone,
        u.role as owner_role,
        COALESCE((
          SELECT SUM(l.amount) 
          FROM financial_ledger l 
          WHERE l.apartment_number = a.number AND l.status = 'LOCKED' AND l.type = 'OWNER_PAYMENT'
        ), 0) as total_paid
      FROM apartments a
      JOIN users u ON a.owner_id = u.id
      ORDER BY a.number ASC
    `).all();

    res.json(rows);
  });

  // 3. FINANCIAL PROJECTS (Requires Double Approval: Syndic A != Syndic B)
  app.get('/api/v1/projects', authMiddleware, (req, res) => {
    const rows = db.prepare(`
      SELECT 
        p.*,
        c.full_name as creator_name,
        ap.full_name as approver_name
      FROM projects p
      JOIN users c ON p.creator_syndic_id = c.id
      LEFT JOIN users ap ON p.approver_syndic_id = ap.id
      ORDER BY p.created_at DESC
    `).all();
    res.json(rows);
  });

  app.post('/api/v1/projects', authMiddleware, syndicOnly, (req: AuthRequest, res) => {
    const { title, description, total_cost } = req.body;
    if (!title || !description || !total_cost || Number(total_cost) <= 0) {
      return res.status(400).json({ error: 'BAD_REQUEST: Valid title, description, and total_cost are required' });
    }

    const cost = Math.floor(Number(total_cost));
    const contribution = Math.floor(cost / 40);
    const projectId = `PRJ-2026-${Date.now().toString().slice(-4)}`;

    db.prepare(`
      INSERT INTO projects (id, title, description, total_cost, apartment_count, contribution_per_apt, creator_syndic_id, status)
      VALUES (?, ?, ?, ?, 40, ?, ?, 'PENDING_APPROVAL')
    `).run(projectId, title, description, cost, contribution, req.user!.id);

    db.prepare(`
      INSERT INTO audit_logs (actor_id, actor_name, action, entity_type, entity_id, details)
      VALUES (?, ?, 'PROJECT_CREATED', 'PROJECT', ?, ?)
    `).run(req.user!.id, req.user!.full_name, projectId, `Création projet: ${title} (${cost} DZD, en attente 2e syndic)`);

    const created = db.prepare('SELECT * FROM projects WHERE id = ?').get(projectId);
    res.status(201).json(created);
  });

  app.post('/api/v1/projects/:id/approve', authMiddleware, syndicOnly, (req: AuthRequest, res) => {
    const { id } = req.params;
    const project = db.prepare('SELECT * FROM projects WHERE id = ?').get(id) as any;
    if (!project) {
      return res.status(404).json({ error: 'NOT_FOUND: Project does not exist' });
    }

    if (project.status !== 'PENDING_APPROVAL') {
      return res.status(400).json({ error: 'BAD_REQUEST: Project is not pending approval' });
    }

    // MANDATORY CRITICAL INVARIANT: Creator cannot approve own project (Self-approval strictly prohibited)
    if (project.creator_syndic_id === req.user!.id) {
      return res.status(403).json({ error: 'ERR_SELF_APPROVAL_PROHIBITED: You cannot approve your own proposed project. The other Syndic must approve.' });
    }

    db.prepare(`
      UPDATE projects 
      SET approver_syndic_id = ?, status = 'APPROVED', approved_at = datetime('now')
      WHERE id = ?
    `).run(req.user!.id, id);

    db.prepare(`
      INSERT INTO audit_logs (actor_id, actor_name, action, entity_type, entity_id, details)
      VALUES (?, ?, 'PROJECT_APPROVED', 'PROJECT', ?, ?)
    `).run(req.user!.id, req.user!.full_name, id, `Projet approuvé par 2e syndic: ${project.title}`);

    const updated = db.prepare('SELECT * FROM projects WHERE id = ?').get(id);
    res.json(updated);
  });

  // 4. OWNER PAYMENT (Direct single-syndic recording, server assigns sequential ID, permanently locked)
  app.post('/api/v1/ledger/payments', authMiddleware, syndicOnly, (req: AuthRequest, res) => {
    const { apartment_number, project_id, amount, payment_method, idempotency_key } = req.body;
    if (!apartment_number || !project_id || !amount || Number(amount) <= 0 || !payment_method) {
      return res.status(400).json({ error: 'BAD_REQUEST: apartment_number, project_id, amount, and payment_method are required' });
    }

    const key = idempotency_key || `key-${Date.now()}-${Math.random()}`;

    // Idempotency check: if key already exists, return existing record
    const existing = db.prepare('SELECT * FROM financial_ledger WHERE idempotency_key = ?').get(key);
    if (existing) {
      return res.json(existing);
    }

    const aptRow = db.prepare('SELECT * FROM apartments WHERE number = ?').get(apartment_number) as any;
    if (!aptRow) {
      return res.status(404).json({ error: 'NOT_FOUND: Apartment number invalid' });
    }

    const txId = getNextTxId();
    const finalAmount = Math.floor(Number(amount));

    db.prepare(`
      INSERT INTO financial_ledger (
        tx_id, type, project_id, apartment_number, owner_id, amount, 
        payment_method, creator_syndic_id, approver_syndic_id, status, 
        idempotency_key, created_at, approved_at
      ) VALUES (?, 'OWNER_PAYMENT', ?, ?, ?, ?, ?, ?, ?, 'LOCKED', ?, datetime('now'), datetime('now'))
    `).run(
      txId,
      project_id,
      apartment_number,
      aptRow.owner_id,
      finalAmount,
      payment_method,
      req.user!.id,
      req.user!.id,
      key
    );

    db.prepare(`
      INSERT INTO audit_logs (actor_id, actor_name, action, entity_type, entity_id, details)
      VALUES (?, ?, 'PAYMENT_RECORDED', 'FINANCE', ?, ?)
    `).run(req.user!.id, req.user!.full_name, txId, `Paiement enregistré: Appt ${apartment_number}, ${finalAmount} DZD (${payment_method})`);

    const created = db.prepare('SELECT * FROM financial_ledger WHERE tx_id = ?').get(txId);
    res.status(201).json(created);
  });

  // 5. EXPENSES (Requires Double Approval: Syndic A != Syndic B)
  app.post('/api/v1/ledger/expenses', authMiddleware, syndicOnly, (req: AuthRequest, res) => {
    const { amount, supplier, invoice_number, expense_category, description, idempotency_key } = req.body;
    if (!amount || Number(amount) <= 0 || !supplier || !expense_category) {
      return res.status(400).json({ error: 'BAD_REQUEST: amount, supplier, and expense_category are required' });
    }

    const key = idempotency_key || `exp-key-${Date.now()}-${Math.random()}`;
    const existing = db.prepare('SELECT * FROM financial_ledger WHERE idempotency_key = ?').get(key);
    if (existing) return res.json(existing);

    const txId = getNextTxId();
    const finalAmount = Math.floor(Number(amount));

    db.prepare(`
      INSERT INTO financial_ledger (
        tx_id, type, amount, payment_method, creator_syndic_id, 
        status, supplier, invoice_number, expense_category, correction_reason, 
        idempotency_key, created_at
      ) VALUES (?, 'EXPENSE', ?, 'BANK_TRANSFER', ?, 'PENDING_APPROVAL', ?, ?, ?, ?, ?, datetime('now'))
    `).run(
      txId,
      finalAmount,
      req.user!.id,
      supplier,
      invoice_number || null,
      expense_category,
      description || null,
      key
    );

    db.prepare(`
      INSERT INTO audit_logs (actor_id, actor_name, action, entity_type, entity_id, details)
      VALUES (?, ?, 'EXPENSE_CREATED', 'FINANCE', ?, ?)
    `).run(req.user!.id, req.user!.full_name, txId, `Dépense proposée: ${supplier}, ${finalAmount} DZD (en attente 2e syndic)`);

    const created = db.prepare('SELECT * FROM financial_ledger WHERE tx_id = ?').get(txId);
    res.status(201).json(created);
  });

  // 6. FINANCIAL CORRECTIONS (Requires Double Approval, parent remains untouched)
  app.post('/api/v1/ledger/corrections', authMiddleware, syndicOnly, (req: AuthRequest, res) => {
    const { original_tx_id, corrected_amount, correction_type, reason, idempotency_key } = req.body;
    if (!original_tx_id || !corrected_amount || !reason || !correction_type) {
      return res.status(400).json({ error: 'BAD_REQUEST: original_tx_id, corrected_amount, correction_type, and reason are required' });
    }

    const original = db.prepare('SELECT * FROM financial_ledger WHERE tx_id = ?').get(original_tx_id) as any;
    if (!original) {
      return res.status(404).json({ error: 'NOT_FOUND: Original transaction not found' });
    }

    const key = idempotency_key || `corr-key-${Date.now()}-${Math.random()}`;
    const existing = db.prepare('SELECT * FROM financial_ledger WHERE idempotency_key = ?').get(key);
    if (existing) return res.json(existing);

    const txId = getNextTxId();
    const finalAmount = Math.floor(Number(corrected_amount));
    const ledgerType = correction_type === 'CREDIT' ? 'CORRECTION_CREDIT' : 'CORRECTION_DEBIT';

    db.prepare(`
      INSERT INTO financial_ledger (
        tx_id, type, project_id, apartment_number, owner_id, amount, 
        payment_method, creator_syndic_id, status, original_tx_id, 
        correction_reason, idempotency_key, created_at
      ) VALUES (?, ?, ?, ?, ?, ?, 'BANK_TRANSFER', ?, 'PENDING_APPROVAL', ?, ?, ?, datetime('now'))
    `).run(
      txId,
      ledgerType,
      original.project_id,
      original.apartment_number,
      original.owner_id,
      finalAmount,
      req.user!.id,
      original_tx_id,
      reason,
      key
    );

    db.prepare(`
      INSERT INTO audit_logs (actor_id, actor_name, action, entity_type, entity_id, details)
      VALUES (?, ?, 'CORRECTION_REQUESTED', 'FINANCE', ?, ?)
    `).run(req.user!.id, req.user!.full_name, txId, `Correction demandée sur ${original_tx_id}: ${finalAmount} DZD (${reason})`);

    const created = db.prepare('SELECT * FROM financial_ledger WHERE tx_id = ?').get(txId);
    res.status(201).json(created);
  });

  // Approve Expense or Correction (Double-approval invariant)
  app.post('/api/v1/ledger/:txId/approve', authMiddleware, syndicOnly, (req: AuthRequest, res) => {
    const { txId } = req.params;
    const tx = db.prepare('SELECT * FROM financial_ledger WHERE tx_id = ?').get(txId) as any;
    if (!tx) {
      return res.status(404).json({ error: 'NOT_FOUND: Transaction does not exist' });
    }

    if (tx.status !== 'PENDING_APPROVAL') {
      return res.status(400).json({ error: 'BAD_REQUEST: Transaction is not pending approval' });
    }

    // MANDATORY CRITICAL INVARIANT: Creator cannot approve own expense or correction
    if (tx.creator_syndic_id === req.user!.id) {
      return res.status(403).json({ error: 'ERR_SELF_APPROVAL_PROHIBITED: You cannot approve your own proposed transaction. The other Syndic must approve.' });
    }

    db.prepare(`
      UPDATE financial_ledger 
      SET approver_syndic_id = ?, status = 'LOCKED', approved_at = datetime('now')
      WHERE tx_id = ?
    `).run(req.user!.id, txId);

    db.prepare(`
      INSERT INTO audit_logs (actor_id, actor_name, action, entity_type, entity_id, details)
      VALUES (?, ?, 'FINANCE_APPROVED', 'FINANCE', ?, ?)
    `).run(req.user!.id, req.user!.full_name, txId, `Transaction ${txId} (${tx.type}) verrouillée et approuvée`);

    const updated = db.prepare('SELECT * FROM financial_ledger WHERE tx_id = ?').get(txId);
    res.json(updated);
  });

  // 7. IMMUTABILITY ENFORCEMENT: Direct update or delete is strictly rejected
  app.put('/api/v1/ledger/:txId', authMiddleware, (req, res) => {
    return res.status(409).json({ error: 'ERR_LEDGER_IMMUTABLE: Financial ledger transactions are immutable and cannot be updated. Use financial corrections instead.' });
  });

  app.delete('/api/v1/ledger/:txId', authMiddleware, (req, res) => {
    return res.status(409).json({ error: 'ERR_LEDGER_IMMUTABLE: Financial ledger transactions are immutable and cannot be deleted.' });
  });

  // 8. AUTHORITATIVE LEDGER LIST & BALANCE
  app.get('/api/v1/ledger', authMiddleware, (req, res) => {
    const rows = db.prepare(`
      SELECT 
        l.*,
        c.full_name as creator_name,
        ap.full_name as approver_name
      FROM financial_ledger l
      JOIN users c ON l.creator_syndic_id = c.id
      LEFT JOIN users ap ON l.approver_syndic_id = ap.id
      ORDER BY l.created_at DESC
    `).all();

    // Compute official server-authoritative balance:
    // Balance = SUM(LOCKED Owner Payments) + SUM(LOCKED Correction Credits) - SUM(LOCKED Expenses) - SUM(LOCKED Correction Debits)
    const stats = db.prepare(`
      SELECT 
        COALESCE(SUM(CASE WHEN type = 'OWNER_PAYMENT' AND status = 'LOCKED' THEN amount ELSE 0 END), 0) as total_payments,
        COALESCE(SUM(CASE WHEN type = 'EXPENSE' AND status = 'LOCKED' THEN amount ELSE 0 END), 0) as total_expenses,
        COALESCE(SUM(CASE WHEN type = 'CORRECTION_CREDIT' AND status = 'LOCKED' THEN amount ELSE 0 END), 0) as total_credits,
        COALESCE(SUM(CASE WHEN type = 'CORRECTION_DEBIT' AND status = 'LOCKED' THEN amount ELSE 0 END), 0) as total_debits
      FROM financial_ledger
    `).get() as any;

    const balance = (stats.total_payments + stats.total_credits) - (stats.total_expenses + stats.total_debits);

    res.json({
      transactions: rows,
      authoritative_balance: balance,
      total_collected: stats.total_payments + stats.total_credits,
      total_spent: stats.total_expenses + stats.total_debits
    });
  });

  // 9. MAINTENANCE
  app.get('/api/v1/maintenance', authMiddleware, (req, res) => {
    const rows = db.prepare(`
      SELECT m.*, u.full_name as reporter_name
      FROM maintenance_reports m
      JOIN users u ON m.reporter_id = u.id
      ORDER BY m.created_at DESC
    `).all();
    res.json(rows);
  });

  app.post('/api/v1/maintenance', authMiddleware, (req: AuthRequest, res) => {
    const { category, description, photo_url } = req.body;
    if (!category || !description) {
      return res.status(400).json({ error: 'BAD_REQUEST: Category and description are required' });
    }

    const reportId = `REP-2026-${Date.now().toString().slice(-4)}`;
    db.prepare(`
      INSERT INTO maintenance_reports (id, apartment_number, reporter_id, category, description, photo_url, status)
      VALUES (?, ?, ?, ?, ?, ?, 'NEW')
    `).run(reportId, req.user!.apartment_number, req.user!.id, category, description, photo_url || null);

    db.prepare(`
      INSERT INTO audit_logs (actor_id, actor_name, action, entity_type, entity_id, details)
      VALUES (?, ?, 'MAINTENANCE_CREATED', 'MAINTENANCE', ?, ?)
    `).run(req.user!.id, req.user!.full_name, reportId, `Signalement panne: ${category}`);

    const created = db.prepare('SELECT * FROM maintenance_reports WHERE id = ?').get(reportId);
    res.status(201).json(created);
  });

  app.put('/api/v1/maintenance/:id/status', authMiddleware, syndicOnly, (req: AuthRequest, res) => {
    const { id } = req.params;
    const { status, notes } = req.body;
    if (!['NEW', 'IN_PROGRESS', 'RESOLVED'].includes(status)) {
      return res.status(400).json({ error: 'BAD_REQUEST: Invalid status' });
    }

    db.prepare(`
      UPDATE maintenance_reports 
      SET status = ?, syndic_notes = ?, updated_at = datetime('now')
      WHERE id = ?
    `).run(status, notes || null, id);

    const updated = db.prepare('SELECT * FROM maintenance_reports WHERE id = ?').get(id);
    res.json(updated);
  });

  // 10. ELEVATOR
  app.get('/api/v1/elevator', authMiddleware, (req, res) => {
    const records = db.prepare('SELECT * FROM elevator_records ORDER BY maintenance_date DESC').all();
    res.json(records);
  });

  // 11. ANNOUNCEMENTS & MEETINGS
  app.get('/api/v1/announcements', authMiddleware, (req, res) => {
    const rows = db.prepare(`
      SELECT a.*, u.full_name as creator_name
      FROM announcements a
      JOIN users u ON a.creator_syndic_id = u.id
      ORDER BY a.created_at DESC
    `).all();
    res.json(rows);
  });

  app.post('/api/v1/announcements', authMiddleware, syndicOnly, (req: AuthRequest, res) => {
    const { title, content, priority } = req.body;
    if (!title || !content) return res.status(400).json({ error: 'Title and content required' });
    const annId = `ANN-${Date.now().toString().slice(-4)}`;
    db.prepare('INSERT INTO announcements (id, title, content, priority, creator_syndic_id) VALUES (?, ?, ?, ?, ?)')
      .run(annId, title, content, priority || 'NORMAL', req.user!.id);
    res.status(201).json(db.prepare('SELECT * FROM announcements WHERE id = ?').get(annId));
  });

  app.get('/api/v1/meetings', authMiddleware, (req, res) => {
    const rows = db.prepare('SELECT * FROM meetings ORDER BY meeting_date DESC').all();
    res.json(rows);
  });

  // 12. VOTING (Public & Non-Anonymous, exactly 1 vote per apartment)
  app.get('/api/v1/voting', authMiddleware, (req, res) => {
    const sessions = db.prepare('SELECT * FROM voting_sessions ORDER BY created_at DESC').all() as any[];
    const result = sessions.map(session => {
      const votes = db.prepare(`
        SELECT v.*, u.full_name as owner_name
        FROM votes v
        JOIN users u ON v.owner_id = u.id
        WHERE v.session_id = ?
        ORDER BY v.apartment_number ASC
      `).all(session.id);
      return { ...session, votes };
    });
    res.json(result);
  });

  app.post('/api/v1/voting/:id/vote', authMiddleware, (req: AuthRequest, res) => {
    const { id } = req.params;
    const { choice } = req.body;
    if (!['YES', 'NO', 'ABSTAIN'].includes(choice)) {
      return res.status(400).json({ error: 'BAD_REQUEST: Choice must be YES, NO, or ABSTAIN' });
    }

    try {
      db.prepare(`
        INSERT INTO votes (session_id, apartment_number, owner_id, choice, cast_at)
        VALUES (?, ?, ?, ?, datetime('now'))
      `).run(id, req.user!.apartment_number, req.user!.id, choice);

      db.prepare(`
        INSERT INTO audit_logs (actor_id, actor_name, action, entity_type, entity_id, details)
        VALUES (?, ?, 'VOTE_CAST', 'VOTING', ?, ?)
      `).run(req.user!.id, req.user!.full_name, id, `Vote public enregistré: Appt ${req.user!.apartment_number} = ${choice}`);

      res.status(201).json({ success: true, choice });
    } catch (err: any) {
      if (err.message && err.message.includes('UNIQUE constraint failed')) {
        return res.status(409).json({ error: 'CONFLICT: Apartment has already cast a vote for this session. Votes cannot be changed.' });
      }
      return res.status(500).json({ error: err.message });
    }
  });

  // 13. AUDIT LOGS
  app.get('/api/v1/audit-logs', authMiddleware, (req, res) => {
    const logs = db.prepare('SELECT * FROM audit_logs ORDER BY timestamp DESC LIMIT 100').all();
    res.json(logs);
  });

  // 14. SYNC PUSH (Batch upload for offline queued items)
  app.post('/api/v1/sync/push', authMiddleware, (req: AuthRequest, res) => {
    const { items } = req.body;
    if (!Array.isArray(items)) {
      return res.status(400).json({ error: 'BAD_REQUEST: items array expected' });
    }

    const results: any[] = [];
    for (const item of items) {
      try {
        if (item.type === 'MAINTENANCE') {
          const reportId = `REP-2026-${Date.now().toString().slice(-4)}`;
          db.prepare(`
            INSERT INTO maintenance_reports (id, apartment_number, reporter_id, category, description, photo_url, status)
            VALUES (?, ?, ?, ?, ?, ?, 'NEW')
          `).run(reportId, req.user!.apartment_number, req.user!.id, item.category, item.description, item.photo_url || null);
          results.push({ local_id: item.local_id, server_id: reportId, status: 'SYNCED' });
        } else if (item.type === 'PAYMENT' && req.user!.role === 'OWNER_SYNDIC') {
          const txId = getNextTxId();
          const aptRow = db.prepare('SELECT * FROM apartments WHERE number = ?').get(item.apartment_number) as any;
          db.prepare(`
            INSERT INTO financial_ledger (
              tx_id, type, project_id, apartment_number, owner_id, amount, 
              payment_method, creator_syndic_id, approver_syndic_id, status, 
              idempotency_key, created_at, approved_at
            ) VALUES (?, 'OWNER_PAYMENT', ?, ?, ?, ?, ?, ?, ?, 'LOCKED', ?, datetime('now'), datetime('now'))
          `).run(
            txId,
            item.project_id,
            item.apartment_number,
            aptRow.owner_id,
            Math.floor(item.amount),
            item.payment_method || 'CASH',
            req.user!.id,
            req.user!.id,
            item.idempotency_key || `sync-${Date.now()}`
          );
          results.push({ local_id: item.local_id, server_id: txId, status: 'LOCKED' });
        }
      } catch (err: any) {
        results.push({ local_id: item.local_id, error: err.message, status: 'FAILED' });
      }
    }

    res.json({ synced: results, server_time: new Date().toISOString() });
  });

  return app;
}

if (require.main === module) {
  const app = createApp();
  app.listen(PORT, () => {
    console.log(`Amarati Central Backend running on port ${PORT}`);
  });
}
