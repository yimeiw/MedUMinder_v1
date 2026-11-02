// Same name rules as app/src/main/java/com/example/meduminderv1/Util/MedicineName.java.
// Change both files together, or the same name could become two private medicines.
const crypto = require('crypto');

const WHITESPACE = /[ \t\n\u000B\f\r ]+/g;

function key(name) {
  if (name === null || name === undefined) return '';
  return String(name).replace(WHITESPACE, ' ').replace(/^ | $/g, '').toLowerCase();
}

function docId(name) {
  return crypto.createHash('sha1').update(key(name), 'utf8').digest('hex');
}

module.exports = { key, docId };
