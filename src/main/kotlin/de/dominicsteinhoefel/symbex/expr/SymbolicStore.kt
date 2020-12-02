package de.dominicsteinhoefel.symbex.expr

import java.util.*

sealed class SymbolicStore

object EmptyStore : SymbolicStore() {
    override fun toString() = "[]"

    override fun hashCode() = EmptyStore.javaClass.hashCode()
    override fun equals(other: Any?) = other === EmptyStore
}

class ElementaryStore(val lhs: LocalVariable, val rhs: SymbolicExpression) : SymbolicStore() {
    override fun toString() = "[${lhs} -> ${rhs}]"

    override fun hashCode() = Objects.hash(ElementaryStore::class, lhs, rhs)
    override fun equals(other: Any?) = (other as? ElementaryStore).let { it?.lhs == lhs && it.rhs == rhs }
}

class ParallelStore private constructor(val lhs: SymbolicStore, val rhs: SymbolicStore) : SymbolicStore() {
    override fun toString() = "${lhs}++${rhs}"

    override fun hashCode() = Objects.hash(ParallelStore::class, lhs, rhs)
    override fun equals(other: Any?) = (other as? ParallelStore).let { it?.lhs == lhs && it.rhs == rhs }

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

    companion object {
        fun create(applied: SymbolicStore, target: SymbolicStore): SymbolicStore =
            if (applied is EmptyStore || target is EmptyStore) target else
                StoreApplStore(applied, target)
    }
}