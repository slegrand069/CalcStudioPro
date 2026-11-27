package com.example.calcstudiopro.history.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HistoryStructuredDataCodecTest {
    @Test fun payloadRoundTripsInputsResultsAndFullDetailsWithoutLosingNumbers() {
        val original = HistoryStructuredData(
            "Triangle",
            inputs = listOf(HistoryDatum("a", "5,0", 5.0)),
            results = listOf(HistoryDatum("Aire", "12,5", 12.5)),
            details = listOf(HistoryDatum("Formule", "S = ab sin(C)/2"), HistoryDatum("X", "-0", -0.0))
        )
        assertEquals(original, HistoryStructuredDataCodec.decode(HistoryStructuredDataCodec.encode(original)))
    }

    @Test fun oldAndCorruptedPayloadsAreIgnoredWithoutThrowing() {
        assertNull(HistoryStructuredDataCodec.decode("{\"version\":1,\"old\":true}"))
        assertNull(HistoryStructuredDataCodec.decode("csp-history-v2:not-base64!"))
        assertNull(HistoryStructuredDataCodec.decode(null))
    }
}
