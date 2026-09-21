package com.cellier.manager.enrichment

import org.json.JSONObject
import java.text.Normalizer

private fun normalizedIdentity(value: String?): String {
    val decomposed = Normalizer.normalize(value.orEmpty(), Normalizer.Form.NFD)
    return decomposed
        .replace(Regex("\\p{M}+"), "")
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")
}

internal fun identityTextMatches(observed: String?, expected: String?): Boolean {
    val actual = normalizedIdentity(observed)
    val wanted = normalizedIdentity(expected)
    if (actual.isBlank() || wanted.isBlank()) return false
    if (actual == wanted || (wanted.length >= 5 && wanted in actual)) return true
    val compactActual = actual.replace(" ", "")
    val compactWanted = wanted.replace(" ", "")
    if (compactWanted.length >= 5 && compactWanted in compactActual) return true

    val wantedWords = wanted.split(' ')
    val actualWords = actual.split(' ').toSet()
    return wantedWords.size >= 2 && wantedWords.all(actualWords::contains)
}

/**
 * Revalide sur Android l'identité observée par le compagnon. Cela permet à
 * une proposition déjà téléchargée de profiter des règles de comparaison
 * corrigées, sans devoir relancer Chrome.
 */
internal fun proposalIdentityMatches(proposal: JSONObject, identitySnapshot: JSONObject): Boolean {
    val observed = proposal.optJSONObject("observedIdentity") ?: return false
    val vintage = identitySnapshot.optString("vintage").takeIf(String::isNotBlank)
    val expectedName = vintage?.let { year ->
        identitySnapshot.optString("name").replace(
            Regex("(?<!\\d)${Regex.escape(year)}(?!\\d)"),
            " ",
        )
    } ?: identitySnapshot.optString("name")
    return identityTextMatches(observed.optString("producer"), identitySnapshot.optString("producer")) &&
        identityTextMatches(observed.optString("name"), expectedName)
}
