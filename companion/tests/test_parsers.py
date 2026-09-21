from pathlib import Path
import uuid

import pytest

from cellier_companion.models import CaptureEnvelope, ProductIdentity
from cellier_companion.parsers import ParseFailure, parse_capture


FIXTURES = Path(__file__).parent / "fixtures"


def capture(source: str, url: str, fixture: str) -> CaptureEnvelope:
    return CaptureEnvelope.model_validate({
        "protocolVersion": 1, "captureId": str(uuid.uuid4()), "requestId": str(uuid.uuid4()),
        "leaseToken": "lease", "source": source, "extensionVersion": "0.1.0",
        "page": {"url": url, "canonicalUrl": None, "title": "Produit", "capturedAt": "2026-09-20T18:03:00Z", "language": "fr"},
        "content": {"jsonLdBlocks": [], "productHtml": (FIXTURES / fixture).read_text(encoding="utf-8"),
                    "visibleText": "produit", "userSelectedText": None, "captureStrategy": "MAIN_ELEMENT", "truncated": False},
    })


@pytest.mark.parametrize(("source", "url", "fixture", "identity", "field", "value"), [
    ("VIVINO", "https://www.vivino.com/w/123", "vivino_product.html", ProductIdentity(type="VIN", producer="Domaine Test", name="Cuvée Test", vintage="2020"), "country", "France"),
    ("UNTAPPD", "https://untappd.com/b/test/1", "untappd_product.html", ProductIdentity(type="BIERE", producer="Brasserie Test", name="IPA Test"), "ibu", 55),
    ("SAQ", "https://www.saq.com/fr/123", "saq_product.html", ProductIdentity(type="VIN", producer="Domaine Test", name="Cuvée Test", vintage="2020"), "region", "Bordeaux"),
])
def test_source_fixtures(source, url, fixture, identity, field, value):
    result = parse_capture(capture(source, url, fixture), identity)
    expected_version = {
        "SAQ": "saq-4", "VIVINO": "vivino-3", "UNTAPPD": "untappd-4",
    }[source]
    assert result["parserVersion"] == expected_version
    assert result["identityAssessment"] == "PLAUSIBLE"
    assert result["fields"][field]["value"] == value


def test_truncated_capture_is_never_a_success():
    item = capture("VIVINO", "https://vivino.com/w/1", "vivino_product.html")
    item.content.truncated = True
    with pytest.raises(ParseFailure) as error:
        parse_capture(item, ProductIdentity(type="VIN", producer="Domaine Test", name="Cuvée Test", vintage="2020"))
    assert error.value.code == "CAPTURE_TOO_LARGE"


def test_duplicate_json_ld_for_same_product_is_not_ambiguous():
    item = capture("SAQ", "https://www.saq.com/fr/15525715", "saq_product.html")
    product = {
        "@type": "Product",
        "name": "Maison Agricole Joy Hill Raisin Brin",
        "sku": "15525715",
    }
    item.content.jsonLdBlocks = [
        product,
        {**product, "manufacturer": {"@type": "Organization", "name": "Maison Agricole Joyhill"}},
    ]

    result = parse_capture(
        item,
        ProductIdentity(type="VIN", producer="Joy Hill", name="Raisin Brin", vintage="2025"),
    )

    assert result["fields"]["name"]["value"] == "Maison Agricole Joy Hill Raisin Brin"
    assert result["fields"]["producer"]["value"] == "Maison Agricole Joyhill"
    assert result["identityAssessment"] == "PLAUSIBLE"


