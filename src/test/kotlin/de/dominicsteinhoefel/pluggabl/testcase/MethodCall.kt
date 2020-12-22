package de.dominicsteinhoefel.pluggabl.testcase

class MethodCall {
    private lateinit var someIF: SomeIF

    fun main(input: Int): Int {
        val tmp = someIF.test(input * 2)
        return tmp + 1
    }
}

interface SomeIF {
    fun test(input: Int): Int
}