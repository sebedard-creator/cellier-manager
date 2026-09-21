from __future__ import annotations

import json
import re
import unicodedata
from dataclasses import dataclass
from html import unescape
from urllib.parse import urlparse
from bs4 import BeautifulSoup

from .models import CaptureEnvelope, ProductIdentity


ALLOWED_DOMAINS = {
    "VIVINO": "vivino.com",
    "UNTAPPD": "untappd.com",
    "SAQ": "saq.com",
}

PARSER_VERSIONS = {
    "VIVINO": "vivino-3",
    "UNTAPPD": "untappd-4",
    "SAQ": "saq-4",
}


class ParseFailure(Exception):
    def __init__(self, code: str, message: str):
        super().__init__(message)
        self.code = code


def _host_matches(host: str, expected: str) -> bool:
    return host == expected or host.endswith("." + expected)


def validate_source_url(url: str, source: str) -> None:
    parsed = urlparse(url)
    if parsed.scheme != "https" or not parsed.hostname or parsed.username or parsed.password:
        raise ParseFailure("SOURCE_MISMATCH", "L'adresse de la source n'est pas permise.")
    if not _host_matches(parsed.hostname.lower(), ALLOWED_DOMAINS[source]):
        raise ParseFailure("SOURCE_MISMATCH", "Le domaine ne correspond pas à la source demandée.")


def _normal(value: str | None) -> str:
    # JSON-LD values can still contain HTML entities.  SAQ notably publishes
    # producers such as ``Vignoble de l&#039;Orpailleur Inc.``; decoding before
    # comparison lets that match the user's ``L'Orpailleur`` identity.
    decomposed = unicodedata.normalize("NFD", unescape(value or ""))
    plain = "".join(char for char in decomposed if unicodedata.category(char) != "Mn")
    return " ".join(re.findall(r"[a-z0-9]+", plain.lower()))


def _json_ld_products(blocks: list[object]) -> list[dict]:
    products: list[dict] = []
    seen: dict[tuple[str, str, str, str], dict] = {}
    queue = list(blocks)
    while queue:
        value = queue.pop(0)
        if isinstance(value, str):
            try:
                queue.append(json.loads(value))
            except (ValueError, TypeError):
                continue
        elif isinstance(value, list):
            queue.extend(value)
        elif isinstance(value, dict):
            graph = value.get("@graph")
            if isinstance(graph, list):
                queue.extend(graph)
            kind = value.get("@type")
            kinds = kind if isinstance(kind, list) else [kind]
            if "Product" in kinds:
                # Some storefronts repeat the same Product schema in several
                # JSON-LD blocks.  It is still one product when its stable
                # identity is identical; only distinct products are ambiguous.
                brand = value.get("brand")
                brand_name = brand.get("name") if isinstance(brand, dict) else brand
                identity = (
                    _normal(str(value.get("sku") or value.get("productID") or value.get("@id") or "")),
                    _normal(str(value.get("name") or "")),
                    _normal(str(brand_name or "")),
                    _normal(str(value.get("url") or "")),
                )
                existing = seen.get(identity)
                if existing is None:
                    seen[identity] = value
                    products.append(value)
                else:
                    # Duplicate schemas often contain complementary fields.
                    # Keep one logical product and fill in what the first block
                    # omitted (for example SAQ's manufacturer metadata).
                    for key, item in value.items():
                        if key not in existing or existing[key] in (None, "", [], {}):
                            existing[key] = item
    return products


def _text_value(soup: BeautifulSoup, labels: tuple[str, ...]) -> str | None:
    for text_node in soup.find_all(string=True):
        label = _normal(str(text_node))
        if not any(label == _normal(candidate) or label.startswith(_normal(candidate) + " ") for candidate in labels):
            continue
        parent = text_node.parent
        if parent is None:
            continue
        combined = " ".join(parent.stripped_strings)
        matched = max(
            (candidate for candidate in labels if label == _normal(candidate) or label.startswith(_normal(candidate) + " ")),
            key=len,
        )
        combined = re.sub(rf"^\s*{re.escape(matched)}(?:\s*:)?\s*", "", combined, flags=re.I)
        if combined.strip() and _normal(combined) != label:
            return combined.strip()[:300]
        sibling = parent.find_next_sibling()
        if sibling:
            candidate = " ".join(sibling.stripped_strings).strip()
            if candidate:
                return candidate[:300]
    return None


