from __future__ import annotations

import shutil
import zipfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "dist"


def zip_tree(destination: Path, roots: list[tuple[Path, str]], excluded: set[str] | None = None) -> None:
    excluded = excluded or set()
    destination.unlink(missing_ok=True)
    with zipfile.ZipFile(destination, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for source, prefix in roots:
            files = [source] if source.is_file() else source.rglob("*")
            for file in files:
                if not file.is_file() or any(part in excluded or part.endswith(".egg-info") for part in file.parts):
                    continue
                relative = file.name if source.is_file() else file.relative_to(source).as_posix()
                archive.write(file, f"{prefix}/{relative}".strip("/"))


def main() -> None:
    OUTPUT.mkdir(exist_ok=True)
    apk = ROOT / "app" / "build" / "outputs" / "apk" / "debug" / "app-debug.apk"
    if not apk.is_file():
        raise SystemExit("Construis d’abord l’APK debug.")
    shutil.copy2(apk, OUTPUT / "cellier-manager-2.1.14-dev-debug.apk")
    zip_tree(
        OUTPUT / "cellier-companion-0.2.13-source.zip",
        [
            (ROOT / "companion" / "pyproject.toml", "companion"),
            (ROOT / "companion" / "start-companion.ps1", "companion"),
            (ROOT / "companion" / "src", "companion/src"),
            (ROOT / "docs" / "installation.md", "docs"),
        ],
        {"__pycache__", ".pytest_cache", ".venv"},
    )
    zip_tree(
        OUTPUT / "cellier-extension-0.6.8.zip",
        [(ROOT / "extension" / "dist", "")],
    )


if __name__ == "__main__":
    main()
