// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.evaluation;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Utility methods for working with rank feature names
 *
 * @author bratseth
 */
public class FeatureNames {

    private static final Pattern identifierRegexp = Pattern.compile("[A-Za-z0-9_][A-Za-z0-9_-]*");

    /**
     * Returns the given feature in canonical form.
     * A feature name consists of a feature shortname, followed by zero or more arguments enclosed in quotes
     * and an optional output prefixed by a dot: shortname[(argument-ist)][.output]
     * Arguments may be identifiers or any strings single or double quoted.
     *
     * Argument string values may not contain comma, single quote nor double quote characters.
     *
     * <i>The canonical form use no quotes for arguments which are identifiers, and double quotes otherwise.</i>
     */
    public static String canonicalize(String feature) {
        int startParenthesis = feature.indexOf('(');
        int endParenthesis = feature.lastIndexOf(')');
        if (startParenthesis < 1) return feature; // No arguments
        if (endParenthesis < startParenthesis)
            throw new IllegalArgumentException("A feature name must be on the form shortname[(argument-ist)][.output], " +
                                               "but was '" + feature + "'");
        String argumentString = feature.substring(startParenthesis + 1, endParenthesis);
        List<String> canonicalizedArguments =
                Arrays.stream(argumentString.split(","))
                      .map(FeatureNames::canonicalizeArgument)
                      .collect(Collectors.toList());
        return feature.substring(0, startParenthesis + 1) +
               canonicalizedArguments.stream().collect(Collectors.joining(",")) +
               feature.substring(endParenthesis);
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

}
