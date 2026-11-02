// Finds the newest backup in ~/MedUMinder-backups for a project and checks it is new and complete enough.
const fs = require('fs');
const os = require('os');
const path = require('path');

function checkBackup({ project, after, needed, root }) {
  const dir = root || path.join(os.homedir(), 'MedUMinder-backups');
  if (!fs.existsSync(dir)) return { ok: false, why: `no backup folder at ${dir}` };
  const found = fs.readdirSync(dir)
    .map((d) => path.join(dir, d, 'summary.json'))
    .filter((f) => fs.existsSync(f))
    .map((f) => ({ dir: path.dirname(f), ...JSON.parse(fs.readFileSync(f, 'utf8')) }))
    .filter((b) => b.project === project)
    .sort((a, b) => Date.parse(b.created) - Date.parse(a.created));
  if (!found.length) return { ok: false, why: `no backup for ${project} in ${dir}` };
  const newest = found[0];
  if (Date.parse(newest.created) <= Date.parse(after)) {
    return { ok: false, why: `newest backup (${newest.created}) is OLDER than ${after}` };
  }
  const missing = needed.filter((c) => !(c in (newest.counts || {})));
  if (missing.length) return { ok: false, why: `newest backup has no ${missing.join(', ')}` };
  return { ok: true, dir: newest.dir, why: `${newest.dir} (${newest.created})` };
}

module.exports = { checkBackup };
