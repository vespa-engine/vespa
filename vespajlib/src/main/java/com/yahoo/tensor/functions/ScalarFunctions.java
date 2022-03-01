// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.functions;

import com.google.common.collect.ImmutableList;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleUnaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Factory of scalar Java functions.
 * The purpose of this is to embellish anonymous functions with a runtime type
 * such that they can be inspected and will return a parsable toString.
 *
 * @author bratseth
 */
public class ScalarFunctions {

    public static DoubleBinaryOperator add() { return new Add(); }
    public static DoubleBinaryOperator divide() { return new Divide(); }
    public static DoubleBinaryOperator equal() { return new Equal(); }
    public static DoubleBinaryOperator greater() { return new Greater(); }
    public static DoubleBinaryOperator less() { return new Less(); }
    public static DoubleBinaryOperator max() { return new Max(); }
    public static DoubleBinaryOperator min() { return new Min(); }
    public static DoubleBinaryOperator mean() { return new Mean(); }
    public static DoubleBinaryOperator multiply() { return new Multiply(); }
    public static DoubleBinaryOperator pow() { return new Pow(); }
    public static DoubleBinaryOperator squareddifference() { return new SquaredDifference(); }
    public static DoubleBinaryOperator subtract() { return new Subtract(); }
    public static DoubleBinaryOperator hamming() { return new Hamming(); }

    public static DoubleUnaryOperator abs() { return new Abs(); }
    public static DoubleUnaryOperator acos() { return new Acos(); }
    public static DoubleUnaryOperator asin() { return new Asin(); }
    public static DoubleUnaryOperator atan() { return new Atan(); }
    public static DoubleUnaryOperator ceil() { return new Ceil(); }
    public static DoubleUnaryOperator cos() { return new Cos(); }
    public static DoubleUnaryOperator exp() { return new Exp(); }
    public static DoubleUnaryOperator floor() { return new Floor(); }
    public static DoubleUnaryOperator log() { return new Log(); }
    public static DoubleUnaryOperator neg() { return new Neg(); }
    public static DoubleUnaryOperator reciprocal() { return new Reciprocal(); }
    public static DoubleUnaryOperator rsqrt() { return new Rsqrt(); }
    public static DoubleUnaryOperator sin() { return new Sin(); }
    public static DoubleUnaryOperator sigmoid() { return new Sigmoid(); }
    public static DoubleUnaryOperator sqrt() { return new Sqrt(); }
    public static DoubleUnaryOperator square() { return new Square(); }
    public static DoubleUnaryOperator tan() { return new Tan(); }
    public static DoubleUnaryOperator tanh() { return new Tanh(); }
    public static DoubleUnaryOperator erf() { return new Erf(); }

    public static DoubleUnaryOperator elu() { return new Elu(); }
    public static DoubleUnaryOperator elu(double alpha) { return new Elu(alpha); }
    public static DoubleUnaryOperator leakyrelu() { return new LeakyRelu(); }
    public static DoubleUnaryOperator leakyrelu(double alpha) { return new LeakyRelu(alpha); }
    public static DoubleUnaryOperator relu() { return new Relu(); }
    public static DoubleUnaryOperator selu() { return new Selu(); }
    public static DoubleUnaryOperator selu(double scale, double alpha) { return new Selu(scale, alpha); }

    public static Function<List<Long>, Double> random() { return new Random(); }
    public static Function<List<Long>, Double> equal(List<String> argumentNames) { return new EqualElements(argumentNames); }
    public static Function<List<Long>, Double> sum(List<String> argumentNames) { return new SumElements(argumentNames); }
    public static Function<List<Long>, Double> constant(double value) { return new Constant(value); }

    // Binary operators -----------------------------------------------------------------------------

    public static class Add implements DoubleBinaryOperator {
        @Override
        public double applyAsDouble(double left, double right) { return left + right; }
        @Override
        public String toString() { return "f(a,b)(a + b)"; }
        @Override
        public int hashCode() { return "add".hashCode(); }
    }

