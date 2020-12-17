package de.dominicsteinhoefel.pluggabl.util

import soot.*
import kotlin.system.exitProcess

class SootBridge {
    companion object {
        fun loadJimpleBody(clazz: String, methodSig: String, sootClassPathElems: List<String>): Body? {
            val sig = "<${clazz}: ${methodSig}>"
            var body: Body? = null

            val seAnalysis = Transform("jtp.symbolicexecution", object : BodyTransformer() {
                override fun internalTransform(b: Body?, phaseName: String?, options: MutableMap<String, String>?) {
                    if (b == null) return

                    if (b.method.signature == sig) {
                        body = b
                    }
                }
            })

            PackManager.v().getPack("jtp").add(seAnalysis)

            if (sootClassPathElems.isNotEmpty()) {
                Scene.v().sootClassPath = sootClassPathElems[0]
                sootClassPathElems.subList(1).forEach{ Scene.v().extendSootClassPath(it) }
            }

            PhaseOptions.v().setPhaseOption("jb", "use-original-names")

            // Add a line like the following when using JRE classes in the future:
            // Scene.v().addBasicClass("java.lang.System", SootClass.SIGNATURES);

            Scene.v().addBasicClass("java.lang.RuntimeException", SootClass.SIGNATURES);

            // We disable assertions since this leads to more verbose errors in case of missing dependent classes.
            // This is in fact an implementation problem of soot: A missing underlying dependent class leads to
            // a failed assertion for the class that we're actually looking for, suppressing the original problem
            SootResolver::class.java.classLoader.setClassAssertionStatus(SootResolver::class.java.name, false)

            try {
                Main.v().run(arrayOf(clazz))
            } catch(classNotFoundExc: SootResolver.SootClassNotFoundException) {
                val regex = Regex("^soot.SootResolver\\\$SootClassNotFoundException: couldn't find class: (.*) \\(is your soot-class-path set properly\\?\\)\$")
                val missingClass = regex.matchEntire(classNotFoundExc.toString())?.groups?.get(1)

                if (missingClass == null) {
                    System.err.println("Could not find a required class, message:")
                    System.err.println(classNotFoundExc.message)
                } else {
                    System.err.println("Could not find required class \"${missingClass.value}\"")
                }

                System.err.println("Consider adding the class / containing library to Soot's classpath")

                exitProcess(1)
            }

            return body
        }
    }
}