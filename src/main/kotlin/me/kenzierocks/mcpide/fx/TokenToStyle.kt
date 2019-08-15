/*
 * This file is part of MCPIDE, licensed under the MIT License (MIT).
 *
 * Copyright (c) kenzierocks <https://kenzierocks.me>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package me.kenzierocks.mcpide.fx

import com.github.javaparser.GeneratedJavaParserConstants
import com.github.javaparser.GeneratedJavaParserConstants.STRING_LITERAL
import com.github.javaparser.JavaToken.Category.COMMENT
import com.github.javaparser.JavaToken.Category.EOL
import com.github.javaparser.JavaToken.Category.IDENTIFIER
import com.github.javaparser.JavaToken.Category.KEYWORD
import com.github.javaparser.JavaToken.Category.LITERAL
import com.github.javaparser.JavaToken.Category.OPERATOR
import com.github.javaparser.JavaToken.Category.SEPARATOR
import com.github.javaparser.JavaToken.Category.WHITESPACE_NO_EOL
import com.github.javaparser.TokenTypes

fun styleFor(kind: Int): String {
    return when (TokenTypes.getCategory(kind)!!) {
        WHITESPACE_NO_EOL, EOL, SEPARATOR, OPERATOR ->
            "default-text"
        COMMENT -> "comment"
        IDENTIFIER -> "identifier"
        KEYWORD -> "keyword"
        LITERAL -> when (kind) {
            STRING_LITERAL -> "string-literal"
            else -> "other-literal"
        }
    }
}