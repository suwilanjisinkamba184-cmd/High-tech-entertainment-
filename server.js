const express = require('express');
const multer = require('multer');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const app = express();
const PORT = Number(process.env.PORT || 3000);
const ROOT = __dirname;
const PUBLIC = path.join(ROOT, 'public');
const UPLOADS = path.join(PUBLIC, 'uploads');
const DATA = path.join(ROOT, 'data');
const DB = path.join(DATA, 'tracks.json');
const SIGNUPS = path.join(DATA, 'signups.json');
const MAX_FILE = 100 * 1024 * 1024;
const ALLOWED_EXT = new Set(['.mp3', '.m4a', '.aac', '.wav', '.ogg', '.webm']);
const ALLOWED_MIME = new Set(['audio/mpeg','audio/mp4','audio/x-m4a','audio/aac','audio/wav','audio/x-wav','audio/ogg','audio/webm','application/octet-stream']);
const ADMIN_UPLOAD_TOKEN = process.env.ADMIN_UPLOAD_TOKEN || '';
const signupHits = new Map();
const uploadHits = new Map();

fs.mkdirSync(UPLOADS, {recursive:true});
fs.mkdirSync(DATA, {recursive:true});
if (!fs.existsSync(DB)) fs.writeFileSync(DB, '[]\n');
if (!fs.existsSync(SIGNUPS)) fs.writeFileSync(SIGNUPS, '[]\n');

function readJson(file, fallback = []) {
  try { const value = JSON.parse(fs.readFileSync(file, 'utf8')); return Array.isArray(value) ? value : fallback; }
  catch (err) { console.error(`Could not read ${path.basename(file)}:`, err.message); return fallback; }
}
function writeJsonAtomic(file, value) {
  const temp = `${file}.${process.pid}.${crypto.randomBytes(4).toString('hex')}.tmp`;
  fs.writeFileSync(temp, JSON.stringify(value, null, 2) + '\n', {mode: 0o600});
  fs.renameSync(temp, file);
}
function readTracks(){ return readJson(DB); }
function writeTracks(list){ writeJsonAtomic(DB, list); }
function cleanName(name){ return String(name || 'audio').replace(/[^a-zA-Z0-9._-]/g,'_').slice(0,100); }
function slug(s){ return String(s||'track').toLowerCase().replace(/[^a-z0-9]+/g,'-').replace(/^-|-$/g,'').slice(0,60) || 'track'; }
function rateLimit(store, max, windowMs) {
  return (req, res, next) => {
    const ip = req.ip || req.socket.remoteAddress || 'unknown';
    const now = Date.now();
    let item = store.get(ip);
    if (!item || now - item.start >= windowMs) item = {start: now, count: 0};
    item.count += 1; store.set(ip, item);
    if (store.size > 5000) for (const [key, value] of store) if (now - value.start >= windowMs) store.delete(key);
    if (item.count > max) return res.status(429).json({ok:false,error:'Too many requests. Please wait and try again.'});
    next();
  };
}
function requireAdmin(req, res, next) {
  if (ADMIN_UPLOAD_TOKEN.length < 32) return res.status(503).json({ok:false,error:'Uploads are disabled until the administrator configures ADMIN_UPLOAD_TOKEN with at least 32 characters.'});
  const supplied = req.get('x-admin-token') || '';
  const a = Buffer.from(supplied), b = Buffer.from(ADMIN_UPLOAD_TOKEN);
  if (a.length !== b.length || !crypto.timingSafeEqual(a, b)) return res.status(401).json({ok:false,error:'Administrator authorisation required.'});
  next();
}
function hasValidAudioSignature(filePath, ext) {
  const fd = fs.openSync(filePath, 'r');
  try {
    const buf = Buffer.alloc(16); const n = fs.readSync(fd, buf, 0, buf.length, 0);
    if (ext === '.mp3') return (n >= 3 && buf.subarray(0,3).toString() === 'ID3') || (n >= 2 && buf[0] === 0xff && (buf[1] & 0xe0) === 0xe0);
    if (ext === '.m4a') return n >= 8 && buf.subarray(4,8).toString() === 'ftyp';
    if (ext === '.wav') return n >= 12 && buf.subarray(0,4).toString() === 'RIFF' && buf.subarray(8,12).toString() === 'WAVE';
    if (ext === '.ogg') return n >= 4 && buf.subarray(0,4).toString() === 'OggS';
    if (ext === '.webm') return n >= 4 && buf[0] === 0x1a && buf[1] === 0x45 && buf[2] === 0xdf && buf[3] === 0xa3;
    if (ext === '.aac') return n >= 2 && buf[0] === 0xff && (buf[1] & 0xf6) === 0xf0;
    return false;
  } finally { fs.closeSync(fd); }
}

const storage = multer.diskStorage({
  destination: (_req, _file, cb) => cb(null, UPLOADS),
  filename: (_req, file, cb) => cb(null, `${Date.now()}-${crypto.randomBytes(8).toString('hex')}${path.extname(file.originalname).toLowerCase()}`)
});
const upload = multer({
  storage,
  limits: { fileSize: MAX_FILE, files: 1, fields: 3, fieldSize: 2048 },
  fileFilter: (_req, file, cb) => {
    const ext = path.extname(file.originalname).toLowerCase();
    if (!ALLOWED_EXT.has(ext) || !ALLOWED_MIME.has(file.mimetype)) return cb(new Error('Unsupported audio type. Use MP3, M4A, AAC, WAV, OGG or WebM.'));
    cb(null, true);
  }
});

