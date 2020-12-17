package de.dominicsteinhoefel.pluggabl.analysis

import soot.jimple.Stmt
import soot.toolkits.graph.UnitGraph

class Loop(
    val head: Stmt,
    val loopStatements: List<Stmt>,
    private val g: UnitGraph
) {
    val backJumpStatements = loopStatements.filter { g.getSuccsOf(it).contains(head) }
    val loopExits = loopStatements.filter { !g.getSuccsOf(it).all(loopStatements::contains) }.toSet()

    companion object {
        fun fromSootLoop(loop: soot.jimple.toolkits.annotation.logic.Loop, g: UnitGraph) =
            Loop(loop.head, loop.loopStatements, g)
    }
}