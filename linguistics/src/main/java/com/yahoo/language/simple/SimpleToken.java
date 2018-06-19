// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.simple;

import com.yahoo.language.process.Token;
import com.yahoo.language.process.TokenScript;
import com.yahoo.language.process.TokenType;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Mathias Mølster Lidal
 */
public class SimpleToken implements Token {

    private final List<Token> components = new ArrayList<>();
    private final String orig;
    private TokenType type = TokenType.UNKNOWN;
    private TokenScript script = TokenScript.UNKNOWN;
    private String tokenString = null;
    private boolean specialToken = false;
    private long offset = 0;

    public SimpleToken(String orig) {
        this.orig = orig;
    }

    @Override
    public String getOrig() {
        return orig;
    }

    @Override
    public int getNumStems() {
        return tokenString != null ? 1 : 0;
    }

    @Override
    public String getStem(int i) {
        return tokenString;
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

    public SimpleToken setTokenString(String str) {
        tokenString = str;
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
    public int hashCode() {
        return orig.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Token)) {
            return false;
        }
        Token rhs = (Token)obj;
        if (!getType().equals(rhs.getType())) {
            return false;
        }
        if (!equalsOpt(getOrig(), rhs.getOrig())) {
            return false;
        }
        if (getOffset() != rhs.getOffset()) {
            return false;
        }
        if (!equalsOpt(getScript(), rhs.getScript())) {
            return false;
        }
        if (!equalsOpt(getTokenString(), rhs.getTokenString())) {
            return false;
        }
        if (isSpecialToken() != rhs.isSpecialToken()) {
            return false;
        }
        if (getNumComponents() != rhs.getNumComponents()) {
            return false;
        }
        for (int i = 0, len = getNumComponents(); i < len; ++i) {
            if (!equalsOpt(getComponent(i), rhs.getComponent(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean equalsOpt(Object lhs, Object rhs) {
        if (lhs == null || rhs == null) {
            return lhs == rhs;
        }
        return lhs.equals(rhs);
    }

    @Override
    public String toString() {
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
        return getType().isIndexable() && (getOrig().length() > 0);
    }

}
