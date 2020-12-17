package de.dominicsteinhoefel.pluggabl.util

import soot.jimple.Stmt
import soot.jimple.toolkits.annotation.logic.Loop
import soot.toolkits.graph.UnitGraph

fun <E> List<E>.subList(from: Int) = this.subList(from, this.size)

fun Loop.getLoopExitsOrdered(g: UnitGraph) =
    loopStatements.filter { !g.getSuccsOf(it).all(loopStatements::contains) }.toSet()