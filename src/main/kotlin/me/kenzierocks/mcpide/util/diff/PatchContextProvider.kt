package me.kenzierocks.mcpide.util.diff

import java.io.IOException

interface PatchContextProvider {

    @Throws(IOException::class)
    fun getData(patch: ContextualPatch.SinglePatch): MutableList<String>?

    @Throws(IOException::class)
    fun setData(patch: ContextualPatch.SinglePatch, data: List<String>)

}
