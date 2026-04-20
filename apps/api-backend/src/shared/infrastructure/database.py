import asyncpg


class Database:
    """Gestiona el pool de conexiones a PostgreSQL."""

    def __init__(self, url: str):
        self._url = url
        self._pool: asyncpg.Pool | None = None

    async def connect(self) -> None:
        # statement_cache_size=0 is required for Supabase Transaction Pooler
        # (pgBouncer in transaction mode) which does not support prepared statements.
        self._pool = await asyncpg.create_pool(
            self._url,
            min_size=2,
            max_size=10,
            statement_cache_size=0,
        )

    async def disconnect(self) -> None:
        if self._pool:
            await self._pool.close()
            self._pool = None

    @property
    def pool(self) -> asyncpg.Pool:
        if self._pool is None:
            raise RuntimeError("Database no conectada. Llama connect() primero.")
        return self._pool

    async def execute(self, query: str, *args) -> str:
        return await self.pool.execute(query, *args)

    async def fetch(self, query: str, *args) -> list[asyncpg.Record]:
        return await self.pool.fetch(query, *args)

    async def fetchrow(self, query: str, *args) -> asyncpg.Record | None:
        return await self.pool.fetchrow(query, *args)

    async def fetchval(self, query: str, *args):
        return await self.pool.fetchval(query, *args)
