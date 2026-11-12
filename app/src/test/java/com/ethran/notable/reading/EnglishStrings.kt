package com.ethran.notable.reading

import com.ethran.notable.R
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/**
 * English text for the unit tests, which run on the JVM without Android resources. It reads the real
 * `res/values/strings_reading.xml` (and `strings.xml`), so a test that checks a sentence checks the sentence the app
 * shows in English, and a missing or misspelt resource fails loudly instead of showing "".
 *
 * Resource ids are mapped back to names by reflection on the generated `R.string` / `R.plurals` classes.
 */
object EnglishStrings : TextResolver {
    override val locale: Locale = Locale.ENGLISH

    private val stringNames: Map<Int, String> = namesOf(R.string::class.java)
    private val pluralNames: Map<Int, String> = namesOf(R.plurals::class.java)
    private val strings = HashMap<String, String>()
    private val plurals = HashMap<String, Map<String, String>>()

    init {
        for (name in listOf("strings_reading.xml")) {
            val file = listOf("src/main/res/values/$name", "app/src/main/res/values/$name").map(::File).firstOrNull { it.isFile } ?: continue
            val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
            val nodes = root.childNodes
            for (i in 0 until nodes.length) {
                val e = nodes.item(i) as? Element ?: continue
                when (e.tagName) {
                    "string" -> strings[e.getAttribute("name")] = unescape(e.textContent)
                    "plurals" -> {
                        val items = e.getElementsByTagName("item")
                        plurals[e.getAttribute("name")] = (0 until items.length).associate {
                            val item = items.item(it) as Element
                            item.getAttribute("quantity") to unescape(item.textContent)
                        }
                    }
                }
            }
        }
    }

    private fun namesOf(cls: Class<*>): Map<Int, String> =
        cls.fields.filter { it.type == Int::class.javaPrimitiveType }.associate { it.getInt(null) to it.name }

    /** Android resource text escapes: \' \" \n and \@. */
    private fun unescape(raw: String): String = raw.replace("\\'", "'").replace("\\\"", "\"").replace("\\n", "\n").replace("\\@", "@")

    override fun string(id: Int, args: Array<out Any>): String {
        val name = stringNames[id] ?: error("no string with id $id")
        val text = strings[name] ?: error("string $name is missing from values/strings_reading.xml")
        return if (args.isEmpty()) text else String.format(Locale.ENGLISH, text, *args)
    }

    override fun plural(id: Int, count: Int, args: Array<out Any>): String {
        val name = pluralNames[id] ?: error("no plurals with id $id")
        val forms = plurals[name] ?: error("plurals $name is missing from values/strings_reading.xml")
        val text = (if (count == 1) forms["one"] else null) ?: forms.getValue("other")
        return String.format(Locale.ENGLISH, text, *args)
    }

    /** Makes the reading logic speak English; call once from a test class's `init`. */
    fun install() {
        RS.resolver = this
    }
}
