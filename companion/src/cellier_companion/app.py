from __future__ import annotations

import hashlib
import json
import logging
import secrets
import sqlite3
import uuid
from datetime import datetime, timedelta, timezone
from threading import Lock, Thread
from time import sleep
from typing import Callable, Literal

from fastapi import BackgroundTasks, Depends, FastAPI, Header, HTTPException, Request, Response
from fastapi.responses import HTMLResponse, JSONResponse
from fastapi.middleware.cors import CORSMiddleware

from . import __version__
from .browser import close_managed_chrome, open_search_in_chrome
from .config import Settings
from .certificates import certificate_pairing_fields, discover_private_ip, ensure_certificate
from .models import AckRequest, CaptureEnvelope, ClaimRequest, CreateRequest, JobProgress, OpenRequest, PairRequest, ProductIdentity
from .parsers import ParseFailure, parse_capture, validate_source_url
from .security import constant_time_hash_match, new_secret, secret_hash, utc_after, utc_now
from .storage import Database


TERMINAL_STATES = {"ACKED", "CANCELLED", "EXPIRED"}
LOOPBACK_HOSTS = {"127.0.0.1", "::1", "testclient"}
LOGGER = logging.getLogger(__name__)


def _error(status: int, code: str, message: str, retryable: bool = False, request_id: str | None = None):
    return JSONResponse(
        status_code=status,
        content={"error": {"code": code, "message": message, "retryable": retryable, "requestId": request_id}},
    )


def _canonical_hash(value: object) -> str:
    encoded = json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
    return hashlib.sha256(encoded).hexdigest()


def _is_loopback(request: Request) -> bool:
    """Vrai seulement pour un client local.

    ``main.py`` sert la même application sur le loopback et sur le LAN, donc
    chaque route ``/local/*`` doit se garder elle-même. Un client inconnu est
    traité comme distant.
    """
    return bool(request.client) and request.client.host in LOOPBACK_HOSTS


def _future(minutes: int) -> str:
    return (datetime.now(timezone.utc) + timedelta(minutes=minutes)).isoformat().replace("+00:00", "Z")


