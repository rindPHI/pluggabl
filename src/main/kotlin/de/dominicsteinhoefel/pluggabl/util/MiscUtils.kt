package de.dominicsteinhoefel.pluggabl.util

fun assertParams(cond: Boolean, msg: String) {
    if (!cond) throw IllegalArgumentException(msg)
}

fun <A> concat(elem: A, vararg s: List<A>): List<A> =
    s.fold(listOf(elem), { acc, elem -> setOf(acc, elem).flatten() })

fun <A> concat(vararg elem: A, s: List<A>): List<A> =
    listOf(elem.toList(), s).flatten()

fun <A> concat(vararg s: List<A>): List<A> =
    s.fold(emptyList(), { acc, elem -> setOf(acc, elem).flatten() })

fun <A> union(vararg s: Set<A>): Set<A> =
    s.fold(emptySet(), { acc, elem -> setOf(acc, elem).flatten().toSet() })