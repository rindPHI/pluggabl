# pluggabl: Automatic, Exhaustive Symbolic Execution for Java Bytecode

![Gradle Build & Tests](https://github.com/rindPHI/pluggabl/workflows/Gradle%20CI/badge.svg)

*pluggabl* symbolically executes Java bytecode. Fullstop. It does not interface
to SMT solvers, does not perform dead branch analysis, invariant reasoning,
contract verification, or test case generation. User-provided specifications
are neither required nor possible; for loops and calls, *pluggabl* creates
abstractions (store updates using abstract function symbols).

## Introduction

*pluggabl* applies quick simplifications (which do not require inference) of
symbolic execution states. Furthermore, abstract function symbols created for
variables changed in loops are parametrized in the values they actually
depend on: When a loop is encountered, it is first executed in isolation. From
the resulting states, information about dependencies is extracted and used to
create abstractions. Afterward, the execution result is added to the main
analysis.

The result of a symbolic execution are sets of input and output symbolic states
attached to CFG nodes of the input program. *pluggabl* uses [nondeterministic
value summaries](https://www.dominic-steinhoefel.de/post/precise-symbolic-state-merging/)
to merge states after the execution of branches in an automatic, fully precise
manner.

### Pluggable *pluggabl*

Further analyses can be plugged in afterward, thus the name. For instance, you
can feed path conditions to an SMT solver to check their satisfiability and
eliminate dead branches. Or you can evaluate a postcondition in all leaf
states by using an external program prover.

Loop invariants or contracts can be used by post-hoc substitutions of the
generated abstract symbols by concrete expressions. For method calls, *pluggabl*
maintains a list of known pure methods for which the heap will not be changed.
Add method signatures to this list if you want to simplify the result for calls
to a method that's known to be pure.

### Implementation

*pluggabl* is based on the Soot framework and written in Kotlin. In fact, it
executes Jimple code which Soot generates from Java bytecode.

Complete examples on how to use the project are provided as test cases.
The currently most complex working example is a simple parenthesis
expression parser, which features loops, character arrays, and pure method
invocation expressions.

Disclaimer: This is work in progress, and the analysis will currently crash for many
input programs (probably because I missed to support a particular expression type).
See TODO list below for more information.

## Getting Started

You can either use *pluggabl* as a library (see shipped tests) or as a command line tool.
For the latter, proceed as follows:

    cd ./path/to/pluggabl/root
    ./gradlew shadowJar
    java -jar build/libs/pluggabl-exe.jar --help

This shows you the options you need to pass to the tool. Apart from the class and
method signature for the method to analyze, you need to pass at least the path to
Java's `rt.jar` (available only until Java 8) as a classpath item, as Soot depends
on that (and *pluggabl* depends on Soot) as well as the path to the class file
of the class to analyze. Frequently, also the `jce.jar` is needed. Should other
classes be missing, you should get an error message telling you that, such that
you can add the required library jars / class file directories.

Currently, the command line tool will print all statements of the analyzed program
along with input/output symbolic execution states. When using *pluggabl* as a library,
you have more possibilities to deal with the outcome.

## Example Run

Consider the following simple program:

    public class Test {
        public int test(int input) {
            int result = input;
            if (input == 42) {
                result = 17;
            } else {
                result *= result;
            }
            return result;
        }
    }

Below you find the results of running *pluggabl* for the `test` method:

    > javac Test.java 
    > java -jar pluggabl-exe.jar -c "Test" -m "int test(int)" -cp "./" -cp "/usr/lib/jvm/java-8-openjdk-amd64/jre/lib/rt.jar"

    Soot started on Mon Dec 21 18:17:30 CET 2020
    Soot finished on Mon Dec 21 18:17:31 CET 2020
    Soot has run for 0 min. 1 sec.
    Node "l0 := @this: Test":
    Input States:  ({}, [])
    Output States: ({}, [])
    
    Node "l1 := @parameter0: int":
    Input States:  ({}, [])
    Output States: ({}, [])
    
    Node "if l1 != 42 goto l2 = l1 * l1":
    Input States:  ({}, [])
    Output States: ({(l1)==(42)}, []), ({!((l1)==(42))}, [])
    
    Node "l2 = 17":
    Input States:  ({(l1)==(42)}, [])
    Output States: ({(l1)==(42)}, [l2 -> 17])
    
    Node "goto [?= return l2]":
    Input States:  ({(l1)==(42)}, [l2 -> 17])
    Output States: ({(l1)==(42)}, [l2 -> 17])
    
    Node "l2 = l1 * l1":
    Input States:  ({!((l1)==(42))}, [])
    Output States: ({!((l1)==(42))}, [l2 -> mulInt(l1, l1)])
    
    Node "return l2":
    Input States:  ({!((l1)==(42))}, [l2 -> mulInt(l1, l1)]), ({(l1)==(42)}, [l2 -> 17])
    Output States: ({}, [l2 -> {(mulInt(l1, l1) | !((l1)==(42))), (17 | (l1)==(42))}]++[result -> {(mulInt(l1, l1) | !((l1)==(42))), (17 | (l1)==(42))}])

## TODO List

The below list is most problably incomplete and contains some of the most pressing
TOODs. If you encounter some problem in using pluggabl, it's likely that this is due
to some of these issues. If there's another problem, feel free to send me a mail!

* Increase support of operations on standard types (numbers, strings)
* Consider exceptions thrown by unknown method calls (custom ThrowAnalysis?)
* Test behavior for complex loops (nested, labeled breaks etc.)
* Test behavior for functional constructs (lambdas...)
* Add example plug-in analysis (e.g., accepting invariants & contracts 
  and interfacing to an SMT solver)

## *pluggabl*'s Developer

This project is maintained by [Dominic Steinhöfel](https://www.dominic-steinhoefel.de).

If you want to support the development by contributing to the code, feel free
to open a pull request!