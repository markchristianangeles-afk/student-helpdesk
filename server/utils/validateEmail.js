const dns = require('dns').promises;

const EMAIL_REGEX = /^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)+$/;

function isValidEmailFormat(email) {
  return typeof email === 'string' && EMAIL_REGEX.test(email.trim());
}

async function domainCanReceiveMail(email) {
  const domain = email.split('@')[1];
  if (!domain) return false;

  try {
    const mxRecords = await dns.resolveMx(domain);
    if (mxRecords && mxRecords.length > 0) return true;
  } catch (err) { /* no MX records, fall back to A/AAAA per RFC 5321 */ }

  try {
    const aRecords = await dns.resolve4(domain);
    if (aRecords && aRecords.length > 0) return true;
  } catch (err) { /* try AAAA next */ }

  try {
    const aaaaRecords = await dns.resolve6(domain);
    if (aaaaRecords && aaaaRecords.length > 0) return true;
  } catch (err) { /* domain cannot receive mail */ }

  return false;
}

// Throws an Error with a user-facing message and an http `status` if the email
// is malformed or its domain cannot be resolved. Does not confirm the mailbox
// itself exists or is owned by the submitter - only that the domain is real
// and configured to receive mail.
async function validateEmailOrThrow(email) {
  if (!isValidEmailFormat(email)) {
    const err = new Error('Please enter a valid email address');
    err.status = 400;
    throw err;
  }

  const domainOk = await domainCanReceiveMail(email);
  if (!domainOk) {
    const err = new Error('This email domain does not appear to exist or cannot receive mail');
    err.status = 400;
    throw err;
  }
}

module.exports = { isValidEmailFormat, domainCanReceiveMail, validateEmailOrThrow };
