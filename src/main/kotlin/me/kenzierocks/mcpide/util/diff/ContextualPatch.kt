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

package me.kenzierocks.mcpide.util.diff

import okhttp3.internal.closeQuietly
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.ArrayList
import java.util.Base64

/**
 * Applies contextual patches to files. The patch file can contain patches for multiple files.
 *
 * Based on https://github.com/cloudbees/diff4j/blob/69289a936ae71e66c0f3a86242f784846cb75ef1/src/main/java/com/cloudbees/diff/ContextualPatch.java
 *
 * @author Maros Sandor
 */
class ContextualPatch private constructor(
    private val patchFile: PatchFile,
    context: PatchContextProvider?
) {

    // first seen in mercurial diffs: characters after the second @@ - ignore them
    private val unifiedRangePattern = Regex("@@ -(\\d+)(,\\d+)? \\+(\\d+)(,\\d+)? @@(\\s.*)?")
    private val baseRangePattern = Regex("\\*\\*\\* (\\d+)(,\\d+)? \\*\\*\\*\\*")
    private val modifiedRangePattern = Regex("--- (\\d+)(,\\d+)? ----")
    private val normalChangeRangePattern = Regex("(\\d+),(\\d+)c(\\d+),(\\d+)")
    private val normalAddRangePattern = Regex("(\\d+)a(\\d+),(\\d+)")
    private val normalDeleteRangePattern = Regex("(\\d+),(\\d+)d(\\d+)")
    private val binaryHeaderPattern = Regex("MIME: (.*?); encoding: (.*?); length: (-?\\d+?)")

    private val contextProvider: PatchContextProvider
    internal var c14nAccess = false
    internal var c14nWhitespace = false
    var maxFuzz = 0
    private val suggestedContext: File?

    private var context: File? = null
    private var patchReader: BufferedReader? = null
    private var patchLine: String? = null
    private var patchLineRead: Boolean = false
    private var lastPatchedLine: Int = 0    // the last line that was successfuly patched

    private fun getNextPatch(): SinglePatch? {
        val patch = SinglePatch()
        while (true) {
            val line = readPatchLine() ?: return null

            if (line.startsWith("Index:")) {
                patch.targetPath = line.substring(6).trim { it <= ' ' }
            } else if (line.startsWith("MIME: application/octet-stream;")) {
                unreadPatchLine()
                readBinaryPatchContent(patch)
                break
            } else if (line.startsWith("--- ")) {
                unreadPatchLine()
                readPatchContent(patch)
                break
            } else if (line.startsWith("*** ")) {
                unreadPatchLine()
                readContextPatchContent(patch)
                break
            } else if (isNormalDiffRange(line)) {
                unreadPatchLine()
                readNormalPatchContent(patch)
                break
            }
        }
        return patch
    }

    init {
        this.suggestedContext = null
        this.contextProvider = context ?: LocalContext(this)
    }

    fun setCanonicalization(access: Boolean, whitespace: Boolean) {
        this.c14nAccess = access
        this.c14nWhitespace = whitespace
    }

    /**
     *
     * @param dryRun true if the method should not make any modifications to files, false otherwise
     * @return
     * @throws IOException
     */
    fun patch(dryRun: Boolean): List<PatchReport> {
        val report = ArrayList<PatchReport>()
        init()
        try {
            patchLine = patchReader!!.readLine()
            val patches = ArrayList<SinglePatch>()
            while (true) {
                val patch = getNextPatch() ?: break
                patches.add(patch)
            }
            computeContext(patches)
            for (patch in patches) {
                try {
                    report.add(applyPatch(patch, dryRun))
                } catch (e: Exception) {
                    report.add(PatchReport(PatchStatus.Failure, ArrayList()))
                }

            }
            return report
        } finally {
            patchReader?.closeQuietly()
        }
    }

    private fun init() {
        var encoding = StandardCharsets.ISO_8859_1
        BufferedReader(InputStreamReader(patchFile.openStream())).let { pr ->
            if (!patchFile.requiresFurtherProcessing()) {
                patchReader = pr
                return
            }
            pr.use {
                val line = pr.readLine()
                if (MAGIC == line) {
                    encoding = StandardCharsets.UTF_8 // NOI18N
                    pr.readLine()
                }
            }
        }

        val buffer = ByteArray(MAGIC.length)
        val read = patchFile.openStream().use { it.read(buffer) }
        if (read != -1 && MAGIC == String(buffer, StandardCharsets.UTF_8)) {  // NOI18N
            encoding = StandardCharsets.UTF_8 // NOI18N
        }
        patchReader = BufferedReader(InputStreamReader(patchFile.openStream(), encoding))
    }

    private fun applyPatch(patch: SinglePatch, dryRun: Boolean): PatchReport {
        lastPatchedLine = 1
        var target = contextProvider.getData(patch)
        val hunkReports = ArrayList<HunkReport>()
        if (target != null && !patch.binary) {
            if (patchCreatesNewFileThatAlreadyExists(patch, target)) {
                for (x in patch.hunks.indices) {
                    hunkReports.add(HunkReport(PatchStatus.Skipped, null, 0, 0, x))
                }
                return PatchReport(PatchStatus.Skipped, hunkReports)
            }
        } else {
            target = ArrayList()
        }
        if (patch.mode == Mode.DELETE) {
            target = ArrayList()
        } else {
            if (!patch.binary) {
                var x = 0
                for (hunk in patch.hunks) {
                    x++
                    try {
                        hunkReports.add(applyHunk(target, hunk, x))
                    } catch (e: Exception) {
                        hunkReports.add(HunkReport(PatchStatus.Failure, e, 0, 0, x))
                    }

                }
            }
        }
        if (!dryRun) {
            contextProvider.setData(patch, target)
        }
        for (hunk in hunkReports) {
            if (hunk.status === PatchStatus.Failure) {
                return PatchReport(PatchStatus.Failure, hunkReports)
            }
        }
        return PatchReport(PatchStatus.Patched, hunkReports)
    }

    private fun patchCreatesNewFileThatAlreadyExists(patch: SinglePatch, originalFile: List<String>): Boolean {
        if (patch.hunks.size != 1) return false
        val hunk = patch.hunks[0]
        if (hunk.baseStart != 0 || hunk.baseCount != 0
            || hunk.modifiedStart != 1 || hunk.modifiedCount != originalFile.size) return false

        val target = ArrayList<String>(hunk.modifiedCount)
        applyHunk(target, hunk, 0)
        return target == originalFile
    }

    @Throws(IOException::class)
    internal fun backup(target: File) {
        if (target.exists()) {
            copyStreamsCloseAll(FileOutputStream(computeBackup(target)), FileInputStream(target))
        }
    }

    private fun computeBackup(target: File): File {
        return File(target.parentFile, target.name + ".original~")
    }

    @Throws(IOException::class)
    private fun copyStreamsCloseAll(writer: OutputStream, reader: InputStream) {
        writer.use { w ->
            reader.use { r -> r.copyTo(w) }
        }
    }

    @Throws(IOException::class)
    internal fun writeFile(patch: SinglePatch, lines: List<String>) {
        if (patch.mode == Mode.DELETE) {
            patch.targetFile.delete()
            return
        }

        patch.targetFile.parentFile.mkdirs()
        if (patch.binary) {
            if (patch.hunks.isEmpty()) {
                patch.targetFile.delete()
            } else {
                patch.targetFile.outputStream().use { out ->
                    patch.hunks[0].lines.forEach { line ->
                        out.write(Base64.getDecoder().decode(line))
                    }
                }
            }
        } else {
            if (lines.isEmpty()) return
            PrintWriter(OutputStreamWriter(FileOutputStream(patch.targetFile), getEncoding(patch.targetFile))).use { w ->
                for (line in lines.subList(0, lines.size - 1)) {
                    w.println(line)
                }
                w.print(lines[lines.size - 1])
                if (!patch.noEndingNewline) {
                    w.println()
                }
            }
        }
    }

    private fun applyHunk(target: MutableList<String>, hunk: Hunk, hunkID: Int): HunkReport {
        var idx = -1
        var fuzz = 0
        while (idx == -1 && fuzz <= this.maxFuzz) {
            idx = findHunkIndex(target, hunk, fuzz, hunkID)
            if (idx != -1) break
            fuzz++
        }
        if (idx == -1) throw RuntimeException("Cannot find hunk target")
        return applyHunk(target, hunk, idx, false, fuzz, hunkID)
    }

    private fun findHunkIndex(target: MutableList<String>, hunk: Hunk, fuzz: Int, hunkID: Int): Int {
        val idx = hunk.modifiedStart  // first guess from the hunk range specification
        if (idx >= lastPatchedLine && applyHunk(target, hunk, idx, true, fuzz, hunkID).status.success) {
            return idx
        } else {
            // try to search for the context
            for (i in idx - 1 downTo lastPatchedLine) {
                if (applyHunk(target, hunk, i, true, fuzz, hunkID).status.success) {
                    return i
                }
            }
            for (i in idx + 1 until target.size) {
                if (applyHunk(target, hunk, i, true, fuzz, hunkID).status.success) {
                    return i
                }
            }
        }
        return -1
    }

    /**
     * @return hunk report
     */
    private fun applyHunk(target: MutableList<String>, hunk: Hunk, idx: Int, dryRun: Boolean, fuzz: Int, hunkID: Int): HunkReport {
        var i = idx
        val startIdx = i
        i-- // indices in the target list are 0-based
        var hunkIdx = -1
        for (hunkLine in hunk.lines) {
            hunkIdx++
            val isAddition = isAdditionLine(hunkLine)
            if (!isAddition) {
                if (i >= target.size) {
                    return if (dryRun) {
                        HunkReport(PatchStatus.Failure, null, i, fuzz, hunkID)
                    } else {
                        throw RuntimeException("Unapplicable hunk #$hunkID @@ $startIdx")
                    }
                }
                var match = PatchUtils.similar(this, target[i], hunkLine.substring(1), hunkLine.get(0))
                if (!match && fuzz != 0 && !isRemovalLine(hunkLine)) {
                    match = hunkIdx < fuzz || hunkIdx >= hunk.lines.size - fuzz
                }
                if (!match) {
                    return if (dryRun) {
                        HunkReport(PatchStatus.Failure, null, i, fuzz, hunkID)
                    } else {
                        throw RuntimeException("Unapplicable hunk #$hunkID @@ $startIdx")
                    }
                }
            }
            if (dryRun) {
                if (isAddition) {
                    i--
                }
            } else {
                if (isAddition) {
                    target.add(i, hunkLine.substring(1))
                } else if (isRemovalLine(hunkLine)) {
                    target.removeAt(i)
                    i--
                }
            }
            i++
        }
        i++ // indices in the target list are 0-based
        lastPatchedLine = i
        return HunkReport(if (fuzz != 0) PatchStatus.Fuzzed else PatchStatus.Patched, null, startIdx, fuzz, hunkID)
    }

    private fun isAdditionLine(hunkLine: String): Boolean {
        return hunkLine[0] == '+'
    }

    private fun isRemovalLine(hunkLine: String): Boolean {
        return hunkLine[0] == '-'
    }

    private fun getEncoding(file: File?): Charset {
        return StandardCharsets.UTF_8
    }

    @Throws(IOException::class)
    internal fun readFile(target: File): List<String> {
        return target.reader(getEncoding(target)).useLines {
            it.toList()
        }
    }

    private fun isNormalDiffRange(line: String): Boolean {
        return (normalAddRangePattern.matches(line)
            || normalChangeRangePattern.matches(line)
            || normalDeleteRangePattern.matches(line))
    }

    /**
     * Reads binary diff hunk.
     */
    private fun readBinaryPatchContent(patch: SinglePatch) {
        val hunks = ArrayList<Hunk>()
        val hunk = Hunk()
        while (true) {
            val line = readPatchLine()
            if (line == null || line.startsWith("Index:") || line.isEmpty()) {
                unreadPatchLine()
                break
            }
            if (patch.binary) {
                hunk.lines.add(line)
            } else {
                val m = binaryHeaderPattern.matchEntire(line)
                if (m != null) {
                    patch.binary = true
                    val length = Integer.parseInt(m.groupValues[3])
                    if (length == -1) break
                    hunks.add(hunk)
                }
            }
        }
        patch.hunks = hunks.toArray(arrayOfNulls<Hunk>(hunks.size))
    }

    /**
     * Reads normal diff hunks.
     */
    @Throws(IOException::class)
    private fun readNormalPatchContent(patch: SinglePatch) {
        val hunks = ArrayList<Hunk>()
        lateinit var hunk: Hunk
        while (true) {
            val line = readPatchLine()
            if (line == null || line.startsWith("Index:")) {
                unreadPatchLine()
                break
            }
            val match = normalAddRangePattern.matchEntire(line)?.let { it to normalAddRangePattern }
                ?: normalChangeRangePattern.matchEntire(line)?.let { it to normalChangeRangePattern }
                ?: normalDeleteRangePattern.matchEntire(line)?.let { it to normalDeleteRangePattern }
            if (match != null) {
                val (m, r) = match
                hunk = Hunk()
                hunks.add(hunk)
                parseNormalRange(hunk, r, m)
            } else {
                when {
                    line.startsWith("> ") -> hunk.lines.add("+" + line.substring(2))
                    line.startsWith("< ") -> hunk.lines.add("-" + line.substring(2))
                    line.startsWith("---") -> {
                        // ignore
                    }
                    else -> throw RuntimeException("Invalid hunk line: $line")
                }
            }
        }
        patch.hunks = hunks.toTypedArray()
    }

    private fun parseNormalRange(hunk: Hunk, r: Regex, m: MatchResult) {
        when (r) {
            normalAddRangePattern -> {
                hunk.baseStart = Integer.parseInt(m.groupValues[1])
                hunk.baseCount = 0
                hunk.modifiedStart = Integer.parseInt(m.groupValues[2])
                hunk.modifiedCount = Integer.parseInt(m.groupValues[3]) - hunk.modifiedStart + 1
            }
            normalDeleteRangePattern -> {
                hunk.baseStart = Integer.parseInt(m.groupValues[1])
                hunk.baseCount = Integer.parseInt(m.groupValues[2]) - hunk.baseStart + 1
                hunk.modifiedStart = Integer.parseInt(m.groupValues[3])
                hunk.modifiedCount = 0
            }
            else -> {
                hunk.baseStart = Integer.parseInt(m.groupValues[1])
                hunk.baseCount = Integer.parseInt(m.groupValues[2]) - hunk.baseStart + 1
                hunk.modifiedStart = Integer.parseInt(m.groupValues[3])
                hunk.modifiedCount = Integer.parseInt(m.groupValues[4]) - hunk.modifiedStart + 1
            }
        }
    }

    /**
     * Reads context diff hunks.
     */
    @Throws(IOException::class)
    private fun readContextPatchContent(patch: SinglePatch) {
        val base = readPatchLine()
        if (base == null || !base.startsWith("*** ")) throw RuntimeException("Invalid context diff header: " + base!!)
        val modified = readPatchLine()
        if (modified == null || !modified.startsWith("--- ")) throw RuntimeException("Invalid context diff header: " + modified!!)
        if (!patch.hasTargetPath()) {
            computeTargetPath(base, modified, patch)
        }

        val hunks = ArrayList<Hunk>()
        lateinit var hunk: Hunk

        var lineCount = -1
        while (true) {
            val line = readPatchLine()
            if (line == null || line.isEmpty() || line.startsWith("Index:")) {
                unreadPatchLine()
                break
            } else if (line.startsWith("***************")) {
                hunk = Hunk()
                parseContextRange(hunk, readPatchLine()!!)
                hunks.add(hunk)
            } else if (line.startsWith("--- ")) {
                lineCount = 0
                parseContextRange(hunk, line)
                hunk.lines.add(line)
            } else {
                val c = line[0]
                if (c == ' ' || c == '+' || c == '-' || c == '!') {
                    if (lineCount < hunk.modifiedCount) {
                        hunk.lines.add(line)
                        if (lineCount != -1) {
                            lineCount++
                        }
                    }
                } else {
                    throw RuntimeException("Invalid hunk line: $line")
                }
            }
        }
        patch.hunks = hunks.toTypedArray()
        convertContextToUnified(patch)
    }

    private fun convertContextToUnified(patch: SinglePatch) {
        patch.hunks = patch.hunks.map { convertContextToUnified(it) }.toTypedArray()
    }

    private fun convertContextToUnified(hunk: Hunk): Hunk {
        val unifiedHunk = Hunk()
        unifiedHunk.baseStart = hunk.baseStart
        unifiedHunk.modifiedStart = hunk.modifiedStart
        var split = -1
        for (i in 0 until hunk.lines.size) {
            if (hunk.lines[i].startsWith("--- ")) {
                split = i
                break
            }
        }
        if (split == -1) throw RuntimeException("Missing split divider in context patch")

        var baseIdx = 0
        var modifiedIdx = split + 1
        val unifiedLines = mutableListOf<String>()
        while (baseIdx < split || modifiedIdx < hunk.lines.size) {
            val baseLine = if (baseIdx < split) hunk.lines[baseIdx] else "~"
            val modifiedLine = if (modifiedIdx < hunk.lines.size) hunk.lines[modifiedIdx] else "~"
            if (baseLine.startsWith("- ")) {
                unifiedLines.add("-" + baseLine.substring(2))
                unifiedHunk.baseCount++
                baseIdx++
            } else if (modifiedLine.startsWith("+ ")) {
                unifiedLines.add("+" + modifiedLine.substring(2))
                unifiedHunk.modifiedCount++
                modifiedIdx++
            } else if (baseLine.startsWith("! ")) {
                unifiedLines.add("-" + baseLine.substring(2))
                unifiedHunk.baseCount++
                baseIdx++
            } else if (modifiedLine.startsWith("! ")) {
                unifiedLines.add("+" + modifiedLine.substring(2))
                unifiedHunk.modifiedCount++
                modifiedIdx++
            } else if (baseLine.startsWith("  ") && modifiedLine.startsWith("  ")) {
                unifiedLines.add(baseLine.substring(1))
                unifiedHunk.baseCount++
                unifiedHunk.modifiedCount++
                baseIdx++
                modifiedIdx++
            } else if (baseLine.startsWith("  ")) {
                unifiedLines.add(baseLine.substring(1))
                unifiedHunk.baseCount++
                unifiedHunk.modifiedCount++
                baseIdx++
            } else if (modifiedLine.startsWith("  ")) {
                unifiedLines.add(modifiedLine.substring(1))
                unifiedHunk.baseCount++
                unifiedHunk.modifiedCount++
                modifiedIdx++
            } else {
                throw RuntimeException("Invalid context patch: $baseLine")
            }
        }
        unifiedHunk.lines = unifiedLines
        return unifiedHunk
    }

    /**
     * Reads unified diff hunks.
     */
    @Throws(IOException::class)
    private fun readPatchContent(patch: SinglePatch) {
        val base = readPatchLine()
        if (base == null || !base.startsWith("--- ")) throw RuntimeException("Invalid unified diff header: " + base!!)
        val modified = readPatchLine()
        if (modified == null || !modified.startsWith("+++ ")) throw RuntimeException("Invalid unified diff header: " + modified!!)
        if (!patch.hasTargetPath()) {
            computeTargetPath(base, modified, patch)
        }

        val hunks = ArrayList<Hunk>()
        var hunk: Hunk? = null

        while (true) {
            val line = readPatchLine()
            if (line == null || line.isEmpty() || line.startsWith("Index:")) {
                unreadPatchLine()
                break
            }
            val c = line[0]
            if (c == '@') {
                hunk = Hunk()
                parseRange(hunk, line)
                hunks.add(hunk)
            } else if (c == ' ' || c == '+' || c == '-') {
                hunk!!.lines.add(line)
            } else if (line == Hunk.ENDING_NEWLINE) {
                patch.noEndingNewline = true
            } else {
                // first seen in mercurial diffs: be optimistic, this is probably the end of this patch
                unreadPatchLine()
                break
            }
        }
        patch.hunks = hunks.toArray(arrayOfNulls<Hunk>(hunks.size))
    }

    private fun computeTargetPath(base: String, modified: String, patch: SinglePatch) {
        var old = base
        var new = modified
        old = old.substring("+++ ".length)
        new = new.substring("--- ".length)
        // first seen in mercurial diffs: base and modified paths are different: base starts with "a/" and modified starts with "b/"
        if ((old == "/dev/null" || old.startsWith("a/")) && (new == "/dev/null" || new.startsWith("b/"))) {
            if (old.startsWith("a/")) old = old.substring(2)
            if (new.startsWith("b/")) new = new.substring(2)
        }
        old = old.substringBefore('\t').trim { it <= ' ' }
        if (old == "/dev/null") {
            // "/dev/null" in base indicates a new file
            patch.targetPath = new.substringBefore('\t').trim { it <= ' ' }
            patch.mode = Mode.ADD
        } else {
            patch.targetPath = old
            patch.mode = if (new == "/dev/null") Mode.DELETE else Mode.CHANGE
        }
    }

    private fun parseRange(hunk: Hunk, range: String) {
        val m = unifiedRangePattern.matchEntire(range)
            ?: throw RuntimeException("Invalid unified diff range: $range")
        hunk.baseStart = Integer.parseInt(m.groupValues[1])
        hunk.baseCount = if (m.groupValues[2].isNotEmpty()) Integer.parseInt(m.groupValues[2].substring(1)) else 1
        hunk.modifiedStart = Integer.parseInt(m.groupValues[3])
        hunk.modifiedCount = if (m.groupValues[4].isNotEmpty()) Integer.parseInt(m.groupValues[4].substring(1)) else 1
    }

    private fun parseContextRange(hunk: Hunk, range: String) {
        if (range[0] == '*') {
            val m = baseRangePattern.matchEntire(range)
                ?: throw RuntimeException("Invalid context diff range: $range")
            hunk.baseStart = Integer.parseInt(m.groupValues[1])
            hunk.baseCount = if (m.groupValues[2].isNotEmpty()) Integer.parseInt(m.groupValues[2].substring(1)) else 1
            hunk.baseCount -= hunk.baseStart - 1
        } else {
            val m = modifiedRangePattern.matchEntire(range)
                ?: throw RuntimeException("Invalid context diff range: $range")
            hunk.modifiedStart = Integer.parseInt(m.groupValues[1])
            hunk.modifiedCount = if (m.groupValues[2].isNotEmpty()) Integer.parseInt(m.groupValues[2].substring(1)) else 1
            hunk.modifiedCount -= hunk.modifiedStart - 1
        }
    }

    @Throws(IOException::class)
    private fun readPatchLine(): String? {
        if (patchLineRead) {
            patchLine = patchReader!!.readLine()
        } else {
            patchLineRead = true
        }
        return patchLine
    }

    private fun unreadPatchLine() {
        patchLineRead = false
    }

    private fun computeContext(patches: List<SinglePatch>) {
        var bestContext = suggestedContext
        var bestContextMatched = 0
        context = suggestedContext
        while (context != null) {
            var patchedFiles = 0
            for (patch in patches) {
                try {
                    applyPatch(patch, true)
                    patchedFiles++
                } catch (e: Exception) {
                    // patch failed to apply
                }

            }
            if (patchedFiles > bestContextMatched) {
                bestContextMatched = patchedFiles
                bestContext = context
                if (patchedFiles == patches.size) break
            }
            context = context!!.parentFile
        }
        context = bestContext
    }

    internal fun computeTargetFile(patch: SinglePatch): File {
        if (!patch.hasTargetPath()) {
            patch.targetPath = context!!.absolutePath
        }
        return context?.takeIf { it.isFile } ?: File(context, patch.targetPath)
    }

    class SinglePatch {
        lateinit var targetPath: String
        fun hasTargetPath() = this::targetPath.isInitialized
        lateinit var hunks: Array<Hunk>
        lateinit var targetFile: File                  // computed later
        var noEndingNewline: Boolean = false            // resulting file should not end with a newline
        var binary: Boolean = false                  // binary patches contain one encoded Hunk
        lateinit var mode: Mode
    }

    enum class Mode {
        /** Update to existing file  */
        CHANGE,
        /** Adding a new file  */
        ADD,
        /** Deleting an existing file  */
        DELETE
    }

    enum class PatchStatus constructor(val success: Boolean) {
        Patched(true),
        Failure(false),
        Skipped(true),
        Fuzzed(true)
    }

    class PatchReport internal constructor(
        val status: PatchStatus,
        val hunkReports: List<HunkReport>
    )

    companion object {

        const val MAGIC = "# This patch file was generated by NetBeans IDE" // NOI18N

        fun create(patchFile: PatchFile, context: PatchContextProvider): ContextualPatch {
            return ContextualPatch(patchFile, context)
        }
    }
}
