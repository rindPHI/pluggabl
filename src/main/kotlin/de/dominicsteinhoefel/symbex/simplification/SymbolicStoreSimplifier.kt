package de.dominicsteinhoefel.symbex.simplification

import de.dominicsteinhoefel.symbex.expr.*

object SymbolicStoreSimplifier {
    fun simplify(store: SymbolicStore): SymbolicStore =
        dropEffectlessElementaries(toParallelNormalForm(store), LinkedHashSet())

    fun simplify(constraint: SymbolicConstraint): SymbolicConstraint =
        when (constraint) {
            is True, is False -> constraint
            is NegatedConstr -> NegatedConstr.create(simplify(constraint.constr))
            is And -> And.create(simplify(constraint.left), simplify(constraint.right))
            is Or -> Or.create(simplify(constraint.left), simplify(constraint.right))
            is GreaterConstr -> GreaterConstr(simplify(constraint.left), simplify(constraint.right))
            is GreaterEqualConstr -> GreaterEqualConstr(simplify(constraint.left), simplify(constraint.right))
            is EqualityConstr -> EqualityConstr.create(simplify(constraint.left), simplify(constraint.right))

            is StoreApplConstraint -> {
                val subst = storeToSubst(simplify(constraint.applied))
                val visitor = SymbolReplaceConstrVisitor(subst)
                simplify(constraint.target).accept(visitor)
            }
        }

    fun simplify(expression: SymbolicExpression): SymbolicExpression =
        when (expression) {
            is Value, is Symbol -> expression
            is ConditionalExpression -> ConditionalExpression.create(
                simplify(expression.condition),
                simplify(expression.vThen),
                simplify(expression.vElse)
            )
            is AdditionExpr -> AdditionExpr(simplify(expression.left), simplify(expression.right))
            is MultiplicationExpr -> MultiplicationExpr(simplify(expression.left), simplify(expression.right))
            is LengthExpression -> LengthExpression(simplify(expression.of))
            is ArrayReference -> ArrayReference(expression.array, simplify(expression.index))
            is MethodInvocationExpression -> MethodInvocationExpression(
                simplify(expression.obj),
                expression.method,
                expression.declaringClass,
                expression.type,
                expression.args.map { simplify(it) }
            )

            is StoreApplExpression -> {
                val subst = storeToSubst(simplify(expression.applied))
                val visitor = SymbolReplaceExprVisitor(subst)
                simplify(expression.target).accept(visitor)
            }
        }

    private fun dropEffectlessElementaries(
        store: SymbolicStore,
        overwrittenLocs: LinkedHashSet<Symbol>
    ): SymbolicStore =
        when (store) {
            is EmptyStore -> store
            is ElementaryStore -> {
                if (overwrittenLocs.contains(store.lhs)) {
                    EmptyStore
                } else {
                    overwrittenLocs += store.lhs
                    store
                }
            }
            is ParallelStore -> {
                val rhs = dropEffectlessElementaries(store.rhs, overwrittenLocs)
                val lhs = dropEffectlessElementaries(store.lhs, overwrittenLocs)
                ParallelStore.create(lhs, rhs)
            }
            is StoreApplStore -> throw UnsupportedOperationException("Store not in normal form")
        }

    private fun toParallelNormalForm(store: SymbolicStore): SymbolicStore =
        when (store) {
            is EmptyStore -> EmptyStore
            is ElementaryStore -> ElementaryStore(store.lhs, simplify(store.rhs))
            is ParallelStore -> ParallelStore.create(toParallelNormalForm(store.lhs), toParallelNormalForm(store.rhs))
            is StoreApplStore -> {
                when (store.target) {
                    is EmptyStore -> EmptyStore
                    is ParallelStore -> {
                        val lhs = toParallelNormalForm(StoreApplStore.create(store.applied, store.target.lhs))
                        val rhs = toParallelNormalForm(StoreApplStore.create(store.applied, store.target.rhs))
                        ParallelStore.create(lhs, rhs)
                    }
                    is ElementaryStore -> {
                        val replVisitor = SymbolReplaceExprVisitor(storeToSubst(simplify(store.applied)))
                        val newRhs = store.target.rhs.accept(replVisitor)
                        ElementaryStore(store.target.lhs, newRhs)
                    }
                    is StoreApplStore -> {
                        val lhs = toParallelNormalForm(store.applied)
                        val rhs = toParallelNormalForm(StoreApplStore.create(store.applied, store.target.applied))
                        ParallelStore.create(lhs, rhs)
                    }
                }
            }
        }

    fun storeToSubst(store: SymbolicStore): LinkedHashMap<Symbol, SymbolicExpression> =
        when (store) {
            is EmptyStore -> linkedMapOf()
            is ElementaryStore -> linkedMapOf(store.lhs to store.rhs)
            is ParallelStore -> {
                val lmap = storeToSubst(store.lhs)
                val rmap = storeToSubst(store.rhs)
                lmap.putAll(rmap)
                lmap
            }
            is StoreApplStore -> throw UnsupportedOperationException("Store not in normal form")
        }
}