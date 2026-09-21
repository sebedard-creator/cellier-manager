from __future__ import annotations

from threading import Thread
import uvicorn

from .app import create_app
from .config import Settings
from .certificates import discover_private_ip, ensure_certificate


def main() -> None:
    settings = Settings.load()
    app = create_app(settings)

    lan_ip = discover_private_ip()
    ensure_certificate(settings, lan_ip)
    loopback = uvicorn.Server(uvicorn.Config(
        app, host=settings.loopback_host, port=settings.loopback_port, log_level="info"
    ))
    thread = Thread(target=loopback.run, name="cellier-loopback", daemon=True)
    thread.start()
    uvicorn.run(
        app, host=settings.lan_host, port=settings.lan_port, log_level="info",
        ssl_certfile=str(settings.certificate_path), ssl_keyfile=str(settings.private_key_path)
    )


if __name__ == "__main__":
    main()
