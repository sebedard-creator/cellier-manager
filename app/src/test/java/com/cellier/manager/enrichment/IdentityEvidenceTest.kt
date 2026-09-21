package com.cellier.manager.enrichment

import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IdentityEvidenceTest {
    @Test
    fun `vivino reordered name still matches the requested identity`() {
        val proposal = JSONObject("""
            {"observedIdentity":{"producer":"Athénaïs","name":"Athénaïs Pinot Noir Bourgogne","vintage":"2023"}}
        """)
        val expected = JSONObject("""
            {"producer":"athenais","name":"bourgogne Pinot noir","vintage":"2023"}
        """)

        assertTrue(proposalIdentityMatches(proposal, expected))
    }

    @Test
    fun `different producer cannot be accepted from reordered words`() {
        val proposal = JSONObject("""
            {"observedIdentity":{"producer":"Autre domaine","name":"Pinot Noir Bourgogne","vintage":"2023"}}
        """)
        val expected = JSONObject("""
            {"producer":"athenais","name":"bourgogne Pinot noir","vintage":"2023"}
        """)

        assertFalse(proposalIdentityMatches(proposal, expected))
    }

    @Test
    fun `year duplicated in requested name does not make exact beer ambiguous`() {
        val proposal = JSONObject("""
            {"observedIdentity":{"producer":"Brett & Sauvage","name":"KR**K","vintage":"2025"}}
        """)
        val expected = JSONObject("""
            {"producer":"Brett & Sauvage","name":"Kr**k 2025","vintage":"2025"}
        """)

        assertTrue(proposalIdentityMatches(proposal, expected))
    }
}
