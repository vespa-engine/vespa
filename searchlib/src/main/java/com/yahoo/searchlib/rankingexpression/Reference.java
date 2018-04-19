// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression;

import com.yahoo.searchlib.rankingexpression.rule.Arguments;
import com.yahoo.searchlib.rankingexpression.rule.CompositeNode;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.searchlib.rankingexpression.rule.NameNode;
import com.yahoo.searchlib.rankingexpression.rule.ReferenceNode;
import com.yahoo.searchlib.rankingexpression.rule.SerializationContext;
import com.yahoo.tensor.evaluation.TypeContext;

import java.util.Deque;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A reference to a feature, function, or value in ranking expressions
 *
 * @author bratseth
 */
public class Reference extends TypeContext.Name {

    private final Arguments arguments;

    /**
     * The output, or null if none
     */
    private final String output;

    public Reference(String name, Arguments arguments, String output) {
        super(name);
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(arguments, "arguments cannot be null");
        this.arguments = arguments;
        this.output = output;
    }

    public Arguments arguments() { return arguments; }

    public String output() { return output; }

    /**
     * Creates a reference to a simple feature consisting of a name and a single argument
     */
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

    /**
     * A <i>simple feature reference</i> is a reference with a single identifier argument
     * (and an optional output).
     */
    public boolean isSimple() {
        return simpleArgument().isPresent();
    }

    /**
     * If the arguments of this contains a single argument which is an identifier, it is returned.
     * Otherwise null is returned.
     */
    public Optional<String> simpleArgument() {
        if (arguments.expressions().size() != 1) return Optional.empty();
        ExpressionNode argument = arguments.expressions().get(0);

        if (argument instanceof ReferenceNode) {
            ReferenceNode refArgument = (ReferenceNode) argument;

            if ( ! refArgument.reference().isIdentifier()) return Optional.empty();

            return Optional.of(refArgument.getName());
        }
        else if (argument instanceof NameNode) {
            return Optional.of(((NameNode) argument).getValue());
        }
        else {
            return Optional.empty();
        }
    }

    /**
     * Returns whether this is a simple identifier - no arguments or output
     */
    public boolean isIdentifier() {
        return this.arguments.expressions().size() == 0 && output == null;
    }

    public Reference withArguments(Arguments arguments) {
        return new Reference(name(), arguments, output);
    }

    public Reference withOutput(String output) {
        return new Reference(name(), arguments, output);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof Reference)) return false;
        Reference other = (Reference) o;
        if (!Objects.equals(other.name(), this.name())) return false;
        if (!Objects.equals(other.arguments, this.arguments)) return false;
        if (!Objects.equals(other.output, this.output)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name(), arguments, output);
    }

    @Override
    public String toString() {
        return toString(new SerializationContext(), null, null);
    }

    public String toString(SerializationContext context, Deque<String> path, CompositeNode parent) {
        StringBuilder b = new StringBuilder(name());
        if (arguments != null && arguments.expressions().size() > 0)
            b.append("(").append(arguments.expressions().stream()
                                                        .map(node -> node.toString(context, path, parent))
                                                        .collect(Collectors.joining(","))).append(")");
        if (output != null)
            b.append(".").append(output);
        return b.toString();
    }

}
