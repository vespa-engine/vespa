// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.document;

import java.util.Locale;

/**
 * The tokens setting of the linguistics block of a field. This describes which tokens the
 * linguistics implementation should produce for the content of this field: The original token
 * only, one or more alternatives to it (stems, decompoundings, synonyms, ...), or both.
 * <p>
 * Each value is equivalent to a {@link Stemming} setting, and thereby to the stem mode carried
 * by the indexing script and the index-info stem command:
 * <pre>
 *     original                  Stemming.NONE       StemMode.NONE
 *     first-alternative         Stemming.BEST       StemMode.BEST
 *     alternatives              Stemming.ALL_STEMS  StemMode.ALL_STEMS
 *     original-and-alternatives Stemming.MULTIPLE   StemMode.ALL
 * </pre>
 * {@link Stemming#SHORTEST} is deliberately not expressible here, so a field which sets tokens
 * cannot ask for it: tokens takes precedence over the stemming setting of the same field.
 *
 * @author arnej27959
 */
public enum TokensMode {

    /** Produce the original token only */
    ORIGINAL("original", Stemming.NONE),

    /** Produce the "best" alternative to the original token */
    FIRST_ALTERNATIVE("first-alternative", Stemming.BEST),

    /** Produce all the alternatives to the original token, but not the original */
    ALTERNATIVES("alternatives", Stemming.ALL_STEMS),

    /** Produce all the alternatives to the original token, and the original */
    ORIGINAL_AND_ALTERNATIVES("original-and-alternatives", Stemming.MULTIPLE);

    private final String name;
    private final Stemming stemming;

    /**
     * Returns the tokens mode for the given string.
     * The legal names are the dashed names of the values: original, first-alternative, alternatives
     * and original-and-alternatives, in any capitalization.
     *
     * @throws IllegalArgumentException if there is no tokens mode with the given name
     */
    public static TokensMode get(String tokensName) {
        String name = tokensName.toLowerCase(Locale.ROOT);
        for (TokensMode mode : values())
            if (mode.name.equals(name)) return mode;
        throw new IllegalArgumentException("'" + tokensName + "' is not a valid tokens setting");
    }

    TokensMode(String name, Stemming stemming) {
        this.name = name;
        this.stemming = stemming;
    }

    public String getName() { return name; }

    @Override
    public String toString() {
        return "tokens " + getName();
    }

    /** Returns the equivalent stemming setting of this. */
    public Stemming toStemming() { return stemming; }

}
