package de.dominicsteinhoefel.symbex

class ParenthesisParser {
    fun parse(input: Array<Char>): Int {
        var opParCnt = 0
        var i = 0

        while (i < input.size) {
            val c = input[i]
            if (c == '(') {
                opParCnt++
            } else if (c == ')') {
                opParCnt--

                if (opParCnt < 0) {
                    // chars[i_0] == ')' && opParCnt < 0
                    return 1
                }
            } else {
                // chars[i_0] != '(' && chars[i_0] != ')'
                return 1
            }

            i++

            // chars[i_0] == '(' || (chars[i_0] == ')' && opParCnt >= 0)
        }

        //    i_0 + 1 >= chars.length
        // && (chars[i_0] == '(' || (chars[i_0] == ')' && opParCnt >= 0))

        if (opParCnt == 0) {
            //    i_0 + 1 >= chars.length
            // && (chars[i_0] == '(' || chars[i_0] == ')')
            // && opParCnt == 0

            return 0
        } else {
            //    i_0 + 1 >= chars.length
            // && (chars[i_0] == '(' || (chars[i_0] == ')' && opParCnt > 0))
            // && opParCnt != 0

            return 1
        }
    }
}