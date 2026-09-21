from __future__ import annotations

import json
import sqlite3
import sys
from pathlib import Path


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit("Usage: verify_live_photos.py <post-install-folder>")
    folder = Path(sys.argv[1]).resolve()
    database = folder / "extracted" / "databases" / "cellier.db"
    connection = sqlite3.connect(f"file:{database.as_posix()}?mode=ro", uri=True)
    try:
        referenced = {
            Path(row[0]).name
            for row in connection.execute(
                "SELECT photoPath FROM cellar_items WHERE photoPath <> ''"
            )
        }
    finally:
        connection.close()

    live = {
        Path(line.strip()).name
        for line in (folder / "live-photo-files.txt").read_text(encoding="utf-8-sig").splitlines()
        if line.strip()
    }
    result = {
        "referencedPhotos": len(referenced),
        "livePhotoFiles": len(live),
        "missingReferencedPhotos": len(referenced - live),
        "unreferencedPhotoFiles": len(live - referenced),
    }
    (folder / "live-photo-verification.json").write_text(
        json.dumps(result, indent=2), encoding="utf-8"
    )
    print(json.dumps(result, indent=2))
    if referenced - live:
        raise SystemExit(2)


if __name__ == "__main__":
    main()
