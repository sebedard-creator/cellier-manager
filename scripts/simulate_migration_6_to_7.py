from __future__ import annotations

import hashlib
import json
import shutil
import sqlite3
import sys
import uuid
from pathlib import Path


FIELDS = [
    "name", "producer", "vintage", "type", "photoPath", "wineColor", "country",
    "region", "grapes", "style", "alcoholVolume", "ibu", "saqUrl", "vivinoUrl", "untappdUrl",
]


def java_name_uuid(value: bytes) -> str:
    digest = bytearray(hashlib.md5(value).digest())
    digest[6] = (digest[6] & 0x0F) | 0x30
    digest[8] = (digest[8] & 0x3F) | 0x80
    return str(uuid.UUID(bytes=bytes(digest)))


def decode_legacy_list(value: str) -> list[str]:
    if not value.strip():
        return []
    if value.lstrip().startswith("["):
        parsed = json.loads(value)
        return [str(item).strip() for item in parsed if str(item).strip()]
    result: list[str] = []
    current: list[str] = []
    escaped = False
    for char in value:
        if escaped:
            current.append(char)
            escaped = False
        elif char == "\\":
            escaped = True
        elif char == "|":
            item = "".join(current).strip()
            if item:
                result.append(item)
            current = []
        else:
            current.append(char)
    if escaped:
        current.append("\\")
    item = "".join(current).strip()
    if item:
        result.append(item)
    return result


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit("Usage: simulate_migration_6_to_7.py <backup-folder>")
    folder = Path(sys.argv[1]).resolve()
    source = folder / "extracted" / "databases" / "cellier.db"
    target = folder / "migration-test-v7.db"
    target.unlink(missing_ok=True)

    source_db = sqlite3.connect(f"file:{source.as_posix()}?mode=ro", uri=True)
    target_db = sqlite3.connect(target)
    source_db.backup(target_db)
    source_db.close()
    target_db.row_factory = sqlite3.Row
    before_rows = [dict(row) for row in target_db.execute("SELECT * FROM cellar_items ORDER BY id")]
    before_domain = {
        row["id"]: {key: row[key] for key in row if key not in {"isSyncPending", "syncFailed", "syncFailureReason", "syncAttempts", "grapes"}}
        for row in before_rows
    }

    dataset_id = str(uuid.uuid4())
    with target_db:
        target_db.execute("CREATE TABLE IF NOT EXISTS app_meta (`key` TEXT NOT NULL, value TEXT NOT NULL, PRIMARY KEY(`key`))")
        target_db.execute("INSERT OR REPLACE INTO app_meta (`key`,value) VALUES ('datasetId',?)", (dataset_id,))
        target_db.execute("INSERT OR REPLACE INTO app_meta (`key`,value) VALUES ('backupFormatVersion','1')")
        target_db.execute("ALTER TABLE cellar_items ADD COLUMN itemUuid TEXT NOT NULL DEFAULT ''")
        target_db.execute("ALTER TABLE cellar_items ADD COLUMN metadataRevision INTEGER NOT NULL DEFAULT 0")
        target_db.execute("ALTER TABLE cellar_items ADD COLUMN identityRevision INTEGER NOT NULL DEFAULT 0")
        target_db.execute("ALTER TABLE cellar_items ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
        for row in before_rows:
            item_uuid = java_name_uuid(f"{dataset_id}:item:{row['id']}".encode())
            grapes = json.dumps(decode_legacy_list(row["grapes"] or ""), ensure_ascii=False, separators=(",", ":"))
            target_db.execute(
                "UPDATE cellar_items SET itemUuid=?,grapes=?,updatedAt=? WHERE id=?",
                (item_uuid, grapes, row["created_at"], row["id"]),
            )
        target_db.execute("CREATE UNIQUE INDEX index_cellar_items_itemUuid ON cellar_items(itemUuid)")
        target_db.executescript("""
        CREATE TABLE field_origins(itemId INTEGER NOT NULL,fieldName TEXT NOT NULL,origin TEXT NOT NULL,
          source TEXT,sourceUrl TEXT,proposalId TEXT,changedAt INTEGER NOT NULL,explicitlyCleared INTEGER NOT NULL,
          PRIMARY KEY(itemId,fieldName),FOREIGN KEY(itemId) REFERENCES cellar_items(id) ON DELETE CASCADE);
        CREATE INDEX index_field_origins_itemId ON field_origins(itemId);
        CREATE TABLE enrichment_requests(requestId TEXT NOT NULL PRIMARY KEY,datasetId TEXT NOT NULL,itemUuid TEXT NOT NULL,
          itemId INTEGER NOT NULL,source TEXT NOT NULL,identitySnapshotJson TEXT NOT NULL,identityRevisionAtRequest INTEGER NOT NULL,
          state TEXT NOT NULL,serverState TEXT,serverRevision INTEGER,createdAt INTEGER NOT NULL,updatedAt INTEGER NOT NULL,
          lastErrorCode TEXT,lastErrorMessage TEXT,supersedesRequestId TEXT,
          FOREIGN KEY(itemId) REFERENCES cellar_items(id) ON DELETE CASCADE);
        CREATE INDEX index_enrichment_requests_itemId ON enrichment_requests(itemId);
        CREATE INDEX index_enrichment_requests_itemUuid ON enrichment_requests(itemUuid);
        CREATE INDEX index_enrichment_requests_state ON enrichment_requests(state);
        CREATE TABLE enrichment_proposals(proposalId TEXT NOT NULL PRIMARY KEY,requestId TEXT NOT NULL,payloadJson TEXT NOT NULL,
          payloadSha256 TEXT NOT NULL,receivedAt INTEGER NOT NULL,state TEXT NOT NULL,appliedAt INTEGER,selectedFieldsJson TEXT,
          FOREIGN KEY(requestId) REFERENCES enrichment_requests(requestId) ON DELETE CASCADE);
        CREATE INDEX index_enrichment_proposals_requestId ON enrichment_proposals(requestId);
        CREATE TABLE outbox_operations(operationId TEXT NOT NULL PRIMARY KEY,kind TEXT NOT NULL,aggregateId TEXT NOT NULL,
          payloadJson TEXT NOT NULL,payloadSha256 TEXT NOT NULL,attempts INTEGER NOT NULL,nextAttemptAt INTEGER NOT NULL,
          state TEXT NOT NULL,lastErrorCode TEXT);
        CREATE INDEX index_outbox_operations_kind_aggregateId_state ON outbox_operations(kind,aggregateId,state);
        CREATE INDEX index_outbox_operations_nextAttemptAt ON outbox_operations(nextAttemptAt);
        """)
        for row in before_rows:
            for field in FIELDS:
                target_db.execute(
                    "INSERT INTO field_origins VALUES (?,?, 'LEGACY',NULL,NULL,NULL,0,0)",
                    (row["id"], field),
                )
        target_db.execute(
            "UPDATE cellar_items SET isSyncPending=0,syncFailed=0,syncFailureReason=NULL,syncAttempts=0"
        )
        target_db.execute("PRAGMA user_version=7")

    after_rows = [dict(row) for row in target_db.execute("SELECT * FROM cellar_items ORDER BY id")]
    after_domain = {
        row["id"]: {key: row[key] for key in before_domain[row["id"]]}
        for row in after_rows
    }
    uuid_count, uuid_distinct = target_db.execute("SELECT COUNT(itemUuid),COUNT(DISTINCT itemUuid) FROM cellar_items").fetchone()
    origin_count = target_db.execute("SELECT COUNT(*) FROM field_origins").fetchone()[0]
    integrity = target_db.execute("PRAGMA integrity_check").fetchone()[0]
    quantity_before = sum(row["quantity"] for row in before_rows)
    quantity_after = sum(row["quantity"] for row in after_rows)
    result = {
        "sqliteIntegrity": integrity,
        "rowsBefore": len(before_rows),
        "rowsAfter": len(after_rows),
        "domainFieldsUnchanged": before_domain == after_domain,
        "quantityBefore": quantity_before,
        "quantityAfter": quantity_after,
        "allUuidsPresentAndDistinct": uuid_count == uuid_distinct == len(after_rows),
        "legacyOrigins": origin_count,
        "expectedLegacyOrigins": len(after_rows) * len(FIELDS),
        "allSyncFlagsNeutralized": target_db.execute(
            "SELECT COUNT(*) FROM cellar_items WHERE isSyncPending<>0 OR syncFailed<>0 OR syncAttempts<>0 OR syncFailureReason IS NOT NULL"
        ).fetchone()[0] == 0,
        "userVersion": target_db.execute("PRAGMA user_version").fetchone()[0],
    }
    target_db.close()
    (folder / "migration-verification.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
    print(json.dumps(result, indent=2))
    if not all([
        integrity == "ok", len(before_rows) == len(after_rows), before_domain == after_domain,
        quantity_before == quantity_after, result["allUuidsPresentAndDistinct"],
        origin_count == result["expectedLegacyOrigins"], result["allSyncFlagsNeutralized"],
        result["userVersion"] == 7,
    ]):
        raise SystemExit(2)


if __name__ == "__main__":
    main()
