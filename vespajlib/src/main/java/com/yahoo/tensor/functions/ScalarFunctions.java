package com.yahoo.tensor.functions;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleSupplier;
import java.util.function.DoubleUnaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    public static Function<List<Integer>, Double> random() { return new Random(); }
    public static Function<List<Integer>, Double> equalArguments(List<String> argumentNames) { 
        return new EqualArguments(argumentNames); 
    }
    public static Function<List<Integer>, Double> sumArguments(List<String> argumentNames) {
        return new SumArguments(argumentNames);
    }

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

    public static class Random implements Function<List<Integer>, Double> {

        @Override
        public Double apply(List<Integer> values) {
            return ThreadLocalRandom.current().nextDouble();
        }

        @Override
        public String toString() { return "random()"; }

    }

    public static class EqualArguments implements Function<List<Integer>, Double> {
        
        private final ImmutableList<String> argumentNames;
        
        private EqualArguments(List<String> argumentNames) {
            this.argumentNames = ImmutableList.copyOf(argumentNames);
        }

        @Override
        public Double apply(List<Integer> values) {
            if (values.isEmpty()) return 1.0;
            for (Integer value : values)
                if ( ! value.equals(values.get(0)))
                    return 0.0;
            return 1.0;
        }

        @Override
        public String toString() { 
            if (argumentNames.size() == 0) return "1";
            if (argumentNames.size() == 1) return "1";
            if (argumentNames.size() == 2) return argumentNames.get(0) + "==" + argumentNames.get(1);
            
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < argumentNames.size() -1; i++) {
                b.append("(").append(argumentNames.get(i)).append("==").append(argumentNames.get(i+1)).append(")");
                if ( i < argumentNames.size() -2)
                    b.append("*");
            }
            return b.toString();
        }

    }

    public static class SumArguments implements Function<List<Integer>, Double> {

        private final ImmutableList<String> argumentNames;

        private SumArguments(List<String> argumentNames) {
            this.argumentNames = ImmutableList.copyOf(argumentNames);
        }

        @Override
        public Double apply(List<Integer> values) {
            int sum = 0;
            for (Integer value : values)
                sum += value;
            return (double)sum;
        }

        @Override
        public String toString() {
            return argumentNames.stream().collect(Collectors.joining("+"));
        }

    }

}
