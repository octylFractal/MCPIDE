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

import mu.KotlinLogging
import net.octyl.aptcreator.GenerateCreator
import net.octyl.aptcreator.Provided
import java.nio.file.Path
import java.util.zip.ZipFile

@GenerateCreator
class McpRunner(
    private val mcpConfigZip: Path,
    private val config: McpConfig,
    side: String,
    private val mcpDirectory: Path,
    @Provided
    private val builtInFunctionProvider: BuiltInFunctionProvider,
    @Provided
    private val executeFunctionProvider: ExecuteFunctionProvider
) {
    private val logger = KotlinLogging.logger("MCPOutput")

    private val context = McpContext(this, config.version, side, logger)

    val steps: Map<String, McpStep>

    var currentStep: McpStep? = null
        private set

    init {
        val data: Map<String, String> = config.data.mapValues { (_, v) ->
            when (v) {
                is Map<*, *> -> v[side]
                else -> v
            } as String
        }

        val stepList: List<McpConfig.Step> = config.steps[side]
            ?: throw IllegalArgumentException("No steps for $side, config zip: $mcpConfigZip")

        steps = stepList.associateBy({ it.name }) { step ->
            val function = builtInFunctionProvider.provide(step.type, data)
                ?: executeFunctionProvider.provide(step, config.functions, data)
            val workDir = mcpDirectory.resolve(step.name)

            McpStep(context, step.name, function, step.values, workDir)
        }
    }

    /**
     * Run steps, stopping at [stop] if specified.
     *
     * @return the final step's output
     */
    suspend fun run(stop: String? = null, onStepChange: suspend (String) -> Unit): Path {
        val untilMsg = when (stop) {
            null -> ""
            else -> " (until '$stop')"
        }

        logger.info { "Setting up MCP environment" }

        val steps: List<McpStep> = sequence {
            steps.values.forEach { step ->
                yield(step)
                if (step.name == stop) {
                    return@sequence
                }
            }
        }.toList()

        logger.info { "Initializing steps$untilMsg" }
        ZipFile(mcpConfigZip.toFile()).use { zip ->
            steps.forEach { step ->
                logger.info { "> Initializing '${step.name}'" }
                currentStep = step
                onStepChange("Initializing '${step.name}'")
                step.initialize(zip)
                logger.info { "> Initialized '${step.name}'" }
            }
        }

        logger.info { "Executing steps$untilMsg" }
        steps.forEach { step ->
            logger.info { "> Running '${step.name}'" }
            currentStep = step
            onStepChange("Running '${step.name}'")
            step.arguments = step.arguments.mapValues { (_, v) ->
                when (v) {
                    is String -> replaceOutputTemplate(v)
                    else -> v
                }
            }
            step.execute()
        }

        if (stop != null) {
            logger.info { "Stopped at requested step: '$stop'" }
        }

        logger.info { "MCP setup complete." }

        return steps.last().safeOutput
    }

    private val outputTemplateRegex = Regex("""^\{(\w+)Output}$""")

    private fun replaceOutputTemplate(template: String): Any {
        val match = outputTemplateRegex.find(template) ?: return template
        val stepName = match.groupValues[1]
        return context.getStepOutput(stepName)
    }

}
