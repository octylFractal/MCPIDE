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

package me.kenzierocks.mcpide.mcp

import mu.KLogger
import java.nio.file.Path
import java.nio.file.Paths

class McpContext(
    private val runner: McpRunner,
    val minecraftVersion: String,
    val side: String,
    val logger: KLogger
) {

    val arguments get() = runner.currentStep!!.arguments

    val workingDirectory get() = runner.currentStep!!.workingDir

    fun file(name: String): Path {
        val file = Paths.get(name)
        return workingDirectory.resolve(file)
    }

    fun getStepOutput(name: String): Path {
        val step = runner.steps[name] ?: throw IllegalArgumentException("No step with name $name")
        return step.safeOutput
    }

    fun getStepOutput(type: Class<out McpFunction>): Path {
        val step = runner.steps.values
            .firstOrNull { it.isOfType(type) }
            ?: throw IllegalArgumentException("No step of type ${type.name}")
        return step.safeOutput
    }

}

inline fun <reified T : McpFunction> McpContext.getStepOutput() = getStepOutput(T::class.java)
