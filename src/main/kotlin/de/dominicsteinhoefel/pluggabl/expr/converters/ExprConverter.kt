package de.dominicsteinhoefel.pluggabl.expr.converters

import de.dominicsteinhoefel.pluggabl.analysis.SymbolsManager
import de.dominicsteinhoefel.pluggabl.expr.FunctionApplication
import de.dominicsteinhoefel.pluggabl.expr.SymbolicExpression
import de.dominicsteinhoefel.pluggabl.theories.Theory
import de.dominicsteinhoefel.pluggabl.util.concat
import soot.jimple.IntConstant
import soot.jimple.NullConstant
import soot.jimple.internal.*

class ExprConverter(private val symbolsManager: SymbolsManager, private val theories: Set<Theory>) {
    companion object {
        /**
         * Simple expressions are constant values, local variables, or
         * composed arithmetic expressions of simple expressions. The latter
         * case is possible since field accesses are decomposed by the
         * conversion to Jimple; in the KeY system, for instance, more
         * complex decomposition rules are necessary and simple expressions
         * are only constants or variables.
         */
        fun isSimpleExpression(value: soot.Value): Boolean =
            when (value) {
                is JimpleLocal, is IntConstant, is JAddExpr, is JSubExpr, is JMulExpr -> true
                else -> false
            }
    }

    fun convert(value: soot.Value): SymbolicExpression =
        when (value) {
            is JimpleLocal -> symbolsManager.localVariableFor(value)
            is NullConstant -> de.dominicsteinhoefel.pluggabl.expr.NullConstant
            is AbstractInstanceInvokeExpr -> {
                FunctionApplication(
                    symbolsManager.getMethodResultSymbol(value.methodRef),
                    concat(convert(value.base), value.args.map { convert(it) })
                )
            }
            is JStaticInvokeExpr -> {
                FunctionApplication(
                    symbolsManager.getMethodResultSymbol(value.methodRef),
                    value.args.map { convert(it) }.toList()
                )
            }
            else -> theories.filter { it.isResponsibleFor(value) }
                .also { if (it.size != 1) throw IllegalArgumentException("No theory/translation found for operator ${value::class}") }[0]
                .translate(value, value.useBoxes.map { it.value }.map { convert(it) })
        }
}