from __future__ import annotations

import hashlib
import secrets
from datetime import datetime, timedelta, timezone


def new_secret() -> str:
    return secrets.token_urlsafe(32)


def secret_hash(secret: str) -> str:
    return hashlib.sha256(secret.encode("utf-8")).hexdigest()


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


def utc_after(minutes: int) -> str:
    return (datetime.now(timezone.utc) + timedelta(minutes=minutes)).isoformat().replace("+00:00", "Z")


def constant_time_hash_match(secret: str, expected_hash: str) -> bool:
    return secrets.compare_digest(secret_hash(secret), expected_hash)
