from pathlib import Path
from types import SimpleNamespace
import asyncio
import uuid

import httpx
from fastapi import FastAPI
from fastapi.testclient import TestClient

from cellier_companion.app import _is_loopback, create_app
from cellier_companion.config import Settings
from cellier_companion.parsers import ParseFailure, validate_source_url
from cellier_companion.security import new_secret
import pytest


def client(tmp_path: Path) -> TestClient:
    return TestClient(create_app(Settings(data_dir=tmp_path)))


def pair(api: TestClient, role: str) -> str:
    pairing = api.post("/local/v1/pairings", params={"role": role}).json()
    body = {
        "pairingId": pairing["pairingId"], "pairingSecret": pairing["pairingSecret"],
        "deviceId": str(uuid.uuid4()), "name": "Test",
        "extensionId": "abcdefghijklmnop" if role == "EXTENSION" else None,
    }
    route = "/extension/v1/pair" if role == "EXTENSION" else "/api/v1/pair"
    return api.post(route, json=body).json()["token"]


def test_request_is_idempotent_and_body_is_protected(tmp_path: Path):
    api = client(tmp_path)
    token = pair(api, "ANDROID")
    request_id = str(uuid.uuid4())
    body = {
        "protocolVersion": 1, "requestId": request_id, "datasetId": str(uuid.uuid4()),
        "itemUuid": str(uuid.uuid4()), "source": "VIVINO", "identityRevision": 0,
        "identity": {"type": "VIN", "producer": "Domaine Test", "name": "Cuvée Test", "vintage": "2020"},
        "createdAt": "2026-09-20T18:00:00Z",
    }
    headers = {"Authorization": f"Bearer {token}", "Idempotency-Key": str(uuid.uuid4())}
    first = api.post("/api/v1/requests", json=body, headers=headers)
    second = api.post("/api/v1/requests", json=body, headers=headers)
    assert first.status_code == 201
    assert second.status_code == 201
    changed = dict(body)
    changed["source"] = "SAQ"
    assert api.post("/api/v1/requests", json=changed, headers=headers).status_code == 409


def test_new_request_opens_chrome_once_after_it_is_accepted(tmp_path: Path):
    launched = []
    api = TestClient(create_app(Settings(data_dir=tmp_path), browser_launcher=launched.append))
    token = pair(api, "ANDROID")
    request_id = str(uuid.uuid4())
    body = {
        "protocolVersion": 1, "requestId": request_id, "datasetId": str(uuid.uuid4()),
        "itemUuid": str(uuid.uuid4()), "source": "SAQ", "identityRevision": 0,
        "identity": {"type": "VIN", "producer": "Domaine Test", "name": "Cuvée Test", "vintage": "2020"},
        "createdAt": "2026-09-20T18:00:00Z",
    }
    headers = {"Authorization": f"Bearer {token}", "Idempotency-Key": str(uuid.uuid4())}

    assert api.post("/api/v1/requests", json=body, headers=headers).status_code == 201
    assert api.post("/api/v1/requests", json=body, headers=headers).status_code == 201

    assert [payload.requestId for payload in launched] == [request_id]


def test_existing_request_can_reopen_chrome_idempotently(tmp_path: Path):
    launched = []
    api = TestClient(create_app(Settings(data_dir=tmp_path), browser_launcher=launched.append))
    token = pair(api, "ANDROID")
    request_id = str(uuid.uuid4())
    body = {
        "protocolVersion": 1, "requestId": request_id, "datasetId": str(uuid.uuid4()),
        "itemUuid": str(uuid.uuid4()), "source": "SAQ", "identityRevision": 0,
        "identity": {"type": "VIN", "producer": "Domaine Test", "name": "Cuvée Test", "vintage": "2020"},
        "createdAt": "2026-09-20T18:00:00Z",
    }
    auth = {"Authorization": f"Bearer {token}"}
    create_headers = {**auth, "Idempotency-Key": str(uuid.uuid4())}
    open_headers = {**auth, "Idempotency-Key": str(uuid.uuid4())}

    assert api.post("/api/v1/requests", json=body, headers=create_headers).status_code == 201
    first_open = api.post(
        f"/api/v1/requests/{request_id}/open",
        json={"requestId": request_id},
        headers=open_headers,
    )
    repeated_open = api.post(
        f"/api/v1/requests/{request_id}/open",
        json={"requestId": request_id},
        headers=open_headers,
    )

    assert first_open.status_code == 200
    assert repeated_open.status_code == 200
    assert [payload.requestId for payload in launched] == [request_id, request_id]


