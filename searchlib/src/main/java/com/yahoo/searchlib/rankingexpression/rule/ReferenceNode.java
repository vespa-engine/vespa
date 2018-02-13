// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.rule;

import com.yahoo.searchlib.rankingexpression.ExpressionFunction;
import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.evaluation.Context;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.evaluation.TypeContext;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * A node referring either to a value in the context or to a named ranking expression (function aka macro).
 *
 * @author simon
 * @author bratseth
 */
// TODO: Using the same node to represent formal function argument, the function itself, and to features is confusing.
//       At least the first two should be split into separate classes.
public final class ReferenceNode extends CompositeNode {

    private final String name, output;

    private final Arguments arguments;

    public ReferenceNode(String name) {
        this(name, null, null);
    }

    public ReferenceNode(String name, List<? extends ExpressionNode> arguments, String output) {
        this.name = name;
        this.arguments = arguments != null ? new Arguments(arguments) : new Arguments();
        this.output = output;
    }

    public String getName() {
        return name;
    }

    /** Returns the arguments, never null */
    public Arguments getArguments() { return arguments; }

    /** Returns a copy of this where the arguments are replaced by the given arguments */
    public ReferenceNode setArguments(List<ExpressionNode> arguments) {
        return new ReferenceNode(name, arguments, output);
    }

    /** Returns the specific output this references, or null if none specified */
    public String getOutput() { return output; }

    /** Returns a copy of this node with a modified output */
    public ReferenceNode setOutput(String output) {
        return new ReferenceNode(name, arguments.expressions(), output);
    }

    /** Returns an empty list as this has no children */
    @Override
    public List<ExpressionNode> children() { return arguments.expressions(); }

    @Override
    public String toString(SerializationContext context, Deque<String> path, CompositeNode parent) {
        return toString(context, path, true);
    }

    private String toString(SerializationContext context, Deque<String> path, boolean includeOutput) {
        if (path == null)
            path = new ArrayDeque<>();
        String myName = this.name;
        String myOutput = this.output;
        List<ExpressionNode> myArguments = this.arguments.expressions();

        String resolvedArgument = context.getBinding(myName);
        if (resolvedArgument != null && isBindableName()) {
            // Replace this whole node with the value of the argument value that it maps to
            myName = resolvedArgument;
            myArguments = null;
            myOutput = null;
        } else if (context.getFunction(myName) != null) {
            // Replace by the referenced expression
            ExpressionFunction function = context.getFunction(myName);
            if (function != null && myArguments != null && function.arguments().size() == myArguments.size() && myOutput == null) {
                String myPath = name + this.arguments.expressions();
                if (path.contains(myPath)) {
                    throw new IllegalStateException("Cycle in ranking expression function: " + path);
                }
                path.addLast(myPath);
                ExpressionFunction.Instance instance = function.expand(context, myArguments, path);
                path.removeLast();
                context.addFunctionSerialization(RankingExpression.propertyName(instance.getName()), instance.getExpressionString());
                myName = "rankingExpression(" + instance.getName() + ")";
                myArguments = null;
                myOutput = null;
            }
        }
        // Always print the same way, the magic is already done.
        StringBuilder ret = new StringBuilder(myName);
        if (myArguments != null && myArguments.size() > 0) {
            ret.append("(");
            for (int i = 0; i < myArguments.size(); ++i) {
                ret.append(myArguments.get(i).toString(context, path, this));
                if (i < myArguments.size() - 1) {
                    ret.append(",");
                }
            }
            ret.append(")");
        }
        if (includeOutput)
            ret.append(myOutput != null ? "." + myOutput : "");
        return ret.toString();
    }

    /** Returns whether this is a name that can be bound to a value (during argument passing) */
    public boolean isBindableName() {
        return this.arguments.expressions().size() == 0 && output == null;
    }

    @Override
    public TensorType type(TypeContext context) {
        String feature = toString(new SerializationContext(), null, false);
        TensorType type = context.getType(feature);
        // TensorType type = context.getType(name, arguments, output); TODO
        if (type == null)
            throw new IllegalArgumentException("Unknown feature '" + feature + "'");
        return type;
    }

    @Override
    public Value evaluate(Context context) {
        if (arguments.expressions().isEmpty() && output == null)
            return context.get(name);
        return context.get(name, arguments, output);
    }

    @Override
    public CompositeNode setChildren(List<ExpressionNode> newChildren) {
        return new ReferenceNode(name, newChildren, output);
    }

}
