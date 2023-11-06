// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.rule;

import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.searchlib.rankingexpression.evaluation.Context;
import com.yahoo.searchlib.rankingexpression.evaluation.MapContext;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.evaluation.TypeContext;
import com.yahoo.tensor.functions.Generate;

import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleUnaryOperator;
import java.util.stream.Collectors;

/**
 * A free, parametrized function
 *
 * @author bratseth
 */
public class LambdaFunctionNode extends CompositeNode {

    private final List<String> arguments;
    private final ExpressionNode functionExpression;

    public LambdaFunctionNode(List<String> arguments, ExpressionNode functionExpression) {
        if ( ! arguments.containsAll(featuresAccessedIn(functionExpression))) {
            throw new IllegalArgumentException("Lambda " + functionExpression + " accesses features outside its scope: " +
                                               featuresAccessedIn(functionExpression).stream()
                                                                                     .filter(f ->  ! arguments.contains(f))
                                                                                     .collect(Collectors.joining(", ")));
        }
        this.arguments = List.copyOf(arguments);
        this.functionExpression = functionExpression;
    }

    public String singleArgumentName() {
        if (arguments.size() != 1) {
            throw new IllegalArgumentException("Cannot apply " + this + " in map, must have a single argument");
        }
        return arguments.get(0);
    }

    @Override
    public List<ExpressionNode> children() {
        return Collections.singletonList(functionExpression);
    }

    @Override
    public CompositeNode setChildren(List<ExpressionNode> children) {
        if ( children.size() != 1)
            throw new IllegalArgumentException("A lambda function must have a single child expression");
        return new LambdaFunctionNode(arguments, children.get(0));
    }

    @Override
    public StringBuilder toString(StringBuilder string, SerializationContext context, Deque<String> path, CompositeNode parent) {
        string.append("f(").append(commaSeparated(arguments)).append(")(");
        return functionExpression.toString(string, context, path, this).append(")");
    }

    private String commaSeparated(List<String> list) {
        StringBuilder b = new StringBuilder();
        for (String element : list)
            b.append(element).append(",");
        if (b.length() > 0)
            b.setLength(b.length()-1);
        return b.toString();
    }

    @Override
    public TensorType type(TypeContext<Reference> context) {
        return TensorType.empty; // by definition - no nested lambdas
    }

    /** Evaluate this in a context which must have the arguments bound */
    @Override
    public Value evaluate(Context context) {
        return functionExpression.evaluate(context);
    }

    /**
     * Returns this as a double unary operator
     *
     * @throws IllegalStateException if this has more than one argument
     */
    public DoubleUnaryOperator asDoubleUnaryOperator() {
        if (arguments.size() > 1)
            throw new IllegalStateException("Cannot apply " + this + " as a DoubleUnaryOperator: " +
                                            "Must have at most one argument " + " but has " + arguments);
        return new DoubleUnaryLambda();
    }

    /**
     * Returns this as a double binary operator
     *
     * @throws IllegalStateException if this has more than two arguments
     */
    public DoubleBinaryOperator asDoubleBinaryOperator() {
        if (arguments.size() > 2)
            throw new IllegalStateException("Cannot apply " + this + " as a DoubleBinaryOperator: " +
                                            "Must have at most two argument " + " but has " + arguments);

        // Optimization: if possible, calculate directly rather than creating a context and evaluating the expression
        return getDirectEvaluator().orElseGet(DoubleBinaryLambda::new);
    }

    private Optional<DoubleBinaryOperator> getDirectEvaluator() {
        if ( ! (functionExpression instanceof OperationNode node)) {
            return Optional.empty();
        }
        if ( ! (node.children().get(0) instanceof ReferenceNode lhs) || ! (node.children().get(1) instanceof ReferenceNode rhs)) {
            return Optional.empty();
        }
        if (! lhs.getName().equals(arguments.get(0)) || ! rhs.getName().equals(arguments.get(1))) {
            return Optional.empty();
        }
        if (node.operators().size() != 1) {
            return Optional.empty();
        }
        Operator operator = node.operators().get(0);
        return switch (operator) {
            case or -> asFunctionExpression((left, right) -> ((left != 0.0) || (right != 0.0)) ? 1.0 : 0.0);
            case and -> asFunctionExpression((left, right) -> ((left != 0.0) && (right != 0.0)) ? 1.0 : 0.0);
            case plus -> asFunctionExpression((left, right) -> left + right);
            case minus -> asFunctionExpression((left, right) -> left - right);
            case multiply -> asFunctionExpression((left, right) -> left * right);
            case divide -> asFunctionExpression((left, right) -> left / right);
            case modulo -> asFunctionExpression((left, right) -> left % right);
            case power -> asFunctionExpression(Math::pow);
            default -> Optional.empty();
        };
    }

    private Optional<DoubleBinaryOperator> asFunctionExpression(DoubleBinaryOperator operator) {
        return Optional.of(new DoubleBinaryOperator() {
            @Override
            public double applyAsDouble(double left, double right) {
                return operator.applyAsDouble(left, right);
            }
            @Override
            public String toString() {
                return LambdaFunctionNode.this.toString();
            }
        });
    }

    private static class FeatureFinder {
        private final Set<String> target;
        private final Set<String> localVariables = new HashSet<>();
        FeatureFinder(Set<String> target) { this.target = target; }
        void process(ExpressionNode node) {
            if (node instanceof ReferenceNode refNode) {
                String featureName = refNode.reference().toString();
                if (! localVariables.contains(featureName)) {
                    target.add(featureName);
                }
                return;
            }
            Optional<FeatureFinder> subProcessor = Optional.empty();
            if (node instanceof TensorFunctionNode t) {
                var fun = t.function();
                if (fun instanceof Generate<?> g) {
                    var ff = new FeatureFinder(target);
                    var genType = g.type(null); // Generate knows its own type without any context
                    for (var dim : genType.dimensions()) {
                        ff.localVariables.add(dim.name());
                    }
                    subProcessor = Optional.of(ff);
                }
            }
            if (node instanceof CompositeNode composite) {
                final FeatureFinder processor = subProcessor.orElse(this);
                composite.children().forEach(child -> processor.process(child));
            }
        }
    }

    private static Set<String> featuresAccessedIn(ExpressionNode node) {
        Set<String> features = new HashSet<>();
        new FeatureFinder(features).process(node);
        return features;
    }

    @Override
    public int hashCode() { return Objects.hash("lambdaFunction", arguments, functionExpression); }

    private class DoubleUnaryLambda implements DoubleUnaryOperator {

        @Override
        public double applyAsDouble(double operand) {
            MapContext context = new MapContext();
            if (arguments.size() > 0)
                context.put(arguments.get(0), operand);
            return evaluate(context).asDouble();
        }

        @Override
        public String toString() {
            return LambdaFunctionNode.this.toString();
        }

    }

    private class DoubleBinaryLambda implements DoubleBinaryOperator {

        @Override
        public double applyAsDouble(double left, double right) {
            MapContext context = new MapContext();
            if (arguments.size() > 0)
                context.put(arguments.get(0), left);
            if (arguments.size() > 1)
                context.put(arguments.get(1), right);
            return evaluate(context).asDouble();
        }

        @Override
        public String toString() {
            return LambdaFunctionNode.this.toString();
        }

    }

}
