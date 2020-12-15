package de.dominicsteinhoefel.pluggabl.simplification

import de.dominicsteinhoefel.pluggabl.expr.*

object SymbolicExpressionSimplifier {
    private val SIMPLIFICATIONS: List<(SymbolicExpression) -> SymbolicExpression> =
        listOf(this::applyStores, this::applyConstraintSimplifications, HeapSimplifier::simplify)

    fun simplify(expression: SymbolicExpression): SymbolicExpression =
        SIMPLIFICATIONS.fold(expression, { acc, elem -> elem(acc) })

    fun applyConstraintSimplifications(expression: SymbolicExpression): SymbolicExpression =
        expression.accept(ExpressionReplacer { e ->
            when (e) {
                is ConditionalExpression -> ConditionalExpression.create(
                    SymbolicConstraintSimplifier.simplify(e.condition),
                    e.vThen,
                    e.vElse
                )
                else -> e
            }
        })

    fun applyStores(expression: SymbolicExpression): SymbolicExpression =
        when (expression) {
            is Value, is LocalVariable -> expression
            is FunctionApplication -> FunctionApplication(expression.f, expression.args.map { applyStores(it) })
            is ConditionalExpression -> ConditionalExpression.create(
                SymbolicConstraintSimplifier.applyStores(expression.condition),
                applyStores(expression.vThen),
                applyStores(expression.vElse)
            )

            is StoreApplExpression -> {
                val subst = SymbolicStoreSimplifier.storeToSubst(SymbolicStoreSimplifier.simplify(expression.applied))
                val visitor = SymbolReplaceExprVisitor(subst)
                applyStores(expression.target).accept(visitor)
            }
        }
}