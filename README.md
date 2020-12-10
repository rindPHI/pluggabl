# pluggabl: Automatic, Exhaustive Symbolic Execution for Java Bytecode

![Gradle Build & Tests](https://github.com/rindPHI/pluggabl/workflows/Gradle%20CI/badge.svg)

pluggabl symbolically executes Java bytecode. Fullstop. It does not interface
to SMT solvers, does not perform dead branch analysis, invariant reasoning,
contract verification, or test case generation. User-provided specifications
are neither required nor possible; for loops and calls, pluggabl creates
abstractions (store updates using abstract function symbols).

pluggabl applies quick simplifications (which do not require inference) of
symbolic execution states. Furthermore, abstract function symbols created for
variables changed in loops are parametrized in the values they actually
depend on: When a loop is encountered, it is first executed in isolation. From
the resulting states, information about dependencies is extracted and used to
create abstractions. Afterward, the execution result is added to the main
analysis.

Further analyses can be plugged in afterward, thus the name. For instance, you
can feed path conditions to an SMT solver to check their satisfiability and
eliminate dead branches. Or you can evaluate a postcondition in all leaf
states by using an external program prover.

Loop invariants or contracts can be used by post-hoc substitutions of the
generated abstract symbols by concrete expressions.

pluggabl is based on the Soot framework and written in Kotlin. In fact, it
executes Jimple code which Soot generates from Java bytecode.

Complete examples on how to use the project are provided as test cases.
Currently, there is no Main class to directly run the project as a standalone
program. The engine is instantiated as follows:

    val analysis = SymbolicExecutionAnalysis(
      "my.full.class.Name",
      "int myMethodSignature(int)"
    )

    analysis.symbolicallyExecute()
    
    // input/output symbolic states are associated to CFG
    // nodes in the analysis object now.

Note that this is all work in progress, and the analysis will crash for many
input programs. Also, additional classes and used library methods are currently
not settable via parameters (but are hard-coded in class `SootBridge`).

<!--The currently most complex working example is a simple parenthesis
expression parser, which features loops, character arrays, and pure method
invocation expressions.-->

This project is maintained by [Dominic Steinhöfel](https://www.dominic-steinhoefel.de).
