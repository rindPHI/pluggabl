package de.dominicsteinhoefel.pluggabl.simplification

import de.dominicsteinhoefel.pluggabl.expr.*
import kotlin.collections.LinkedHashMap
import kotlin.collections.LinkedHashSet

object SymbolicStoreSimplifier {
    fun simplify(store: SymbolicStore): SymbolicStore =
        dropEffectlessElementaries(toParallelNormalForm(store), LinkedHashSet())

    private fun dropEffectlessElementaries(
        store: SymbolicStore,
        overwrittenLocs: LinkedHashSet<LocalVariable>
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
            is ElementaryStore -> ElementaryStore(store.lhs, SymbolicExpressionSimplifier.applyStores(store.rhs))
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

    fun storeToSubst(store: SymbolicStore): LinkedHashMap<LocalVariable, SymbolicExpression> =
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