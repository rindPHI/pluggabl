# SootSymbolicExecution

A symbolic execution analysis based on the Soot analysis framework written in Kotlin. 
Defines a branched forward-analysis "SymbolicExecutionAnalysis" and a transformer 
"SymbolicExecutionAnalysisTransformer" that can be hooked into Soot as follows:

    val seAnalysis = Transform("jtp.symbolicexecution", SymbolicExecutionAnalysisTransformer(postProcess))
    seAnalysis.declaredOptions = SymbolicExecutionAnalysisTransformer.getDeclaredOptions()

    PackManager.v().getPack("jtp").add(seAnalysis)
    
The analysis generally won't terminate in the presence of loops with symbolic guards, 
as usual for full symbolic execution. To address this, a transformer "CutLoopTransformation"
is provided which breaks loop cycles and havocs local variables written in the body
to retain soundness of the analysis. Alternatively, a different (not provided) transformer
could be used to finitely unroll all loops in the program.

Complete examples on how to use the project are provided as test cases. Currently, there
is no Main class to directly run the project as a standalone program (it's on my TODO list!).

Note that this is all work in progress, and the analysis will crash for many input programs.
The currently most complex working example is a simple parenthesis expression parser, which
features loops, character arrays, and pure method invocation expressions.

This project is maintained by [Dominic Steinhöfel](https://www.dominic-steinhoefel.de).