    public static class Equal implements DoubleBinaryOperator {
        @Override
        public double applyAsDouble(double left, double right) { return left == right ? 1 : 0; }
        @Override
        public String toString() { return "f(a,b)(a==b)"; }
        @Override
        public int hashCode() { return "equal".hashCode(); }
    }

    public static class Greater implements DoubleBinaryOperator {
        @Override
        public double applyAsDouble(double left, double right) { return left > right ? 1 : 0; }
        @Override
        public String toString() { return "f(a,b)(a > b)"; }
        @Override
        public int hashCode() { return "greater".hashCode(); }
    }

    public static class Less implements DoubleBinaryOperator {
        @Override
        public double applyAsDouble(double left, double right) { return left < right ? 1 : 0; }
        @Override
        public String toString() { return "f(a,b)(a < b)"; }
        @Override
        public int hashCode() { return "less".hashCode(); }
    }

    public static class Max implements DoubleBinaryOperator {
        @Override
        public double applyAsDouble(double left, double right) { return Math.max(left, right); }
        @Override
        public String toString() { return "f(a,b)(max(a, b))"; }
        @Override
        public int hashCode() { return "max".hashCode(); }
    }

    public static class Min implements DoubleBinaryOperator {
        @Override
        public double applyAsDouble(double left, double right) { return Math.min(left, right); }
        @Override
        public String toString() { return "f(a,b)(min(a, b))"; }
        @Override
        public int hashCode() { return "min".hashCode(); }
    }

    public static class Mean implements DoubleBinaryOperator {
        @Override
        public double applyAsDouble(double left, double right) { return (left + right) / 2; }
        @Override
        public String toString() { return "f(a,b)((a + b) / 2)"; }
        @Override
        public int hashCode() { return "mean".hashCode(); }
    }

    public static class Multiply implements DoubleBinaryOperator {
        @Override
        public double applyAsDouble(double left, double right) { return left * right; }
        @Override
        public String toString() { return "f(a,b)(a * b)"; }
        @Override
        public int hashCode() { return "multiply".hashCode(); }
    }

    public static class Pow implements DoubleBinaryOperator {
        @Override
        public double applyAsDouble(double left, double right) { return Math.pow(left, right); }
        @Override
        public String toString() { return "f(a,b)(pow(a, b))"; }
        @Override
        public int hashCode() { return "pow".hashCode(); }
    }

    public static class Divide implements DoubleBinaryOperator {
        @Override
        public double applyAsDouble(double left, double right) { return left / right; }
        @Override
        public String toString() { return "f(a,b)(a / b)"; }
        @Override
        public int hashCode() { return "divide".hashCode(); }
    }

    public static class SquaredDifference implements DoubleBinaryOperator {
        @Override
        public double applyAsDouble(double left, double right) { return (left - right) * (left - right); }
        @Override
        public String toString() { return "f(a,b)((a-b) * (a-b))"; }
        @Override
        public int hashCode() { return "squareddifference".hashCode(); }
    }

    public static class Subtract implements DoubleBinaryOperator {
        @Override
        public double applyAsDouble(double left, double right) { return left - right; }
        @Override
        public String toString() { return "f(a,b)(a - b)"; }
        @Override
        public int hashCode() { return "subtract".hashCode(); }
    }

    
    public static class Hamming implements DoubleBinaryOperator {
        public static double hamming(double left, double right) {
            double distance = 0;
            byte a = (byte) left;
            byte b = (byte) right;
            for (int i = 0; i < 8; i++) {
                byte bit = (byte) (1 << i);
                if ((a & bit) != (b & bit)) {
                    distance += 1;
                }
            }
            return distance;
        }
        @Override
        public double applyAsDouble(double left, double right) { return hamming(left, right); }
        @Override
        public String toString() { return "f(a,b)(hamming(a,b))"; }
        @Override
        public int hashCode() { return "hamming".hashCode(); }
    }


