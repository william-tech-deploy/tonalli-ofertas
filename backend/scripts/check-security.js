'use strict';
/**
 * Revisión estática automática (se ejecuta en CI y antes de cada despliegue).
 * No sustituye una auditoría: atrapa regresiones obvias de las reglas del proyecto.
 */
const fs = require('node:fs');
const path = require('node:path');

const root = path.join(__dirname, '..');
const problems = [];
const walk = (d) => fs.readdirSync(d, { withFileTypes: true }).flatMap((e) => (e.isDirectory() ? walk(path.join(d, e.name)) : [path.join(d, e.name)]));
const rel = (f) => path.relative(root, f);

const files = walk(path.join(root, 'src')).filter((f) => f.endsWith('.js'));
const RULES = [
  [/Math\.random\s*\(/, 'Math.random no es criptográfico: usa node:crypto'],
  [/\beval\s*\(|new\s+Function\s*\(/, 'eval/new Function prohibidos'],
  [/child_process/, 'child_process prohibido (inyección de comandos)'],
  [/console\.(log|error|warn|info|debug)\s*\(/, 'usa el logger (redacta secretos), no console'],
  [/createHash\(\s*['"](md5|sha1)['"]\s*\)/, 'md5/sha1 prohibidos'],
  [/rejectUnauthorized\s*:\s*false|NODE_TLS_REJECT_UNAUTHORIZED/, 'no se desactiva la verificación TLS'],
  [/Access-Control-Allow-Origin['"]\s*,\s*['"]\*/, 'CORS comodín prohibido'],
  [/innerHTML|document\.write/, 'sin sinks de DOM en el servidor'],
];
for (const f of files) {
  const text = fs.readFileSync(f, 'utf8');
  const r = rel(f);
  const code = text.replace(/\/\*[\s\S]*?\*\//g, '').replace(/^\s*\/\/.*$/gm, '');
  for (const [re, msg] of RULES) {
    // SHA-1 solo se admite en password.js: la API k-anonimato de HIBP lo exige (no es para guardar nada).
    if (r === path.join('src', 'lib', 'password.js') && /md5\|sha1/.test(re.source)) continue;
    if (re.test(code)) problems.push(`${r}: ${msg}`);
  }
  if (/process\.env/.test(code) && r !== path.join('src', 'config.js')) problems.push(`${r}: process.env solo se lee en config.js`);
  if (/\b(?:db|database|this\.db)\.(prepare|exec)\s*\(|\.prepare\s*\(/.test(code) && r !== path.join('src', 'db.js')) problems.push(`${r}: SQL directo solo en db.js (usa sql\`\`)`);
}

const pkg = JSON.parse(fs.readFileSync(path.join(root, 'package.json'), 'utf8'));
for (const k of ['dependencies', 'optionalDependencies', 'peerDependencies', 'devDependencies']) {
  if (pkg[k] && Object.keys(pkg[k]).length) problems.push(`package.json: ${k} debe estar vacío (cero dependencias)`);
}
for (const s of ['preinstall', 'install', 'postinstall', 'prepare']) {
  if (pkg.scripts && pkg.scripts[s]) problems.push(`package.json: script de ciclo de vida "${s}" prohibido`);
}

const SECRETS = [/-----BEGIN [A-Z ]*PRIVATE KEY-----/, /\bre_[A-Za-z0-9]{20,}\b/, /\bAKIA[0-9A-Z]{16}\b/, /\bghp_[A-Za-z0-9]{30,}\b/];
for (const f of walk(root).filter((x) => !/node_modules|[\\/]data[\\/]/.test(x) && /\.(js|json|sql|md|yml|yaml|env|example|service)$/.test(x) && !x.endsWith('check-security.js'))) {
  const text = fs.readFileSync(f, 'utf8');
  for (const re of SECRETS) if (re.test(text)) problems.push(`${rel(f)}: parece contener un secreto`);
}
if (fs.existsSync(path.join(root, '.env'))) problems.push('.env existe dentro del proyecto: no debe versionarse ni empaquetarse');

if (problems.length) {
  process.stderr.write(`check:security FALLÓ\n - ${problems.join('\n - ')}\n`);
  process.exit(1);
}
process.stdout.write(`check:security OK (${files.length} archivos revisados)\n`);
