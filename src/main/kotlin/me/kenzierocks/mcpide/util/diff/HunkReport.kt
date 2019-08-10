package me.kenzierocks.mcpide.util.diff

class HunkReport internal constructor(
    val status: ContextualPatch.PatchStatus,
    val failure: Throwable?,
    val index: Int,
    val fuzz: Int,
    val hunkID: Int,
    val hunk: Hunk? = null
) {

    fun hasFailed(): Boolean {
        return status === ContextualPatch.PatchStatus.Failure
    }

}