package de.dominicsteinhoefel.pluggabl.simplification

import de.dominicsteinhoefel.pluggabl.expr.*
import de.dominicsteinhoefel.pluggabl.theories.HeapTheory
import de.dominicsteinhoefel.pluggabl.theories.HeapTheory.CREATED
import de.dominicsteinhoefel.pluggabl.theories.HeapTheory.STORE

object HeapSimplifier {
    private val SIMPLIFICATIONS: List<(SymbolicExpression) -> SymbolicExpression> =
        listOf(this::selectOfStore)

    private val SIMPLIFY =
        fun(heapExpr: SymbolicExpression) =
            SIMPLIFICATIONS.fold(heapExpr, { acc, elem -> elem(acc) })

    fun simplify(heapExpr: SymbolicExpression): SymbolicExpression =
        if (isHeapExpression(heapExpr)) heapExpr.accept(ExpressionReplacer(SIMPLIFY)) else heapExpr

    private fun isHeapExpression(expr: SymbolicExpression) =
        expr.accept(FunctionSymbolExpressionCollector()).any { HeapTheory.functions().contains(it) }

    private fun selectOfStore(heapExpr: SymbolicExpression) =
        if (heapExpr is FunctionApplication &&
            heapExpr.f is HeapTheory.Select &&
            heapExpr.args[0].let { it is FunctionApplication && it.f == STORE }
        ) {
            val storeApp = heapExpr.args[0] as FunctionApplication
            val o1 = heapExpr.args[1]
            val f1 = heapExpr.args[2] as FunctionApplication

            val h = storeApp.args[0]
            val o2 = storeApp.args[1]
            val f2 = storeApp.args[2] as FunctionApplication
            val x = storeApp.args[3]

            if (o1 == o2 && f1 == f2 && f2 != FunctionApplication(CREATED)) x
            else {
                ConditionalExpression.create(
                    And.create(
                        EqualityConstr.create(o1, o2),
                        EqualityConstr.create(f1, f2),
                        NegatedConstr.create(EqualityConstr.create(f2, FunctionApplication(CREATED)))
                    ),
                    x,
                    FunctionApplication(heapExpr.f, h, o1, f1)
                )
            }
        } else heapExpr
}