package me.kenzierocks.mcpide.fx

import com.google.common.base.Preconditions.checkElementIndex
import javafx.collections.ListChangeListener
import javafx.collections.ObservableList
import javafx.collections.transformation.TransformationList
import java.util.ArrayList
import java.util.Collections

class MappingList<O, N>(val originalList: ObservableList<out O>,
                        val mappingFunc: (O) -> N,
                        val removeTest: (N) -> Boolean = { true }) : TransformationList<N, O>(originalList) {
    private val storageList = ArrayList<N?>(originalList.size)

    init {
        setFromOriginal()
    }

    private fun ensureCapacity(capacity: Int) {
        if (storageList.size < capacity) {
            val diff = capacity - storageList.size
            storageList.ensureCapacity(capacity)
            storageList.addAll(Collections.nCopies(diff, null))
        }
    }

    private fun setFromOriginal() {
        ensureCapacity(originalList.size)
        originalList.forEachIndexed { i, o ->
            storageList[i] = mappingFunc(o)
        }
    }

    override val size: Int
        get() = originalList.size

    override fun get(index: Int): N {
        checkElementIndex(index, size, "get")
        return storageList[index]!!
    }

    // direct mapping -- no index changes
    override fun getSourceIndex(index: Int) = index

    fun invalidateCaches() {
        setFromOriginal()
    }

    override fun sourceChanged(c: ListChangeListener.Change<out O>?) {
        if (c == null) return
        // fire a new change similar to the one that came in
        beginChange()
        while (c.next()) {
            if (c.wasPermutated()) {
                val permRange = c.from..(c.to - 1)
                val permArray = IntArray(permRange.count())
                for (i in permRange) {
                    val perm = c.getPermutation(i)
                    val tmp = storageList[i]
                    storageList[i] = storageList[perm]
                    storageList[perm] = tmp
                    permArray[i - c.from] = perm
                }
                nextPermutation(c.from, c.to, permArray)
            } else {
                if (c.wasRemoved()) {
                    // removes section that was removed
                    val sbl = storageList.subList(c.from, c.from + c.removedSize)
                    (sbl.size - 1 downTo 0)
                        .filter { removeTest(sbl[it]!!) }
                        .forEach {
                            nextRemove(it, sbl[it])
                            sbl.removeAt(it)
                        }
                }
                if (c.wasAdded()) {
                    // the add should just be a range, we can insert the transformed list at from index
                    storageList.addAll(c.from, c.addedSubList.map(mappingFunc))
                    nextAdd(c.from, c.to)
                }
            }
        }
        endChange()
    }
}
