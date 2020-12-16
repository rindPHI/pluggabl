package de.dominicsteinhoefel.pluggabl.expr

import de.dominicsteinhoefel.pluggabl.simplification.SymbolicStoreSimplifier
import java.util.*

class SymbolicExecutionState() {
    var constraints = SymbolicConstraintSet()
    var store: SymbolicStore = EmptyStore

    constructor(constraints: Collection<SymbolicConstraint>, store: SymbolicStore) : this() {
        this.constraints = SymbolicConstraintSet.from(constraints)
        this.store = store
    }

    fun isEmpty() = constraints.isEmpty() && store === EmptyStore

    fun addConstraint(c: SymbolicConstraint): SymbolicExecutionState =
        SymbolicExecutionState(constraints.add(StoreApplConstraint.create(store, c)), store)

    fun addAssignment(s: LocalVariable, e: SymbolicExpression) =
        SymbolicExecutionState(
            constraints, SymbolicStoreSimplifier.simplify(
                ParallelStore.create(
                    store,
                    StoreApplStore.create(store, ElementaryStore(s, e))
                )
            )
        )

    fun apply(other: SymbolicExecutionState) =
        SymbolicExecutionState(
            SymbolicConstraintSet.from(
                listOf(
                    constraints.map { oldC -> StoreApplConstraint.create(other.store, oldC) },
                    other.constraints
                ).flatten()
            ),
            ParallelStore.create(other.store, StoreApplStore.create(other.store, store))
        )

    fun simplify() = SymbolicExecutionState(
        constraints.simplify(),
        SymbolicStoreSimplifier.simplify(store)
    )

    override fun toString(): String {
        val sConstraints = "{${constraints.joinToString()}}"
        return "(${sConstraints}, ${store})"
    }

    override fun hashCode() = Objects.hash(SymbolicExecutionState::class, constraints, store)

    override fun equals(other: Any?) =
        (other as? SymbolicExecutionState).let { it?.constraints == constraints && it.store == store }

    companion object {
        fun merge(ses: List<SymbolicExecutionState>): SymbolicExecutionState {
            var result = ses[0]
            for (i in 1..ses.size - 1) {
                result = merge(result, ses[i])
            }
            return result
        }

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
    }
}