def _brand(product: dict) -> str | None:
    for key in ("brand", "manufacturer"):
        brand = product.get(key)
        if isinstance(brand, dict):
            value = unescape(str(brand.get("name") or "")).strip()
            if value:
                return value
        if isinstance(brand, str) and brand.strip():
            return unescape(brand).strip()
    return None


def _vivino_values(soup: BeautifulSoup) -> dict[str, str]:
    """Read Vivino's stable fact rows and breadcrumb attributes.

    Generic label scanning can wander into the footer ("Grapes", "Regions")
    or the winery card ("Winery in Quebec").  Vivino exposes exact product
    facts as table rows and exact identity parts through `data-cy` attributes.
    """
    values: dict[str, str] = {}
    for row in soup.select("tr[data-testid='wineFactRow']"):
        heading = row.find("th")
        fact = row.find("td")
        label = _normal(" ".join(heading.stripped_strings) if heading else "")
        value = " ".join(fact.stripped_strings).strip() if fact else ""
        if label and value:
            values[label] = unescape(value)

    breadcrumbs = {
        "country": "breadcrumb-country",
        "region": "breadcrumb-region",
        "producer": "breadcrumb-winery",
        "style": "breadcrumb-winetype",
    }
    for key, attribute in breadcrumbs.items():
        node = soup.select_one(f"[data-cy='{attribute}']")
        value = " ".join(node.stripped_strings).strip() if node else ""
        if value:
            # Breadcrumbs already separate country and region and therefore
            # are more precise than the combined fact value "Canada / Quebec".
            values[key] = unescape(value)

    values.setdefault("producer", values.get("winery", ""))
    region_path = values.get("region", "")
    if not values.get("country") and " / " in region_path:
        country, region = (part.strip() for part in region_path.split(" / ", 1))
        values["country"] = country
        values["region"] = region
    return values


def _untappd_values(soup: BeautifulSoup) -> dict[str, str]:
    selectors = {
        "producer": ".brewery",
        "style": ".style",
        "alcohol": ".abv",
        "ibu": ".ibu",
    }
    values: dict[str, str] = {}
    for key, selector in selectors.items():
        node = soup.select_one(selector)
        value = " ".join(node.stripped_strings).strip() if node else ""
        if value:
            values[key] = unescape(value)
    return values


def _identity_text_matches(observed: str | None, expected: str | None) -> bool:
    observed_normal = _normal(observed)
    expected_normal = _normal(expected)
    if not observed_normal or not expected_normal:
        return False
    if observed_normal == expected_normal:
        return True
    if len(expected_normal) >= 5 and expected_normal in observed_normal:
        return True
    # Vivino often prefixes the winery and reorders the wine name in its H1,
    # e.g. ``Bourgogne Pinot noir`` becomes
    # ``Athenaïs Pinot Noir Bourgogne``. Requiring every expected word keeps
    # the comparison strict while making word order irrelevant.
    expected_tokens = expected_normal.split()
    observed_tokens = set(observed_normal.split())
    if len(expected_tokens) >= 2 and all(token in observed_tokens for token in expected_tokens):
        return True
    # Producer spellings frequently differ only by spaces (Joy Hill/Joyhill).
    observed_compact = observed_normal.replace(" ", "")
    expected_compact = expected_normal.replace(" ", "")
    return len(expected_compact) >= 5 and expected_compact in observed_compact


def _repair_product_title(value: str) -> str:
    """Repair the degree sign lost by some Windows/Chrome captures."""
    return re.sub(r"(?<=\d)\ufffd(?=[NSEWnsew]\b)", "°", unescape(value)).strip()


