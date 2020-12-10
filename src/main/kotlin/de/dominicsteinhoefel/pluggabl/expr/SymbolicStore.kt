package de.dominicsteinhoefel.pluggabl.expr

import de.dominicsteinhoefel.pluggabl.util.union
import java.util.*

sealed class SymbolicStore {
    companion object {
        fun elementaries(store: SymbolicStore): List<ElementaryStore> =
            when (store) {
                is EmptyStore -> emptyList()
                is ElementaryStore -> listOf(store)
                is ParallelStore -> listOf(elementaries(store.lhs), elementaries(store.rhs)).flatten()
                is StoreApplStore -> throw IllegalArgumentException("Method may only be called with a simplified store")
            }
    }

    abstract fun <T> accept(visitor: SymbolicStoreVisitor<T>): T
}

interface SymbolicStoreVisitor<T> {
    fun visit(store: EmptyStore): T
    fun visit(store: ElementaryStore): T
    fun visit(store: ParallelStore): T
    fun visit(store: StoreApplStore): T
}

object EmptyStore : SymbolicStore() {
    override fun toString() = "[]"
    override fun hashCode() = EmptyStore.javaClass.hashCode()
    override fun equals(other: Any?) = other === EmptyStore
    override fun <T> accept(visitor: SymbolicStoreVisitor<T>) = visitor.visit(this)
}

class ElementaryStore(val lhs: LocalVariable, val rhs: SymbolicExpression) : SymbolicStore() {
    override fun toString() = "[${lhs} -> ${rhs}]"
    override fun hashCode() = Objects.hash(ElementaryStore::class, lhs, rhs)
    override fun equals(other: Any?) = (other as? ElementaryStore).let { it?.lhs == lhs && it.rhs == rhs }
    override fun <T> accept(visitor: SymbolicStoreVisitor<T>) = visitor.visit(this)
}

class ParallelStore private constructor(val lhs: SymbolicStore, val rhs: SymbolicStore) : SymbolicStore() {
    override fun toString() = "${lhs}++${rhs}"
    override fun hashCode() = Objects.hash(ParallelStore::class, lhs, rhs)
    override fun equals(other: Any?) = (other as? ParallelStore).let { it?.lhs == lhs && it.rhs == rhs }
    override fun <T> accept(visitor: SymbolicStoreVisitor<T>) = visitor.visit(this)

    companion object {
        fun create(lhs: SymbolicStore, rhs: SymbolicStore): SymbolicStore =
            when {
                lhs is EmptyStore -> rhs
                rhs is EmptyStore -> lhs
                else -> ParallelStore(lhs, rhs)
            }
    }
}

class StoreApplStore private constructor(val applied: SymbolicStore, val target: SymbolicStore) : SymbolicStore() {
    override fun toString() = "{${applied}}(${target})"
    override fun hashCode() = Objects.hash(StoreApplStore::class, applied, target)
    override fun equals(other: Any?) = (other as? StoreApplStore).let { it?.applied == applied && it.target == target }
    override fun <T> accept(visitor: SymbolicStoreVisitor<T>) = visitor.visit(this)

    companion object {
        fun create(applied: SymbolicStore, target: SymbolicStore): SymbolicStore =
            if (applied is EmptyStore || target is EmptyStore) target else
                StoreApplStore(applied, target)
    }
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

class FunctionSymbolStoreCollector() : SymbolicStoreCollector<FunctionSymbol>(
    { store ->
        when (store) {
            is ElementaryStore -> store.rhs.accept(FunctionSymbolExpressionCollector())
            else -> emptySet()
        }
    }
)
