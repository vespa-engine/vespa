// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.simple;

import com.yahoo.language.process.Token;
import com.yahoo.language.process.TokenScript;
import com.yahoo.language.process.TokenType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author Mathias Mølster Lidal
 */
public class SimpleToken implements Token {

    private final List<Token> components = new ArrayList<>();
    private final String original;
    private TokenType type = TokenType.UNKNOWN;
    private TokenScript script = TokenScript.UNKNOWN;
    private String tokenString;
    private List<String> stems = null; // Any additional stems after tokenString
    private boolean specialToken = false;
    private long offset = 0;

    public SimpleToken(String original) {
        this(original, (String)null);
    }

    public SimpleToken(String original, String tokenString) {
        this.original = original;
        this.tokenString = tokenString;
    }

    /** Exposed as fromStems */
    private SimpleToken(String original, List<String> stems) {
        this.type = TokenType.ALPHABETIC; // Only type which may have stems
        this.original = original;
        this.tokenString = stems.get(0);
        this.stems = List.copyOf(stems.subList(1, stems.size()));
    }

    @Override
    public String getOrig() {
        return original;
    }

    @Override
    public int getNumStems() {
        return (tokenString != null ? 1 : 0) + (stems != null ? stems.size() : 0);
    }

    @Override
    public String getStem(int i) {
        if (i == 0)
            return tokenString;
        if (stems != null && i-1 < stems.size())
            return stems.get(i-1);
        return tokenString; // TODO Vespa 9: throw new IllegalArgumentException() instead
    }

    @Override
    public int getNumComponents() {
        return components.size();
    }

    @Override
    public Token getComponent(int i) {
        return components.get(i);
    }

    public SimpleToken addComponent(Token token) {
        components.add(token);
        return this;
    }

    @Override
    public String getTokenString() {
        return tokenString;
    }

    public SimpleToken setTokenString(String string) {
        tokenString = string;
        return this;
    }

    @Override
    public TokenType getType() {
        return type;
    }

    public SimpleToken setType(TokenType type) {
        this.type = type;
        return this;
    }

    @Override
    public TokenScript getScript() {
        return script;
    }

    public SimpleToken setScript(TokenScript script) {
        this.script = script;
        return this;
    }

    @Override
    public boolean isSpecialToken() {
        return specialToken;
    }

    public SimpleToken setSpecialToken(boolean specialToken) {
        this.specialToken = specialToken;
        return this;
    }

    @Override
    public long getOffset() {
        return offset;
    }

    public SimpleToken setOffset(long offset) {
        this.offset = offset;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Token other)) return false;

        if (getType() != other.getType()) return false;
        if (!Objects.equals(getOrig(), other.getOrig())) return false;
        if (getOffset() != other.getOffset()) return false;
        if (!Objects.equals(getScript(), other.getScript())) return false;
        if (!Objects.equals(getTokenString(), other.getTokenString())) return false;
        if (isSpecialToken() != other.isSpecialToken()) return false;
        if (getNumComponents() != other.getNumComponents()) return false;
        for (int i = 0, len = getNumComponents(); i < len; ++i) {
            if (!Objects.equals(getComponent(i), other.getComponent(i)))
                return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return original.hashCode();
    }

    @Override
    public String toString() {
        return "token '" + tokenString + "'" + ( ! tokenString.equals(original) ? " (original: " + original + ")" : "");
    }

    public String toDetailString() {
        return "token : " + getClass().getSimpleName() + " {\n" + toString(this, "    ") + "}";
    }

    private static String toString(Token token, String indent) {
        StringBuilder builder = new StringBuilder();
        builder.append(indent).append("components : {\n");
        for (int i = 0, len = token.getNumComponents(); i < len; ++i) {
            Token comp = token.getComponent(i);
            builder.append(indent).append("    [").append(i).append("] : ").append(comp.getClass().getSimpleName());
            builder.append(" {\n").append(toString(comp, indent + "        "));
            builder.append(indent).append("    }\n");
        }
        builder.append(indent).append("}\n");
        builder.append(indent).append("offset : ").append(token.getOffset()).append("\n");
        builder.append(indent).append("orig : ").append(quoteString(token.getOrig())).append("\n");
        builder.append(indent).append("script : ").append(token.getScript()).append("\n");
        builder.append(indent).append("special : ").append(token.isSpecialToken()).append("\n");
        builder.append(indent).append("token string : ").append(quoteString(token.getTokenString())).append("\n");
        builder.append(indent).append("type : ").append(token.getType()).append("\n");
        return builder.toString();
    }

    private static String quoteString(String str) {
        return str != null ? "'" + str + "'" : null;
    }

    @Override
    public boolean isIndexable() {
        return getType().isIndexable() && ( ! getOrig().isEmpty());
    }

    public static SimpleToken fromStems(String original, List<String> stems) {
        return new SimpleToken(original, stems);
    }

}
