package de.dominicsteinhoefel.pluggabl.theories

import de.dominicsteinhoefel.pluggabl.expr.*
import de.dominicsteinhoefel.pluggabl.theories.IntTheory.INT_TYPE
import de.dominicsteinhoefel.pluggabl.theories.LocationSetTheory.LOCATION_SET_TYPE
import soot.Value

object HeapTheory : Theory {
    val HEAP_TYPE = Type("Heap")
    val FIELD_TYPE = Type("Field")

    val HEAP_VAR = LocalVariable("heap", HEAP_TYPE)

    val STORE = FunctionSymbol("store", HEAP_TYPE, HEAP_TYPE, OBJECT_TYPE, FIELD_TYPE, ANY_TYPE)
    val CREATE = FunctionSymbol("create", HEAP_TYPE, HEAP_TYPE, OBJECT_TYPE)
    val ANON = FunctionSymbol("anon", HEAP_TYPE, LOCATION_SET_TYPE, HEAP_TYPE)

    val CREATED = FunctionSymbol("<java.lang.Object: boolean <created>", FIELD_TYPE, unique = true)
    val ARRAY_FIELD = FunctionSymbol("arr", FIELD_TYPE, INT_TYPE, unique = true)
    val ARRAY_LENGTH = FunctionSymbol("length", INT_TYPE, OBJECT_TYPE)

    override fun getType() = HEAP_TYPE
    override fun getSootType(): soot.Type? = null

    override fun functions() = setOf(STORE, CREATE, ANON, CREATED, ARRAY_FIELD, ARRAY_LENGTH)

    override fun isResponsibleFor(jimpleExpr: Value) = false

    override fun translate(jimpleExpr: Value, subs: List<SymbolicExpression>): SymbolicExpression {
        throw UnsupportedOperationException("Heap theory does not correspond to bytecode expressions.")
    }

    class Select private constructor(type: Type) : FunctionSymbol("select", type, HEAP_TYPE, OBJECT_TYPE, FIELD_TYPE) {
        companion object {
            private val cache = LinkedHashMap<Type, Select>()
            fun create(type: Type) =
                cache[type] ?: Select(type).also { cache[type] = it }
        }
    }
}