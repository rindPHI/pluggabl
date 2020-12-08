package de.dominicsteinhoefel.pluggabl.simplification

import de.dominicsteinhoefel.pluggabl.expr.*
import java.util.*
import kotlin.collections.LinkedHashSet

object SymbolicConstraintSimplifier {
    fun applyStores(constraint: SymbolicConstraint): SymbolicConstraint =
        when (constraint) {
            is True, is False -> constraint
            is NegatedConstr -> NegatedConstr.create(applyStores(constraint.constr))
            is And -> And.create(applyStores(constraint.left), applyStores(constraint.right))
            is Or -> Or.create(applyStores(constraint.left), applyStores(constraint.right))
            is GreaterConstr -> GreaterConstr(
                SymbolicExpressionSimplifier.applyStores(constraint.left),
                SymbolicExpressionSimplifier.applyStores(constraint.right)
            )
            is GreaterEqualConstr -> GreaterEqualConstr(
                SymbolicExpressionSimplifier.applyStores(constraint.left),
                SymbolicExpressionSimplifier.applyStores(constraint.right)
            )
            is EqualityConstr -> EqualityConstr.create(
                SymbolicExpressionSimplifier.applyStores(constraint.left),
                SymbolicExpressionSimplifier.applyStores(constraint.right)
            )

            is StoreApplConstraint -> {
                val subst = SymbolicStoreSimplifier.storeToSubst(SymbolicStoreSimplifier.simplify(constraint.applied))
                val visitor = SymbolReplaceConstrVisitor(subst)
                applyStores(constraint.target).accept(visitor)
            }
        }

    fun compress(constraints: Set<SymbolicConstraint>): Set<SymbolicConstraint> =
        if (constraints.contains(False)) setOf(False)
        else constraints.filterNot(True::class::isInstance).toSet()

    fun substituteFacts(constraints: Set<SymbolicConstraint>): Set<SymbolicConstraint> {
        if (constraints.size < 2) {
            return constraints
        }

        val result = LinkedList<SymbolicConstraint>(constraints)

        for (i in result.indices) {
            val trueFacts = LinkedHashSet<SymbolicConstraint>()
            val falseFacts = LinkedHashSet<SymbolicConstraint>()

            val factsVisitor = object : SymbolicConstraintVisitor<Unit> {
                private var inNegationScope = false

                private fun addAtom(c: SymbolicConstraint) {
                    if (inNegationScope) {
                        trueFacts.add(NegatedConstr.create(c))
                        falseFacts.add(c)
                    } else {
                        trueFacts.add(c)
                        falseFacts.add(NegatedConstr.create(c))
                    }
                }

                override fun visit(c: StoreApplConstraint) {
                    throw IllegalArgumentException("Unsimplified constraint $c encountered, simplify and convert to CNF first")
                }

                override fun visit(c: True) {}
                override fun visit(c: False) {}

                override fun visit(c: GreaterConstr) = addAtom(c)
                override fun visit(c: EqualityConstr) = addAtom(c)
                override fun visit(c: GreaterEqualConstr) = addAtom(c)

                override fun visit(c: NegatedConstr) {
                    inNegationScope = !inNegationScope
                    c.constr.accept(this)
                }

                override fun visit(c: Or) {
                    if (inNegationScope) {
                        inNegationScope = false
                        And.create(NegatedConstr.create(c.left), NegatedConstr.create(c.right)).accept(this)
                    } else {
                        addAtom(c)
                    }
                }

                override fun visit(c: And) {
                    if (inNegationScope) {
                        inNegationScope = false
                        addAtom(Or.create(NegatedConstr.create(c.left), NegatedConstr.create(c.right)))
                    } else {
                        val origNegScope = inNegationScope
                        c.left.accept(this)
                        inNegationScope = origNegScope
                        c.right.accept(this)
                    }
                }
            }

            result[i].accept(factsVisitor)

            for (j in result.indices.filterNot { it == i }) {
                fun substitute(inConstr: SymbolicConstraint, inNegationScope: Boolean = false): SymbolicConstraint {
                    if (trueFacts.contains(inConstr)) {
                        return if (inNegationScope) False else True
                    } else if (falseFacts.contains(inConstr)) {
                        return if (inNegationScope) True else False
                    }

                    return when (inConstr) {
                        is NegatedConstr -> substitute(inConstr.constr, !inNegationScope)
                        is Or -> Or.create(
                            substitute(inConstr.left, inNegationScope),
                            substitute(inConstr.right, inNegationScope)
                        )
                        is And -> And.create(
                            substitute(inConstr.left, inNegationScope),
                            substitute(inConstr.right, inNegationScope)
                        )
                        is StoreApplConstraint ->
                            throw IllegalArgumentException("Unsimplified constraint $inConstr encountered, simplify and convert to CNF first")
                        else -> if (inNegationScope) NegatedConstr.create(inConstr) else inConstr
                    }
                }

                result[j] = substitute(result[j])
            }
        }

        return result.toSet()
    }

    /**
     * Converts the given SymbolicConstraint to a set of constraints which, when read as a conjunctions,
     * is equivalent to the CNF of constraint.
     *
     * The passed constraint is assumed to be simplified (call `simplify(constraint)` before). In particular,
     * it will throw an IllegalArgumentException if constraint contains an application of a symbolic store
     * (to a sub constraint).
     */
    fun toCNFClauses(constraint: SymbolicConstraint): Set<SymbolicConstraint> =
        when (constraint) {
            True -> emptySet()
            is False, is GreaterConstr, is GreaterEqualConstr, is EqualityConstr -> setOf(constraint)
            is NegatedConstr -> constraint.constr.let { sub ->
                when (sub) {
                    True -> toCNFClauses(False)
                    False -> toCNFClauses(True)
                    is GreaterConstr, is GreaterEqualConstr, is EqualityConstr -> setOf(constraint)
                    is NegatedConstr -> toCNFClauses(sub.constr)
                    is Or -> toCNFClauses(And.create(NegatedConstr.create(sub.left), NegatedConstr.create(sub.right)))
                    is And -> toCNFClauses(Or.create(NegatedConstr.create(sub.left), NegatedConstr.create(sub.right)))
                    is StoreApplConstraint -> throw IllegalArgumentException("Unsimplified constraint $constraint cannot be converted to CNF, simplify first")
                }
            }
            is Or -> {
                val l = toCNFClauses(constraint.left)
                val r = toCNFClauses(constraint.right)

                if (l.size <= 1 && r.size <= 1) {
                    setOf(Or.create(setOf(And.create(l), And.create(r))))
                } else {
                    l.associateWith { r }
                        .map {
                            it.value.fold(
                                True as SymbolicConstraint,
                                { acc, elem -> And.create(acc, Or.create(it.key, elem)) })
                        }.map { toCNFClauses(it) }.flatten().toSet()
                }
            }
            is And -> setOf(toCNFClauses(constraint.left), toCNFClauses(constraint.right)).flatten().toSet()
            is StoreApplConstraint ->
                throw IllegalArgumentException("Unsimplified constraint $constraint cannot be converted to CNF, simplify first")
        }
}