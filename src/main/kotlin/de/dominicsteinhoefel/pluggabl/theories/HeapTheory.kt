package de.dominicsteinhoefel.pluggabl.theories

import de.dominicsteinhoefel.pluggabl.expr.*
import soot.jimple.internal.JInstanceFieldRef

val HEAP_TYPE = Type("Heap")
val FIELD_TYPE = Type("Field")

val HEAP_VAR = LocalVariable("heap", HEAP_TYPE)
val STORE = FunctionSymbol("store", HEAP_TYPE, HEAP_TYPE, OBJECT_TYPE, FIELD_TYPE, ANY_TYPE)
val CREATE = FunctionSymbol("create", HEAP_TYPE, HEAP_TYPE, OBJECT_TYPE)
val ANON = FunctionSymbol("anon", HEAP_TYPE, LOCATION_SET_TYPE, HEAP_TYPE)

val CREATED = FunctionSymbol("<java.lang.Object: boolean <created>", FIELD_TYPE)
val ARRAY_FIELD = FunctionSymbol("arr", FIELD_TYPE, INT_TYPE)
val ARRAY_LENGTH = FunctionSymbol("length", INT_TYPE, OBJECT_TYPE)

val HEAP_SYMBOLS = listOf(STORE, CREATE, ANON, CREATED, ARRAY_FIELD, ARRAY_LENGTH)

class Select(type: Type) : FunctionSymbol("select", type, HEAP_TYPE, OBJECT_TYPE, FIELD_TYPE)

object HeapSimplifier {
    private val SIMPLIFICATIONS: List<(SymbolicExpression) -> SymbolicExpression> =
        listOf(this::selectOfStore)

    private val SIMPLIFY =
        fun(heapExpr: SymbolicExpression) =
            SIMPLIFICATIONS.fold(heapExpr, { acc, elem -> elem(acc) })

    fun simplify(heapExpr: SymbolicExpression): SymbolicExpression =
        heapExpr.accept(ExpressionReplacer(SIMPLIFY))

    fun isHeapExpression(expr: SymbolicExpression) =
        expr.accept(FunctionSymbolExpressionCollector()).intersect(HEAP_SYMBOLS).isNotEmpty()

    private fun selectOfStore(heapExpr: SymbolicExpression) =
        if (heapExpr is FunctionApplication &&
            heapExpr.f is Select &&
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