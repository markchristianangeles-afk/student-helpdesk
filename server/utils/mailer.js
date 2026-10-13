const nodemailer = require('nodemailer');

let transporter = null;

function getTransporter() {
  if (transporter) return transporter;
  if (!process.env.GMAIL_USER || !process.env.GMAIL_APP_PASSWORD) {
    return null;
  }
  transporter = nodemailer.createTransport({
    service: 'gmail',
    auth: {
      user: process.env.GMAIL_USER,
      pass: process.env.GMAIL_APP_PASSWORD,
    },
  });
  return transporter;
}

async function sendVerificationEmail(toEmail, name, verifyUrl) {
  const t = getTransporter();
  if (!t) {
    console.log(`[mailer] GMAIL_USER/GMAIL_APP_PASSWORD not configured. Verification link for ${toEmail}: ${verifyUrl}`);
    return;
  }

  await t.sendMail({
    from: `"Student Helpdesk" <${process.env.GMAIL_USER}>`,
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

module.exports = { sendVerificationEmail };
