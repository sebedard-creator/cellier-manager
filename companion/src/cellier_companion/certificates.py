from __future__ import annotations

import base64
import hashlib
import ipaddress
import socket
from datetime import datetime, timedelta, timezone

from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.x509.oid import NameOID

from .config import Settings


def discover_private_ip() -> str:
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        sock.connect(("192.0.2.1", 9))
        candidate = sock.getsockname()[0]
    except OSError:
        candidate = "127.0.0.1"
    finally:
        sock.close()
    parsed = ipaddress.ip_address(candidate)
    return candidate if parsed.is_private else "127.0.0.1"


def ensure_certificate(settings: Settings, lan_ip: str) -> x509.Certificate:
    if settings.certificate_path.exists() and settings.private_key_path.exists():
        existing = x509.load_pem_x509_certificate(settings.certificate_path.read_bytes())
        names = existing.extensions.get_extension_for_class(x509.SubjectAlternativeName).value
        valid_ip = ipaddress.ip_address(lan_ip) in names.get_values_for_type(x509.IPAddress)
        still_valid = existing.not_valid_after_utc > datetime.now(timezone.utc) + timedelta(days=7)
        if valid_ip and still_valid:
            return existing

    key = ec.generate_private_key(ec.SECP256R1())
    subject = issuer = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, "Cellier Manager Companion")])
    now = datetime.now(timezone.utc)
    certificate = (
        x509.CertificateBuilder()
        .subject_name(subject)
        .issuer_name(issuer)
        .public_key(key.public_key())
        .serial_number(x509.random_serial_number())
        .not_valid_before(now - timedelta(minutes=5))
        .not_valid_after(now + timedelta(days=825))
        .add_extension(
            x509.SubjectAlternativeName([
                x509.IPAddress(ipaddress.ip_address(lan_ip)),
                x509.IPAddress(ipaddress.ip_address("127.0.0.1")),
                x509.DNSName("localhost"),
            ]),
            critical=False,
        )
        .sign(key, hashes.SHA256())
    )
    settings.data_dir.mkdir(parents=True, exist_ok=True)
    settings.private_key_path.write_bytes(key.private_bytes(
        serialization.Encoding.PEM,
        serialization.PrivateFormat.PKCS8,
        serialization.NoEncryption(),
    ))
    settings.certificate_path.write_bytes(certificate.public_bytes(serialization.Encoding.PEM))
    return certificate


def certificate_pairing_fields(certificate: x509.Certificate) -> dict[str, str]:
    der = certificate.public_bytes(serialization.Encoding.DER)
    return {
        "certificateDerBase64Url": base64.urlsafe_b64encode(der).decode("ascii").rstrip("="),
        "certificateSha256": hashlib.sha256(der).hexdigest(),
    }