def create_app(
    settings: Settings | None = None,
    browser_launcher: Callable[[CreateRequest], None] | None = None,
) -> FastAPI:
    selected = settings or Settings.load()
    selected.data_dir.mkdir(parents=True, exist_ok=True)
    database = Database(selected.database_path)
    database.cleanup()
    app = FastAPI(title="Cellier Manager Companion", version=__version__)
    app.add_middleware(
        CORSMiddleware,
        allow_origin_regex=r"^chrome-extension://[a-z]{32}$",
        allow_credentials=False,
        allow_methods=["GET", "POST", "OPTIONS"],
        allow_headers=["Authorization", "Content-Type", "Idempotency-Key"],
    )
    app.state.settings = selected
    app.state.database = database
    watchdog_lock = Lock()
    watchdog_tokens: dict[str, str] = {}

    def fail_stalled_search(request_id: str, token: str) -> None:
        sleep(45)
        with watchdog_lock:
            if watchdog_tokens.get(request_id) != token:
                return
            watchdog_tokens.pop(request_id, None)
        should_close = False
        with database.transaction() as connection:
            row = connection.execute(
                "SELECT state FROM jobs WHERE request_id=?", (request_id,),
            ).fetchone()
            if row and row["state"] in {
                "QUEUED", "CLAIMED", "SEARCHING", "NAVIGATING", "CAPTURING", "PARSING",
            }:
                connection.execute(
                    "UPDATE jobs SET state='FAILED',updated_at=?,server_revision=server_revision+1 "
                    "WHERE request_id=?",
                    (utc_now(), request_id),
                )
                should_close = True
        if should_close:
            close_managed_chrome()

    def start_search_watchdog(request_id: str) -> None:
        token = str(uuid.uuid4())
        with watchdog_lock:
            watchdog_tokens[request_id] = token
        Thread(
            target=fail_stalled_search,
            args=(request_id, token),
            name=f"cellier-search-watchdog-{request_id[:8]}",
            daemon=True,
        ).start()

    def launch_search_safely(payload: CreateRequest) -> None:
        if not selected.auto_open_searches and browser_launcher is None:
            return
        try:
            if browser_launcher is not None:
                browser_launcher(payload)
            else:
                open_search_in_chrome(payload, selected)
                start_search_watchdog(payload.requestId)
        except Exception:
            LOGGER.exception("Impossible d’ouvrir Chrome pour la recherche %s", payload.requestId)
            with database.connect() as connection:
                connection.execute(
                    "UPDATE jobs SET state='FAILED',updated_at=?,server_revision=server_revision+1 "
                    "WHERE request_id=? AND state NOT IN ('READY','ACKED','CANCELLED','EXPIRED')",
                    (utc_now(), payload.requestId),
                )

    @app.exception_handler(ParseFailure)
    async def parse_failure_handler(_: Request, exc: ParseFailure):
        return _error(422, exc.code, str(exc), False)

    def authenticated_device(
        authorization: str | None = Header(default=None),
        expected_role: Literal["ANDROID", "EXTENSION"] | None = None,
    ) -> dict:
        if not authorization or not authorization.startswith("Bearer "):
            raise HTTPException(status_code=401, detail="UNAUTHORIZED")
        token = authorization[7:]
        token_digest = secret_hash(token)
        with database.connect() as connection:
            row = connection.execute(
                "SELECT * FROM devices WHERE token_hash = ? AND revoked_at IS NULL", (token_digest,)
            ).fetchone()
            if row is None:
                raise HTTPException(status_code=401, detail="UNAUTHORIZED")
            if expected_role and row["role"] != expected_role:
                raise HTTPException(status_code=403, detail="WRONG_ROLE")
            connection.execute("UPDATE devices SET last_seen_at = ? WHERE device_id = ?", (utc_now(), row["device_id"]))
            return dict(row)

    def android_device(authorization: str | None = Header(default=None)) -> dict:
        return authenticated_device(authorization, "ANDROID")

    def extension_device(authorization: str | None = Header(default=None)) -> dict:
        return authenticated_device(authorization, "EXTENSION")

    @app.get("/api/v1/health")
    def health():
        return {"protocolVersion": 1, "serviceVersion": __version__, "status": "ok"}

    @app.get("/", response_class=HTMLResponse)
    def local_home(request: Request):
        if not _is_loopback(request):
            return HTMLResponse("Interface locale seulement", status_code=403)
        return """<!doctype html><html lang='fr'><meta charset='utf-8'><title>Cellier Manager</title>
        <style>body{font:16px system-ui;max-width:760px;margin:3rem auto;padding:0 1rem;background:#171214;color:#f5edef}
        button{padding:.7rem 1rem;background:#8b3548;color:white;border:0;border-radius:.5rem}pre{white-space:pre-wrap;background:#2a2023;padding:1rem}</style>
        <h1>Compagnon Cellier Manager</h1><p>Le service local fonctionne.</p>
        <button onclick="pair('ANDROID')">Créer un fichier d’appairage Android</button>
        <button onclick="pair('EXTENSION')">Créer un code d’appairage extension</button><pre id='result'></pre>
        <script>async function pair(role){const r=await fetch('/local/v1/pairing-bundle?role='+role,{method:'POST'});
        const x=await r.json();document.querySelector('#result').textContent=JSON.stringify(x,null,2);
        if(role==='ANDROID'){const b=new Blob([JSON.stringify(x,null,2)],{type:'application/json'});const a=document.createElement('a');
        a.href=URL.createObjectURL(b);a.download='cellier-pairing.json';a.click();URL.revokeObjectURL(a.href);}}</script></html>"""

    def new_pairing(role: Literal["ANDROID", "EXTENSION"]) -> dict:
        pairing_id = str(uuid.uuid4())
        secret = new_secret()
        expires = utc_after(5)
        with database.connect() as connection:
            connection.execute(
                "INSERT INTO pairing_sessions(pairing_id, secret_hash, role, expires_at) VALUES (?,?,?,?)",
                (pairing_id, secret_hash(secret), role, expires),
            )
        return {"pairingId": pairing_id, "pairingSecret": secret, "role": role, "expiresAt": expires}

    @app.post("/local/v1/pairings")
    def create_pairing(request: Request, role: Literal["ANDROID", "EXTENSION"]):
        if not _is_loopback(request):
            raise HTTPException(status_code=403, detail="LOOPBACK_ONLY")
        return new_pairing(role)

    @app.post("/local/v1/pairing-bundle")
    def create_pairing_bundle(request: Request, role: Literal["ANDROID", "EXTENSION"]):
        if not _is_loopback(request):
            raise HTTPException(status_code=403, detail="LOOPBACK_ONLY")
        base = new_pairing(role)
        if role == "EXTENSION":
            return base
        lan_ip = discover_private_ip()
        certificate = ensure_certificate(selected, lan_ip)
        return {
            "format": "cellier-pairing", "version": 1, "role": role,
            "baseUrl": f"https://{lan_ip}:{selected.lan_port}",
            "serverName": "Mon ordinateur", **certificate_pairing_fields(certificate), **base,
        }

    def consume_pairing(payload: PairRequest, expected_role: str) -> dict:
        token = new_secret()
        now = utc_now()
        with database.transaction() as connection:
            row = connection.execute(
                "SELECT * FROM pairing_sessions WHERE pairing_id = ?", (payload.pairingId,)
            ).fetchone()
            if row is None or row["role"] != expected_role:
                raise HTTPException(status_code=404, detail="PAIRING_NOT_FOUND")
            if row["consumed_at"] is not None:
                raise HTTPException(status_code=409, detail="PAIRING_USED")
            if row["expires_at"] <= now:
                raise HTTPException(status_code=410, detail="PAIRING_EXPIRED")
            if not constant_time_hash_match(payload.pairingSecret, row["secret_hash"]):
                raise HTTPException(status_code=401, detail="UNAUTHORIZED")
            connection.execute(
                "INSERT OR REPLACE INTO devices(device_id, role, name, token_hash, extension_id, created_at, revoked_at, last_seen_at) VALUES (?,?,?,?,?,?,NULL,?)",
                (payload.deviceId, expected_role, payload.name, secret_hash(token), payload.extensionId, now, now),
            )
            connection.execute("UPDATE pairing_sessions SET consumed_at = ? WHERE pairing_id = ?", (now, payload.pairingId))
        return {"protocolVersion": 1, "deviceId": payload.deviceId, "token": token}

    @app.post("/api/v1/pair")
    def pair_android(payload: PairRequest):
        return consume_pairing(payload, "ANDROID")

    @app.post("/extension/v1/pair")
    def pair_extension(payload: PairRequest):
        if not payload.extensionId:
            raise HTTPException(status_code=422, detail="EXTENSION_ID_REQUIRED")
        return consume_pairing(payload, "EXTENSION")

    def idempotent_mutation(device_id: str, route: str, key: str | None, body: object, action):
        if not key:
            return _error(400, "IDEMPOTENCY_KEY_REQUIRED", "Une clé d'idempotence est requise.")
        body_hash = _canonical_hash(body)
        with database.transaction() as connection:
            old = connection.execute(
                "SELECT * FROM idempotency_records WHERE device_id=? AND route=? AND idempotency_key=?",
                (device_id, route, key),
            ).fetchone()
            if old:
                if old["body_hash"] != body_hash:
                    return _error(409, "IDEMPOTENCY_CONFLICT", "Cette clé a déjà servi avec un contenu différent.")
                return JSONResponse(status_code=old["status_code"], content=json.loads(old["response_json"]))
            status, result = action(connection)
            connection.execute(
                "INSERT INTO idempotency_records VALUES (?,?,?,?,?,?,?)",
                (device_id, route, key, body_hash, status, json.dumps(result, ensure_ascii=False), utc_now()),
            )
            return JSONResponse(status_code=status, content=result)

    @app.post("/api/v1/requests")
    def create_request(
        payload: CreateRequest,
        background_tasks: BackgroundTasks,
        device: dict = Depends(android_device),
        idempotency_key: str | None = Header(default=None, alias="Idempotency-Key"),
    ):
        def action(connection: sqlite3.Connection):
            cancelled = connection.execute(
                "SELECT 1 FROM cancellation_tombstones WHERE owner_device_id=? AND request_id=?",
                (device["device_id"], payload.requestId),
            ).fetchone()
            if cancelled:
                return 200, {"protocolVersion": 1, "requestId": payload.requestId, "state": "CANCELLED", "serverRevision": 1}
            existing = connection.execute("SELECT * FROM jobs WHERE request_id=?", (payload.requestId,)).fetchone()
            if existing:
                if existing["owner_device_id"] != device["device_id"]:
                    return 409, {"error": {"code": "IDEMPOTENCY_CONFLICT", "message": "Identifiant déjà utilisé.", "retryable": False, "requestId": payload.requestId}}
                return 200, {"protocolVersion": 1, "requestId": payload.requestId, "state": existing["state"], "serverRevision": existing["server_revision"]}
            now = utc_now()
            connection.execute(
                """INSERT INTO jobs(request_id,owner_device_id,dataset_id,item_uuid,source,identity_revision,
                identity_json,state,created_at,updated_at) VALUES (?,?,?,?,?,?,?,'QUEUED',?,?)""",
                (payload.requestId, device["device_id"], payload.datasetId, payload.itemUuid, payload.source,
                 payload.identityRevision, payload.identity.model_dump_json(), now, now),
            )
            if selected.auto_open_searches or browser_launcher is not None:
                background_tasks.add_task(launch_search_safely, payload)
            return 201, {"protocolVersion": 1, "requestId": payload.requestId, "state": "QUEUED", "serverRevision": 1}

        return idempotent_mutation(device["device_id"], "/api/v1/requests", idempotency_key, payload.model_dump(), action)

    @app.get("/api/v1/requests/{request_id}")
    def request_status(request_id: str, device: dict = Depends(android_device)):
        with database.connect() as connection:
            row = connection.execute(
                "SELECT request_id,state,server_revision,updated_at,resolution FROM jobs WHERE request_id=? AND owner_device_id=?",
                (request_id, device["device_id"]),
            ).fetchone()
        if not row:
            return _error(404, "JOB_NOT_FOUND", "Recherche introuvable.", request_id=request_id)
        return {"protocolVersion": 1, "requestId": row["request_id"], "state": row["state"],
                "serverRevision": row["server_revision"], "updatedAt": row["updated_at"], "resolution": row["resolution"]}

    @app.post("/api/v1/requests/{request_id}/open")
    def open_request(
        request_id: str,
        payload: OpenRequest,
        background_tasks: BackgroundTasks,
        device: dict = Depends(android_device),
        idempotency_key: str | None = Header(default=None, alias="Idempotency-Key"),
    ):
        if payload.requestId != request_id:
            return _error(422, "VALIDATION_ERROR", "Les identifiants de recherche ne correspondent pas.")

        def action(connection: sqlite3.Connection):
            row = connection.execute(
                "SELECT * FROM jobs WHERE request_id=? AND owner_device_id=?",
                (request_id, device["device_id"]),
            ).fetchone()
            if not row:
                return 404, {"error": {"code": "JOB_NOT_FOUND", "message": "Recherche introuvable.",
                                        "retryable": False, "requestId": request_id}}
            if row["state"] in TERMINAL_STATES:
                return 409, {"error": {"code": "JOB_CLOSED", "message": "Cette recherche est terminée.",
                                        "retryable": False, "requestId": request_id}}
            launch_payload = CreateRequest(
                protocolVersion=1,
                requestId=row["request_id"],
                datasetId=row["dataset_id"],
                itemUuid=row["item_uuid"],
                source=row["source"],
                identityRevision=row["identity_revision"],
                identity=ProductIdentity.model_validate_json(row["identity_json"]),
                createdAt=row["created_at"],
            )
            if selected.auto_open_searches or browser_launcher is not None:
                background_tasks.add_task(launch_search_safely, launch_payload)
            return 200, {"protocolVersion": 1, "requestId": request_id, "state": row["state"],
                         "serverRevision": row["server_revision"]}

        return idempotent_mutation(
            device["device_id"], f"/api/v1/requests/{request_id}/open",
            idempotency_key, payload.model_dump(), action,
        )

    @app.get("/api/v1/requests/{request_id}/proposal")
    def get_proposal(request_id: str, device: dict = Depends(android_device)):
        with database.connect() as connection:
            row = connection.execute(
                """SELECT p.payload_json FROM proposals p JOIN jobs j ON j.request_id=p.request_id
                   WHERE p.request_id=? AND j.owner_device_id=?""", (request_id, device["device_id"]),
            ).fetchone()
        if not row:
            return Response(status_code=204)
        return JSONResponse(content=json.loads(row["payload_json"]))

    @app.post("/api/v1/requests/{request_id}/cancel")
    def cancel_request(
        request_id: str,
        device: dict = Depends(android_device),
        idempotency_key: str | None = Header(default=None, alias="Idempotency-Key"),
    ):
        def action(connection: sqlite3.Connection):
            now = utc_now()
            connection.execute(
                "INSERT OR IGNORE INTO cancellation_tombstones VALUES (?,?,?)",
                (device["device_id"], request_id, now),
            )
            connection.execute(
                "UPDATE jobs SET state='CANCELLED',server_revision=server_revision+1,updated_at=? WHERE request_id=? AND owner_device_id=? AND state NOT IN ('ACKED','EXPIRED')",
                (now, request_id, device["device_id"]),
            )
            return 200, {"protocolVersion": 1, "requestId": request_id, "state": "CANCELLED"}
        return idempotent_mutation(device["device_id"], f"/api/v1/requests/{request_id}/cancel", idempotency_key, {"requestId": request_id}, action)

    @app.post("/api/v1/proposals/{proposal_id}/ack")
    def acknowledge(proposal_id: str, payload: AckRequest, device: dict = Depends(android_device)):
        now = utc_now()
        with database.transaction() as connection:
            row = connection.execute(
                """SELECT p.*,j.owner_device_id,j.state FROM proposals p JOIN jobs j ON j.request_id=p.request_id
                WHERE p.proposal_id=?""", (proposal_id,),
            ).fetchone()
            if not row or row["owner_device_id"] != device["device_id"]:
                return _error(404, "JOB_NOT_FOUND", "Proposition introuvable.")
            if row["resolution"] == payload.resolution:
                return {"protocolVersion": 1, "proposalId": proposal_id, "resolution": payload.resolution}
            if payload.resolution == "RECEIVED":
                connection.execute("UPDATE proposals SET received_at=COALESCE(received_at,?) WHERE proposal_id=?", (now, proposal_id))
            else:
                connection.execute("UPDATE proposals SET resolved_at=?,resolution=? WHERE proposal_id=?", (now, payload.resolution, proposal_id))
                connection.execute("UPDATE jobs SET state='ACKED',resolution=?,updated_at=?,server_revision=server_revision+1 WHERE request_id=?", (payload.resolution, now, row["request_id"]))
        return {"protocolVersion": 1, "proposalId": proposal_id, "resolution": payload.resolution}

    @app.post("/extension/v1/heartbeat")
    def heartbeat(device: dict = Depends(extension_device)):
        return {"protocolVersion": 1, "extensionId": device["extension_id"], "seenAt": utc_now()}

    @app.get("/extension/v1/jobs")
    def extension_jobs(device: dict = Depends(extension_device), limit: int = 50):
        limit = max(1, min(limit, 50))
        with database.connect() as connection:
            rows = connection.execute(
                "SELECT request_id,source,identity_json,state,created_at FROM jobs "
                "WHERE state IN ('QUEUED','CLAIMED','SEARCHING','NAVIGATING','CAPTURING','NEEDS_USER','FAILED') "
                "ORDER BY created_at LIMIT ?",
                (limit,),
            ).fetchall()
        return {"protocolVersion": 1, "jobs": [
            {"requestId": row["request_id"], "source": row["source"], "identity": json.loads(row["identity_json"]),
             "state": row["state"], "createdAt": row["created_at"]} for row in rows
        ]}

    @app.post("/extension/v1/jobs/{request_id}/claim")
    def claim_job(request_id: str, _: ClaimRequest, device: dict = Depends(extension_device)):
        token = new_secret()
        now = utc_now()
        expires = _future(15)
        with database.transaction() as connection:
            row = connection.execute("SELECT * FROM jobs WHERE request_id=?", (request_id,)).fetchone()
            if not row:
                return _error(404, "JOB_NOT_FOUND", "Recherche introuvable.")
            if row["state"] in TERMINAL_STATES or row["state"] == "READY":
                return _error(409, "LEASE_CONFLICT", "Cette recherche n'accepte plus de capture.")
            if row["lease_expires_at"] and row["lease_expires_at"] > now and row["lease_owner"] != device["device_id"]:
                return _error(409, "LEASE_CONFLICT", "Cette recherche est déjà ouverte dans une autre extension.")
            connection.execute(
                "UPDATE jobs SET state='SEARCHING',lease_owner=?,lease_token_hash=?,lease_expires_at=?,updated_at=?,server_revision=server_revision+1 WHERE request_id=?",
                (device["device_id"], secret_hash(token), expires, now, request_id),
            )
        return {"protocolVersion": 1, "requestId": request_id, "leaseToken": token, "leaseExpiresAt": expires}

    @app.post("/extension/v1/jobs/{request_id}/progress")
    def update_job_progress(
        request_id: str,
        payload: JobProgress,
        background_tasks: BackgroundTasks,
        device: dict = Depends(extension_device),
    ):
        now = utc_now()
        with database.transaction() as connection:
            row = connection.execute("SELECT * FROM jobs WHERE request_id=?", (request_id,)).fetchone()
            if not row:
                return _error(404, "JOB_NOT_FOUND", "Recherche introuvable.")
            if row["state"] in TERMINAL_STATES or row["state"] == "READY":
                return _error(409, "JOB_CLOSED", "Cette recherche est terminée.")
            if row["lease_owner"] != device["device_id"]:
                return _error(409, "LEASE_CONFLICT", "Cette extension ne possède pas cette recherche.")
            connection.execute(
                "UPDATE jobs SET state=?,updated_at=?,server_revision=server_revision+1 WHERE request_id=?",
                (payload.state, now, request_id),
            )
        if payload.state in {"FAILED", "NEEDS_USER"}:
            background_tasks.add_task(close_managed_chrome)
        return {"protocolVersion": 1, "requestId": request_id, "state": payload.state}

    @app.post("/extension/v1/jobs/{request_id}/captures")
    def submit_capture(
        request_id: str,
        payload: CaptureEnvelope,
        background_tasks: BackgroundTasks,
        device: dict = Depends(extension_device),
    ):
        if request_id != payload.requestId:
            return _error(422, "VALIDATION_ERROR", "Les identifiants de capture ne correspondent pas.")
        raw = payload.model_dump(mode="json")
        encoded = json.dumps(raw, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        if len(encoded) > selected.capture_max_bytes:
            return _error(413, "CAPTURE_TOO_LARGE", "La capture dépasse la taille permise.")
        content_hash = hashlib.sha256(encoded).hexdigest()
        now = utc_now()
        with database.transaction() as connection:
            job = connection.execute("SELECT * FROM jobs WHERE request_id=?", (request_id,)).fetchone()
            if not job:
                return _error(404, "JOB_NOT_FOUND", "Recherche introuvable.")
            if job["state"] == "READY":
                return _error(409, "LEASE_CONFLICT", "Une proposition existe déjà.")
            if job["lease_owner"] != device["device_id"] or not job["lease_token_hash"] or not constant_time_hash_match(payload.leaseToken, job["lease_token_hash"]):
                return _error(409, "LEASE_CONFLICT", "Le bail ne correspond pas à cette extension.")
            if not job["lease_expires_at"] or job["lease_expires_at"] <= now:
                return _error(409, "LEASE_EXPIRED", "Le bail de capture a expiré.")
            prior = connection.execute("SELECT * FROM captures WHERE capture_id=?", (payload.captureId,)).fetchone()
            if prior:
                if prior["content_hash"] != content_hash:
                    return _error(409, "IDEMPOTENCY_CONFLICT", "Cet identifiant de capture a déjà un autre contenu.")
                proposal = connection.execute("SELECT payload_json FROM proposals WHERE capture_id=?", (payload.captureId,)).fetchone()
                if proposal:
                    background_tasks.add_task(close_managed_chrome)
                return JSONResponse(status_code=200 if proposal else 202, content=json.loads(proposal["payload_json"]) if proposal else {"captureId": payload.captureId, "state": prior["state"]})
            connection.execute(
                "INSERT INTO captures(capture_id,request_id,content_hash,envelope_json,state,created_at) VALUES (?,?,?,?,?,?)",
                (payload.captureId, request_id, content_hash, json.dumps(raw, ensure_ascii=False), "PARSING", now),
            )
            connection.execute(
                "UPDATE jobs SET state='PARSING',updated_at=?,server_revision=server_revision+1 WHERE request_id=?",
                (now, request_id),
            )

        try:
            expected = json.loads(job["identity_json"])
            extraction = parse_capture(payload, ProductIdentity(**expected))
            source_url = payload.page.url
            if payload.page.canonicalUrl:
                try:
                    validate_source_url(payload.page.canonicalUrl, payload.source)
                    source_url = payload.page.canonicalUrl
                except ParseFailure:
                    pass
            proposal_id = str(uuid.uuid4())
            proposal = {
                "protocolVersion": 1, "proposalId": proposal_id, "requestId": request_id,
                "datasetId": job["dataset_id"], "itemUuid": job["item_uuid"],
                "identityRevision": job["identity_revision"], "source": job["source"],
                "sourceUrl": source_url, "capturedAt": payload.page.capturedAt,
                **extraction,
            }
            proposal_json = json.dumps(proposal, ensure_ascii=False, sort_keys=True)
            with database.transaction() as connection:
                connection.execute(
                    "INSERT INTO proposals VALUES (?,?,?,?,?,?,NULL,NULL,NULL)",
                    (proposal_id, payload.captureId, request_id, proposal_json, hashlib.sha256(proposal_json.encode()).hexdigest(), now),
                )
                connection.execute("UPDATE captures SET state='READY',parser_version=? WHERE capture_id=?", (extraction["parserVersion"], payload.captureId))
                connection.execute("UPDATE jobs SET state='READY',updated_at=?,server_revision=server_revision+1 WHERE request_id=?", (utc_now(), request_id))
            background_tasks.add_task(close_managed_chrome)
            return JSONResponse(status_code=201, content=proposal)
        except ParseFailure as exc:
            with database.connect() as connection:
                connection.execute("UPDATE captures SET state='ERROR',error_code=?,error_message=? WHERE capture_id=?", (exc.code, str(exc), payload.captureId))
                connection.execute("UPDATE jobs SET state='NEEDS_USER',updated_at=?,server_revision=server_revision+1 WHERE request_id=?", (utc_now(), request_id))
            background_tasks.add_task(close_managed_chrome)
            return _error(422, exc.code, str(exc), request_id=request_id)

    return app


app = create_app()
