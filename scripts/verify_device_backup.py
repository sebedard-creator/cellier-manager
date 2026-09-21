from __future__ import annotations

import hashlib
import json
import shutil
import sqlite3
import sys
import tarfile
from pathlib import Path


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit("Usage: verify_device_backup.py <backup-folder>")
    folder = Path(sys.argv[1]).resolve()
    archive = folder / "com.cellier.manager-data.tar"
    extracted = folder / "extracted"
    if extracted.exists():
        shutil.rmtree(extracted)
    extracted.mkdir()

    with tarfile.open(archive, "r:") as source:
        members = source.getmembers()
        names: set[str] = set()
        for member in members:
            name = member.name.replace("\\", "/").lstrip("./")
            if not name or name in names or name.startswith("/") or ".." in Path(name).parts:
                raise SystemExit(f"Entrée TAR interdite: {member.name}")
            if not (member.isfile() or member.isdir()):
                raise SystemExit(f"Type d’entrée TAR interdit: {member.name}")
            names.add(name)
            target = (extracted / name).resolve()
            if extracted not in target.parents and target != extracted:
                raise SystemExit(f"Chemin TAR interdit: {member.name}")
        source.extractall(extracted)

    db_path = extracted / "databases" / "cellier.db"
    connection = sqlite3.connect(f"file:{db_path.as_posix()}?mode=ro", uri=True)
    try:
        integrity = connection.execute("PRAGMA integrity_check").fetchone()[0]
        user_version = connection.execute("PRAGMA user_version").fetchone()[0]
        total, in_stock, depleted, quantity = connection.execute(
            "SELECT COUNT(*), SUM(quantity > 0), SUM(quantity = 0), SUM(quantity) FROM cellar_items"
        ).fetchone()
        photo_paths = [row[0] for row in connection.execute(
            "SELECT photoPath FROM cellar_items WHERE photoPath <> ''"
        ).fetchall()]
        columns = [row[1] for row in connection.execute("PRAGMA table_info(cellar_items)").fetchall()]
    finally:
        connection.close()

    photos_dir = extracted / "files" / "photos"
    photos = [path for path in photos_dir.glob("*") if path.is_file()] if photos_dir.exists() else []
    photo_names = {path.name for path in photos}
    referenced_names = {Path(path).name for path in photo_paths}
    summary = {
        "archiveSha256": sha256(archive),
        "archiveBytes": archive.stat().st_size,
        "tarEntries": len(members),
        "sqliteIntegrity": integrity,
        "roomUserVersion": user_version,
        "cellarColumns": columns,
        "totalItems": total,
        "inStockItems": in_stock or 0,
        "depletedItems": depleted or 0,
        "totalQuantity": quantity or 0,
        "photoReferences": len(photo_paths),
        "uniquePhotoReferences": len(referenced_names),
        "photoFiles": len(photos),
        "missingReferencedPhotos": len(referenced_names - photo_names),
        "orphanPhotoFiles": len(photo_names - referenced_names),
        "databaseSha256": sha256(db_path),
    }
    (folder / "verification.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")
    print(json.dumps(summary, indent=2))
    if integrity != "ok" or summary["missingReferencedPhotos"]:
        raise SystemExit(2)


if __name__ == "__main__":
    main()
