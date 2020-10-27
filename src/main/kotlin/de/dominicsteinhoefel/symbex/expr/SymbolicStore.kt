package de.dominicsteinhoefel.symbex.expr

sealed class SymbolicStore

object EmptyStore : SymbolicStore() {
    override fun toString() = "[]"
}

class ElementaryStore(val lhs: Symbol, val rhs: SymbolicExpression) : SymbolicStore() {
    override fun toString() = "[${lhs} -> ${rhs}]"
}

class ParallelStore private constructor(val lhs: SymbolicStore, val rhs: SymbolicStore) : SymbolicStore() {
    override fun toString() = "${lhs}++${rhs}"

    companion object {
        fun create(lhs: SymbolicStore, rhs: SymbolicStore): SymbolicStore =
            if (lhs is EmptyStore) rhs else
                if (rhs is EmptyStore) lhs else
                    ParallelStore(lhs, rhs)
    }
}

class StoreApplStore private constructor(val applied: SymbolicStore, val target: SymbolicStore) : SymbolicStore() {
    override fun toString() = "{${applied}}(${target})"

    companion object {
        fun create(applied: SymbolicStore, target: SymbolicStore): SymbolicStore =
            if (applied is EmptyStore || target is EmptyStore) target else
                StoreApplStore(applied, target)
    }
}