package de.dominicsteinhoefel.symbex

class SimpleMethod {
    fun simpleMethod(input: Int): Int {
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