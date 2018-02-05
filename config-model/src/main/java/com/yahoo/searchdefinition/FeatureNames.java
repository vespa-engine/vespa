/*
 * // Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
 *
 *
 */
package com.yahoo.searchdefinition;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Utility methods for query, document and constant rank feature names
 *
 * @author bratseth
 */
public class FeatureNames {

    private static final Pattern identifierRegexp = Pattern.compile("[A-Za-z0-9_][A-Za-z0-9_-]*");

    /**
     * <p>Returns the given query, document or constant feature in canonical form.
     * A feature name consists of a feature type name (query, attribute or constant),
     * followed by one argument enclosed in quotes.
     * The argument may be an identifier or any string single or double quoted.</p>
     *
     * <p>Argument string values may not contain comma, single quote nor double quote characters.</p>
     *
     * <p><i>The canonical form use no quotes for arguments which are identifiers, and double quotes otherwise.</i></p>
     *
     * <p>Note that the above definition is not true for features in general, which accept any ranking expression
     * as argument.</p>
     *
     * @throws IllegalArgumentException if the feature name is not valid
     */
    // Note that this implementation is more general than what is described above:
    // It accepts any number of arguments and an optional output
    public static String canonicalize(String feature) {
        return canonicalizeIfValid(feature).orElseThrow(() ->
            new IllegalArgumentException("A feature name must be on the form query(name), attribute(name) or " +
                                         "constant(name), but was '" + feature + "'"
        ));
    }

    /**
     * Canonicalizes the given argument as in canonicalize, but returns empty instead of throwing an exception if
     * the argument is not a valid feature
     */
    public static Optional<String> canonicalizeIfValid(String feature) {
        int startParenthesis = feature.indexOf('(');
        if (startParenthesis < 0)
            return Optional.empty();
        int endParenthesis = feature.lastIndexOf(')');
        String featureType = feature.substring(0, startParenthesis);
        if ( ! ( featureType.equals("query") || featureType.equals("attribute") || featureType.equals("constant")))
            return Optional.empty();
        if (startParenthesis < 1) return Optional.of(feature); // No arguments
        if (endParenthesis < startParenthesis)
            return Optional.empty();
        String argumentString = feature.substring(startParenthesis + 1, endParenthesis);
        List<String> canonicalizedArguments =
                Arrays.stream(argumentString.split(","))
                        .map(FeatureNames::canonicalizeArgument)
                        .collect(Collectors.toList());
        return Optional.of(featureType + "(" +
                           canonicalizedArguments.stream().collect(Collectors.joining(",")) +
                           feature.substring(endParenthesis));
    }

    /** Canomicalizes a single argument */
    private static String canonicalizeArgument(String argument) {
        if (argument.startsWith("'")) {
            if ( ! argument.endsWith("'"))
                throw new IllegalArgumentException("Feature arguments starting by a single quote " +
                                                   "must end by a single quote, but was \"" + argument + "\"");
            argument = argument.substring(1, argument.length() - 1);
        }
        if (argument.startsWith("\"")) {
            if ( ! argument.endsWith("\""))
                throw new IllegalArgumentException("Feature arguments starting by a double quote " +
                                                   "must end by a double quote, but was '" + argument + "'");
            argument = argument.substring(1, argument.length() - 1);
        }
        if (identifierRegexp.matcher(argument).matches())
            return argument;
        else
            return "\"" + argument + "\"";
    }

    public static String asConstantFeature(String constantName) {
        return canonicalize("constant(\"" + constantName + "\")");
    }

    public static String asAttributeFeature(String attributeName) {
        return canonicalize("attribute(\"" + attributeName + "\")");
    }

    public static String asQueryFeature(String propertyName) {
        return canonicalize("query(\"" + propertyName + "\")");
    }

    /**
     * Returns the single argument of the given feature name, without any quotes,
     * or empty if it is not a valid query, attribute or constant feature name
     */
    public static Optional<String> argumentOf(String feature) {
        return canonicalizeIfValid(feature).map(f -> {
            int startParenthesis = f.indexOf("(");
            int endParenthesis = f.indexOf(")");
            String possiblyQuotedArgument = f.substring(startParenthesis + 1, endParenthesis);
            if (possiblyQuotedArgument.startsWith("\""))
                return possiblyQuotedArgument.substring(1, possiblyQuotedArgument.length() - 1);
            else
                return possiblyQuotedArgument;
        });
    }

}