def test_capture_produces_reviewable_proposal(tmp_path: Path):
    api = client(tmp_path)
    android = pair(api, "ANDROID")
    extension = pair(api, "EXTENSION")
    request_id = str(uuid.uuid4())
    body = {
        "protocolVersion": 1, "requestId": request_id, "datasetId": str(uuid.uuid4()),
        "itemUuid": str(uuid.uuid4()), "source": "VIVINO", "identityRevision": 2,
        "identity": {"type": "VIN", "producer": "Domaine Test", "name": "Cuvée Test", "vintage": "2020"},
        "createdAt": "2026-09-20T18:00:00Z",
    }
    api.post("/api/v1/requests", json=body, headers={"Authorization": f"Bearer {android}", "Idempotency-Key": str(uuid.uuid4())})
    claim = api.post(
        f"/extension/v1/jobs/{request_id}/claim", json={"extensionVersion": "0.1.0"},
        headers={"Authorization": f"Bearer {extension}"},
    ).json()
    capture = {
        "protocolVersion": 1, "captureId": str(uuid.uuid4()), "requestId": request_id,
        "leaseToken": claim["leaseToken"], "source": "VIVINO", "extensionVersion": "0.1.0",
        "page": {"url": "https://www.vivino.com/en/test/w/12345678?year=2020#cellier-request=test",
                 "canonicalUrl": "https://www.vivino.com/en/test/w/12345678?year=2020",
                 "title": "Cuvée Test 2020", "capturedAt": "2026-09-20T18:03:00Z", "language": "fr"},
        "content": {"jsonLdBlocks": [{"@type": "Product", "name": "Cuvée Test 2020", "brand": {"name": "Domaine Test"}}],
                    "productHtml": "<main><h1>Cuvée Test 2020</h1><p>Alcool: 13,5 %</p></main>",
                    "visibleText": "Cuvée Test 2020 Alcool 13,5 %", "userSelectedText": None,
                    "captureStrategy": "MAIN_ELEMENT", "truncated": False},
    }
    response = api.post(
        f"/extension/v1/jobs/{request_id}/captures", json=capture,
        headers={"Authorization": f"Bearer {extension}"},
    )
    assert response.status_code == 201
    assert response.json()["identityAssessment"] == "PLAUSIBLE"
    proposal = api.get(f"/api/v1/requests/{request_id}/proposal", headers={"Authorization": f"Bearer {android}"})
    assert proposal.status_code == 200
    assert proposal.json()["itemUuid"] == body["itemUuid"]
    assert proposal.json()["sourceUrl"] == "https://www.vivino.com/en/test/w/12345678?year=2020"


def test_extension_progress_is_visible_to_android(tmp_path: Path):
    api = client(tmp_path)
    android = pair(api, "ANDROID")
    extension = pair(api, "EXTENSION")
    request_id = str(uuid.uuid4())
    body = {
        "protocolVersion": 1, "requestId": request_id, "datasetId": str(uuid.uuid4()),
        "itemUuid": str(uuid.uuid4()), "source": "UNTAPPD", "identityRevision": 0,
        "identity": {"type": "BIERE", "producer": "Brasserie Test", "name": "Bière Test", "vintage": "2024"},
        "createdAt": "2026-09-21T12:00:00Z",
    }
    android_headers = {"Authorization": f"Bearer {android}"}
    extension_headers = {"Authorization": f"Bearer {extension}"}
    api.post(
        "/api/v1/requests", json=body,
        headers={**android_headers, "Idempotency-Key": str(uuid.uuid4())},
    )

    api.post(
        f"/extension/v1/jobs/{request_id}/claim",
        json={"extensionVersion": "0.6.0"}, headers=extension_headers,
    )
    searching = api.get(f"/api/v1/requests/{request_id}", headers=android_headers)
    assert searching.json()["state"] == "SEARCHING"

    progress = api.post(
        f"/extension/v1/jobs/{request_id}/progress",
        json={"state": "CAPTURING"}, headers=extension_headers,
    )
    assert progress.status_code == 200
    status = api.get(f"/api/v1/requests/{request_id}", headers=android_headers)
    assert status.json()["state"] == "CAPTURING"


@pytest.mark.parametrize("url", [
    "http://vivino.com/w/123",
    "https://vivino.com.evil.example/w/123",
    "https://user:password@vivino.com/w/123",
])
def test_source_url_rejects_unsafe_or_lookalike_domains(url: str):
    with pytest.raises(ParseFailure) as error:
        validate_source_url(url, "VIVINO")
    assert error.value.code == "SOURCE_MISMATCH"


def test_wrong_source_domain_is_rejected():
    with pytest.raises(ParseFailure):
        validate_source_url("https://www.untappd.com/b/test/1", "VIVINO")


def from_lan(app: FastAPI, method: str, path: str, **kwargs) -> httpx.Response:
    """Rejoue une requête comme si elle venait d'un voisin sur le réseau local.

    ``TestClient`` s'annonce toujours comme ``testclient``, un hôte local
    autorisé, et ne peut donc pas couvrir les gardes ``/local/*``. ``main.py``
    sert la même application sur le loopback et sur le LAN, alors ces routes
    sont bel et bien joignables depuis le réseau.
    """
    async def run() -> httpx.Response:
        transport = httpx.ASGITransport(app=app, client=("192.168.1.66", 51234))
        async with httpx.AsyncClient(transport=transport, base_url="https://192.168.1.10:8766") as remote:
            return await remote.request(method, path, **kwargs)

    return asyncio.run(run())


@pytest.mark.parametrize("route", ["/local/v1/pairings", "/local/v1/pairing-bundle"])
def test_lan_client_cannot_obtain_a_pairing_secret(tmp_path: Path, route: str):
    app = create_app(Settings(data_dir=tmp_path))
    refused = from_lan(app, "POST", route, params={"role": "ANDROID"})
    assert refused.status_code == 403
    assert "pairingSecret" not in refused.text


def test_lan_client_cannot_forge_a_device_token(tmp_path: Path):
    app = create_app(Settings(data_dir=tmp_path))
    forged = from_lan(app, "POST", "/api/v1/pair", json={
        "pairingId": str(uuid.uuid4()), "pairingSecret": new_secret(),
        "deviceId": "attaquant-lan", "name": "Appareil pirate",
    })
    assert forged.status_code == 404
    assert "token" not in forged.json()


def test_lan_client_cannot_reach_the_local_home_page(tmp_path: Path):
    app = create_app(Settings(data_dir=tmp_path))
    assert from_lan(app, "GET", "/").status_code == 403


def test_loopback_pairing_still_works(tmp_path: Path):
    api = client(tmp_path)
    granted = api.post("/local/v1/pairings", params={"role": "ANDROID"})
    assert granted.status_code == 200
    assert granted.json()["pairingSecret"]


def test_client_of_unknown_origin_is_treated_as_remote():
    assert _is_loopback(SimpleNamespace(client=None)) is False
