package com.ethran.notable.io

import java.io.File

/**
 * Builds the recognition glossary from files in the inbox folder:
 *  - glossary.txt   one word per line, "#" starts a comment (edit it on the PC; it syncs back)
 *  - titles of notes already exported there (names and terms the user really writes)
 *  - tags in the front matter of exported notes
 */
object HwrGlossaryLoader {
    const val FILE_NAME = "glossary.txt"

    private val timestampSuffix = Regex("""-\d{4}-\d{2}-\d{2}-\d{2}-\d{2}-\d{2}$""")

    /** Everyday English engineering words; the recognizer is Turkish-first and mangles them. */
    private val devTerms = listOf(
        "API", "backend", "frontend", "fullstack", "database", "server", "client", "endpoint", "webhook", "cache",
        "latency", "throughput", "scalability", "microservice", "container", "Docker", "Kubernetes", "pipeline",
        "deploy", "deployment", "release", "rollback", "staging", "production", "monitoring", "logging", "dashboard",
        "commit", "branch", "repository", "pull request", "refactor", "debug", "bug", "feature", "framework",
        "library", "package", "dependency", "compiler", "runtime", "algorithm", "async", "thread", "query", "schema",
        "authentication", "authorization", "token", "encryption", "middleware", "serverless", "cloud", "prototype",
        "roadmap", "sprint", "backlog", "agile", "startup", "open source", "GitHub", "Python", "Kotlin",
        "JavaScript", "TypeScript", "React", "Node", "SQL", "JSON", "REST", "GraphQL", "OAuth", "Linux", "AWS",
        "Android", "backtest", "performance", "optimization", "integration", "testing", "workflow", "template"
    )

    /**
     * Neutral examples. Your real names (people, companies, projects) belong in glossary.txt in the inbox folder,
     * which lives on the device, not in this repository: edit it on the PC and it syncs back.
     */
    private val starterWords = listOf(
        "Aragonite", "DeusNote", "ÖrnekFirma", "ÖrnekYazılım", "ÖrnekAtölye", "ProjeAlfa", "ProjeBeta", "OrnekKisi",
        "Sofistike", "LinkedIn", "Startup", "Şirket", "Obsidian", "Syncthing", "Boox", "Windows", "Podcast",
        "Outsource", "Regulation"
    ) + devTerms

    /** Creates a starter glossary.txt when none exists, so the user has something to edit. */
    fun ensureDefaultFile(dir: File) {
        val file = File(dir, FILE_NAME)
        if (file.exists()) return
        try {
            dir.mkdirs()
            file.writeText(
                "# Words the handwriting recognizer should prefer (one per line, # = comment).\n" +
                    "# Names, companies, English terms. Case and Turkish accents are ignored when matching.\n" +
                    starterWords.joinToString("\n", postfix = "\n")
            )
        } catch (_: Exception) { /* read-only folder: run without a glossary file */ }
    }

    fun load(dir: File): HwrGlossary {
        val words = LinkedHashSet<String>()
        try {
            File(dir, FILE_NAME).takeIf { it.isFile }?.readLines()?.forEach { line ->
                // "Proje Beta" on one line adds both words: the corrector matches word by word.
                TextSanitizer.cleanLine(line.substringBefore('#')).split(' ').filter { it.isNotEmpty() }.forEach { words.add(it) }
            }
            dir.listFiles { f -> f.isFile && f.extension == "md" }?.forEach { md ->
                TextSanitizer.cleanLine(md.nameWithoutExtension).replace(timestampSuffix, "")
                    .split(Regex("""[\s\-_]+""")).filter { it.length >= 4 && it.any(Char::isLetter) }
                    .forEach { words.add(it) }
                try {
                    md.useLines { lines ->
                        lines.take(20).filter { it.startsWith("  - ") }
                            .map { TextSanitizer.cleanLine(it.removePrefix("  - ")) }.filter { it.isNotEmpty() }
                            .forEach { words.add(it) }
                    }
                } catch (_: Exception) { }
            }
        } catch (_: Exception) { }
        return HwrGlossary(words)
    }
}
