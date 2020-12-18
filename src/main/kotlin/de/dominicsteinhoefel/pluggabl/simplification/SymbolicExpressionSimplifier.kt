package de.dominicsteinhoefel.pluggabl.simplification

import de.dominicsteinhoefel.pluggabl.expr.*

object SymbolicExpressionSimplifier {
    private val SIMPLIFICATIONS: List<(SymbolicExpression) -> SymbolicExpression> =
        listOf(this::applyStores, this::applyConstraintSimplifications, HeapSimplifier::simplify)

    fun simplify(expression: SymbolicExpression): SymbolicExpression =
        SIMPLIFICATIONS.fold(expression, { acc, elem -> elem(acc) })

    fun applyStores(expression: SymbolicExpression): SymbolicExpression =
        expression.accept(ExpressionReplacer { e ->
            when (e) {
                is ConditionalExpression ->
                    ConditionalExpression.create(
                        SymbolicConstraintSimplifier.applyStores(e.condition),
                        e.vThen, e.vElse
                    )

                is StoreApplExpression -> {
                    val subst = SymbolicStoreSimplifier.storeToSubst(SymbolicStoreSimplifier.simplify(e.applied))
                    e.target.accept(SymbolReplaceExprVisitor(subst))
                }

                else -> e
            }
        })

    private fun applyConstraintSimplifications(expression: SymbolicExpression): SymbolicExpression =
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
}