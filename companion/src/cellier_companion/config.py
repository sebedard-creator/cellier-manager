from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class Settings:
    data_dir: Path
    loopback_host: str = "127.0.0.1"
    loopback_port: int = 18765
    lan_host: str = "0.0.0.0"
    lan_port: int = 8766
    capture_max_bytes: int = 2 * 1024 * 1024
    auto_open_searches: bool = False
    chrome_executable: Path | None = None
    chrome_extension_path: Path | None = None

    @classmethod
    def load(cls) -> "Settings":
        base = Path(os.environ.get("LOCALAPPDATA", Path.home() / ".local"))
        data_dir = Path(os.environ.get("CELLIER_COMPANION_DATA", base / "CellierManagerCompanion"))
        chrome_path = os.environ.get("CELLIER_CHROME_PATH", "").strip()
        extension_path = os.environ.get("CELLIER_EXTENSION_PATH", "").strip()
        bundled_extension = Path(__file__).resolve().parents[3] / "extension" / "dist"
        auto_open = os.environ.get("CELLIER_AUTO_OPEN_SEARCHES", "1").strip().lower()
        return cls(
            data_dir=data_dir,
            loopback_port=int(os.environ.get("CELLIER_COMPANION_LOOPBACK_PORT", "18765")),
            lan_port=int(os.environ.get("CELLIER_COMPANION_LAN_PORT", "8766")),
            auto_open_searches=auto_open not in {"0", "false", "no", "off"},
            chrome_executable=Path(chrome_path) if chrome_path else None,
            chrome_extension_path=(
                Path(extension_path) if extension_path
                else bundled_extension if (bundled_extension / "manifest.json").is_file()
                else None
            ),
        )

    @property
    def database_path(self) -> Path:
        return self.data_dir / "companion.db"

    @property
    def certificate_path(self) -> Path:
        return self.data_dir / "companion-cert.pem"

    @property
    def private_key_path(self) -> Path:
        return self.data_dir / "companion-key.pem"

    @property
    def chrome_profile_path(self) -> Path:
        return self.data_dir / "ChromeProfile"