    // Unary operators ------------------------------------------------------------------------------

    public static class Abs implements DoubleUnaryOperator {
        @Override
        public double applyAsDouble(double operand) { return Math.abs(operand); }
        @Override
        public String toString() { return "f(a)(fabs(a))"; }
        @Override
        public int hashCode() { return "abs".hashCode(); }
    }

    public static class Acos implements DoubleUnaryOperator {
        @Override
        public double applyAsDouble(double operand) { return Math.acos(operand); }
        @Override
        public String toString() { return "f(a)(acos(a))"; }
        @Override
        public int hashCode() { return "acos".hashCode(); }
    }

    public static class Asin implements DoubleUnaryOperator {
        @Override
        public double applyAsDouble(double operand) { return Math.asin(operand); }
        @Override
        public String toString() { return "f(a)(asin(a))"; }
        @Override
        public int hashCode() { return "asin".hashCode(); }
    }

    public static class Atan implements DoubleUnaryOperator {
        @Override
        public double applyAsDouble(double operand) { return Math.atan(operand); }
        @Override
        public String toString() { return "f(a)(atan(a))"; }
        @Override
        public int hashCode() { return "atan".hashCode(); }
    }

    public static class Ceil implements DoubleUnaryOperator {
        @Override
        public double applyAsDouble(double operand) { return Math.ceil(operand); }
        @Override
        public String toString() { return "f(a)(ceil(a))"; }
        @Override
        public int hashCode() { return "ceil".hashCode(); }
    }

    public static class Cos implements DoubleUnaryOperator {
        @Override
        public double applyAsDouble(double operand) { return Math.cos(operand); }
        @Override
        public String toString() { return "f(a)(cos(a))"; }
        @Override
        public int hashCode() { return "cos".hashCode(); }
    }

    public static class Elu implements DoubleUnaryOperator {
        private final double alpha;
        public Elu() {
            this(1.0);
        }
        public Elu(double alpha) {
            this.alpha = alpha;
        }
        @Override
        public double applyAsDouble(double operand) { return operand < 0 ? alpha * (Math.exp(operand) - 1) : operand; }
        @Override
        public String toString() { return "f(a)(if(a < 0, " + alpha + " * (exp(a)-1), a))"; }
        @Override
        public int hashCode() { return Objects.hash("elu", alpha); }
    }

    public static class Exp implements DoubleUnaryOperator {
        @Override
        public double applyAsDouble(double operand) { return Math.exp(operand); }
        @Override
        public String toString() { return "f(a)(exp(a))"; }
        @Override
        public int hashCode() { return "exp".hashCode(); }
    }

    public static class Floor implements DoubleUnaryOperator {
        @Override
        public double applyAsDouble(double operand) { return Math.floor(operand); }
        @Override
        public String toString() { return "f(a)(floor(a))"; }
        @Override
        public int hashCode() { return "floor".hashCode(); }
    }

    public static class Log implements DoubleUnaryOperator {
        @Override
        public double applyAsDouble(double operand) { return Math.log(operand); }
        @Override
        public String toString() { return "f(a)(log(a))"; }
        @Override
        public int hashCode() { return "log".hashCode(); }
    }

    public static class Neg implements DoubleUnaryOperator {
        @Override
        public double applyAsDouble(double operand) { return -operand; }
        @Override
        public String toString() { return "f(a)(-a)"; }
        @Override
        public int hashCode() { return "neg".hashCode(); }
    }

    public static class Reciprocal implements DoubleUnaryOperator {
        @Override
        public double applyAsDouble(double operand) { return 1.0 / operand; }
        @Override
        public String toString() { return "f(a)(1 / a)"; }
        @Override
        public int hashCode() { return "reciprocal".hashCode(); }
    }

