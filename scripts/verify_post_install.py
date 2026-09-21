from __future__ import annotations

import hashlib
import json
import shutil
import sqlite3
import sys
import tarfile
from pathlib import Path


INTENTIONALLY_MIGRATED_FIELDS = {
    "grapes",
    "isSyncPending",
    "syncFailed",
    "syncFailureReason",
    "syncAttempts",
}


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def extract_safely(archive: Path, destination: Path) -> None:
    if destination.exists():
        shutil.rmtree(destination)
    destination.mkdir(parents=True)
    with tarfile.open(archive, "r:") as source:
        names: set[str] = set()
        for member in source.getmembers():
            name = member.name.replace("\\", "/").lstrip("./")
            if not name or name in names or name.startswith("/") or ".." in Path(name).parts:
                raise SystemExit(f"Entrée TAR interdite: {member.name}")
            if not (member.isfile() or member.isdir()):
                raise SystemExit(f"Type d’entrée TAR interdit: {member.name}")
            names.add(name)
            target = (destination / name).resolve()
            if destination not in target.parents and target != destination:
                raise SystemExit(f"Chemin TAR interdit: {member.name}")
        source.extractall(destination)


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


def read_rows(path: Path) -> tuple[sqlite3.Connection, list[dict[str, object]]]:
    connection = sqlite3.connect(f"file:{path.as_posix()}?mode=ro", uri=True)
    connection.row_factory = sqlite3.Row
    rows = [dict(row) for row in connection.execute("SELECT * FROM cellar_items ORDER BY id")]
    return connection, rows


def main() -> None:
    if len(sys.argv) != 3:
        raise SystemExit("Usage: verify_post_install.py <before-folder> <after-folder>")

    before_folder = Path(sys.argv[1]).resolve()
    after_folder = Path(sys.argv[2]).resolve()
    archive = after_folder / "com.cellier.manager-databases.tar"
    extracted = after_folder / "extracted"
    extract_safely(archive, extracted)

    before_db = before_folder / "extracted" / "databases" / "cellier.db"
    after_db = extracted / "databases" / "cellier.db"
    before_connection, before_rows = read_rows(before_db)
    after_connection, after_rows = read_rows(after_db)
    try:
        integrity = after_connection.execute("PRAGMA integrity_check").fetchone()[0]
        user_version = after_connection.execute("PRAGMA user_version").fetchone()[0]
        total, in_stock, depleted, quantity = after_connection.execute(
            "SELECT COUNT(*), SUM(quantity > 0), SUM(quantity = 0), SUM(quantity) FROM cellar_items"
        ).fetchone()

        before_by_id = {row["id"]: row for row in before_rows}
        after_by_id = {row["id"]: row for row in after_rows}
        common_ids = before_by_id.keys() == after_by_id.keys()
        preserved_fields = True
        grapes_unchanged = True
        for item_id, before in before_by_id.items():
            after = after_by_id.get(item_id)
            if after is None:
                preserved_fields = False
                grapes_unchanged = False
                continue
            for field, value in before.items():
                if field not in INTENTIONALLY_MIGRATED_FIELDS and after.get(field) != value:
                    preserved_fields = False
            if decode_legacy_list(str(before.get("grapes") or "")) != decode_legacy_list(str(after.get("grapes") or "")):
                grapes_unchanged = False

        uuid_count, uuid_distinct = after_connection.execute(
            "SELECT COUNT(NULLIF(itemUuid,'')), COUNT(DISTINCT NULLIF(itemUuid,'')) FROM cellar_items"
        ).fetchone()
        origin_count = after_connection.execute("SELECT COUNT(*) FROM field_origins").fetchone()[0]
        dataset_id = after_connection.execute(
            "SELECT value FROM app_meta WHERE `key`='datasetId'"
        ).fetchone()
        request_count = after_connection.execute("SELECT COUNT(*) FROM enrichment_requests").fetchone()[0]
        proposal_count = after_connection.execute("SELECT COUNT(*) FROM enrichment_proposals").fetchone()[0]
        outbox_count = after_connection.execute("SELECT COUNT(*) FROM outbox_operations").fetchone()[0]
        photo_references = after_connection.execute(
            "SELECT COUNT(*) FROM cellar_items WHERE photoPath <> ''"
        ).fetchone()[0]
    finally:
        before_connection.close()
        after_connection.close()

    result = {
        "archiveSha256": sha256(archive),
        "archiveBytes": archive.stat().st_size,
        "databaseSha256": sha256(after_db),
        "sqliteIntegrity": integrity,
        "roomUserVersion": user_version,
        "totalItems": total,
        "inStockItems": in_stock or 0,
        "depletedItems": depleted or 0,
        "totalQuantity": quantity or 0,
        "photoReferences": photo_references,
        "sameItemIds": common_ids,
        "preservedLegacyFields": preserved_fields,
        "grapesSemanticallyUnchanged": grapes_unchanged,
        "allUuidsPresentAndDistinct": uuid_count == uuid_distinct == total,
        "legacyOrigins": origin_count,
        "expectedLegacyOrigins": total * 15,
        "datasetIdPresent": bool(dataset_id and dataset_id[0]),
        "enrichmentRequests": request_count,
        "enrichmentProposals": proposal_count,
        "outboxOperations": outbox_count,
    }
    report = after_folder / "post-install-verification.json"
    report.write_text(json.dumps(result, indent=2), encoding="utf-8")
    print(json.dumps(result, indent=2))

    checks = [
        integrity == "ok",
        user_version == 7,
        total == len(before_rows),
        common_ids,
        preserved_fields,
        grapes_unchanged,
        result["allUuidsPresentAndDistinct"],
        origin_count == result["expectedLegacyOrigins"],
        result["datasetIdPresent"],
    ]
    if not all(checks):
        raise SystemExit(2)


if __name__ == "__main__":
    main()
