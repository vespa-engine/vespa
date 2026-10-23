// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.document;

import com.yahoo.language.process.StemMode;

import java.util.Locale;

/**
 * The stemming setting of a field. This describes how the search engine
 * should transform content of this field into base forms (stems) to increase
 * recall (find "car" when you search for "cars" etc.).
 *
 * @author bratseth
 */
public enum Stemming {

     /** No stemming */
    NONE("none"),

    /** Select shortest possible stem */
    SHORTEST("shortest"),

    /** Select the "best" stem alternative */
    BEST("best"),

    /** Index all stems, not the original */
    ALL_STEMS("all-stems"),

    /** Index all stems and the original */
    MULTIPLE("multiple");

    private final String name;

    /**
     * Returns the stemming object for the given string.
     * The legal stemming names are the stemming constants in any capitalization.
     *
     * @throws IllegalArgumentException if there is no stemming type with the given name
     */
    public static Stemming get(String stemmingName) {
        return switch (stemmingName.toLowerCase(Locale.ROOT)) {
            case "none" -> Stemming.NONE;
            case "shortest" -> Stemming.SHORTEST;
            case "best" -> Stemming.BEST;
            case "all-stems" -> Stemming.ALL_STEMS;
            case "multiple" -> Stemming.MULTIPLE;
            default -> throw new IllegalArgumentException("'" + stemmingName + "' is not a valid stemming setting");
        };
    }

    Stemming(String name) {
        this.name = name;
    }

    public String getName() { return name; }

    @Override
    public String toString() {
        return "stemming " + name;
    }

    public StemMode toStemMode() {
        return switch (this) {
            case NONE -> StemMode.NONE;
            case SHORTEST -> StemMode.SHORTEST;
            case BEST -> StemMode.BEST;
            case ALL_STEMS -> StemMode.ALL_STEMS;
            case MULTIPLE -> StemMode.ALL;
        };
    }

}
