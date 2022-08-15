// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query.parser;

import com.yahoo.prelude.query.Substring;

/**
 * A query token.
 *
 * @author bratseth
 */
public class Token {

    public enum Kind {
        EOF("<EOF>"),
        NUMBER("<NUMBER>"),
        WORD("<WORD>"),
        LETTER("<LETTER>"),
        DIGIT("<DIGIT>"),
        SPACE("\" \""),
        NOISE("<NOISE>"),
        LATINSIGN("<LATINSIGN>"),
        QUOTE("\"\\\"\""),
        MINUS("\"-\""),
        PLUS("\"+\""),
        DOT("\".\""),
        COMMA("\",\""),
        COLON("\":\""),
        LBRACE("\"(\""),
        RBRACE("\")\""),
        LSQUAREBRACKET("\"[\""),
        RSQUAREBRACKET("\"]\""),
        SEMICOLON("\";\""),
        GREATER("\">\""),
        SMALLER("\"<\""),
        EXCLAMATION("\"!\""),
        UNDERSCORE("\"_\""),
        HAT("\"^\""),
        STAR("\"*\""),
        DOLLAR("\"$\""),
        DEFAULT("");

        public final String image;

        private Kind(String image) {
            this.image = image;
        }
    }

    /** The raw substring causing this token, never null */
    public final Substring substring;

    public final Token.Kind kind;

    /** Lowercase image */
    public final String image;

    /** True if this is a <i>special token</i> */
    private final boolean special;

    /** Crates a token which fails to know its origin (as a substring). Do not use, except for testing. */
    public Token(Token.Kind kind, String image) {
        this(kind,image,false,null);
    }

    public Token(Token.Kind kind, String image, Substring substring) {
        this(kind,image,false,substring);
    }

    public Token(Token.Kind kind, String image, boolean special, Substring substring) {
        this.kind = kind;
        this.image = image;
        this.special = special;
        this.substring = substring;
    }

    /** Returns whether this is a <i>special token</i> */
    public boolean isSpecial() { return special; }

    /**
     * Returns the substring containing the image ins original form (including casing),
     * as well as all the text surrounding the token
     *
     * @return the image in original casing, never null
     */
    public Substring getSubstring() { return substring; }

    @Override
    public String toString() { return image; }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null) return false;
        if (object.getClass() != this.getClass()) return false;

        Token other = (Token) object;
        if (this.kind != other.kind) return false;
        if (!(this.image.equals(other.image))) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return image.hashCode() ^ kind.hashCode();
    }

}
