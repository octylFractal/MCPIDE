package me.kenzierocks.mcpide.util.diff

object PatchUtils {

    internal fun similar(patch: ContextualPatch, target: String, hunk: String, lineType: Char): Boolean {
        var target = target
        var hunk = hunk
        if (patch.c14nAccess) {
            if (patch.c14nWhitespace) {
                target = target.replace("[\t| ]+".toRegex(), " ")
                hunk = hunk.replace("[\t| ]+".toRegex(), " ")
            }
            val t = target.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val h = hunk.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

            //don't check length, changing any modifier to default (removing it) will change length
            var targetIndex = 0
            var hunkIndex = 0
            while (targetIndex < t.size && hunkIndex < h.size) {
                val isTargetAccess = isAccess(t[targetIndex])
                val isHunkAccess = isAccess(h[hunkIndex])
                if (isTargetAccess || isHunkAccess) {
                    //Skip access modifiers
                    if (isTargetAccess) {
                        targetIndex++
                    }
                    if (isHunkAccess) {
                        hunkIndex++
                    }
                    continue
                }
                val hunkPart = h[hunkIndex]
                val targetPart = t[targetIndex]
                val labels = isLabel(targetPart) && isLabel(hunkPart)
                if (!labels && targetPart != hunkPart) {
                    return false
                }
                hunkIndex++
                targetIndex++
            }
            return h.size == hunkIndex && t.size == targetIndex
        }
        return if (patch.c14nWhitespace) {
            target.replace("[\t| ]+".toRegex(), " ") == hunk.replace("[\t| ]+".toRegex(), " ")
        } else {
            target == hunk
        }
    }

    private fun isAccess(data: String): Boolean {
        return data.equals("public", ignoreCase = true) ||
            data.equals("private", ignoreCase = true) ||
            data.equals("protected", ignoreCase = true) ||
            data.equals("final", ignoreCase = true)
    }

    private fun isLabel(data: String): Boolean { //Damn FernFlower
        return data.startsWith("label")
    }

}
