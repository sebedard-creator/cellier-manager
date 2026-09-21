from __future__ import annotations

import os
import re
import shutil
import subprocess
import sys
from threading import Lock
from pathlib import Path
from urllib.parse import urlencode

from .config import Settings
from .models import CreateRequest


_PROCESS_LOCK = Lock()
_managed_chrome_process: subprocess.Popen | None = None


def search_terms(payload: CreateRequest) -> str:
    name = payload.identity.name.strip()
    vintage = (payload.identity.vintage or "").strip()
    if vintage:
        name = re.sub(rf"(?<!\d){re.escape(vintage)}(?!\d)", " ", name)
        name = re.sub(r"\(\s*\)|\[\s*\]|\{\s*\}", " ", name)
        name = re.sub(r"\s+", " ", name).strip(" -–—")
    return " ".join(
        value for value in (payload.identity.producer.strip(), name, vintage) if value
    )


def build_search_url(payload: CreateRequest) -> str:
    terms = search_terms(payload)
    query = urlencode({"q": terms})
    if payload.source == "VIVINO":
        base = f"https://www.vivino.com/search/wines?{query}"
    elif payload.source == "UNTAPPD":
        base = f"https://untappd.com/search?{query}"
    else:
        base = f"https://www.saq.com/fr/catalogsearch/result/?{query}"
    return f"{base}#cellier-request={payload.requestId}"


def find_chrome(settings: Settings) -> Path | None:
    if settings.chrome_executable and settings.chrome_executable.is_file():
        return settings.chrome_executable

    for executable in ("chrome.exe", "chrome", "google-chrome", "chromium"):
        found = shutil.which(executable)
        if found:
            return Path(found)

    if os.name != "nt":
        return None

    candidates = [
        Path(os.environ.get("LOCALAPPDATA", "")) / "Google/Chrome/Application/chrome.exe",
        Path(os.environ.get("PROGRAMFILES", "")) / "Google/Chrome/Application/chrome.exe",
        Path(os.environ.get("PROGRAMFILES(X86)", "")) / "Google/Chrome/Application/chrome.exe",
    ]
    for candidate in candidates:
        if candidate.is_file():
            return candidate

    try:
        import winreg

        for hive in (winreg.HKEY_CURRENT_USER, winreg.HKEY_LOCAL_MACHINE):
            for flags in (winreg.KEY_READ, winreg.KEY_READ | winreg.KEY_WOW64_32KEY):
                try:
                    with winreg.OpenKey(
                        hive,
                        r"SOFTWARE\Microsoft\Windows\CurrentVersion\App Paths\chrome.exe",
                        0,
                        flags,
                    ) as key:
                        registered = Path(winreg.QueryValue(key, None))
                        if registered.is_file():
                            return registered
                except OSError:
                    continue
    except ImportError:
        pass
    return None


def build_chrome_command(url: str, settings: Settings) -> list[str]:
    chrome = find_chrome(settings)
    if chrome is None:
        raise RuntimeError(
            "Google Chrome est introuvable. Définis CELLIER_CHROME_PATH vers chrome.exe."
        )
    command = [
        str(chrome),
        f"--user-data-dir={settings.chrome_profile_path}",
        "--no-first-run",
        "--no-default-browser-check",
        "--disable-background-mode",
    ]
    if settings.chrome_extension_path and (settings.chrome_extension_path / "manifest.json").is_file():
        command.append(f"--load-extension={settings.chrome_extension_path.resolve()}")
    command.extend(["--new-tab", url])
    return command


def open_url_in_chrome(url: str, settings: Settings) -> None:
    global _managed_chrome_process
    command = build_chrome_command(url, settings)
    process = subprocess.Popen(
        command,
        stdin=subprocess.DEVNULL,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
        close_fds=True,
    )
    with _PROCESS_LOCK:
        _managed_chrome_process = process


def close_managed_chrome() -> None:
    """Force la fermeture du Chrome dédié lancé par le compagnon.

    Chrome 153 peut ignorer ``chrome.windows.remove`` depuis un service worker
    d'extension. Le compagnon conserve donc le processus qu'il a lui-même
    créé et ferme tout son arbre après la capture terminale.
    """
    global _managed_chrome_process
    with _PROCESS_LOCK:
        process = _managed_chrome_process
        _managed_chrome_process = None
    if process is None or process.poll() is not None:
        return
    if sys.platform == "win32":
        subprocess.run(
            ["taskkill", "/PID", str(process.pid), "/T", "/F"],
            stdin=subprocess.DEVNULL,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            check=False,
        )
    else:
        process.terminate()


def open_search_in_chrome(payload: CreateRequest, settings: Settings) -> None:
    open_url_in_chrome(build_search_url(payload), settings)
