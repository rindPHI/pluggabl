package de.dominicsteinhoefel.symbex.expr

import de.dominicsteinhoefel.symbex.simplification.SymbolicStoreSimplifier

class SymbolicExecutionState() {
    var constraints = SymbolicConstraintSet()
    var store: SymbolicStore = EmptyStore

    constructor(constraints: Collection<SymbolicConstraint>, store: SymbolicStore) : this() {
        this.constraints = SymbolicConstraintSet().let { it.addAll(constraints); it }
        this.store = store
    }

    fun addConstraint(c: SymbolicConstraint) = constraints.add(StoreApplConstraint.create(store, c))

    fun addAssignment(s: Symbol, e: SymbolicExpression) {
        store = SymbolicStoreSimplifier.simplify(
            ParallelStore.create(
                store,
                StoreApplStore.create(store, ElementaryStore(s, e))
            )
        )
    }

    fun apply(other: SymbolicExecutionState): SymbolicExecutionState {
        SymbolicConstraintSet().let {
            it.addAll(constraints.map { oldC -> StoreApplConstraint.create(other.store, oldC) })
            it.addAll(other.constraints)
            constraints = it
        }
        store = ParallelStore.create(other.store, StoreApplStore.create(other.store, store))
        return this
    }

    fun mergeFromSESs(ses1: SymbolicExecutionState, ses2: SymbolicExecutionState) {
        val mSES = merge(ses1, ses2)
        constraints = mSES.constraints
        store = mSES.store
    }

    fun simplify() = simplify(this).let {
        constraints = it.constraints
        store = it.store
    }

    override fun toString(): String {
        val sConstraints = "{${constraints.joinToString()}}"
        return "(${sConstraints}, ${store})"
    }

    companion object {
        fun merge(ses1: SymbolicExecutionState, ses2: SymbolicExecutionState): SymbolicExecutionState {
            val mc1 = ses1.constraints.fold(True, { a: SymbolicConstraint, b -> And.create(a, b) })
            val mc2 = ses2.constraints.fold(True, { a: SymbolicConstraint, b -> And.create(a, b) })
            val constraints = linkedSetOf(Or.create(mc1, mc2))

            val simpSt1 = SymbolicStoreSimplifier.simplify(ses1.store)
            val simpSt2 = SymbolicStoreSimplifier.simplify(ses2.store)
            val subst1 = SymbolicStoreSimplifier.storeToSubst(simpSt1)
            val subst2 = SymbolicStoreSimplifier.storeToSubst(simpSt2)

            val store = linkedSetOf(subst1.keys, subst2.keys).flatten()
                .map {
                    ElementaryStore(
                        it,
                        ConditionalExpression.create(mc1, subst1[it] ?: it, subst2[it] ?: it)
                    )
                }
                .fold(EmptyStore, { acc: SymbolicStore, elem -> ParallelStore.create(acc, elem) })

            return SymbolicExecutionState(constraints, store)
        }

        fun simplify(ses: SymbolicExecutionState) =
            SymbolicExecutionState(
                ses.constraints.map { SymbolicStoreSimplifier.simplify(it) }.toList(),
                SymbolicStoreSimplifier.simplify(ses.store)
            )
    }
}