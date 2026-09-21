from pathlib import Path
import uuid
from urllib.parse import parse_qs, urlparse

import pytest

from cellier_companion import browser
from cellier_companion.browser import build_chrome_command, build_search_url
from cellier_companion.config import Settings
from cellier_companion.models import CreateRequest


def request(source: str) -> CreateRequest:
    return CreateRequest.model_validate({
        "protocolVersion": 1,
        "requestId": str(uuid.uuid4()),
        "datasetId": str(uuid.uuid4()),
        "itemUuid": str(uuid.uuid4()),
        "source": source,
        "identityRevision": 0,
        "identity": {
            "type": "VIN" if source != "UNTAPPD" else "BIERE",
            "producer": "Domaine de l'Étoile",
            "name": "Cuvée Réserve",
            "vintage": "2020",
        },
        "createdAt": "2026-09-20T18:00:00Z",
    })


@pytest.mark.parametrize(("source", "host", "path"), [
    ("VIVINO", "www.vivino.com", "/search/wines"),
    ("UNTAPPD", "untappd.com", "/search"),
    ("SAQ", "www.saq.com", "/fr/catalogsearch/result/"),
])
def test_build_search_url_identifies_the_job(source: str, host: str, path: str):
    payload = request(source)
    parsed = urlparse(build_search_url(payload))
    assert parsed.scheme == "https"
    assert parsed.hostname == host
    assert parsed.path == path
    assert parse_qs(parsed.query)["q"] == ["Domaine de l'Étoile Cuvée Réserve 2020"]
    assert parsed.fragment == f"cellier-request={payload.requestId}"


def test_build_search_url_writes_vintage_only_once_when_name_already_contains_it():
    payload = request("UNTAPPD")
    payload.identity.producer = "The Referend Bier Blendery"
    payload.identity.name = "Metamorfosis (2018)"
    payload.identity.vintage = "2018"

    parsed = urlparse(build_search_url(payload))

    assert parse_qs(parsed.query)["q"] == ["The Referend Bier Blendery Metamorfosis 2018"]


def test_managed_chrome_stops_when_its_last_window_closes(tmp_path: Path):
    chrome = tmp_path / "chrome.exe"
    chrome.write_bytes(b"")
    extension = tmp_path / "extension"
    extension.mkdir()
    (extension / "manifest.json").write_text("{}", encoding="utf-8")
    settings = Settings(
        data_dir=tmp_path / "data",
        chrome_executable=chrome,
        chrome_extension_path=extension,
    )

    command = build_chrome_command("https://example.com", settings)

    assert "--disable-background-mode" in command
    assert f"--user-data-dir={settings.chrome_profile_path}" in command
    assert f"--load-extension={extension.resolve()}" in command


def test_companion_force_closes_the_chrome_process_tree(monkeypatch):
    class Process:
        pid = 4242

        @staticmethod
        def poll():
            return None

    calls: list[list[str]] = []
    monkeypatch.setattr(browser.sys, "platform", "win32")
    monkeypatch.setattr(
        browser.subprocess,
        "run",
        lambda command, **_: calls.append(command),
    )
    monkeypatch.setattr(browser, "_managed_chrome_process", Process())

    browser.close_managed_chrome()

    assert calls == [["taskkill", "/PID", "4242", "/T", "/F"]]
    assert browser._managed_chrome_process is None
