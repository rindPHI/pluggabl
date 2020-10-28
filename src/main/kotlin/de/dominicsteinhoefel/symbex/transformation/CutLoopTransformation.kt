package de.dominicsteinhoefel.symbex.transformation

import soot.*
import soot.jimple.IntConstant
import soot.jimple.Jimple
import soot.jimple.toolkits.annotation.logic.Loop
import soot.jimple.toolkits.annotation.logic.LoopFinder
import soot.util.Chain


/**
 * Cuts all loop back edges of loops in the body and anonymizes written variables in the loop body, both as first
 * action in the body as well as directly after the loop is left.
 *
 * @author Dominic Steinhoefel
 */
object CutLoopTransformation : BodyTransformer() {
    override fun internalTransform(b: Body?, phaseName: String?, options: MutableMap<String, String>?) {
        if (b == null) {
            return
        }

        val clazz = options?.get("class")
        val methodSig = options?.get("method")
        val methodsToTransform = arrayOf("<$clazz: $methodSig>")

        if (!methodsToTransform.contains(b.method.signature)) {
            return
        }

        for (loop in LoopFinder().getLoops(b)) {
            breakLoop(loop, b)
            anonymizeLoopVars(loop, b)
        }
    }

    private fun anonymizeLoopVars(loop: Loop, b: Body) {
        val writtenVars = loop.loopStatements.map { it.defBoxes }.flatten().map { it.value }
        if (writtenVars.any { it !is Local }) {
            throw NotImplementedError("Anonymization of written heap locations currently not implemented")
        }

        val freshVarMap = writtenVars.associateWith { freshVar(it as Local, b.locals) }
        freshVarMap.forEach { b.locals.addLast(it.value) }
        freshVarMap.map { Jimple.v().newAssignStmt(it.key, it.value) }.reversed()
            .forEach { b.units.insertAfter(it, loop.head) }
        freshVarMap.map { Jimple.v().newAssignStmt(it.key, it.value) }.reversed()
            .forEach {
                loop.head.unitBoxes.forEach { loopExit -> b.units.insertBefore(it, loopExit.unit) }
            }
    }

    private fun freshVar(forVar: Local, locals: Chain<Local>): Local {
        var curr = "_${forVar.name}"
        var i = 1
        while (locals.any { it.name == curr }) {
            curr += i
            i++
        }

        return Jimple.v().newLocal(curr, forVar.type)
    }

    private fun breakLoop(loop: Loop, b: Body) {
        val backjump = loop.backJumpStmt

        val exitMethod = Scene.v().getMethod("<java.lang.System: void exit(int)>")
        val exitNode = Jimple.v().newInvokeStmt(
            Jimple.v().newStaticInvokeExpr(exitMethod.makeRef(), IntConstant.v(0))
        )

        b.units.insertBefore(exitNode, backjump)
        b.units.remove(backjump)
    }

    fun getDeclaredOptions() = "class method"
}