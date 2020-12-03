package de.dominicsteinhoefel.symbex.expr

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

class LocalVariableStoreCollector() : SymbolicStoreVisitor<Set<LocalVariable>> {
    override fun visit(store: EmptyStore) = emptySet<LocalVariable>()

    override fun visit(store: ElementaryStore) =
        listOf(listOf(store.lhs), store.rhs.accept(LocalVariableExpressionCollector())).flatten().toSet()

    override fun visit(store: ParallelStore) =
        listOf(store.lhs.accept(this), store.rhs.accept(this)).flatten().toSet()

    override fun visit(store: StoreApplStore) =
        listOf(store.applied.accept(this), store.target.accept(this)).flatten().toSet()

}
