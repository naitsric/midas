"""
Sistema de migraciones SQL simple.

Las migraciones son archivos .sql numerados en el directorio `migrations/`:
  0001_create_advisors.sql
  0002_create_conversations.sql
  ...

Se trackean en la tabla `schema_migrations` para no re-ejecutar.
"""

import asyncio
import os
import sys
from pathlib import Path

import asyncpg

MIGRATIONS_DIR = Path(__file__).parent.parent.parent.parent / "migrations"

TRACKING_TABLE = """
CREATE TABLE IF NOT EXISTS schema_migrations (
    version VARCHAR(255) PRIMARY KEY,
    applied_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
"""


async def get_applied_migrations(conn: asyncpg.Connection) -> set[str]:
    await conn.execute(TRACKING_TABLE)
    rows = await conn.fetch("SELECT version FROM schema_migrations ORDER BY version")
    return {row["version"] for row in rows}


async def get_pending_migrations(conn: asyncpg.Connection) -> list[tuple[str, str]]:
    applied = await get_applied_migrations(conn)

    migration_files = sorted(MIGRATIONS_DIR.glob("*.sql"))
    pending = []

    for f in migration_files:
        version = f.stem  # e.g. "0001_create_advisors"
        if version not in applied:
            pending.append((version, f.read_text()))

    return pending


async def run_migrations(database_url: str) -> list[str]:
    """Ejecuta migraciones pendientes. Retorna lista de versiones aplicadas."""
    conn = await asyncpg.connect(database_url)
    try:
        pending = await get_pending_migrations(conn)

        applied = []
        for version, sql in pending:
            async with conn.transaction():
                await conn.execute(sql)
                await conn.execute("INSERT INTO schema_migrations (version) VALUES ($1)", version)
            applied.append(version)

        return applied
    finally:
        await conn.close()


async def migration_status(database_url: str) -> tuple[list[str], list[str]]:
    """Retorna (aplicadas, pendientes)."""
    conn = await asyncpg.connect(database_url)
    try:
        applied_set = await get_applied_migrations(conn)
        migration_files = sorted(f.stem for f in MIGRATIONS_DIR.glob("*.sql"))

        applied = [v for v in migration_files if v in applied_set]
        pending = [v for v in migration_files if v not in applied_set]

        return applied, pending
    finally:
        await conn.close()


async def main():
    """CLI para ejecutar migraciones."""
    from dotenv import load_dotenv

    load_dotenv()

    database_url = os.getenv("DATABASE_URL")
    if not database_url:
        print("ERROR: DATABASE_URL no configurada en .env")
        sys.exit(1)

    command = sys.argv[1] if len(sys.argv) > 1 else "run"

    if command == "run":
        print(f"Ejecutando migraciones desde {MIGRATIONS_DIR}...")
        applied = await run_migrations(database_url)
        if applied:
            for v in applied:
                print(f"  ✓ {v}")
            print(f"\n{len(applied)} migración(es) aplicada(s).")
        else:
            print("No hay migraciones pendientes.")

    elif command == "status":
        applied, pending = await migration_status(database_url)
        if applied:
            print("Aplicadas:")
            for v in applied:
                print(f"  ✓ {v}")
        if pending:
            print("Pendientes:")
            for v in pending:
                print(f"  ○ {v}")
        if not applied and not pending:
            print("No hay migraciones.")

    else:
        print(f"Comando desconocido: {command}")
        print("Uso: python -m src.shared.infrastructure.migrator [run|status]")
        sys.exit(1)


if __name__ == "__main__":
    asyncio.run(main())
