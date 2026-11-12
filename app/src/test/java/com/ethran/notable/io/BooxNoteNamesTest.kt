package com.ethran.notable.io

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files

class BooxNoteNamesTest {
    @Test fun `names become hashtags without spaces and to do is left out`() {
        assertEquals("Örnek-cüzdan", BooxNoteNames.tag("Örnek cüzdan"))
        assertEquals("ProjeAlfa", BooxNoteNames.tag(" ProjeAlfa "))
        assertNull(BooxNoteNames.tag("To do"))
        assertNull(BooxNoteNames.tag("TODO"))
        assertNull(BooxNoteNames.tag(".hidden"))
    }

    @Test fun `folders and files are listed once`() {
        val root = Files.createTempDirectory("note").toFile()
        File(root, "Örnek cüzdan").mkdirs(); File(root, "Örnek cüzdan/Örnek cüzdan.pdf").writeText("x")
        File(root, "ProjeAlfa/ProjeAlfaNotlar").mkdirs()
        File(root, "ProjeAlfa/ProjeAlfaNotlar/ProjeAlfaNotlar.note").writeText("x")
        File(root, "To do").mkdirs()
        assertEquals(listOf("Örnek-cüzdan", "ProjeAlfa", "ProjeAlfaNotlar"), BooxNoteNames.scan(root).sortedBy { listOf("Örnek-cüzdan", "ProjeAlfa", "ProjeAlfaNotlar").indexOf(it) })
    }
}