def test_plural_label_is_not_mistaken_for_singular_prefix():
    item = capture("SAQ", "https://www.saq.com/fr/15525715", "saq_product.html")
    item.content.productHtml = """
      <main><h1>Raisin Brin</h1><ul>
        <li><span>Cépages</span><span>Gamay 67 %, Blaufränkisch 21 %, Chardonnay 12 %</span></li>
        <li><span>Classification</span><span>Vin du Québec certifié</span></li>
      </ul></main>
    """

    result = parse_capture(
        item,
        ProductIdentity(type="VIN", producer="Domaine Test", name="Raisin Brin"),
    )

    assert result["fields"]["grapes"]["value"] == [
        "Gamay", "Blaufränkisch", "Chardonnay",
    ]
    assert result["fields"]["style"]["value"] == "Vin du Québec certifié"


def test_saq_html_entity_in_producer_matches_local_identity():
    item = capture("SAQ", "https://www.saq.com/fr/12685490", "saq_product.html")
    item.content.jsonLdBlocks = [{
        "@type": "Product",
        "name": "L'Orpailleur Gris",
        "sku": "12685490",
        "manufacturer": {
            "@type": "Organization",
            "name": "Vignoble de l&#039;Orpailleur Inc.",
        },
    }]

    result = parse_capture(
        item,
        ProductIdentity(
            type="VIN", producer="L'Orpailleur",
            name="L'Orpailleur Gris", vintage="2024",
        ),
    )

    assert result["fields"]["producer"]["value"] == "Vignoble de l'Orpailleur Inc."
    assert result["identityAssessment"] == "PLAUSIBLE"
    assert result["vintageAssessment"] == "NOT_OBSERVED"


def test_vivino_uses_product_facts_instead_of_footer_labels():
    item = capture("VIVINO", "https://www.vivino.com/en/test/w/8299665?year=2018", "vivino_product.html")
    item.content.productHtml = """
      <div class="wrap">
        <h1>L'Orpailleur Gris 2018</h1>
        <div class="breadcrumbs">
          <a data-cy="breadcrumb-country">Canada</a>
          <a data-cy="breadcrumb-region">Quebec</a>
          <a data-cy="breadcrumb-winery">L'Orpailleur</a>
          <a data-cy="breadcrumb-winetype">White wine</a>
        </div>
        <table>
          <tr data-testid="wineFactRow"><th>Winery</th><td>L'Orpailleur</td></tr>
          <tr data-testid="wineFactRow"><th>Grapes</th><td>Muscat Blanc, Seyval Blanc, Vidal Blanc</td></tr>
          <tr data-testid="wineFactRow"><th>Region</th><td>Canada / Quebec</td></tr>
        </table>
        <footer><a>Grapes</a><a>Regions</a><p>Winery in Quebec</p></footer>
      </div>
    """

    result = parse_capture(
        item,
        ProductIdentity(
            type="VIN", producer="L'Orpailleur",
            name="L'Orpailleur Gris", vintage="2018",
        ),
    )

    assert result["parserVersion"] == "vivino-3"
    assert result["identityAssessment"] == "PLAUSIBLE"
    assert result["vintageAssessment"] == "MATCH"
    assert result["fields"]["producer"]["value"] == "L'Orpailleur"
    assert result["fields"]["country"]["value"] == "Canada"
    assert result["fields"]["region"]["value"] == "Quebec"
    assert result["fields"]["grapes"]["value"] == ["Muscat Blanc", "Seyval Blanc", "Vidal Blanc"]
    assert result["fields"]["style"]["value"] == "White wine"


def test_vivino_accepts_same_name_words_in_a_different_order():
    item = capture(
        "VIVINO",
        "https://www.vivino.com/en/athenais-pinot-noir-bourgogne/w/7280339?year=2023",
        "vivino_product.html",
    )
    item.content.productHtml = """
      <main>
        <h1>Athénaïs Pinot Noir Bourgogne 2023</h1>
        <a data-cy="breadcrumb-winery">Athénaïs</a>
        <a data-cy="breadcrumb-country">France</a>
        <a data-cy="breadcrumb-region">Bourgogne</a>
      </main>
    """

    result = parse_capture(
        item,
        ProductIdentity(
            type="VIN", producer="athenais",
            name="bourgogne Pinot noir", vintage="2023",
        ),
    )

    assert result["identityAssessment"] == "PLAUSIBLE"
    assert result["vintageAssessment"] == "MATCH"


