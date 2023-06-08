// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.process;

/**
 * An enumeration of token types.
 *
 * @author Mathias Mølster Lidal
 */
public enum TokenType {

    UNKNOWN(0),
    SPACE(1),
    PUNCTUATION(2),
    SYMBOL(3),
    ALPHABETIC(4),
    NUMERIC(5),
    INDEXABLE_SYMBOL(6),
    MARKER(255);

    private final int value;

    TokenType(int value) {
        this.value = value;
    }

    /** Returns an int code for this type */
    public int getValue() { return value; }

    /**
     * Marker for whether this type of token can be indexed for search.
     * Note that a Token can be excluded from an index, even though the token type marks
     * it as indexable.
     *
     * @see com.yahoo.language.process.Token#isIndexable()
     * @return whether this type of token can be indexed
     */
    public boolean isIndexable() {
        return switch (this) {
            case ALPHABETIC, NUMERIC, INDEXABLE_SYMBOL -> true;
            default -> false;
        };
    }

    /** Translates this from the int code representation returned from {@link #getValue} */
    public static TokenType valueOf(int value) {
        for (TokenType type : values()) {
            if (value == type.value) return type;
        }
        return UNKNOWN;
    }

}
