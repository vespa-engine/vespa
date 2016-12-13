package com.yahoo.tensor.functions;

import com.google.common.annotations.Beta;

import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleUnaryOperator;

/**
 * Factory of scalar Java functions.
 * The purpose of this is to embellish anonymous functions with a runtime type
 * such that they can be inspected and will return a parseable toString.
 * 
 * @author bratseth
 */
@Beta
public class ScalarFunctions {

    public static DoubleBinaryOperator add() { return new Addition(); }
    public static DoubleBinaryOperator multiply() { return new Multiplication(); }
    public static DoubleBinaryOperator divide() { return new Division(); }
    public static DoubleUnaryOperator square() { return new Square(); }
    public static DoubleUnaryOperator sqrt() { return new Sqrt(); }
    public static DoubleUnaryOperator exp() { return new Exponent(); }

    public static class Addition implements DoubleBinaryOperator {

        @Override
        public double applyAsDouble(double left, double right) { return left + right; }

        @Override
        public String toString() { return "f(a,b)(a + b)"; }

    }

    public static class Multiplication implements DoubleBinaryOperator {

        @Override
        public double applyAsDouble(double left, double right) { return left * right; }
        
        @Override
        public String toString() { return "f(a,b)(a * b)"; }

    }

    public static class Division implements DoubleBinaryOperator {

        @Override
        public double applyAsDouble(double left, double right) { return left / right; }

        @Override
        public String toString() { return "f(a,b)(a / b)"; }
    }

    public static class Square implements DoubleUnaryOperator {

        @Override
        public double applyAsDouble(double operand) { return operand * operand; }

        @Override
        public String toString() { return "f(a)(a * a)"; }

    }

    public static class Sqrt implements DoubleUnaryOperator {

        @Override
        public double applyAsDouble(double operand) { return Math.sqrt(operand); }

        @Override
        public String toString() { return "f(a)(sqrt(a))"; }

    }

    public static class Exponent implements DoubleUnaryOperator {

        @Override
        public double applyAsDouble(double operand) { return Math.exp(operand); }

        @Override
        public String toString() { return "f(a)(exp(a))"; }

    }

}
