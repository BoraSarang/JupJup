// Vercel serverless entry — PG pool is module-cached across warm invocations.
// Env: DATABASE_URL (Supabase pooler :6543 권장), INGEST_TOKEN, ALLOW_ORIGIN
import { Pool } from 'pg';
import { createHandler } from '../src/server.js';
import { createPgStore } from '../src/store-pg.js';

const pool = new Pool({
  connectionString: process.env.DATABASE_URL,
  max: 1,
  idleTimeoutMillis: 10_000,
});
const handler = createHandler(createPgStore(pool), {
  allowOrigin: process.env.ALLOW_ORIGIN ?? '*',
});

export default function (req, res) {
  return handler(req, res);
}
