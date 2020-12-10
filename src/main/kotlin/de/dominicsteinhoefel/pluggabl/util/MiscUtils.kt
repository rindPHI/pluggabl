package de.dominicsteinhoefel.pluggabl.util

fun assertParams(cond: Boolean, msg: String) {
    if (!cond) throw IllegalArgumentException(msg)
}

fun <A> union(vararg s: Set<A>): Set<A> =
    s.fold(emptySet(), { acc, elem -> setOf(acc, elem).flatten().toSet() })