// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.predicate;

import com.yahoo.document.predicate.parser.PredicateLexer;
import com.yahoo.document.predicate.parser.PredicateParser;
import com.yahoo.text.Ascii;
import org.antlr.runtime.ANTLRStringStream;
import org.antlr.runtime.CommonTokenStream;
import org.antlr.runtime.RecognitionException;

import java.nio.charset.StandardCharsets;

/**
 * @author Simon Thoresen Hult
 */
public abstract class Predicate implements Cloneable {

    private final static char QUOTE_CHAR = '\'';
    private final static Ascii.Encoder ASCII_ENCODER = Ascii.newEncoder(StandardCharsets.UTF_8, QUOTE_CHAR);
    private final static Ascii.Decoder ASCII_DECODER = Ascii.newDecoder(StandardCharsets.UTF_8);

    @Override
    public Predicate clone() throws CloneNotSupportedException {
        return (Predicate)super.clone();
    }

    @Override
    public final String toString() {
        StringBuilder out = new StringBuilder();
        appendTo(out);
        return out.toString();
    }

    protected abstract void appendTo(StringBuilder out);

    protected static void appendQuotedTo(String str, StringBuilder out) {
        String encoded = asciiEncode(str);
        if (requiresQuote(encoded)) {
            out.append(QUOTE_CHAR).append(encoded).append(QUOTE_CHAR);
        } else {
            out.append(str);
        }
    }

    private static boolean requiresQuote(String str) {
        for (int i = 0, len = str.length(); i < len; i = str.offsetByCodePoints(i, 1)) {
            int c = str.codePointAt(i);
            if (c == Ascii.ESCAPE_CHAR || !Character.isLetterOrDigit(c)) {
                return true;
            }
        }
        return false;
    }

    public static String asciiEncode(String str) {
        return ASCII_ENCODER.encode(str);
    }

    public static String asciiDecode(String str) {
        return ASCII_DECODER.decode(str);
    }

    public static Predicate fromBinary(byte[] buf) {
        return BinaryFormat.decode(buf);
    }

    public static Predicate fromString(String str) {
        ANTLRStringStream input = new ANTLRStringStream(str);
        PredicateLexer lexer = new PredicateLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        PredicateParser parser = new PredicateParser(tokens);
        try {
            return parser.predicate();
        } catch (RecognitionException e) {
            throw new IllegalArgumentException(e);
        }
    }

}
