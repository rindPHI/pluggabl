package de.dominicsteinhoefel.pluggabl

class SimpleMethods {
    fun simpleTwoBranchedMethod(input: Int): Int {
        var test = input
        test += 1

        if (test == 42) {
            test += 2
        } else {
            return test + 3
        }

        test += 4

        return test
    }

    fun simpleTwoBranchedMethodWithMerge(input: Int): Int {
        var test = input
        test += 1

        if (test == 42) {
            test += 2
        } else {
            test += 3
        }

        test += 4

        return test
    }
}