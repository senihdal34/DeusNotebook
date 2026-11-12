package com.ethran.notable.reading.chapters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

/** Files made to break a recursive reader: each one must give "no chapters" (or its real chapters), never a crash. */
class PdfHostileFilesTest {

    private fun noChapters(bytes: ByteArray) {
        val r = PdfChapters.read(bytes)
        assertTrue("expected no chapters, got $r", r is ChapterResult.None)
    }

    @Test fun aStreamWhoseLengthIsItself() {
        // One object: the catalog is a stream whose /Length points back at it.
        noChapters(PdfTestFiles.classic(mapOf(1 to "<< /Type /Catalog /Pages 1 0 R /Length 1 0 R >>\nstream\nabc\nendstream")))
    }

    @Test fun twoStreamsWhoseLengthsPointAtEachOther() {
        val bytes = PdfTestFiles.classic(
            mapOf(
                1 to "<< /Type /Catalog /Pages 2 0 R /Length 2 0 R >>\nstream\nabc\nendstream",
                2 to "<< /Type /Pages /Kids [] /Count 0 /Length 1 0 R >>\nstream\nabc\nendstream"
            )
        )
        noChapters(bytes)
    }

    @Test fun aDeepChainOfOneKidPageNodes() {
        val depth = 60_000
        val first = 10
        val last = first + depth
        val bodies = LinkedHashMap<Int, String>()
        bodies[1] = "<< /Type /Catalog /Pages $first 0 R /Outlines 3 0 R >>"
        bodies[3] = "<< /Type /Outlines /First 4 0 R /Count 1 >>"
        bodies[4] = "<< /Title (Deep) /Parent 3 0 R /Dest [$last 0 R /Fit] >>"
        for (n in first until last) bodies[n] = "<< /Type /Pages /Kids [${n + 1} 0 R] /Count 1 >>"
        bodies[last] = "<< /Type /Page /MediaBox [0 0 100 100] >>"
        // Walked without recursion, the chain is a valid one-page book with one chapter.
        val r = PdfChapters.read(PdfTestFiles.classic(bodies))
        val found = r as? ChapterResult.Found ?: error("expected the chapter, got $r")
        assertEquals(1, found.map.pages)
        assertEquals(listOf("Deep"), found.map.entries.map { it.title })
    }

    @Test fun aPageTreeThatContainsItself() {
        val bodies = LinkedHashMap<Int, String>()
        bodies[1] = "<< /Type /Catalog /Pages 2 0 R /Outlines 3 0 R >>"
        bodies[2] = "<< /Type /Pages /Kids [5 0 R] /Count 1 >>"
        bodies[3] = "<< /Type /Outlines /First 4 0 R /Count 1 >>"
        bodies[4] = "<< /Title (Loop) /Parent 3 0 R /Dest [5 0 R /Fit] >>"
        bodies[5] = "<< /Type /Pages /Kids [2 0 R 5 0 R] /Count 2 >>"
        noChapters(PdfTestFiles.classic(bodies))
    }

    @Test fun anXrefSectionWhosePrevIsItself() {
        val out = ByteArrayOutputStream()
        out.write("%PDF-1.4\n".toByteArray())
        val at1 = out.size()
        out.write("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n".toByteArray())
        val at2 = out.size()
        out.write("2 0 obj\n<< /Type /Pages /Kids [] /Count 0 >>\nendobj\n".toByteArray())
        val xrefAt = out.size()
        out.write("xref\n0 3\n0000000000 65535 f \n%010d 00000 n \n%010d 00000 n \n".format(at1, at2).toByteArray())
        out.write("trailer\n<< /Size 3 /Root 1 0 R /Prev $xrefAt >>\nstartxref\n$xrefAt\n%%EOF\n".toByteArray())
        noChapters(out.toByteArray())
    }

    @Test fun anObjectStreamWhoseCountLivesInsideItself() {
        // The same length as the original header, so every offset in the file stays right.
        val original = String(PdfTestFiles.withObjectStream(), Charsets.ISO_8859_1)
        val header = "/Type /ObjStm /N 11 "
        val cyclic = "/Type/ObjStm/N 9 0 R"
        assertEquals(header.length, cyclic.length)
        assertTrue(original.contains(header))
        noChapters(original.replace(header, cyclic).toByteArray(Charsets.ISO_8859_1))
    }
}
