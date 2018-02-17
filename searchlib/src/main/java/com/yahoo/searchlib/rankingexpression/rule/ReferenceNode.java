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
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A node referring either to a value in the context or to a named ranking expression (function aka macro).
 *
 * @author simon
 * @author bratseth
 */
// TODO: Using the same node to represent formal function argument, the function itself, and to features is confusing.
//       At least the first two should be split into separate classes.
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
        return toString(context, path, true);
    }

    private String toString(SerializationContext context, Deque<String> path, boolean includeOutput) {
        if (path == null)
            path = new ArrayDeque<>();
        String myName = getName();
        String myOutput = getOutput();
        List<ExpressionNode> myArguments = getArguments().expressions();

        String resolvedArgument = context.getBinding(myName);
        if (resolvedArgument != null && reference.isIdentifier()) {
            // Replace this whole node with the value of the argument value that it maps to
            myName = resolvedArgument;
            myArguments = null;
            myOutput = null;
        } else if (context.getFunction(myName) != null) {
            // Replace by the referenced expression
            ExpressionFunction function = context.getFunction(myName);
            if (function != null && myArguments != null && function.arguments().size() == myArguments.size() && myOutput == null) {
                String myPath = getName() + getArguments().expressions();
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
        return context.get(reference.toString());
    }

    @Override
    public CompositeNode setChildren(List<ExpressionNode> newChildren) {
        return setArguments(newChildren);
    }

    /** Wraps the content of this in a form which can be passed to a type context */
    // TODO: Extract to top level?
    public static class Reference extends TypeContext.Name {

        private final String name;
        private final Arguments arguments;

        /** The output, or null if none */
        private final String output;

        public Reference(String name, Arguments arguments, String output) {
            super(name);
            Objects.requireNonNull(name, "name cannot be null");
            Objects.requireNonNull(arguments, "arguments cannot be null");
            this.name = name;
            this.arguments = arguments;
            this.output = output;
        }

        public String name() { return name; }
        public Arguments arguments() { return arguments; }
        public String output() { return output; }

        /** Creates a reference to a simple feature consisting of a name and a single argument */
        public static Reference simple(String name, String argumentValue) {
            return new Reference(name,
                                 new Arguments(new ReferenceNode(argumentValue)),
                                 null);
        }

        /**
         * Returns the given simple feature as a reference, or empty if it is not a valid simple
         * feature string on the form name(argument).
         */
        public static Optional<Reference> simple(String feature) {
            int startParenthesis = feature.indexOf('(');
            if (startParenthesis < 0)
                return Optional.empty();
            int endParenthesis = feature.lastIndexOf(')');
            String featureName = feature.substring(0, startParenthesis);
            if (startParenthesis < 1 || endParenthesis < startParenthesis) return Optional.empty();
            String argument = feature.substring(startParenthesis + 1, endParenthesis);
            if (argument.startsWith("'") || argument.startsWith("\""))
                argument = argument.substring(1);
            if (argument.endsWith("'") || argument.endsWith("\""))
                argument = argument.substring(0, argument.length() - 1);
            return Optional.of(simple(featureName, argument));
        }

        /** Returns whether this is a simple identifier - no arguments or output */
        public boolean isIdentifier() {
            return this.arguments.expressions().size() == 0 && output == null;
        }

        public Reference withArguments(Arguments arguments) {
            return new Reference(name, arguments, output);
        }

        public Reference withOutput(String output) {
            return new Reference(name, arguments, output);
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) return true;
            if ( ! (o instanceof Reference)) return false;
            Reference other = (Reference)o;
            if ( ! Objects.equals(other.name, this.name)) return false;
            if ( ! Objects.equals(other.arguments, this.arguments)) return false;
            if ( ! Objects.equals(other.output, this.output)) return false;
            return true;
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, arguments, output);
        }

        @Override
        public String toString() {
            StringBuilder b = new StringBuilder(name);
            if (arguments != null && arguments.expressions().size() > 0)
                b.append("(").append(arguments.expressions().stream().map(ExpressionNode::toString).collect(Collectors.joining(","))).append(")");
            if (output !=null)
                b.append(".").append(output);
            return b.toString();
        }

    }

}
