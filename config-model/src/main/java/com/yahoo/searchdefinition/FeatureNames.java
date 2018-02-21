/*
 * // Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
 *
 *
 */
package com.yahoo.searchdefinition;

import com.yahoo.searchlib.rankingexpression.Reference;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Utility methods for query, document and constant rank feature names
 *
 * @author bratseth
 */
public class FeatureNames {

    private static final Pattern identifierRegexp = Pattern.compile("[A-Za-z0-9_][A-Za-z0-9_-]*");

    public static Reference asConstantFeature(String constantName) {
        return Reference.simple("constant", quoteIfNecessary(constantName));
    }

    public static Reference asAttributeFeature(String attributeName) {
        return Reference.simple("attribute", quoteIfNecessary(attributeName));
    }

    public static Reference asQueryFeature(String propertyName) {
        return Reference.simple("query", quoteIfNecessary(propertyName));
    }

    /**
     * Returns the single argument of the given feature name, without any quotes,
     * or empty if it is not a valid query, attribute or constant feature name
     */
    public static Optional<String> argumentOf(String feature) {
        Optional<Reference> reference = Reference.simple(feature);
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
