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

    // Result: \old(arr[1]) - arr[2]
    // Side effects: arr[0] == arr[1] - arr[2] + 1
    //               arr[1] == arr[1] + 1
    fun simpleArrayAccess(arr: Array<Int>): Int {
        arr[0] = arr[1]++ - arr[2]
        return arr[0] + 1
    }

}