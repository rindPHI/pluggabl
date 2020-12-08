# pluggabl: Automatic, Exhaustive Symbolic Execution for Java Bytecode

pluggabl is a symbolic execution engine for Java bytecode based on the Soot
framework written in Kotlin.

User-provided specifications are neither required nor possible; this
implementation performs "pure" (heavyweight/static) symbolic execution on
Jimple code. Loops are treated using trivial invariants; essentially, all
values changed in the loop are anonymized afterward. The intuition is that
specifications like invariants can be plugged in later, if necessary, to
substitute or constrain these symbols.

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
