package de.dominicsteinhoefel.pluggabl

import de.dominicsteinhoefel.pluggabl.analysis.SymbolicExecutionAnalysis
import picocli.CommandLine
import picocli.CommandLine.Option
import picocli.CommandLine.Command
import soot.jimple.Stmt
import java.util.concurrent.Callable
import kotlin.system.exitProcess

@Command(name = "java -jar pluggabl-exe.jar", mixinStandardHelpOptions = true)
class Main: Callable<Unit> {
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
    var sootClassPathItems: List<String> = emptyList()

    override fun call() {
        val analysis = SymbolicExecutionAnalysis.create(clazz, methodSig, sootClassPathItems)
        analysis.symbolicallyExecute()

        printSESs(analysis)
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            exitProcess(CommandLine(Main()).execute(*args))
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