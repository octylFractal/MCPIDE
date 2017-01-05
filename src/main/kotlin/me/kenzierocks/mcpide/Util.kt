package me.kenzierocks.mcpide

/**
 * Marker for immediately invoked function expressions.
 */
inline fun <O> IIFE(iife: (() -> O)) = iife()