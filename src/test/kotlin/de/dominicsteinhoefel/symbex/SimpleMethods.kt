package de.dominicsteinhoefel.symbex

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
}