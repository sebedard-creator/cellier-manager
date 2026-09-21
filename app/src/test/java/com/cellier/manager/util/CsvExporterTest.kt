package com.cellier.manager.util

import org.junit.Assert.assertEquals
import org.junit.Test

class CsvExporterTest {
    @Test
    fun neutralizesSpreadsheetFormulaPrefixes() {
        assertEquals("'=2+2", CsvExporter.csvEscape("=2+2"))
        assertEquals("'+cmd", CsvExporter.csvEscape("+cmd"))
        assertEquals("'@value", CsvExporter.csvEscape("@value"))
    }

    @Test
    fun quotesAndDoublesRfc4180Characters() {
        assertEquals("\"Cuvée, \"\"Réserve\"\"\"", CsvExporter.csvEscape("Cuvée, \"Réserve\""))
    }
}
