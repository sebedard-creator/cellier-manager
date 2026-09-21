from __future__ import annotations

import contextlib
import sqlite3
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Iterator


SCHEMA = """
PRAGMA foreign_keys = ON;
CREATE TABLE IF NOT EXISTS schema_migrations(version INTEGER PRIMARY KEY, applied_at TEXT NOT NULL);
CREATE TABLE IF NOT EXISTS devices(
  device_id TEXT PRIMARY KEY, role TEXT NOT NULL CHECK(role IN ('ANDROID','EXTENSION')),
  name TEXT NOT NULL, token_hash TEXT NOT NULL UNIQUE, extension_id TEXT,
  created_at TEXT NOT NULL, revoked_at TEXT, last_seen_at TEXT
);
CREATE TABLE IF NOT EXISTS pairing_sessions(
  pairing_id TEXT PRIMARY KEY, secret_hash TEXT NOT NULL, role TEXT NOT NULL,
  expires_at TEXT NOT NULL, consumed_at TEXT
);
CREATE TABLE IF NOT EXISTS jobs(
  request_id TEXT PRIMARY KEY, owner_device_id TEXT NOT NULL, dataset_id TEXT NOT NULL,
  item_uuid TEXT NOT NULL, source TEXT NOT NULL, identity_revision INTEGER NOT NULL,
  identity_json TEXT NOT NULL, state TEXT NOT NULL, server_revision INTEGER NOT NULL DEFAULT 1,
  created_at TEXT NOT NULL, updated_at TEXT NOT NULL, lease_owner TEXT,
  lease_token_hash TEXT, lease_expires_at TEXT, resolution TEXT,
  FOREIGN KEY(owner_device_id) REFERENCES devices(device_id)
);
CREATE TABLE IF NOT EXISTS captures(
  capture_id TEXT PRIMARY KEY, request_id TEXT NOT NULL, content_hash TEXT NOT NULL,
  envelope_json TEXT NOT NULL, state TEXT NOT NULL, parser_version TEXT,
  created_at TEXT NOT NULL, error_code TEXT, error_message TEXT,
  UNIQUE(request_id, content_hash), FOREIGN KEY(request_id) REFERENCES jobs(request_id)
);
CREATE TABLE IF NOT EXISTS proposals(
  proposal_id TEXT PRIMARY KEY, capture_id TEXT NOT NULL UNIQUE, request_id TEXT NOT NULL UNIQUE,
  payload_json TEXT NOT NULL, payload_hash TEXT NOT NULL, created_at TEXT NOT NULL,
  received_at TEXT, resolved_at TEXT, resolution TEXT,
  FOREIGN KEY(capture_id) REFERENCES captures(capture_id),
  FOREIGN KEY(request_id) REFERENCES jobs(request_id)
);
CREATE TABLE IF NOT EXISTS idempotency_records(
  device_id TEXT NOT NULL, route TEXT NOT NULL, idempotency_key TEXT NOT NULL,
  body_hash TEXT NOT NULL, status_code INTEGER NOT NULL, response_json TEXT NOT NULL,
  created_at TEXT NOT NULL, PRIMARY KEY(device_id, route, idempotency_key)
);
CREATE TABLE IF NOT EXISTS cancellation_tombstones(
  owner_device_id TEXT NOT NULL, request_id TEXT NOT NULL, created_at TEXT NOT NULL,
  PRIMARY KEY(owner_device_id, request_id)
);
CREATE INDEX IF NOT EXISTS idx_jobs_owner_state ON jobs(owner_device_id, state);
CREATE INDEX IF NOT EXISTS idx_jobs_state_created ON jobs(state, created_at);
CREATE INDEX IF NOT EXISTS idx_captures_request ON captures(request_id);
"""


class Database:
    def __init__(self, path: Path):
        self.path = path
        path.parent.mkdir(parents=True, exist_ok=True)
        with self.connect() as connection:
            connection.executescript(SCHEMA)
            connection.execute(
                "INSERT OR IGNORE INTO schema_migrations(version, applied_at) VALUES (1, datetime('now'))"
            )

    def cleanup(self) -> None:
        now = datetime.now(timezone.utc)
        cutoff_7d = (now - timedelta(days=7)).isoformat().replace("+00:00", "Z")
        cutoff_30d = (now - timedelta(days=30)).isoformat().replace("+00:00", "Z")
        cutoff_1d = (now - timedelta(days=1)).isoformat().replace("+00:00", "Z")
        with self.transaction() as connection:
            connection.execute(
                "UPDATE jobs SET state='EXPIRED',updated_at=?,server_revision=server_revision+1 "
                "WHERE state IN ('QUEUED','CLAIMED','NEEDS_USER') AND created_at < ?",
                (now.isoformat().replace("+00:00", "Z"), cutoff_7d),
            )
            old_jobs = "SELECT request_id FROM jobs WHERE state IN ('ACKED','CANCELLED','EXPIRED') AND updated_at < ?"
            connection.execute(f"DELETE FROM proposals WHERE request_id IN ({old_jobs})", (cutoff_30d,))
            connection.execute(f"DELETE FROM captures WHERE request_id IN ({old_jobs})", (cutoff_30d,))
            connection.execute(f"DELETE FROM jobs WHERE request_id IN ({old_jobs})", (cutoff_30d,))
            connection.execute("DELETE FROM pairing_sessions WHERE expires_at < ?", (cutoff_1d,))
            connection.execute("DELETE FROM idempotency_records WHERE created_at < ?", (cutoff_7d,))
            connection.execute("DELETE FROM cancellation_tombstones WHERE created_at < ?", (cutoff_30d,))

    @contextlib.contextmanager
    def connect(self) -> Iterator[sqlite3.Connection]:
        connection = sqlite3.connect(self.path, timeout=5, isolation_level=None)
        connection.row_factory = sqlite3.Row
        connection.execute("PRAGMA foreign_keys = ON")
        connection.execute("PRAGMA busy_timeout = 5000")
        try:
            yield connection
        finally:
            connection.close()

    @contextlib.contextmanager
    def transaction(self) -> Iterator[sqlite3.Connection]:
        with self.connect() as connection:
            connection.execute("BEGIN IMMEDIATE")
            try:
                yield connection
                connection.execute("COMMIT")
            except Exception:
                connection.execute("ROLLBACK")
                raise