def _normal_identity_name(value: str | None) -> str:
    """Normalize names without treating an embedded vintage as another word."""
    normal = _normal(_repair_product_title(value or ""))
    normal = re.sub(r"\b(\d+)\s+([nsew])\b", r"\1\2", normal)
    normal = re.sub(r"\b(?:18|19|20)\d{2}\b", " ", normal)
    return re.sub(r"\s+", " ", normal).strip()


def _identity_name_matches(observed: str | None, expected: str | None) -> bool:
    observed_normal = _normal_identity_name(observed)
    expected_normal = _normal_identity_name(expected)
    if not observed_normal or not expected_normal:
        return False
    if _identity_text_matches(observed_normal, expected_normal):
        return True

    # A local name can omit the second half of a coordinate printed by
    # Untappd, for example ``50N`` versus ``50°N - 4°E``. Permit one such
    # short numbered qualifier while keeping named variants distinct.
    expected_tokens = expected_normal.split()
    observed_tokens = observed_normal.split()
    expected_index = 0
    extras: list[str] = []
    for token in observed_tokens:
        if expected_index < len(expected_tokens) and token == expected_tokens[expected_index]:
            expected_index += 1
        else:
            extras.append(token)
    return (
        expected_index == len(expected_tokens)
        and len(extras) == 1
        and len(extras[0]) <= 3
        and any(char.isdigit() for char in extras[0])
    )


def _product_name_and_vintage(title: str) -> tuple[str | None, str | None]:
    repaired = _repair_product_title(title)
    vintage_match = re.search(r"\b(18|19|20)\d{2}\b", repaired)
    vintage = vintage_match.group(0) if vintage_match else None
    if not vintage:
        return repaired or None, None

    # Remove a normal vintage suffix, including ``(2024)``. Keep the year
    # when it belongs to a larger product qualifier such as
    # ``(Batch 7 - 2020)``; it is part of Untappd's canonical beer name.
    name = re.sub(rf"\s*[\(\[]\s*{vintage}\s*[\)\]]\s*$", "", repaired)
    if name == repaired:
        name = re.sub(rf"\s*(?:[-–—]\s*)?{vintage}\s*$", "", repaired)
    name = re.sub(r"\(\s*\)|\[\s*\]", "", name).strip(" -–—")
    return name or None, vintage


def _field(value: object, method: str, evidence: str | None = None) -> dict | None:
    if value is None or value == "" or value == []:
        return None
    return {"value": value, "method": method, "evidence": (evidence or str(value))[:300]}


