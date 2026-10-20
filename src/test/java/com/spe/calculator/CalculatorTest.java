package com.spe.calculator;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CalculatorTest {

    private static BigDecimal n(String value) {
        return new BigDecimal(value);
    }

    @Test
    void addTwoNumbers() {
        assertEquals(0, n("5").compareTo(Calculator.add(n("2"), n("3"))));
    }

    @Test
    void addNegativeAndDecimal() {
        assertEquals(0, n("-1.25").compareTo(Calculator.add(n("-4"), n("2.75"))));
    }

    @Test
    void subtractTwoNumbers() {
        assertEquals(0, n("-1").compareTo(Calculator.subtract(n("2"), n("3"))));
    }

    @Test
    void multiplyTwoNumbers() {
        assertEquals(0, n("12").compareTo(Calculator.multiply(n("3"), n("4"))));
    }

    @Test
    void multiplyByZero() {
        assertEquals(0, BigDecimal.ZERO.compareTo(Calculator.multiply(n("99.9"), n("0"))));
    }

    @Test
    void divideTwoNumbers() {
        assertEquals(0, n("2.5").compareTo(Calculator.divide(n("10"), n("4"))));
    }

    @Test
    void divideNonTerminatingResultDoesNotThrow() {
        assertEquals("0.3333333333333333", Calculator.divide(n("1"), n("3")).toPlainString());
    }

    @Test
    void divideByZeroThrows() {
        ArithmeticException e = assertThrows(ArithmeticException.class,
                () -> Calculator.divide(n("5"), n("0")));
        assertEquals("Division by zero is not allowed", e.getMessage());
    }

    @Test
    void divideByDecimalZeroAlsoThrows() {
        assertThrows(ArithmeticException.class, () -> Calculator.divide(n("5"), n("0.00")));
    }
}