app.disable('x-powered-by');
app.set('trust proxy', process.env.TRUST_PROXY === 'true' ? 1 : false);
app.use((req, res, next) => {
  res.setHeader('X-Content-Type-Options', 'nosniff');
  res.setHeader('Referrer-Policy', 'strict-origin-when-cross-origin');
  res.setHeader('X-Frame-Options', 'DENY');
  res.setHeader('Permissions-Policy', 'camera=(), microphone=(), geolocation=()');
  res.setHeader('Content-Security-Policy', "default-src 'self'; img-src 'self' data: https://wqrxacjbsldbnoekbkbf.supabase.co; media-src 'self' https://wqrxacjbsldbnoekbkbf.supabase.co; connect-src 'self' https://wqrxacjbsldbnoekbkbf.supabase.co wss://wqrxacjbsldbnoekbkbf.supabase.co; script-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net; style-src 'self' 'unsafe-inline'; object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'");
  next();
});
app.use(express.json({limit:'20kb', strict:true}));
app.use(express.static(PUBLIC, {extensions:['html'], dotfiles:'deny', index:'index.html'}));

app.get('/api/health', (_req,res) => res.json({ok:true, service:'High Tech Entertainment Music API'}));
app.get('/api/tracks', (_req,res) => {
  const tracks = readTracks().map(t => ({...t, url:`/uploads/${encodeURIComponent(t.filename)}`}));
  res.setHeader('Cache-Control', 'no-store');
  res.json({ok:true, tracks});
});

app.post('/api/upload', rateLimit(uploadHits, 10, 15 * 60 * 1000), requireAdmin, upload.single('audio'), (req,res) => {
  if (!req.file) return res.status(400).json({ok:false,error:'Choose a supported audio file.'});
  const ext = path.extname(req.file.originalname).toLowerCase();
  if (!hasValidAudioSignature(req.file.path, ext)) {
    fs.rmSync(req.file.path, {force:true});
    return res.status(400).json({ok:false,error:'The file content does not match a supported audio format.'});
  }
  try {
    const uploader = String(req.body.uploader || '').trim().slice(0,80);
    const title = String(req.body.title || path.parse(req.file.originalname).name).trim().slice(0,140);
    const artist = String(req.body.artist || 'Unknown artist').trim().slice(0,180) || 'Unknown artist';
    if (!uploader || !title) { fs.rmSync(req.file.path, {force:true}); return res.status(400).json({ok:false,error:'Uploader name and track title are required.'}); }
    const id = `${slug(title)}-${Date.now().toString(36)}`;
    const track = {id,title,artist,filename:req.file.filename,mime:req.file.mimetype,duration:null,uploader,uploadedAt:new Date().toISOString(),brand:'High Tech Entertainment'};
    const tracks = readTracks(); tracks.unshift(track); writeTracks(tracks);
    res.status(201).json({ok:true,track:{...track,url:`/uploads/${encodeURIComponent(track.filename)}`}});
  } catch (err) {
    fs.rmSync(req.file.path, {force:true});
    console.error('Upload save failed:', err.message);
    res.status(500).json({ok:false,error:'Could not save the uploaded track. Please try again.'});
  }
});

app.post('/api/signup', rateLimit(signupHits, 5, 15 * 60 * 1000), (req,res) => {
  const email = String(req.body?.email || '').trim().toLowerCase();
  if (email.length > 254 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) return res.status(400).json({ok:false,error:'Enter a valid email address.'});
  try {
    const list = readJson(SIGNUPS);
    if (!list.some(x => x.email === email)) { list.push({email,createdAt:new Date().toISOString()}); writeJsonAtomic(SIGNUPS, list); }
    res.json({ok:true,message:'You are on the High Tech Entertainment list.'});
  } catch (err) { console.error('Signup save failed:', err.message); res.status(500).json({ok:false,error:'Signup could not be saved right now.'}); }
});

app.use((req,res) => res.status(404).json({ok:false,error:'Endpoint not found.'}));
app.use((err, _req, res, _next) => {
  if (err instanceof multer.MulterError) return res.status(err.code === 'LIMIT_FILE_SIZE' ? 413 : 400).json({ok:false,error:err.code === 'LIMIT_FILE_SIZE' ? 'File is too large (100 MB maximum).' : 'Upload request was invalid.'});
  if (err.message && err.message.startsWith('Unsupported audio type')) return res.status(415).json({ok:false,error:err.message});
  console.error('Request failed:', err.message);
  res.status(500).json({ok:false,error:'Server error. Please try again later.'});
});

app.listen(PORT, () => {
  console.log(`High Tech Entertainment server running on port ${PORT}`);
  if (!ADMIN_UPLOAD_TOKEN) console.warn('Uploads are disabled: set ADMIN_UPLOAD_TOKEN in the server environment before enabling administrator uploads.');
});
