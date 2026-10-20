package com.spe.calculator;

import java.math.BigDecimal;
import java.math.MathContext;

/** The maths. No HTTP in here, so it is trivial to unit test. */
public final class Calculator {

    private Calculator() {
    }

    public static BigDecimal add(BigDecimal a, BigDecimal b) {
        return a.add(b);
    }

    public static BigDecimal subtract(BigDecimal a, BigDecimal b) {
        return a.subtract(b);
    }

    public static BigDecimal multiply(BigDecimal a, BigDecimal b) {
        return a.multiply(b);
    }

    /** @throws ArithmeticException if b is zero */
    public static BigDecimal divide(BigDecimal a, BigDecimal b) {
        if (b.signum() == 0) {
            throw new ArithmeticException("Division by zero is not allowed");
        }
        // 16 significant digits, so 1/3 does not throw "non-terminating decimal expansion"
        return a.divide(b, MathContext.DECIMAL64);
    }
}
