package de.dominicsteinhoefel.pluggabl.testcase

class Loops {
    fun reallySimpleLoop(input: Int): Int {
        var i = 0

        while (i < input) {
            i++
        }

        return i
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

    fun simpleLoopWithContinueAndBreak(input: Int): Int {
        var result = input
        if (input < 0) {
            result *= -1
        }

        //@ assert (result == input || result == -input) && result >= 0;

        var i = 0

        //@ maintaining result <= i && i >= 0;
        //@ decreases result - i;
        while (i < result) {
            if (i == 17) {
                i++
                continue
            } else if (i == 42) {
                break
            }

            i++
        }

        //@ assert result == i || 42 == i;

        return i
    }

    fun loopWithNonTrivialGuard(input: Array<Int>): Int {
        var i = 0

        // Loop header is not an if statement, i.e. a loop exit, but the evaluation of the size expression
        while (i < input.size) {
            i++
        }
        
        return i
    }
}