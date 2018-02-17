/*
 * // Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
 *
 *
 */
package com.yahoo.searchdefinition;

import com.yahoo.searchlib.rankingexpression.rule.ReferenceNode;

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

    public static ReferenceNode.Reference asConstantFeature(String constantName) {
        return ReferenceNode.Reference.simple("constant", quoteIfNecessary(constantName));
    }

    public static ReferenceNode.Reference asAttributeFeature(String attributeName) {
        return ReferenceNode.Reference.simple("attribute", quoteIfNecessary(attributeName));
    }

    public static ReferenceNode.Reference asQueryFeature(String propertyName) {
        return ReferenceNode.Reference.simple("query", quoteIfNecessary(propertyName));
    }

    /**
     * Returns the single argument of the given feature name, without any quotes,
     * or empty if it is not a valid query, attribute or constant feature name
     */
    public static Optional<String> argumentOf(String feature) {
        Optional<ReferenceNode.Reference> reference = ReferenceNode.Reference.simple(feature);
        if ( ! reference.isPresent()) return Optional.empty();
        if ( ! ( reference.get().name().equals("attribute") ||
                 reference.get().name().equals("constant") ||
                 reference.get().name().equals("query")))
            return Optional.empty();

        return Optional.of(reference.get().arguments().expressions().get(0).toString());
    }

    private static String quoteIfNecessary(String s) {
        if (identifierRegexp.matcher(s).matches())
            return s;
        else
            return "\"" + s + "\"";
    }

}
