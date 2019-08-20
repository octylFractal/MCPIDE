package me.kenzierocks.mcpide.fx

import com.github.javaparser.ast.Modifier
import me.kenzierocks.mcpide.detectSrgType
import org.fxmisc.richtext.model.StyleSpan
import org.fxmisc.richtext.model.StyleSpansBuilder

/**
 * Helper for printing code as spans
 */
class SpansPrinter {
    private val spansBuilder = StyleSpansBuilder<MapStyle>()
    private var indent: Int = 0
    private var needIndent = false

    fun spans() = spansBuilder.create()!!

    fun addSpace() = addText(" ")

    fun addKeyword(text: String) = addText(text, Style.KEYWORD)

    fun addIdentifier(text: String, jumpTarget: JumpTarget? = null) = addSpan(text, jumpTarget, Style.IDENTIFIER)

    /**
     * Note: implicitly starts a new line, since there's no other reason to add indent.
     */
    fun indent(amount: Int = 1) = apply {
        indent += amount
        addLine()
    }

    /**
     * Note: implicitly starts a new line, since there's no other reason to remove indent.
     */
    fun dedent(amount: Int = 1) = apply {
        indent -= amount
        addLine()
    }

    inline fun withIndent(amount: Int = 1, block: SpansPrinter.() -> Unit) = apply {
        indent(amount)
        block()
        dedent(amount)
    }

    /**
     * Only add newlines via this method. Other methods may not appropriately indent.
     */
    fun addLine() = apply {
        addText("\n")
        needIndent = true
    }

    /**
     * Finish a standard Java line.
     *
     * Calls [addText] with `;`, then [addLine].
     */
    fun finishLine() = apply {
        addText(";").addLine()
    }

    /**
     * Standard procedure for adding a Java block.
     *
     * Adds a `{`, newline, then indents one level. Code in [block] is run.
     * Dedents one level, adds a newline, then a `}`.
     */
    inline fun addBlock(block: SpansPrinter.() -> Unit) = apply {
        addText("{").withIndent(block = block).addText("}")
    }

    /**
     * Add some plain text.
     */
    fun addText(text: String, style: Style = Style.DEFAULT_TEXT) = apply {
        addSpan(text, null, style)
    }

    fun addSpan(text: String, jumpTarget: JumpTarget? = null, style: Style = Style.DEFAULT_TEXT) = apply {
        if (needIndent) {
            spansBuilder.add(newSpan("    ".repeat(indent), null, Style.DEFAULT_TEXT))
            needIndent = false
        }
        spansBuilder.add(newSpan(text, jumpTarget, style))
    }

    private fun newSpan(text: String, jumpTarget: JumpTarget?, style: Style): StyleSpan<MapStyle> {
        val srgName = text.takeIf { it.detectSrgType() != null }
        return StyleSpan(MapStyle(
            text = text,
            styleClasses = setOf(style.styleClass),
            srgName = srgName,
            jumpTarget = jumpTarget
        ), text.length)
    }

    inline fun <T> addCsv(collection: Collection<T>?,
                          prefix: SpansPrinter.() -> Unit = {},
                          suffix: SpansPrinter.() -> Unit = {},
                          block: SpansPrinter.(T) -> Unit) = apply {
        if (collection.isNullOrEmpty()) {
            return@apply
        }
        prefix()
        val iter = collection.iterator()
        block(iter.next())
        while (iter.hasNext()) {
            addText(", ")
            block(iter.next())
        }
        suffix()
    }

    fun addModifiers(modifiers: List<Modifier>) = apply {
        modifiers.map { it.keyword.asString() }.forEach {
            addKeyword(it).addSpace()
        }
    }

}