    public static class Relu implements DoubleUnaryOperator {
        @Override
        public double applyAsDouble(double operand) { return Math.max(operand, 0); }
        @Override
        public String toString() { return "f(a)(max(0, a))"; }
        @Override
        public int hashCode() { return "relu".hashCode(); }
    }

    public static class Selu implements DoubleUnaryOperator {
        // See https://arxiv.org/abs/1706.02515
        private final double scale; // 1.0507009873554804934193349852946;
        private final double alpha; // 1.6732632423543772848170429916717;
        public Selu() {
            this(1.0507009873554804934193349852946, 1.6732632423543772848170429916717);
        }
        public Selu(double scale, double alpha) {
            this.scale = scale;
            this.alpha = alpha;
        }
        @Override
        public double applyAsDouble(double operand) { return scale * (operand >= 0.0 ? operand : alpha * (Math.exp(operand)-1)); }
        @Override
        public String toString() { return "f(a)(" + scale + " * if(a >= 0, a, " + alpha + " * (exp(a) - 1)))"; }
        @Override
        public int hashCode() { return Objects.hash("selu", scale, alpha); }
    }

    public static class LeakyRelu implements DoubleUnaryOperator {
        private final double alpha;
        public LeakyRelu() {
            this(0.01);
        }
        public LeakyRelu(double alpha) {
            this.alpha = alpha;
        }
        @Override
        public double applyAsDouble(double operand) { return Math.max(alpha * operand, operand); }
        @Override
        public String toString() { return "f(a)(max(" + alpha + " * a, a))"; }
        @Override
        public int hashCode() { return Objects.hash("leakyrelu", alpha); }
    }

    public static class Sin implements DoubleUnaryOperator {
        @Override
        public double applyAsDouble(double operand) { return Math.sin(operand); }
        @Override
        public String toString() { return "f(a)(sin(a))"; }
        @Override
        public int hashCode() { return "sin".hashCode(); }
    }

    public static class Rsqrt implements DoubleUnaryOperator {
        @Override
        public double applyAsDouble(double operand) { return 1.0 / Math.sqrt(operand); }
        @Override
        public String toString() { return "f(a)(1.0 / sqrt(a))"; }
        @Override
        public int hashCode() { return "rsqrt".hashCode(); }
    }

    public static class Sigmoid implements DoubleUnaryOperator {
        @Override
        public double applyAsDouble(double operand) { return 1.0 / (1.0 + Math.exp(-operand)); }
        @Override
        public String toString() { return "f(a)(1 / (1 + exp(-a)))"; }
        @Override
        public int hashCode() { return "sigmoid".hashCode(); }
    }

    public static class Sqrt implements DoubleUnaryOperator {
        @Override
        public double applyAsDouble(double operand) { return Math.sqrt(operand); }
        @Override
        public String toString() { return "f(a)(sqrt(a))"; }
        @Override
        public int hashCode() { return "sqrt".hashCode(); }
    }

    public static class Square implements DoubleUnaryOperator {
        @Override
        public double applyAsDouble(double operand) { return operand * operand; }
        @Override
        public String toString() { return "f(a)(a * a)"; }
        @Override
        public int hashCode() { return "square".hashCode(); }
    }

    public static class Tan implements DoubleUnaryOperator {
        @Override
        public double applyAsDouble(double operand) { return Math.tan(operand); }
        @Override
        public String toString() { return "f(a)(tan(a))"; }
        @Override
        public int hashCode() { return "tan".hashCode(); }
    }

    public static class Tanh implements DoubleUnaryOperator {
        @Override
        public double applyAsDouble(double operand) { return Math.tanh(operand); }
        @Override
        public String toString() { return "f(a)(tanh(a))"; }
        @Override
        public int hashCode() { return "tanh".hashCode(); }
    }

    public static class Erf implements DoubleUnaryOperator {
        static final Comparator<Double> byAbs = (x,y) -> Double.compare(Math.abs(x), Math.abs(y));

