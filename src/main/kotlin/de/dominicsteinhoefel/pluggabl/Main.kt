package de.dominicsteinhoefel.pluggabl

import de.dominicsteinhoefel.pluggabl.analysis.SymbolicExecutionAnalysis
import picocli.CommandLine
import picocli.CommandLine.Option
import picocli.CommandLine.Command
import soot.jimple.Stmt
import kotlin.system.exitProcess

@Command(name = "java -jar pluggabl-exe.jar")
class Main {
    @Option(
        names = ["-c", "--class"],
        required = true,
        paramLabel = "<FULLY_QUALIFIED_CLASS_NAME>",
        description = ["The fully qualified name of the class containing the method to execute."]
    )
    lateinit var clazz: String

    @Option(
        names = ["-m", "--method"],
        required = true,
        paramLabel = "<METHOD_SIGNATURE>",
        description = ["The signature of the method to execute.", "Example: java.lang.Integer[] constructArray(int, int)"]
    )
    lateinit var methodSig: String

    @Option(
        names = ["-cp", "--classpath"],
        required = false,
        paramLabel = "<SOOT_CLASS_PATH_ITEM>",
        description = ["Classpath item for dependencies of the executed method/class.",
            "Use multiple -cp ... parameters for multiple entries.",
            "Note: Soot depends on Java's rt.jar and jce.jar, which are only available up to Java 8."]
    )
    lateinit var sootClassPathItems: List<String>

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val main = Main()
            val parseResult = CommandLine(main).execute(*args)
            if (parseResult != 0) exitProcess(parseResult)

            val analysis = SymbolicExecutionAnalysis.create(main.clazz, main.methodSig, main.sootClassPathItems)
            analysis.symbolicallyExecute()

            printSESs(analysis)
        }

        fun printSESs(a: SymbolicExecutionAnalysis) {
            for (node in a.cfg) {
                println("Node \"$node\":")
                println("Input States:  ${a.getInputSESs(node as Stmt).joinToString(", ")}")
                println("Output States: ${a.getOutputSESs(node).joinToString(", ")}\n")
            }
        }
    }
}