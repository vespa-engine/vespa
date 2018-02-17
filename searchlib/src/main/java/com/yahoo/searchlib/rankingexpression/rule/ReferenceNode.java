// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.rule;

import com.yahoo.searchlib.rankingexpression.ExpressionFunction;
import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.Reference;
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
public final class ReferenceNode extends CompositeNode {

    private final Reference reference;

    /* Creates a node with a simple identifier reference */
    public ReferenceNode(String name) {
        this(name, null, null);
    }

    public ReferenceNode(String name, List<? extends ExpressionNode> arguments, String output) {
        this.reference = new Reference(name,
                                       arguments != null ? new Arguments(arguments) : new Arguments(),
                                       output);
    }

    public ReferenceNode(Reference reference) {
        this.reference = reference;
    }

    public String getName() {
        return reference.name();
    }

    /** Returns the arguments, never null */
    public Arguments getArguments() { return reference.arguments(); }

    /** Returns a copy of this where the arguments are replaced by the given arguments */
    public ReferenceNode setArguments(List<ExpressionNode> arguments) {
        return new ReferenceNode(reference.withArguments(new Arguments(arguments)));
    }

    /** Returns the specific output this references, or null if none specified */
    public String getOutput() { return reference.output(); }

    /** Returns a copy of this node with a modified output */
    public ReferenceNode setOutput(String output) {
        return new ReferenceNode(reference.withOutput(output));
    }

    /** Returns an empty list as this has no children */
    @Override
    public List<ExpressionNode> children() { return reference.arguments().expressions(); }

    @Override
    public String toString(SerializationContext context, Deque<String> path, CompositeNode parent) {
        if (path == null)
            path = new ArrayDeque<>();
        String myName = getName();
        String myOutput = getOutput();
        List<ExpressionNode> myArguments = getArguments().expressions();

        if (reference.isIdentifier() && context.getBinding(myName) != null) {
            // a bound identifier: replace by the value it is bound to
            return context.getBinding(myName);
        }

        ExpressionFunction function = context.getFunction(myName);
        if (function != null  && function.arguments().size() == myArguments.size() && myOutput == null) {
            // a function reference: replace by the referenced function wrapped in rankingExpression
            String myPath = getName() + getArguments().expressions();
            if (path.contains(myPath))
                throw new IllegalStateException("Cycle in ranking expression function: " + path);
            path.addLast(myPath);
            ExpressionFunction.Instance instance = function.expand(context, myArguments, path);
            path.removeLast();
            context.addFunctionSerialization(RankingExpression.propertyName(instance.getName()), instance.getExpressionString());
            return "rankingExpression(" + instance.getName() + ")";
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
        ret.append(myOutput != null ? "." + myOutput : "");
        return ret.toString();
    }

    /** Returns the reference of this node */
    public Reference reference() { return reference; }

    @Override
    public TensorType type(TypeContext context) {
        TensorType type = context.getType(reference);
        if (type == null)
            throw new IllegalArgumentException("Unknown feature '" + toString() + "'");
        return type;
    }

    @Override
    public Value evaluate(Context context) {
        // TODO: Context should accept a Reference instead.

        if (reference.isIdentifier())
            return context.get(reference.name());
        return context.get(getName(), getArguments(), getOutput());
    }

    @Override
    public CompositeNode setChildren(List<ExpressionNode> newChildren) {
        return setArguments(newChildren);
    }

}