def parse_capture(capture: CaptureEnvelope, expected: ProductIdentity) -> dict:
    validate_source_url(capture.page.url, capture.source)
    if capture.content.truncated:
        raise ParseFailure("CAPTURE_TOO_LARGE", "La capture est incomplète.")

    soup = BeautifulSoup(capture.content.productHtml, "html.parser")
    products = _json_ld_products(capture.content.jsonLdBlocks)
    if len(products) > 1:
        exact = [p for p in products if _normal(str(p.get("name") or "")) == _normal(expected.name)]
        if len(exact) != 1:
            raise ParseFailure("EXTRACTION_AMBIGUOUS", "Plusieurs produits sont présents dans la page.")
        product = exact[0]
    else:
        product = products[0] if products else {}

    heading = soup.find("h1")
    heading_text = heading.get_text(" ", strip=True) if heading else ""
    title = (heading_text if capture.source == "UNTAPPD" else "") or unescape(str(product.get("name") or "")).strip() or heading_text
    title = _repair_product_title(title)
    vivino = _vivino_values(soup) if capture.source == "VIVINO" else {}
    untappd = _untappd_values(soup) if capture.source == "UNTAPPD" else {}
    producer = _brand(product) or vivino.get("producer") or untappd.get("producer") or _text_value(soup, ("Producteur", "Winery", "Brewery", "Brasserie"))
    name, title_vintage = _product_name_and_vintage(title)
    vintage = title_vintage or _text_value(soup, ("Millésime", "Vintage", "Year"))
    source_labels = {
        "VIVINO": {
            "country": ("Pays", "Country"), "region": ("Région", "Region", "Wine region"),
            "style": ("Style", "Wine style"), "grapes": ("Cépages", "Grapes")
        },
        "UNTAPPD": {
            "country": ("Pays", "Country"), "region": ("Région", "Region"),
            "style": ("Style", "Type de bière", "Beer style"), "grapes": ("Cépages",)
        },
        "SAQ": {
            "country": ("Pays", "Country of origin"), "region": ("Région", "Region"),
            "style": ("Classification", "Appellation", "Type"),
            "grapes": ("Cépage", "Cépages", "Grape varieties")
        },
    }[capture.source]
    country = vivino.get("country") or _text_value(soup, source_labels["country"])
    region = vivino.get("region") or _text_value(soup, source_labels["region"])
    style = vivino.get("style") or untappd.get("style") or _text_value(soup, source_labels["style"])
    grapes_raw = vivino.get("grapes") or _text_value(soup, source_labels["grapes"])
    grapes = [
        re.sub(r"\s+\d+(?:[,.]\d+)?\s*%\s*$", "", part).strip()
        for part in re.split(r"[,;/]", grapes_raw or "")
        if part.strip()
    ] or None
    alcohol_raw = untappd.get("alcohol") or _text_value(soup, ("Alcool", "Alcohol", "ABV", "Degré d'alcool"))
    alcohol_match = re.search(r"\d+(?:[,.]\d+)?", alcohol_raw or "")
    alcohol = float(alcohol_match.group(0).replace(",", ".")) if alcohol_match else None
    if alcohol is not None and not 0 <= alcohol <= 100:
        alcohol = None
    ibu_raw = untappd.get("ibu") or _text_value(soup, ("IBU",))
    ibu_match = re.search(r"\d+", ibu_raw or "")
    ibu = int(ibu_match.group(0)) if ibu_match else None
    if ibu is not None and not 0 <= ibu <= 1000:
        ibu = None

    observed = {"producer": producer, "name": name, "vintage": vintage}
    producer_match = _identity_text_matches(producer, expected.producer)
    # The local name can contain a year even when its dedicated vintage field
    # is empty. Name comparison therefore always ignores vintage tokens.
    name_match = _identity_name_matches(name, expected.name)
    identity_assessment = "PLAUSIBLE" if producer_match and name_match else "INSUFFICIENT"
    if producer and name and not producer_match and not name_match:
        identity_assessment = "MISMATCH"
    if expected.vintage is None:
        vintage_assessment = "NOT_APPLICABLE"
    elif vintage is None:
        vintage_assessment = "NOT_OBSERVED"
    else:
        vintage_assessment = "MATCH" if vintage == expected.vintage else "MISMATCH"

    fields = {
        "producer": _field(producer, "JSON_LD" if _brand(product) else "LABELED_DOM"),
        "name": _field(name, "JSON_LD" if product.get("name") else "LABELED_DOM"),
        "vintage": _field(vintage, "LABELED_DOM"),
        "country": _field(country, "LABELED_DOM"),
        "region": _field(region, "LABELED_DOM"),
        "grapes": _field(grapes, "LABELED_DOM", grapes_raw),
        "style": _field(style, "LABELED_DOM"),
        "alcoholVolume": _field(alcohol, "LABELED_DOM", alcohol_raw),
        "ibu": _field(ibu, "LABELED_DOM", ibu_raw),
    }
    if not any(fields.values()):
        raise ParseFailure("EXTRACTION_EMPTY", "Aucun renseignement produit n'a été trouvé.")
    warnings = ["PARTIAL_EXTRACTION"] if any(value is None for value in fields.values()) else []
    return {
        "parserVersion": PARSER_VERSIONS[capture.source],
        "identityAssessment": identity_assessment,
        "vintageAssessment": vintage_assessment,
        "observedIdentity": observed,
        "fields": fields,
        "warnings": warnings,
    }
