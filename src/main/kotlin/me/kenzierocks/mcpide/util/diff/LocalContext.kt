package me.kenzierocks.mcpide.util.diff

import java.io.IOException

internal class LocalContext(private val contextualPatch: ContextualPatch) : PatchContextProvider {

    @Throws(IOException::class)
    override fun getData(patch: ContextualPatch.SinglePatch): MutableList<String>? {
        patch.targetFile = contextualPatch.computeTargetFile(patch)
        return when {
            !patch.targetFile.exists() || patch.binary -> null
            else -> contextualPatch.readFile(patch.targetFile).toMutableList()
        }
    }

    @Throws(IOException::class)
    override fun setData(patch: ContextualPatch.SinglePatch, data: List<String>) {
        contextualPatch.backup(patch.targetFile)
        contextualPatch.writeFile(patch, data)
    }

}
