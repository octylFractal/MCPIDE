package me.kenzierocks.mcpide

import java.util.LinkedHashMap

fun <K, V> lru(size: Int, func: (K) -> V): (K) -> V {
    val cache = LRUCache<K, V>(size)
    return { k ->
        cache.computeIfAbsent(k, func)
    }
}

fun <K, V> lruAndCache(size: Int, func: (K) -> V): Pair<(K) -> V, LRUCache<K, V>> {
    if (size == 0) {
        return func.to(LRUCache(0))
    }
    val cache = LRUCache<K, V>(size)
    return { k: K ->
        cache.computeIfAbsent(k, func)
    }.to(cache)
}

class LRUCache<K, V>(val maxSize: Int) : LinkedHashMap<K, V>(maxSize * 4 / 3, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
        return size > maxSize
    }
}