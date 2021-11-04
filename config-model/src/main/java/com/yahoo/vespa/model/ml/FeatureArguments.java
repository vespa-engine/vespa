// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.ml;

import com.yahoo.path.Path;
import com.yahoo.searchlib.rankingexpression.rule.Arguments;
import com.yahoo.searchlib.rankingexpression.rule.ConstantNode;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;

import java.util.Optional;

/**
 * Encapsulates the arguments of a specific model output
 *
 * @author bratseth
 */
public class FeatureArguments {

    private final Path path;

    /** Optional arguments */
    private final Optional<String> signature, output;

    public FeatureArguments(Arguments arguments) {
        this(Path.fromString(argument(0, arguments)),
             optionalArgument(1, arguments),
             optionalArgument(2, arguments));
    }

    private FeatureArguments(Path path, Optional<String> signature, Optional<String> output) {
        this.path = path;
        this.signature = signature;
        this.output = output;
    }

    public Path path() { return path; }
    public Optional<String> signature() { return signature; }
    public Optional<String> output() { return output; }

    public String toName() {
        return (signature.isPresent() ? signature.get() : "") +
               (output.isPresent() ? "." + output.get() : "");
    }

    private static String argument(int argumentIndex, Arguments arguments) {
        if (argumentIndex >= arguments.expressions().size())
            throw new IllegalArgumentException("Requires at least " + argumentIndex +
                                               " arguments, but got just " + arguments.size());
        return asString(arguments.expressions().get(argumentIndex));
    }

    private static Optional<String> optionalArgument(int argumentIndex, Arguments arguments) {
        if (argumentIndex >= arguments.expressions().size())
            return Optional.empty();
        return Optional.of(asString(arguments.expressions().get(argumentIndex)));
    }

    private static String asString(ExpressionNode node) {
        if ( ! (node instanceof ConstantNode))
            throw new IllegalArgumentException("Expected a constant string as argument, but got '" + node);
        return stripQuotes(node.toString());
    }

    private static String stripQuotes(String s) {
        if ( ! isQuoteSign(s.codePointAt(0))) return s;
        if ( ! isQuoteSign(s.codePointAt(s.length() - 1 )))
            throw new IllegalArgumentException("argument [" + s + "] is missing endquote");
        return s.substring(1, s.length()-1);
    }

    private static boolean isQuoteSign(int c) {
        return c == '\'' || c == '"';
    }

}
