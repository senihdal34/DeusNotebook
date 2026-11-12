package com.ethran.notable.reading

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingTextTest {
    init { EnglishStrings.install() }   // the logic speaks the app language; the test reads the English file

    @Test fun greetingFollowsTheHour() {
        assertEquals("Good morning, Sample", ReadingText.greeting(8, "Sample"))
        assertEquals("Good afternoon, Sample", ReadingText.greeting(14, "Sample"))
        assertEquals("Good evening, Sample", ReadingText.greeting(20, "Sample"))
        assertEquals("Good night", ReadingText.greeting(2, ""))
    }

    @Test fun minutesAreShort() {
        assertEquals("0m", ReadingText.minutes(0))
        assertEquals("45m", ReadingText.minutes(45))
        assertEquals("1h 20m", ReadingText.minutes(80))
        assertEquals("2h", ReadingText.minutes(120))
    }

    @Test fun paceComparesWithTheCalendar() {
        // Goal 15 books, day 183 of 365: half a year gone, 7.5 books expected.
        assertEquals("On pace", ReadingText.pace(8, 15, 183, 365))
        assertEquals("4 books behind", ReadingText.pace(4, 15, 183, 365))
        assertEquals("2 books ahead", ReadingText.pace(10, 15, 183, 365))
        assertEquals("1 book behind", ReadingText.pace(7, 15, 183, 365))
        assertEquals("Goal reached", ReadingText.pace(15, 15, 100, 365))
    }

    @Test fun mimeOfCommonBooks() {
        assertEquals("application/pdf", ReadingLauncher.mimeOf("/sdcard/Books/a.PDF"))
        assertEquals("application/epub+zip", ReadingLauncher.mimeOf("/sdcard/Books/b.epub"))
        assertEquals("*/*", ReadingLauncher.mimeOf("/sdcard/Books/c"))
    }
}