def test_untappd_reads_the_product_header_and_stats():
    item = capture(
        "UNTAPPD",
        "https://untappd.com/b/brasserie-dieu-du-ciel-peche-mortel-bourbon-2024/6061213",
        "untappd_product.html",
    )
    item.content.jsonLdBlocks = [{
        "@type": "Product",
        "name": "Brasserie Dieu du Ciel! Péché Mortel Bourbon (2024)",
        "brand": {"@type": "Thing", "name": "Brasserie Dieu du Ciel!"},
        "sku": "6061213",
    }]
    item.content.productHtml = """
      <div class="box b_info"><div class="content">
        <h1>Péché Mortel Bourbon (2024)</h1>
        <p class="brewery">Brasserie Dieu du Ciel!</p>
        <p class="style">Stout - Imperial / Double Coffee</p>
        <p class="abv">9.5% ABV</p>
        <p class="ibu">N/A IBU</p>
      </div></div>
    """

    result = parse_capture(
        item,
        ProductIdentity(
            type="BIERE", producer="Dieu du Ciel",
            name="Péché Mortel Bourbon", vintage="2024",
        ),
    )

    assert result["parserVersion"] == "untappd-4"
    assert result["identityAssessment"] == "PLAUSIBLE"
    assert result["vintageAssessment"] == "MATCH"
    assert result["fields"]["name"]["value"] == "Péché Mortel Bourbon"
    assert result["fields"]["producer"]["value"] == "Brasserie Dieu du Ciel!"
    assert result["fields"]["style"]["value"] == "Stout - Imperial / Double Coffee"
    assert result["fields"]["alcoholVolume"]["value"] == 9.5
    assert result["fields"]["ibu"] is None


def test_untappd_ignores_year_duplicated_in_requested_name():
    item = capture(
        "UNTAPPD",
        "https://untappd.com/b/brett-and-sauvage-kr-k-2025/6644022",
        "untappd_product.html",
    )
    item.content.productHtml = """
      <div class="box b_info"><div class="content">
        <h1>KR**K (2025)</h1>
        <p class="brewery">Brett &amp; Sauvage</p>
        <p class="style">Lambic - Fruit</p>
      </div></div>
    """

    result = parse_capture(
        item,
        ProductIdentity(
            type="BIERE", producer="Brett & Sauvage",
            name="Kr**k 2025", vintage="2025",
        ),
    )

    assert result["identityAssessment"] == "PLAUSIBLE"
    assert result["vintageAssessment"] == "MATCH"


def test_untappd_accepts_coordinate_name_and_preserves_batch_year():
    item = capture(
        "UNTAPPD",
        "https://untappd.com/b/brasserie-cantillon-50-deg-n-4-deg-e-batch-7-2020/3999624",
        "untappd_product.html",
    )
    item.content.productHtml = """
      <div class="box b_info"><div class="content">
        <h1>50�N - 4�E (Batch 7 - 2020)</h1>
        <p class="brewery">Brasserie Cantillon</p>
        <p class="style">Lambic - Traditional</p>
        <p class="abv">7% ABV</p>
      </div></div>
    """

    result = parse_capture(
        item,
        ProductIdentity(
            type="BIERE", producer="Cantillon",
            name="50N (Batch 7- 2020)", vintage=None,
        ),
    )

    assert result["identityAssessment"] == "PLAUSIBLE"
    assert result["vintageAssessment"] == "NOT_APPLICABLE"
    assert result["fields"]["name"]["value"] == "50°N - 4°E (Batch 7 - 2020)"
    assert result["fields"]["vintage"]["value"] == "2020"
    assert result["fields"]["producer"]["value"] == "Brasserie Cantillon"
