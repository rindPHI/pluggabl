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

    fun simpleLoop(input: Int): Int {
        var result = input
        if (input < 0) {
            result *= -1
        }

        //@ assert (result == input || result == -input) && result >= 0;

        var i = 0

        //@ maintaining result <= i && i >= 0;
        //@ decreases result - i;
        while (i < result) {
            i++
        }

        //@ assert result == i;

        return i
    }
}