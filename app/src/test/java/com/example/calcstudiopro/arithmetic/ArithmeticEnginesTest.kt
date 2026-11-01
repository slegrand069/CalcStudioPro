package com.example.calcstudiopro.arithmetic

import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger as B

class ArithmeticEnginesTest {
    @Test fun divisibilityExact() {
        assertEquals(B.valueOf(6), ArithmeticEngine.gcd(B.valueOf(-84), B.valueOf(30)))
        assertEquals(B.ZERO, ArithmeticEngine.gcd(B.ZERO, B.ZERO))
        assertEquals(B.valueOf(36), ArithmeticEngine.lcm(B.valueOf(12), B.valueOf(18)))
        val division = ArithmeticEngine.divide(B.valueOf(-17), B.valueOf(-5))
        assertEquals(B.valueOf(4), division.quotient)
        assertEquals(B.valueOf(3), division.remainder)
        val bezout = ArithmeticEngine.bezout(B.valueOf(84), B.valueOf(30))
        assertEquals(bezout.gcd, B.valueOf(84).multiply(bezout.u).add(B.valueOf(30).multiply(bezout.v)))
    }

    @Test fun primesFactorSieveAndTree() {
        listOf(2, 3, 97).forEach { assertTrue(PrimeEngine.isPrime(B.valueOf(it.toLong()))) }
        listOf(0, 1, 4, 100).forEach { assertFalse(PrimeEngine.isPrime(B.valueOf(it.toLong()))) }
        val factors = PrimeEngine.factor(B.valueOf(360))
        assertEquals(linkedMapOf(B.TWO to 3, B.valueOf(3) to 2, B.valueOf(5) to 1), factors)
        assertEquals(10, PrimeEngine.primesTo(30).size)
        val tree = buildFactorTree(B.valueOf(360), factors)
        fun product(node: FactorTreeNode): B {
            return if (node.prime) node.value else product(node.left!!).multiply(product(node.right!!))
        }
        assertEquals(B.valueOf(360), product(tree))
        assertEquals(B.valueOf(97) to B.valueOf(101), PrimeEngine.neighbors(B.valueOf(100)))
    }

    @Test fun combinatoricsExact() {
        assertEquals(B.ONE, CombinatoricsEngine.factorial(0))
        assertEquals(B.valueOf(120), CombinatoricsEngine.factorial(5))
        assertEquals(B.valueOf(60), CombinatoricsEngine.arrangements(5, 3))
        assertEquals(B.TEN, CombinatoricsEngine.combinations(5, 3))
        assertEquals(B.valueOf(120), CombinatoricsEngine.combinations(10, 3))
        assertEquals(B.valueOf(27), CombinatoricsEngine.repetitions(3, 3))
        assertEquals(B.valueOf(30), CombinatoricsEngine.multiset(listOf(2, 2, 1)))
    }

    @Test fun modularAndSequencesExact() {
        assertEquals(B.valueOf(3), ArithmeticEngine.modulo(B.valueOf(-7), B.valueOf(5)))
        assertEquals(B.valueOf(24), B.TWO.modPow(B.TEN, B.valueOf(1000)))
        assertEquals(B.valueOf(4), B.valueOf(3).modInverse(B.valueOf(11)))
        assertEquals(B.ZERO, NumberSequencesEngine.fibonacci(0))
        assertEquals(B.ONE, NumberSequencesEngine.fibonacci(1))
        assertEquals(B.valueOf(6765), NumberSequencesEngine.fibonacci(20))
        assertEquals(B.valueOf(55), NumberSequencesEngine.triangular(B.TEN))
        assertEquals(B.valueOf(29), NumberSequencesEngine.digitSum(B.valueOf(9875)))
        assertEquals(B.TWO, NumberSequencesEngine.digitalRoot(B.valueOf(9875)))
        assertTrue(NumberSequencesEngine.isSquare(B.valueOf(10_000)))
        assertTrue(NumberSequencesEngine.isCube(B.valueOf(1000)))
    }
}
