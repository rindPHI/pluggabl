package de.dominicsteinhoefel.pluggabl.testcase

class HeapAccess {
    private var input = 0
    private var test = 0

    fun simpleTwoBranchedMethodWithMergeFieldAccess() {
        test = input++

        if (test == 42) {
            test += 2
        } else {
            test += 3
        }

        test += 4
    }
}