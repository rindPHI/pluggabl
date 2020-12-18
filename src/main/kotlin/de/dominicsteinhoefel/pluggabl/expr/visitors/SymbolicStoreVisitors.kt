package de.dominicsteinhoefel.pluggabl.expr

import de.dominicsteinhoefel.pluggabl.util.union

interface SymbolicStoreVisitor<T> {
    fun visit(store: EmptyStore): T
    fun visit(store: ElementaryStore): T
    fun visit(store: ParallelStore): T
    fun visit(store: StoreApplStore): T
}

open class SymbolicStoreCollector<T>(private val coll: (SymbolicStore) -> Set<T>) : SymbolicStoreVisitor<Set<T>> {
    override fun visit(store: EmptyStore) = coll(store)

    override fun visit(store: ElementaryStore) = coll(store)

    override fun visit(store: ParallelStore) =
        union(coll(store), store.lhs.accept(this), store.rhs.accept(this))

    override fun visit(store: StoreApplStore) =
        union(coll(store), store.applied.accept(this))
}

class LocalVariableStoreCollector() : SymbolicStoreCollector<LocalVariable>(
    { store ->
        when (store) {
            is ElementaryStore -> union(setOf(store.lhs), store.rhs.accept(LocalVariableExpressionCollector()))
            else -> emptySet()
        }
    }
)

class HeapExpressionStoreCollector() : SymbolicStoreCollector<HeapExpression>(
    { store ->
        when (store) {
            is ElementaryStore -> store.rhs.accept(HeapExpressionInExpressionCollector())
            else -> emptySet()
        }
    }
)

class FunctionSymbolStoreCollector() : SymbolicStoreCollector<FunctionSymbol>(
    { store ->
        when (store) {
            is ElementaryStore -> store.rhs.accept(FunctionSymbolExpressionCollector())
            else -> emptySet()
        }
    }
)

open class SymbolicStoreReplacer(private val repl: (SymbolicStore) -> SymbolicStore) :
    SymbolicStoreVisitor<SymbolicStore> {
    override fun visit(store: EmptyStore) = repl(store)
    override fun visit(store: ElementaryStore) = repl(store)

    override fun visit(store: ParallelStore) =
        repl(ParallelStore.create(store.lhs.accept(this), store.rhs.accept(this)))

    override fun visit(store: StoreApplStore) =
        repl(StoreApplStore.create(store.applied.accept(this), store.target.accept(this)))
}