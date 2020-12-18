package de.dominicsteinhoefel.pluggabl.simplification

import de.dominicsteinhoefel.pluggabl.expr.*
import de.dominicsteinhoefel.pluggabl.theories.HeapTheory

object SymbolicStoreSimplifier {
    private val SIMPLIFICATIONS: List<(SymbolicStore) -> SymbolicStore> =
        listOf(
            this::simplifyExpressions,
            this::toParallelNormalForm,
            this::dropEffectlessElementaries,
            this::heapUpdateToLastPosition
        )

    fun simplify(store: SymbolicStore): SymbolicStore =
        SIMPLIFICATIONS.fold(store, { acc, elem -> elem(acc) })

    fun simplifyExpressions(store: SymbolicStore): SymbolicStore =
        when (store) {
            EmptyStore -> store
            is ElementaryStore -> ElementaryStore(store.lhs, SymbolicExpressionSimplifier.simplify(store.rhs))
            is ParallelStore -> ParallelStore.create(simplifyExpressions(store.lhs), simplifyExpressions(store.rhs))
            is StoreApplStore -> StoreApplStore.create(
                simplifyExpressions(store.applied),
                simplifyExpressions(store.target)
            )
        }

    private fun heapUpdateToLastPosition(
        store: SymbolicStore
    ): SymbolicStore {
        val elems = store.elementaries()
        val heapElem = elems.firstOrNull { it.lhs == HeapTheory.HEAP_VAR }

        return if (heapElem == null) store
        else ParallelStore.create(
            listOf(
                elems.filterNot { it.lhs == HeapTheory.HEAP_VAR },
                listOf(heapElem)
            ).flatten()
        )
    }

    private fun dropEffectlessElementaries(store: SymbolicStore) =
        dropEffectlessElementaries(store, LinkedHashSet())

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
            is ElementaryStore -> ElementaryStore(store.lhs, store.rhs)
            is ParallelStore ->
                when (store.lhs) {
                    is ParallelStore -> toParallelNormalForm(
                        ParallelStore.create(
                            store.lhs.lhs,
                            ParallelStore.create(store.lhs.rhs, store.rhs)
                        )
                    )
                    else -> ParallelStore.create(toParallelNormalForm(store.lhs), toParallelNormalForm(store.rhs))
                }
            is StoreApplStore -> {
                when (store.target) {
                    is EmptyStore -> EmptyStore
                    is ParallelStore -> {
                        val lhs = toParallelNormalForm(StoreApplStore.create(store.applied, store.target.lhs))
                        val rhs = toParallelNormalForm(StoreApplStore.create(store.applied, store.target.rhs))
                        ParallelStore.create(lhs, rhs)
                    }
                    is ElementaryStore -> {
                        val replVisitor = SymbolReplaceExprVisitor(storeToSubst(toParallelNormalForm(store.applied)))
                        val newRhs = store.target.rhs.accept(replVisitor)
                        ElementaryStore(store.target.lhs, newRhs)
                    }
                    is StoreApplStore -> {
                        toParallelNormalForm(
                            StoreApplStore.create(
                                ParallelStore.create(
                                    store.applied,
                                    StoreApplStore.create(store.applied, store.target.applied)
                                ), store.target.target
                            )
                        )
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