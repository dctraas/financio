package com.financio.core.importer

import com.financio.core.model.SourceFormat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class FormatDetectorTest {

    @Test
    fun `recognizes an MT940 statement by its leading tag`() {
        assertEquals(SourceFormat.MT940, FormatDetector.detect(":20:REF1234\n:25:NL12INGB0001234567"))
    }

    @Test
    fun `recognizes an ING CSV export by its header row`() {
        assertEquals(SourceFormat.CSV, FormatDetector.detect("Datum;Naam / Omschrijving;Tegenrekening"))
    }

    @Test
    fun `refuses to guess at an unrecognized file`() {
        assertThrows(UnrecognizedFormatException::class.java) {
            FormatDetector.detect("dit is geen bankbestand")
        }
    }
}