        static double kummer(double a, double b, double z) {
            PriorityQueue<Double> terms = new PriorityQueue<>(byAbs);
            double term = 1.0;
            long n = 0;
            while (Math.abs(term) > Double.MIN_NORMAL) {
                terms.add(term);
                term *= (a+n);
                term /= (b+n);
                ++n;
                term *= z;
                term /= n;
            }
            double sum = terms.remove();
            while (! terms.isEmpty()) {
                sum += terms.remove();
                terms.add(sum);
                sum = terms.remove();
            }
            return sum;
        }

        static double approx_erfc(double x) {
            double sq = x*x;
            double mult = Math.exp(-sq) / (x * Math.sqrt(Math.PI));
            double term = 1.0;
            long n = 1;
            double sum = 0.0;
            while ((sum + term) != sum) {
                double pterm = term;
                sum += term;
                term = 0.5 * pterm * n / sq;
                if (term > pterm) {
                    sum -= 0.5 * pterm;
                    return sum*mult;
                }
                n += 2;
                pterm = term;
                sum -= term;
                term = 0.5 * pterm * n / sq;
                if (term > pterm) {
                    sum += 0.5 * pterm;
                    return sum*mult;
                }
                n += 2;
            }
            return sum*mult;
        }

        @Override
        public double applyAsDouble(double operand) { return erf(operand); }
        @Override
        public String toString() { return "f(a)(erf(a))"; }
        @Override
        public int hashCode() { return "erf".hashCode(); }

        static final double nearZeroMultiplier = 2.0 / Math.sqrt(Math.PI);

        public static double erf(double v) {
            if (v < 0) {
                return -erf(Math.abs(v));
            }
            if (v < 1.0e-10) {
                // Just use the derivate when very near zero:
                return v * nearZeroMultiplier;
            }
            if (v <= 1.0) {
                // works best when v is small
                return v * nearZeroMultiplier * kummer(0.5, 1.5, -v*v);
            }
            if (v < 4.3) {
                // slower, but works with bigger v
                return v * nearZeroMultiplier * Math.exp(-v*v) * kummer(1.0, 1.5, v*v);
            }
            // works only with "very big" v
            return 1.0 - approx_erfc(v);
        }
    }

    // Variable-length operators -----------------------------------------------------------------------------

    public static class EqualElements implements Function<List<Long>, Double> {
        private final ImmutableList<String> argumentNames;
        private EqualElements(List<String> argumentNames) {
            this.argumentNames = ImmutableList.copyOf(argumentNames);
        }

        @Override
        public Double apply(List<Long> values) {
            if (values.isEmpty()) return 1.0;
            for (Long value : values)
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
        @Override
        public int hashCode() { return Objects.hash("equal", argumentNames); }
    }

    public static class Random implements Function<List<Long>, Double> {
        @Override
        public Double apply(List<Long> values) {
            return ThreadLocalRandom.current().nextDouble();
        }
        @Override
        public String toString() { return "random"; }
        @Override
        public int hashCode() { return "random".hashCode(); }
    }

    public static class SumElements implements Function<List<Long>, Double> {
        private final ImmutableList<String> argumentNames;
        private SumElements(List<String> argumentNames) {
            this.argumentNames = ImmutableList.copyOf(argumentNames);
        }

        @Override
        public Double apply(List<Long> values) {
            long sum = 0;
            for (Long value : values)
                sum += value;
            return (double)sum;
        }
        @Override
        public String toString() {
            return argumentNames.stream().collect(Collectors.joining("+"));
        }
        @Override
        public int hashCode() { return Objects.hash("sum", argumentNames); }
    }

    public static class Constant implements Function<List<Long>, Double> {
        private final double value;

        public Constant(double value) {
            this.value = value;
        }
        @Override
        public Double apply(List<Long> values) {
            return value;
        }
        @Override
        public String toString() { return Double.toString(value); }
        @Override
        public int hashCode() { return Objects.hash("constant", value); }
    }


}
