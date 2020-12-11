package de.dominicsteinhoefel.pluggabl.testcase

class Loops {
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
}