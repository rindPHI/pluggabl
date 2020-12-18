package de.dominicsteinhoefel.pluggabl.expr

import de.dominicsteinhoefel.pluggabl.theories.HeapTheory
import de.dominicsteinhoefel.pluggabl.util.union

interface SymbolicExpressionsVisitor<T> {
    fun visit(e: Value): T
    fun visit(e: LocalVariable): T
    fun visit(e: FieldRef): T
    fun visit(e: ArrayRef): T
    fun visit(e: FunctionApplication): T
    fun visit(e: ConditionalExpression): T
    fun visit(e: StoreApplExpression): T
}

open class ExpressionCollector<T>(private val coll: (SymbolicExpression) -> Set<T>) :
    SymbolicExpressionsVisitor<Set<T>> {
    override fun visit(e: Value) = coll(e)
    override fun visit(e: LocalVariable) = coll(e)
    override fun visit(e: FunctionApplication) =
        union(coll(e), e.args.map { it.accept(this) }.flatten().toSet())

    override fun visit(e: FieldRef) = union(coll(e), e.obj.accept(this))
    override fun visit(e: ArrayRef) = union(coll(e), e.base.accept(this), e.index.accept(this))

    override fun visit(e: StoreApplExpression) = union(coll(e), e.target.accept(this))

    override fun visit(e: ConditionalExpression) =
        union(
            coll(e),
            e.vThen.accept(this),
            e.vElse.accept(this)
        )
}

class HeapExpressionInExpressionCollector() : ExpressionCollector<HeapExpression>({ e ->
    when (e) {
        is ArrayRef -> setOf(e)
        is FieldRef -> setOf(e)
        is FunctionApplication ->
            if (e.f is HeapTheory.Select) ((e.args[2] as FunctionApplication).f).let { field ->
                if (field == HeapTheory.ARRAY_FIELD) setOf(
                    ArrayRef(
                        e.args[1],
                        (e.args[2] as FunctionApplication).args[0]
                    )
                )
                else setOf(FieldRef(e.args[1], field as Field))
            } else emptySet()
        else -> emptySet()
    }
})

class LocalVariableExpressionCollector() : ExpressionCollector<LocalVariable>({ e ->
    when (e) {
        is LocalVariable -> setOf(e)
        is StoreApplExpression -> e.applied.accept(LocalVariableStoreCollector())
        is ConditionalExpression -> e.condition.accept(LocalVariableConstraintCollector())
        else -> emptySet()
    }
})

class FunctionSymbolExpressionCollector() : ExpressionCollector<FunctionSymbol>({ e ->
    when (e) {
        is FunctionApplication -> setOf(e.f)
        is StoreApplExpression -> e.applied.accept(FunctionSymbolStoreCollector())
        is ConditionalExpression -> e.condition.accept(FunctionSymbolConstraintCollector())
        else -> emptySet()
    }
})

open class ExpressionReplacer(private val repl: (SymbolicExpression) -> SymbolicExpression) :
    SymbolicExpressionsVisitor<SymbolicExpression> {
    override fun visit(e: Value) = repl(e)
    override fun visit(e: LocalVariable) = repl(e)
    override fun visit(e: FunctionApplication) = repl(FunctionApplication(e.f, e.args.map { it.accept(this) }))
    override fun visit(e: FieldRef) = repl(FieldRef(e.obj.accept(this), e.field))
    override fun visit(e: ArrayRef) = repl(ArrayRef(e.base.accept(this), e.index.accept(this)))

    override fun visit(e: StoreApplExpression) =
        repl(StoreApplExpression.create(e.applied, e.target.accept(this)))

    override fun visit(e: ConditionalExpression) =
        repl(
            ConditionalExpression.create(
                e.condition,
                e.vThen.accept(this),
                e.vElse.accept(this)
            )
        )

}

class SymbolReplaceExprVisitor(val replMap: Map<LocalVariable, SymbolicExpression>) : ExpressionReplacer({ e ->
    when (e) {
        is LocalVariable -> replMap[e] ?: e
        is ConditionalExpression -> ConditionalExpression.create(
            e.condition.accept(SymbolReplaceConstrVisitor(replMap)),
            e.vThen,
            e.vElse
        )
        is StoreApplExpression ->
            throw UnsupportedOperationException("Symbol replacement below the scope of store application unsupported, normalize first")
        else -> e
    }
})