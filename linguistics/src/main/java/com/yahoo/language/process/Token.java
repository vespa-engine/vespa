// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.process;

/**
 * A single token produced by the tokenizer.
 *
 * @author Mathias Mølster Lidal
 */
public interface Token {

    /** Returns the type of this token - word, space or punctuation etc. */
    TokenType getType();

    /** Returns the original form of this token */
    String getOrig();

    /** Returns the number of stem forms available for this token. */
    int getNumStems();

    /** Returns the stem at position i */
    String getStem(int i);

    /**
     * Returns the number of components, if this token is a compound word
     * (e.g. german "kommunikationsfehler". Otherwise, return 0
     *
     * @return number of components, or 0 if none
     */
    int getNumComponents();

    /** Returns a component token of this */
    Token getComponent(int i);

    /** Returns the offset position of this token */
    long getOffset();

    /** Returns the script of this token */
    TokenScript getScript();

    /**
     * Returns token string in a form suitable for indexing: The
     * most lowercased variant of the most processed token form available.
     * If called on a compound token this returns a lowercased form of the
     * entire word.
     *
     * @return token string value
     */
    String getTokenString();

    /** Returns whether this is an instance of a declared special token (e.g. c++) */
    boolean isSpecialToken();

    /** Whether this token should be indexed */
    boolean isIndexable();

}
