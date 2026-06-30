---
name: database-migrations
description: Database migration best practices for schema changes, data migrations, rollbacks, and zero-downtime deployments using Liquibase, PostgreSQL, and Spring Boot.
origin: ECC
---

# Database Migration Patterns (Liquibase + PostgreSQL)

Safe, reversible database schema changes for production systems.

## When to Activate

- Creating or altering database tables
- Adding/removing columns or indexes
- Running data migrations (backfill, transform)
- Planning zero-downtime schema changes
- Setting up Liquibase changelogs for a new module

## Core Principles

1. **Every change is a migration** — never alter production databases manually
2. **Migrations are forward-only in production** — rollbacks use new forward migrations or explicit rollback tags
3. **Schema and data migrations are separate** — never mix DDL and DML in one migration changeset
4. **Test migrations against production-sized data** — a migration that works on 100 rows may lock on 10M
5. **Migrations are immutable once deployed** — never edit a changeset that has run in production (Liquibase MD5 checksum will fail)

## Liquibase Safety Checklist

Before applying any migration:

- [ ] Changeset has a `<rollback>` tag (unless it's an additive-only schema change)
- [ ] No full table locks on large tables (use concurrent operations)
- [ ] New columns have defaults or are nullable (never add NOT NULL without default on existing tables)
- [ ] Indexes created concurrently (not inline with CREATE TABLE for existing tables)
- [ ] Data backfill is a separate changeset from the schema change
- [ ] Rollback plan documented

## PostgreSQL Patterns with Liquibase

### Adding a Column Safely

```xml
<!-- GOOD: Nullable column, no lock -->
<changeSet id="add-avatar-url" author="dev">
    <addColumn tableName="users">
        <column name="avatar_url" type="varchar(255)"/>
    </addColumn>
</changeSet>

<!-- GOOD: Column with default -->
<changeSet id="add-is-active" author="dev">
    <addColumn tableName="users">
        <column name="is_active" type="boolean" defaultValueBoolean="true">
            <constraints nullable="false"/>
        </column>
    </addColumn>
</changeSet>
```

### Adding an Index Without Downtime

To avoid locking tables in Postgres, use `CREATE INDEX CONCURRENTLY`. Since Liquibase doesn't natively support `CONCURRENTLY` in its `<createIndex>` tag safely across all transactions, use plain SQL and ensure `runInTransaction="false"`.

```xml
<changeSet id="add-email-index" author="dev" runInTransaction="false">
    <sql>
        CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_email ON users (email);
    </sql>
    <rollback>
        DROP INDEX IF EXISTS idx_users_email;
    </rollback>
</changeSet>
```

### Renaming a Column (Zero-Downtime)

Never rename directly in production. Use the expand-contract pattern:

1. **Phase 1 (Expand)**: Add the new column `display_name`. Deploy app to write to both, read from old.
2. **Phase 2 (Migrate)**: Data migration backfilling `display_name` from `username`. Deploy app to read from new.
3. **Phase 3 (Contract)**: Drop `username` column.

### Large Data Migrations

Avoid large `UPDATE` statements that lock the whole table. Use batching in Spring Boot or chunked PL/pgSQL.

```xml
<!-- Example of a batched data migration script in Liquibase -->
<changeSet id="backfill-emails" author="dev">
    <sql splitStatements="false">
        DO $$
        DECLARE
          batch_size INT := 10000;
          rows_updated INT;
        BEGIN
          LOOP
            UPDATE users
            SET normalized_email = LOWER(email)
            WHERE id IN (
              SELECT id FROM users
              WHERE normalized_email IS NULL
              LIMIT batch_size
              FOR UPDATE SKIP LOCKED
            );
            GET DIAGNOSTICS rows_updated = ROW_COUNT;
            EXIT WHEN rows_updated = 0;
            COMMIT;
          END LOOP;
        END $$;
    </sql>
</changeSet>
```

## Anti-Patterns

| Anti-Pattern | Why It Fails | Better Approach |
|-------------|-------------|-----------------|
| Manual SQL in production | No audit trail, unrepeatable | Always use Liquibase XML/SQL |
| Editing deployed migrations | Fails MD5 checksums | Create a new changeset |
| NOT NULL without default | Locks table, rewrites all rows | Add nullable, backfill, then add constraint |
| Schema + data in one changeset | Hard to rollback, long transactions | Separate changesets |
