const nodemailer = require('nodemailer');

let transporter = null;
let fromAddress = null;

// Without these, an SMTP host that accepts the TCP connection but never
// answers leaves the send hanging for the OS default, which is minutes.
const TIMEOUTS = {
  connectionTimeout: 10000,
  greetingTimeout: 10000,
  socketTimeout: 20000,
};

// Render (and most PaaS hosts) blocks outbound SMTP ports to stop their IP
// ranges being used for spam - confirmed here by an ETIMEDOUT against
// smtp.gmail.com:587 with otherwise-correct credentials. Brevo's HTTPS API
// uses port 443, the same port the app itself is served on, so it is not
// subject to that block. SMTP is kept as a fallback for hosts where it isn't
// blocked (a VPS, for instance).
const BREVO_ENDPOINT = 'https://api.brevo.com/v3/smtp/email';
const BREVO_TIMEOUT_MS = 15000;

function brevoFromAddress() {
  return process.env.EMAIL_FROM || process.env.SMTP_USER || process.env.GMAIL_USER || '';
}

async function sendViaBrevo({ to, subject, html }) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), BREVO_TIMEOUT_MS);
  try {
    const res = await fetch(BREVO_ENDPOINT, {
      method: 'POST',
      signal: controller.signal,
      headers: {
        'api-key': process.env.BREVO_API_KEY,
        'Content-Type': 'application/json',
        Accept: 'application/json',
      },
      body: JSON.stringify({
        sender: { name: 'Student Helpdesk', email: brevoFromAddress() },
        to: [{ email: to }],
        subject,
        htmlContent: html,
      }),
    });
    if (!res.ok) {
      const body = await res.text().catch(() => '');
      throw new Error(`Brevo ${res.status}: ${body.slice(0, 300)}`);
    }
  } finally {
    clearTimeout(timer);
  }
}

function getTransporter() {
  if (transporter) return transporter;

  // Generic SMTP: works with any provider (Gmail, Outlook/Office365, Yahoo,
  // a company mail server, SendGrid/Mailgun SMTP relay, etc).
  if (process.env.SMTP_HOST) {
    const port = parseInt(process.env.SMTP_PORT, 10) || 587;
    transporter = nodemailer.createTransport({
      host: process.env.SMTP_HOST,
      port,
      secure: process.env.SMTP_SECURE
        ? process.env.SMTP_SECURE === 'true'
        : port === 465,
      auth: process.env.SMTP_USER
        ? { user: process.env.SMTP_USER, pass: process.env.SMTP_PASS }
        : undefined,
      ...TIMEOUTS,
    });
    fromAddress = process.env.EMAIL_FROM || process.env.SMTP_USER;
    return transporter;
  }

  // Back-compat: Gmail-only configuration via an App Password.
  if (process.env.GMAIL_USER && process.env.GMAIL_APP_PASSWORD) {
    transporter = nodemailer.createTransport({
      service: 'gmail',
      auth: {
        user: process.env.GMAIL_USER,
        pass: process.env.GMAIL_APP_PASSWORD,
      },
      ...TIMEOUTS,
    });
    fromAddress = process.env.EMAIL_FROM || process.env.GMAIL_USER;
    return transporter;
  }

  return null;
}

function escapeHtml(value) {
  return String(value == null ? '' : value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

// Delivery is best effort. A transport failure must not reject into the route
// handler that triggered it: Express 4 does not catch rejected promises from
// async handlers, so that surfaces as an unhandled rejection and terminates the
// process. On failure the link is logged so it can still be recovered manually.
async function deliver({ to, subject, html, linkLabel, link }) {
  if (process.env.BREVO_API_KEY) {
    try {
      await sendViaBrevo({ to, subject, html });
      return;
    } catch (err) {
      console.error(`[mailer] Brevo send to ${to} failed: ${err.message}. ${linkLabel}: ${link}`);
      return;
    }
  }

  const t = getTransporter();
  if (!t) {
    console.log(`[mailer] No BREVO_API_KEY or SMTP_HOST (or GMAIL_USER/GMAIL_APP_PASSWORD) configured. ${linkLabel} for ${to}: ${link}`);
    return;
  }

  try {
    await t.sendMail({ from: `"Student Helpdesk" <${fromAddress}>`, to, subject, html });
  } catch (err) {
    console.error(`[mailer] Failed to send to ${to}: ${err.message}. ${linkLabel}: ${link}`);
  }
}

async function sendVerificationEmail(toEmail, name, verifyUrl) {
  await deliver({
    to: toEmail,
    subject: 'Verify your Student Helpdesk account',
    linkLabel: 'Verification link',
    link: verifyUrl,
    html: `
      <p>Hi ${escapeHtml(name)},</p>
      <p>Thanks for signing up for Student Helpdesk. Please verify your email address to activate your account:</p>
      <p><a href="${verifyUrl}">${verifyUrl}</a></p>
      <p>This link expires in 24 hours. If you didn't create this account, you can ignore this email.</p>
    `,
  });
}

async function sendPasswordResetEmail(toEmail, name, resetUrl) {
  await deliver({
    to: toEmail,
    subject: 'Reset your Student Helpdesk password',
    linkLabel: 'Password reset link',
    link: resetUrl,
    html: `
      <p>Hi ${escapeHtml(name)},</p>
      <p>We received a request to reset your Student Helpdesk password. Click the link below to choose a new one:</p>
      <p><a href="${resetUrl}">${resetUrl}</a></p>
      <p>This link expires in 1 hour. If you didn't request this, you can ignore this email.</p>
    `,
  });
}

// Called once at startup so a broken mail setup is obvious in the logs rather
// than only surfacing when someone tries to register or reset a password.
// Never logs the password, API key or app password.
async function verifyTransport() {
  if (process.env.BREVO_API_KEY) {
    console.log(`[mailer] Config: provider=brevo from=${brevoFromAddress() || 'MISSING'} key=set`);
    try {
      const res = await fetch('https://api.brevo.com/v3/account', {
        headers: { 'api-key': process.env.BREVO_API_KEY, Accept: 'application/json' },
      });
      if (!res.ok) {
        const body = await res.text().catch(() => '');
        throw new Error(`HTTP ${res.status}: ${body.slice(0, 300)}`);
      }
      console.log('[mailer] Brevo API key OK - email will be delivered.');
    } catch (err) {
      console.error(`[mailer] Brevo FAILED: ${err.message}`);
      console.error('[mailer] Email will NOT be delivered. Links are printed to this log as a fallback.');
    }
    return;
  }

  const t = getTransporter();
  if (!t) {
    console.log('[mailer] No BREVO_API_KEY or SMTP configured. Verification and reset links will be printed to this log instead of emailed.');
    console.log('[mailer] To send real email, set BREVO_API_KEY and EMAIL_FROM (preferred on hosts that block SMTP), or SMTP_HOST/PORT/USER/PASS/EMAIL_FROM.');
    return;
  }

  const opts = t.options || {};
  console.log(`[mailer] Config: provider=smtp host=${opts.host || opts.service || '?'} port=${opts.port || '?'} secure=${opts.secure} user=${opts.auth && opts.auth.user ? 'set' : 'MISSING'} from=${fromAddress || 'MISSING'}`);

  try {
    await t.verify();
    console.log('[mailer] SMTP connection OK - email will be delivered.');
  } catch (err) {
    console.error(`[mailer] SMTP FAILED (${err.code || 'no code'}): ${err.message}`);
    console.error('[mailer] Email will NOT be delivered. Links are printed to this log as a fallback.');
  }
}

module.exports = { sendVerificationEmail, sendPasswordResetEmail, verifyTransport };
