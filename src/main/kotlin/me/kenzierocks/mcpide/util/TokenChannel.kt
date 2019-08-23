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

package me.kenzierocks.mcpide.util

import com.github.javaparser.GeneratedJavaParserConstants.EOF
import com.github.javaparser.GeneratedJavaParserConstants.EXPORTS
import com.github.javaparser.GeneratedJavaParserConstants.IDENTIFIER
import com.github.javaparser.GeneratedJavaParserConstants.MODULE
import com.github.javaparser.GeneratedJavaParserConstants.OPEN
import com.github.javaparser.GeneratedJavaParserConstants.OPENS
import com.github.javaparser.GeneratedJavaParserConstants.PROVIDES
import com.github.javaparser.GeneratedJavaParserConstants.REQUIRES
import com.github.javaparser.GeneratedJavaParserConstants.TO
import com.github.javaparser.GeneratedJavaParserConstants.TRANSITIVE
import com.github.javaparser.GeneratedJavaParserConstants.USES
import com.github.javaparser.GeneratedJavaParserConstants.WITH
import com.github.javaparser.GeneratedJavaParserTokenManager
import com.github.javaparser.Provider
import com.github.javaparser.SimpleCharStream
import com.github.javaparser.Token
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce

private val MODULE_TOKEN_KINDS = setOf(
    OPEN, MODULE, REQUIRES, TRANSITIVE, EXPORTS, OPENS, TO, USES, PROVIDES, WITH
)

fun CoroutineScope.produceTokens(provider: Provider): ReceiveChannel<Token> {
    return produce<Token>(Dispatchers.IO) {
        val tkmg = GeneratedJavaParserTokenManager(SimpleCharStream(provider))
        while (true) {
            val tk = tkmg.nextToken
            if (tk.kind == EOF) {
                break
            }
            // Module file tokens that we want as IDENTIFIER
            if (tk.kind in MODULE_TOKEN_KINDS) {
                tk.kind = IDENTIFIER
            }
            channel.send(tk)
        }
    }
}