package me.kenzierocks.mcpide.fx

import com.github.javaparser.Position
import com.github.javaparser.TokenRange
import org.fxmisc.richtext.CodeArea
import org.fxmisc.richtext.LineNumberFactory
import java.util.Scanner

class JavaEditorArea : CodeArea() {
    private var lineOffsets: IntArray? = null

    init {
        isEditable = false
        paragraphGraphicFactory = LineNumberFactory.get(this)
    }

    fun setText(text: String) {
        replaceText(text)
        lineOffsets = sequence {
            val s = Scanner(text)
            var offset = 0
            while (s.hasNextLine()) {
                yield(offset)
                val line = s.nextLine()
                offset += line.length + 1
            }
        }.toList().toIntArray()
    }

    fun styleTokenRange(style: String, tokenRange: TokenRange) {
        val range = tokenRange.toRange().orElse(null) ?: return
        val begin = computeTextIndex(range.begin) ?: return
        val end = computeTextIndex(range.end) ?: return
        setStyleClass(begin, end, style)
    }

    private fun computeTextIndex(position: Position): Int? {
        val offsets = lineOffsets ?: return null
        if (position.line > offsets.lastIndex) {
            return null
        }
        return offsets[position.line] + position.column
    }
}
