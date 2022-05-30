// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema;

import com.yahoo.searchlib.rankingexpression.Reference;

import java.util.Optional;

/**
 * Utility methods for query, document and constant rank feature names
 *
 * @author bratseth
 */
public class FeatureNames {

    public static Reference asConstantFeature(String constantName) {
        return Reference.simple("constant", quoteIfNecessary(constantName));
    }

    public static Reference asAttributeFeature(String attributeName) {
        return Reference.simple("attribute", attributeName);
    }

    public static Reference asQueryFeature(String propertyName) {
        return Reference.simple("query", quoteIfNecessary(propertyName));
    }

    /** Returns true if the given reference is an attribute, constant or query feature */
    public static boolean isSimpleFeature(Reference reference) {
        if ( ! reference.isSimple()) return false;
        String name = reference.name();
        return name.equals("attribute") || name.equals("constant") || name.equals("query");
    }

    /** Returns true if this is a constant */
    public static boolean isConstantFeature(Reference reference) {
        if ( ! isSimpleFeature(reference)) return false;
        return reference.name().equals("constant");
    }

    /** Returns true if this is a query feature */
    public static boolean isQueryFeature(Reference reference) {
        if ( ! isSimpleFeature(reference)) return false;
        return reference.name().equals("query");
    }

    /** Returns true if this is an attribute feature */
    public static boolean isAttributeFeature(Reference reference) {
        if ( ! isSimpleFeature(reference)) return false;
        return reference.name().equals("attribute");
    }

    /**
     * Returns the single argument of the given feature name, without any quotes,
     * or empty if it is not a valid query, attribute or constant feature name
     */
    public static Optional<String> argumentOf(String feature) {
        Optional<Reference> reference = Reference.simple(feature);
        if ( reference.isEmpty()) return Optional.empty();
        if ( ! ( reference.get().name().equals("attribute") ||
                 reference.get().name().equals("constant") ||
                 reference.get().name().equals("query")))
            return Optional.empty();

        return Optional.of(reference.get().arguments().expressions().get(0).toString());
    }

    private static String quoteIfNecessary(String s) {
        if (notNeedQuotes(s))
            return s;
        else
            return "\"" + s + "\"";
    }

    static boolean notNeedQuotes(String s) {
        // Faster version of the regexp [A-Za-z0-9_][A-Za-z0-9_-]*
        if (s.isEmpty()) return false;
        if ( ! isValidFirst(s.charAt(0))) return false;
        for (int i = 1; i < s.length(); i++) {
            if (!isValidAny(s.charAt(i))) return false;
        }
        return true;
    }
    private static boolean isValidFirst(char c) {
        // [A-Za-z0-9_]
        return (c == '_') || ((c >= 'a') && (c <= 'z')) || ((c >= 'A') && (c <= 'Z')) || ((c >= '0') && (c <= '9'));
    }
    private static boolean isValidAny(char c) {
        // [A-Za-z0-9_-]*
        return c == '-' || isValidFirst(c);
    }

}
