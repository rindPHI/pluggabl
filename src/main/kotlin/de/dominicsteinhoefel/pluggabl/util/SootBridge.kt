package de.dominicsteinhoefel.pluggabl.util

import soot.*

class SootBridge {
    companion object {
        fun loadJimpleBody(clazz: String, methodSig: String): Body? {
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

            Scene.v().sootClassPath = "./build/classes/kotlin/test"
            Scene.v().extendSootClassPath("./lib/kotlin-stdlib-1.3.72.jar")
            Scene.v().extendSootClassPath("/usr/lib/jvm/java-8-openjdk-amd64/jre/lib/rt.jar")
            Scene.v().extendSootClassPath("/usr/lib/jvm/java-8-openjdk-amd64/jre/lib/jce.jar")

            PhaseOptions.v().setPhaseOption("jb", "use-original-names")

            // Add a line like the following when using JRE classes in the future:
            // Scene.v().addBasicClass("java.lang.System", SootClass.SIGNATURES);

            Scene.v().addBasicClass("java.lang.RuntimeException", SootClass.SIGNATURES);

            Main.main(arrayOf(clazz))

            return body
        }
    }
}