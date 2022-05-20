// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.document;

import com.yahoo.language.process.StemMode;

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

    /** select shortest possible stem */
    SHORTEST("shortest"),

    /** select the "best" stem alternative */
    BEST("best"),

    /** index multiple stems */
    MULTIPLE("multiple");

    private final String name;

    /**
     * Returns the stemming object for the given string.
     * The legal stemming names are the stemming constants in any capitalization.
     *
     * @throws IllegalArgumentException if there is no stemming type with the given name
     */
    public static Stemming get(String stemmingName) {
        try {
            return Stemming.valueOf(stemmingName.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("'" + stemmingName + "' is not a valid stemming setting");
        }
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
        switch(this) {
            case SHORTEST: return StemMode.SHORTEST;
            case MULTIPLE: return StemMode.ALL;
            case BEST : return StemMode.BEST;
            case NONE: return StemMode.NONE;
            default: throw new IllegalStateException("Inconvertible stem mode " + this);
        }
    }

}
