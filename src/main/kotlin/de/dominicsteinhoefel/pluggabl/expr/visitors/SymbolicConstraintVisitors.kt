package de.dominicsteinhoefel.pluggabl.expr

import de.dominicsteinhoefel.pluggabl.util.union

interface SymbolicConstraintVisitor<T> {
    fun visit(c: StoreApplConstraint): T
    fun visit(c: True): T
    fun visit(c: False): T
    fun visit(c: GreaterConstr): T
    fun visit(c: EqualityConstr): T
    fun visit(c: NegatedConstr): T
    fun visit(c: Or): T
    fun visit(c: And): T
    fun visit(c: GreaterEqualConstr): T
}

class SymbolReplaceConstrVisitor(val replMap: Map<LocalVariable, SymbolicExpression>) :
    SymbolicConstraintVisitor<SymbolicConstraint> {
    override fun visit(c: True): SymbolicConstraint = c
    override fun visit(c: False): SymbolicConstraint = c

    override fun visit(c: GreaterConstr) =
        SymbolReplaceExprVisitor(replMap).let {
            GreaterConstr(c.left.accept(it), c.right.accept(it))
        }

    override fun visit(c: GreaterEqualConstr) =
        SymbolReplaceExprVisitor(replMap).let {
            GreaterEqualConstr(c.left.accept(it), c.right.accept(it))
        }

    override fun visit(c: EqualityConstr) =
        SymbolReplaceExprVisitor(replMap).let {
            EqualityConstr.create(c.left.accept(it), c.right.accept(it))
        }

    override fun visit(c: NegatedConstr): SymbolicConstraint =
        NegatedConstr.create(c.constr.accept(this))

    override fun visit(c: Or): SymbolicConstraint =
        Or.create(c.left.accept(this), c.right.accept(this))

    override fun visit(c: And): SymbolicConstraint =
        And.create(c.left.accept(this), c.right.accept(this))

    override fun visit(c: StoreApplConstraint): SymbolicConstraint =
        throw UnsupportedOperationException("Symbol replacement below the scope of store application unsupported, normalize first")
}

open class ConstraintCollector<T>(private val coll: (SymbolicConstraint) -> Set<T>) :
    SymbolicConstraintVisitor<Set<T>> {
    override fun visit(c: True) = coll(c)
    override fun visit(c: False) = coll(c)
    override fun visit(c: GreaterConstr) = coll(c)
    override fun visit(c: EqualityConstr) = coll(c)
    override fun visit(c: GreaterEqualConstr) = coll(c)

    override fun visit(c: NegatedConstr) = union(coll(c), c.constr.accept(this))
    override fun visit(c: StoreApplConstraint) = union(coll(c), c.target.accept(this))
    override fun visit(c: Or) = union(coll(c), c.left.accept(this), c.right.accept(this))
    override fun visit(c: And) = union(coll(c), c.left.accept(this), c.right.accept(this))
}

class LocalVariableConstraintCollector() : ConstraintCollector<LocalVariable>({ c ->
    when (c) {
        is BinarySymbolicConstraint ->
            LocalVariableExpressionCollector().let {
                union(c.left().accept(it), c.right().accept(it))
            }
        is StoreApplConstraint -> c.applied.accept(LocalVariableStoreCollector())
        else -> emptySet()
    }
})

class HeapExpressionConstraintCollector() : ConstraintCollector<HeapExpression>({ c ->
    when (c) {
        is BinarySymbolicConstraint ->
            HeapExpressionInExpressionCollector().let {
                union(c.left().accept(it), c.right().accept(it))
            }
        is StoreApplConstraint -> c.applied.accept(HeapExpressionStoreCollector())
        else -> emptySet()
    }
})

class FunctionSymbolConstraintCollector() : ConstraintCollector<FunctionSymbol>({ c ->
    when (c) {
        is BinarySymbolicConstraint ->
            FunctionSymbolExpressionCollector().let {
                union(c.left().accept(it), c.right().accept(it))
            }
        is StoreApplConstraint -> c.applied.accept(FunctionSymbolStoreCollector())
        else -> emptySet()
    }
})

open class ConstraintReplacer(private val repl: (SymbolicConstraint) -> SymbolicConstraint) :
    SymbolicConstraintVisitor<SymbolicConstraint> {
    override fun visit(c: True) = repl(c)
    override fun visit(c: False) = repl(c)
    override fun visit(c: GreaterConstr) = repl(c)
    override fun visit(c: EqualityConstr) = repl(c)
    override fun visit(c: GreaterEqualConstr) = repl(c)

    override fun visit(c: NegatedConstr) = repl(NegatedConstr.create(c.constr.accept(this)))
    override fun visit(c: Or) = repl(Or.create(c.left.accept(this), c.right.accept(this)))
    override fun visit(c: And) = repl(And.create(c.left.accept(this), c.right.accept(this)))

    override fun visit(c: StoreApplConstraint) = repl(StoreApplConstraint.create(c.applied, c.target.accept(this)))

}