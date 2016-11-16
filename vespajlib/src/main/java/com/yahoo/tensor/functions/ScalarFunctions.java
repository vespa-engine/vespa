package com.yahoo.tensor.functions;

import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleUnaryOperator;

/**
 * Factory of scalar Java functions.
 * The purpose of this is to embellish anonymous functions with a runtime type
 * such that they can be inspected and return a usable toString.
 * 
 * @author bratseth
 */
public class ScalarFunctions {
    
    public static DoubleBinaryOperator multiply() { return new Multiplication(); }
    public static DoubleBinaryOperator divide() { return new Division(); }
    public static DoubleUnaryOperator square() { return new Square(); }
    
    public static class Multiplication implements DoubleBinaryOperator {

        @Override
        public double applyAsDouble(double left, double right) { return left * right; }
        
        @Override
        public String toString() { return "a * b"; }

    }

    public static class Division implements DoubleBinaryOperator {

        @Override
        public double applyAsDouble(double left, double right) { return left / right; }

        @Override
        public String toString() { return "a / b"; }
    }

    public static class Square implements DoubleUnaryOperator {

        @Override
        public double applyAsDouble(double operand) { return operand * operand; }

        @Override
        public String toString() { return "a * a"; }

    }

}
