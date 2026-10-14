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

async function sendVerificationEmail(toEmail, name, verifyUrl) {
  const t = getTransporter();
  if (!t) {
    console.log(`[mailer] No SMTP_HOST (or GMAIL_USER/GMAIL_APP_PASSWORD) configured. Verification link for ${toEmail}: ${verifyUrl}`);
    return;
  }

  await t.sendMail({
    from: `"Student Helpdesk" <${fromAddress}>`,
    to: toEmail,
    subject: 'Verify your Student Helpdesk account',
    html: `
      <p>Hi ${name},</p>
      <p>Thanks for signing up for Student Helpdesk. Please verify your email address to activate your account:</p>
      <p><a href="${verifyUrl}">${verifyUrl}</a></p>
      <p>This link expires in 24 hours. If you didn't create this account, you can ignore this email.</p>
    `,
  });
}

async function sendPasswordResetEmail(toEmail, name, resetUrl) {
  const t = getTransporter();
  if (!t) {
    console.log(`[mailer] No SMTP_HOST (or GMAIL_USER/GMAIL_APP_PASSWORD) configured. Password reset link for ${toEmail}: ${resetUrl}`);
    return;
  }

  await t.sendMail({
    from: `"Student Helpdesk" <${fromAddress}>`,
    to: toEmail,
    subject: 'Reset your Student Helpdesk password',
    html: `
      <p>Hi ${name},</p>
      <p>We received a request to reset your Student Helpdesk password. Click the link below to choose a new one:</p>
      <p><a href="${resetUrl}">${resetUrl}</a></p>
      <p>This link expires in 1 hour. If you didn't request this, you can ignore this email.</p>
    `,
  });
}

module.exports = { sendVerificationEmail, sendPasswordResetEmail };
