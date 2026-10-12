// Standalone runner: memory store by default, PG when DATABASE_URL is set.
import { createServer } from './server.js';
import { createMemoryStore } from './store-memory.js';

const port = Number(process.env.PORT ?? 3021);
let store = createMemoryStore();
if (process.env.DATABASE_URL) {
  const [{ Pool }, { createPgStore }] = await Promise.all([import('pg'), import('./store-pg.js')]);
  store = createPgStore(new Pool({ connectionString: process.env.DATABASE_URL }));
  console.log('cloud: using postgres store');
} else {
  console.log('cloud: using memory store (set DATABASE_URL for postgres)');
}
createServer(store).listen(port, () => console.log(`cloud: listening :${port}`));
