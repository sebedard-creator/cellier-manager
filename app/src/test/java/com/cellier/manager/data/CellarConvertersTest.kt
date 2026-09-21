package com.cellier.manager.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CellarConvertersTest {
    @Test
    fun readsJsonListWithAccentsAndSeparators() {
        assertEquals(
            listOf("Cabernet franc", "Mourvèdre|Syrah"),
            CellarConverters.decodeStringList("[\"Cabernet franc\",\"Mourvèdre|Syrah\"]")
        )
    }

    @Test
    fun readsLegacyEscapedPipeFormat() {
        assertEquals(
            listOf("Grenache|Syrah", "Cinsault"),
            CellarConverters.decodeStringList("Grenache\\|Syrah|Cinsault")
        )
    }

    @Test
    fun ignoresEmptyLegacyValues() {
        assertEquals(listOf("Riesling"), CellarConverters.decodeStringList(" | Riesling ||"))
    }
}
