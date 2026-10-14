const nodemailer = require('nodemailer');

let transporter = null;
let fromAddress = null;

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
  const t = getTransporter();
  if (!t) {
    console.log(`[mailer] No SMTP_HOST (or GMAIL_USER/GMAIL_APP_PASSWORD) configured. ${linkLabel} for ${to}: ${link}`);
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

module.exports = { sendVerificationEmail, sendPasswordResetEmail };
