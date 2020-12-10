package de.dominicsteinhoefel.pluggabl.simplification

import de.dominicsteinhoefel.pluggabl.expr.*
import de.dominicsteinhoefel.pluggabl.theories.HeapSimplifier

object SymbolicExpressionSimplifier {
    fun simplify(expression: SymbolicExpression): SymbolicExpression =
        applyStores(expression).let {
            if (HeapSimplifier.isHeapExpression(expression)) HeapSimplifier.simplify(it)
            else it
        }

    fun applyStores(expression: SymbolicExpression): SymbolicExpression =
        when (expression) {
            is Value, is LocalVariable -> expression
            is FunctionApplication -> FunctionApplication(expression.f, expression.args.map { applyStores(it) })
            is ConditionalExpression -> ConditionalExpression.create(
                SymbolicConstraintSimplifier.applyStores(expression.condition),
                applyStores(expression.vThen),
                applyStores(expression.vElse)
            )
            is AdditionExpr -> AdditionExpr(applyStores(expression.left), applyStores(expression.right))
            is MultiplicationExpr -> MultiplicationExpr(applyStores(expression.left), applyStores(expression.right))
            is LengthExpression -> LengthExpression(applyStores(expression.of))
            is ArrayReference -> ArrayReference(expression.array, applyStores(expression.index))
            is MethodInvocationExpression -> MethodInvocationExpression(
                applyStores(expression.obj),
                expression.method,
                expression.declaringClass,
                expression.type,
                expression.args.map { applyStores(it) }
            )

            is StoreApplExpression -> {
                val subst = SymbolicStoreSimplifier.storeToSubst(SymbolicStoreSimplifier.simplify(expression.applied))
                val visitor = SymbolReplaceExprVisitor(subst)
                applyStores(expression.target).accept(visitor)
            }
        }
}