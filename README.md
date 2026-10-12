# Student Helpdesk

A standalone student helpdesk ticketing system with three role-based access levels:
**Super Admin**, **Admin**, and **Student**.

Single Node.js process, SQLite database file, no build step, no external
services required — easy to run locally and easy to deploy publicly.

## Features

- Secure authentication (bcrypt password hashing, JWT session cookie)
- Role-based dashboards for Super Admin, Admin, and Student
- Ticket submission and tracking, with categories
- Status (open / in progress / resolved / closed) and priority (low / medium / high / urgent) management
- Ticket assignment to Admins
- Threaded comments on tickets
- In-app notifications (new tickets, status changes, assignments, comments)
- Reporting dashboard (status/priority/category breakdowns, trend chart, admin workload, avg resolution time)
- User management (Super Admin can create Admins/Students, change roles, activate/deactivate accounts)
- Responsive Bootstrap-based UI

## Roles

| Role        | Can do |
|-------------|--------|
| Student     | Register, submit tickets, track their own tickets, comment |
| Admin       | View/manage all tickets, change status/priority, assign tickets, comment, view reports |
| Super Admin | Everything Admin can do, plus manage all users and their roles |

Only Students can self-register. Admin and Super Admin accounts are created
by an existing Super Admin from the **Users** page.

## Local setup

```bash
npm install
cp .env.example .env
```

Edit `.env`:
- Set `JWT_SECRET` to a long random string (e.g. `node -e "console.log(require('crypto').randomBytes(48).toString('hex'))"`).
- Set `SUPER_ADMIN_EMAIL` / `SUPER_ADMIN_PASSWORD` to whatever you want the first Super Admin login to be.

Then:

```bash
npm start
```

Visit `http://localhost:3000`. On first run, a Super Admin account is seeded
automatically from your `.env` values (or defaults — check the server log).
**Log in and change that password immediately** from the Users page.

The SQLite database file is created at `data/helpdesk.sqlite`.

## Making it publicly accessible

This app is a single Express server that listens on `process.env.PORT` and
binds to `0.0.0.0`, so it can be deployed anywhere that runs Node.js.

### Option A — Render / Railway / Fly.io (simplest)

1. Push this folder to a GitHub repo.
2. Create a new Web Service from that repo.
3. Build command: `npm install`. Start command: `npm start`.
4. Set environment variables: `JWT_SECRET`, `SUPER_ADMIN_EMAIL`, `SUPER_ADMIN_PASSWORD`, `NODE_ENV=production`.
5. Attach a persistent disk/volume mounted at `./data` (otherwise the SQLite
   file is wiped on every redeploy/restart).
6. Deploy — the platform gives you a public HTTPS URL.

### Option B — Your own VPS

1. Install Node.js 18+ on the server.
2. Copy this project to the server, run `npm install --production`.
3. Set env vars (via `.env` or systemd `Environment=`), including `NODE_ENV=production`.
4. Run it under a process manager, e.g. PM2: `pm2 start server/index.js --name helpdesk`.
5. Put nginx (or Caddy) in front as a reverse proxy to terminate HTTPS and
   forward to `http://127.0.0.1:3000`.
6. Point your domain's DNS at the server.

Either way, keep the `data/` directory on persistent storage — it holds the
entire database.

## Project structure

```
server/
  index.js            Express app entry point
  db.js               SQLite schema + seed data
  middleware/auth.js   JWT auth + role-check middleware
  routes/              auth, categories, users, tickets, notifications, reports
  utils/notify.js      in-app notification helper
public/
  *.html               pages (login, register, dashboard, tickets, ticket detail, users, reports)
  css/style.css
  js/                   api client, nav/notifications, per-page logic
data/                  SQLite database file (created on first run)
```

## Notes / possible follow-ups

- Email notifications are not implemented — only in-app notifications. Hook
  an email provider into `server/utils/notify.js` if you need that.
- File attachments on tickets are not implemented.
- Passwords require a minimum of 6 characters; adjust in the route files if
  you need a stronger policy.
